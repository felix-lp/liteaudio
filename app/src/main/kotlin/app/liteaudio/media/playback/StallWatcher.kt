package app.liteaudio.media.playback

import androidx.media3.common.util.UnstableApi
import app.liteaudio.R
import app.liteaudio.core.StatusBus
import app.liteaudio.data.repo.CacheStatus
import app.liteaudio.data.repo.CacheStatusRepository
import app.liteaudio.data.db.TrackDao
import app.liteaudio.data.settings.NetworkDeathBehavior
import app.liteaudio.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Watches for a starved buffer. Default: wait forever (the retry policy keeps
 * hammering and the strip shows the countdown). Optional mode: hop to the
 * nearest fully-cached track in the queue and keep the music going.
 */
@UnstableApi
class StallWatcher(
    private val controller: PlayerController,
    private val cacheStatus: CacheStatusRepository,
    private val trackDao: TrackDao,
    private val settings: SettingsRepository,
    private val statusBus: StatusBus,
    scope: CoroutineScope,
) {
    /** how long a buffering state must persist before we call it a stall */
    private val stallThresholdMs = 25_000L

    init {
        scope.launch(Dispatchers.Main) {
            var bufferingSince = 0L
            var lastBuffered = 0L
            while (isActive) {
                val s = controller.state.value
                if (s.isBuffering && s.queue.isNotEmpty()) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (bufferingSince == 0L || s.bufferedMs != lastBuffered) {
                        // (re)arm whenever bytes actually move
                        bufferingSince = now
                        lastBuffered = s.bufferedMs
                    }
                    updateBufferingStatus(s)
                    val stalled = now - bufferingSince > stallThresholdMs
                    if (stalled &&
                        settings.settings.value.onNetworkDeath == NetworkDeathBehavior.SKIP_TO_CACHED
                    ) {
                        bufferingSince = 0L
                        skipToNearestCached(s)
                    }
                } else {
                    bufferingSince = 0L
                    if (!s.isBuffering) statusBus.clear(StatusBus.Key.Playback)
                }
                delay(1_000)
            }
        }
    }

    private fun updateBufferingStatus(s: PlayerController.PlayerUiState) {
        val percent = if (s.durationMs > 0) {
            (s.bufferedMs * 100 / s.durationMs).toInt().coerceIn(0, 100)
        } else 0
        statusBus.set(
            StatusBus.Key.Playback,
            StatusBus.Entry(
                text = StatusBus.Text.Res(R.string.status_buffering, listOf(percent)),
                severity = StatusBus.Severity.Progress,
                progress = percent / 100f,
            ),
        )
    }

    private suspend fun skipToNearestCached(s: PlayerController.PlayerUiState) {
        val startFrom = (s.currentIndex + 1).coerceAtLeast(0)
        val order = (startFrom until s.queue.size) + (0 until s.currentIndex.coerceAtLeast(0))
        val target = withContext(Dispatchers.IO) {
            order.firstOrNull { idx ->
                val track = trackDao.get(s.queue[idx].videoId) ?: return@firstOrNull false
                cacheStatus.statusOf(track) is CacheStatus.Full
            }
        } ?: return
        withContext(Dispatchers.Main) { controller.skipTo(target) }
    }
}
