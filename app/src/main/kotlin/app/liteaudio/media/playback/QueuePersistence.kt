package app.liteaudio.media.playback

import androidx.media3.common.util.UnstableApi
import app.liteaudio.data.db.PlayQueueEntity
import app.liteaudio.data.db.QueueDao
import app.liteaudio.data.db.QueueStateEntity
import app.liteaudio.data.db.TrackDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persists the play queue + position so a killed process resumes exactly
 * where it stopped: same queue, same track, same second.
 */
@UnstableApi
class QueuePersistence(
    private val controller: PlayerController,
    private val queueDao: QueueDao,
    private val trackDao: TrackDao,
    private val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            controller.state
                .debounce(1_000)
                .collect { state ->
                    if (state.queue.isEmpty()) return@collect
                    queueDao.replaceQueue(
                        state.queue.mapIndexed { i, item ->
                            PlayQueueEntity(
                                position = i,
                                videoId = item.videoId,
                                addedBy = item.source,
                                sourcePlaylistId = item.sourcePlaylistId,
                            )
                        },
                    )
                    queueDao.saveState(
                        QueueStateEntity(
                            currentPosition = state.currentIndex.coerceAtLeast(0),
                            positionMs = state.positionMs,
                            shuffleOn = state.shuffleOn,
                            repeatMode = state.repeatMode,
                        ),
                    )
                }
        }
    }

    /** Restore on app start; returns true when a queue was restored. */
    suspend fun restore(): Boolean {
        val queue = queueDao.getQueue()
        if (queue.isEmpty()) return false
        val state = queueDao.getState() ?: QueueStateEntity()
        val tracks = trackDao.getAll(queue.map { it.videoId }).associateBy { it.videoId }
        val items = queue.sortedBy { it.position }.mapNotNull { q ->
            val t = tracks[q.videoId] ?: return@mapNotNull null
            PlayerController.QueueItem(
                videoId = t.videoId,
                title = t.title,
                uploader = t.uploader,
                thumbnailUrl = t.thumbnailUrl,
                source = q.addedBy,
                sourcePlaylistId = q.sourcePlaylistId,
            )
        }
        if (items.isEmpty()) return false
        withContext(Dispatchers.Main) {
            controller.restore(
                queue = items,
                index = state.currentPosition,
                positionMs = state.positionMs,
                shuffle = state.shuffleOn,
                repeat = state.repeatMode,
            )
        }
        return true
    }
}
