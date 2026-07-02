package app.liteaudio.media.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PriorityTaskManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.liteaudio.core.StatusBus
import app.liteaudio.data.db.QueueItemSource
import app.liteaudio.media.source.MediaSourceChain
import app.liteaudio.media.source.TrackUri
import app.liteaudio.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * App-scoped owner of the ExoPlayer (single process, main-thread access).
 * PlaybackService wraps this player in a MediaSession for background playback
 * and the system notification.
 */
@UnstableApi
class PlayerController(
    private val context: Context,
    chain: MediaSourceChain,
    statusBus: StatusBus,
    private val scope: CoroutineScope,
) {
    data class QueueItem(
        val videoId: String,
        val title: String,
        val uploader: String,
        val thumbnailUrl: String?,
        val source: QueueItemSource,
        val sourcePlaylistId: Long? = null,
    )

    data class PlayerUiState(
        val currentVideoId: String? = null,
        val currentIndex: Int = -1,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val positionMs: Long = 0,
        val bufferedMs: Long = 0,
        val durationMs: Long = 0,
        val shuffleOn: Boolean = false,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val queue: List<QueueItem> = emptyList(),
    )

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(chain.playbackFactory())
                .setLoadErrorHandlingPolicy(InfiniteRetryPolicy(statusBus)),
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 60_000,
                    /* maxBufferMs = */ 600_000,
                    /* bufferForPlaybackMs = */ 4_000,
                    /* bufferForPlaybackAfterRebufferMs = */ 8_000,
                )
                .setTargetBufferBytes(32 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .setBackBuffer(0, false)
                .build(),
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .also { it.setPriorityTaskManager(chain.priorityTaskManager) }

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** Queue metadata parallel to the player's timeline, index-aligned. */
    private var items: MutableList<QueueItem> = mutableListOf()

    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                pushState()
            }
        })
        // position ticker while UI needs it
        scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
                    pushState()
                }
                delay(500)
            }
        }
    }

    private fun pushState() {
        val index = player.currentMediaItemIndex
        _state.value = PlayerUiState(
            currentVideoId = player.currentMediaItem?.mediaId,
            currentIndex = index,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            bufferedMs = player.bufferedPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0,
            shuffleOn = shuffleOn,
            repeatMode = player.repeatMode,
            queue = items.toList(),
        )
    }

    private var shuffleOn = false
    private var shuffleSeed = System.currentTimeMillis()

    // ---- queue operations (Spotify semantics) ----

    /** Replace the queue with a playlist context, starting at [startIndex]. */
    fun playContext(tracks: List<QueueItem>, startIndex: Int, shuffled: Boolean = false) {
        shuffleOn = shuffled
        originalOrder = tracks.mapIndexed { i, q -> q.videoId to i }.toMap()
        var ordered = tracks
        var start = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        if (shuffled && tracks.isNotEmpty()) {
            shuffleSeed = System.currentTimeMillis()
            val first = tracks[start]
            ordered = listOf(first) + (tracks - first).shuffled(kotlin.random.Random(shuffleSeed))
            start = 0
        }
        items = ordered.toMutableList()
        player.setMediaItems(ordered.map { it.toMediaItem() }, start, 0)
        prepareAndPlay()
    }

    /** Insert right after the current track (manual queue block). */
    fun playNext(item: QueueItem) {
        val insertAt = insertionIndexForNext()
        items.add(insertAt, item.copy(source = QueueItemSource.USER_NEXT))
        player.addMediaItem(insertAt, item.toMediaItem())
        ensurePrepared()
    }

    /** Append to the end of the manual queue block. */
    fun enqueue(item: QueueItem) {
        val insertAt = endOfManualBlock()
        items.add(insertAt, item.copy(source = QueueItemSource.USER_LAST))
        player.addMediaItem(insertAt, item.toMediaItem())
        ensurePrepared()
    }

    private fun insertionIndexForNext(): Int =
        (player.currentMediaItemIndex + 1).coerceAtMost(items.size)

    private fun endOfManualBlock(): Int {
        var i = player.currentMediaItemIndex + 1
        while (i < items.size && items[i].source != QueueItemSource.PLAYLIST) i++
        return i
    }

    fun removeAt(index: Int) {
        if (index !in items.indices || index == player.currentMediaItemIndex) return
        items.removeAt(index)
        player.removeMediaItem(index)
        pushState()
    }

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
        player.moveMediaItem(from, to)
        pushState()
    }

    fun skipTo(index: Int) {
        if (index !in items.indices) return
        player.seekTo(index, 0)
        prepareAndPlay()
    }

    fun toggleShuffle() {
        shuffleOn = !shuffleOn
        val current = player.currentMediaItemIndex
        if (items.isEmpty() || current !in items.indices) {
            pushState()
            return
        }
        // reshuffle/restore only the upcoming PLAYLIST block; manual items stay
        val manualEnd = endOfManualBlock()
        val upcoming = items.subList(manualEnd, items.size).toList()
        val reordered = if (shuffleOn) {
            shuffleSeed = System.currentTimeMillis()
            upcoming.shuffled(kotlin.random.Random(shuffleSeed))
        } else {
            upcoming.sortedBy { original -> originalOrder[original.videoId] ?: Int.MAX_VALUE }
        }
        for (i in items.size - 1 downTo manualEnd) {
            items.removeAt(i)
            player.removeMediaItem(i)
        }
        reordered.forEach { qi ->
            items.add(qi)
            player.addMediaItem(qi.toMediaItem())
        }
        pushState()
    }

    /** original positions for un-shuffling, captured on playContext */
    private var originalOrder: Map<String, Int> = emptyMap()

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        pushState()
    }

    fun playPause() {
        if (player.isPlaying) player.pause() else prepareAndPlay()
    }

    fun next() = player.seekToNextMediaItem()
    fun previous() {
        if (player.currentPosition > 3_000) player.seekTo(0) else player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    private fun ensurePrepared() {
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        pushState()
    }

    private fun prepareAndPlay() {
        startService()
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
        pushState()
    }

    private fun startService() {
        runCatching {
            context.startForegroundService(Intent(context, PlaybackService::class.java))
        }
    }

    /** Restore a persisted queue without starting playback. */
    fun restore(queue: List<QueueItem>, index: Int, positionMs: Long, shuffle: Boolean, repeat: Int) {
        if (queue.isEmpty()) return
        items = queue.toMutableList()
        originalOrder = queue.mapIndexed { i, q -> q.videoId to i }.toMap()
        shuffleOn = shuffle
        player.repeatMode = repeat
        player.setMediaItems(
            queue.map { it.toMediaItem() },
            index.coerceIn(0, queue.size - 1),
            positionMs.coerceAtLeast(0),
        )
        pushState()
    }

    fun captureOriginalOrder(tracks: List<QueueItem>) {
        originalOrder = tracks.mapIndexed { i, q -> q.videoId to i }.toMap()
    }

    private fun QueueItem.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(TrackUri.forVideo(videoId))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(uploader)
                .setArtworkUri(thumbnailUrl?.let { android.net.Uri.parse(it) })
                .build(),
        )
        .build()
}
