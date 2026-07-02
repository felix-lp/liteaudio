package app.liteaudio.media.transfer

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.PriorityTaskManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheWriter
import app.liteaudio.R
import app.liteaudio.core.StatusBus
import app.liteaudio.data.db.DownloadDao
import app.liteaudio.data.db.DownloadState
import app.liteaudio.data.db.TrackDao
import app.liteaudio.data.settings.SettingsRepository
import app.liteaudio.media.cache.CacheHolder
import app.liteaudio.media.playback.InfiniteRetryPolicy
import app.liteaudio.media.playback.PlayerController
import app.liteaudio.media.source.MediaSourceChain
import app.liteaudio.media.source.StreamUrlStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.net.SocketTimeoutException

/**
 * The single-connection scheduler. Exactly one CacheWriter runs at a time,
 * always on the highest-priority pending work:
 *
 *   1. finish caching the currently playing track;
 *   2. prefetch the next N tracks of the play queue;
 *   3. the top of the explicit download queue.
 *
 * Playback preempts everything via PriorityTaskManager: the writer's data
 * source throws PriorityTooLowException the moment the player wants bytes,
 * and the engine blocks until the channel is free again — resuming from the
 * cached position, so no byte is ever fetched twice.
 */
@UnstableApi
class TransferEngine(
    private val context: Context,
    private val chain: MediaSourceChain,
    private val cacheHolder: CacheHolder,
    private val urlStore: StreamUrlStore,
    private val trackDao: TrackDao,
    private val downloadDao: DownloadDao,
    private val controller: PlayerController,
    private val settings: SettingsRepository,
    private val statusBus: StatusBus,
    private val scope: CoroutineScope,
) {
    sealed interface Task {
        val videoId: String

        data class CompleteCurrent(override val videoId: String) : Task
        data class Prefetch(override val videoId: String, val queueDistance: Int) : Task
        data class Download(override val videoId: String, val position: Int, val ofTotal: Int) : Task
    }

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** videoIds that failed non-retryably this session — don't hot-loop on them */
    private val sessionSkip = mutableSetOf<String>()

    fun start() {
        val playbackShape = controller.state
            .map { s ->
                PlaybackShape(
                    current = s.currentVideoId,
                    upcoming = if (s.currentIndex >= 0) {
                        s.queue.drop(s.currentIndex + 1).map { it.videoId }
                    } else emptyList(),
                )
            }
            .distinctUntilChanged()

        val downloadShape = downloadDao.observeQueue()
            .map { list -> list.map { DownloadShape(it.videoId, it.state, it.queuePosition) } }
            .distinctUntilChanged()

        val prefetchCount = settings.settings.map { it.prefetchCount }.distinctUntilChanged()

        scope.launch(Dispatchers.IO) {
            combine(playbackShape, downloadShape, prefetchCount) { p, d, n ->
                buildCandidates(p, d, n)
            }
                .distinctUntilChanged()
                .collectLatest { candidates ->
                    runCandidates(candidates)
                }
        }

        // round-robin retry: errored downloads re-enter the queue periodically
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(90_000)
                downloadDao.resumeErrored()
            }
        }
    }

    private data class PlaybackShape(val current: String?, val upcoming: List<String>)
    private data class DownloadShape(val videoId: String, val state: DownloadState, val pos: Int)

    private fun buildCandidates(
        p: PlaybackShape,
        downloads: List<DownloadShape>,
        prefetchN: Int,
    ): List<Task> {
        val tasks = mutableListOf<Task>()
        val seen = mutableSetOf<String>()
        p.current?.let {
            tasks += Task.CompleteCurrent(it)
            seen += it
        }
        p.upcoming.take(prefetchN).forEachIndexed { i, id ->
            if (seen.add(id)) tasks += Task.Prefetch(id, i + 1)
        }
        val queued = downloads.filter { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING }
        queued.forEachIndexed { i, d ->
            if (seen.add(d.videoId)) tasks += Task.Download(d.videoId, i + 1, queued.size)
        }
        return tasks
    }

    private suspend fun runCandidates(candidates: List<Task>) {
        try {
            for (task in candidates) {
                if (task.videoId in sessionSkip) continue
                val didWork = runCatching { transfer(task) }.getOrElse { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    false
                }
                // after finishing (or skipping) one task, re-evaluation happens
                // naturally: DB/state changes re-emit candidates via collectLatest
                if (didWork) continue
            }
        } finally {
            _active.value = false
            statusBus.clear(StatusBus.Key.Transfer)
        }
    }

    /** Returns true when the task is complete (cached fully / marked). */
    private suspend fun transfer(task: Task): Boolean {
        val videoId = task.videoId
        var attempt = 0

        while (true) {
            // resolve fresh URL (also fixes itag + contentLength on first touch)
            val resolved = try {
                urlStore.resolve(videoId, forDownload = task is Task.Download)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val err = app.liteaudio.extractor.ExtractorError.from(e)
                if (!err.retryable) {
                    markUnavailable(task, err)
                    return true
                }
                attempt++
                backoff(attempt)
                continue
            }

            val track = trackDao.get(videoId)
            val total = resolved.contentLength ?: track?.contentLength
            val key = resolved.cacheKey

            if (total != null && cacheHolder.isFullyCached(key, total)) {
                if (task is Task.Download) downloadDao.markDone(videoId, System.currentTimeMillis(), total)
                return true
            }

            if (task is Task.Download) {
                downloadDao.setState(videoId, DownloadState.RUNNING)
                cacheHolder.evictor.pin(key)
            }
            _active.value = true
            ensureService()

            val title = track?.title ?: videoId
            val startBytes = cacheHolder.cachedBytes(key, total)
            val speed = SpeedMeter()

            val progressListener = CacheWriter.ProgressListener { requestLength, bytesCached, newBytes ->
                speed.add(newBytes)
                publishStatus(task, title, bytesCached, requestLength, speed.bytesPerSec())
                if (task is Task.Download) {
                    throttledProgress(videoId, bytesCached, requestLength.takeIf { it != C.LENGTH_UNSET.toLong() })
                }
            }

            val dataSpec = DataSpec.Builder()
                .setUri(resolved.url)
                .setKey(key)
                .build()

            try {
                runInterruptible(Dispatchers.IO) {
                    CacheWriter(
                        chain.newTransferCacheDataSource(),
                        dataSpec,
                        null,
                        progressListener,
                    ).cache()
                }
                // fully written
                val finalBytes = cacheHolder.cachedBytes(key, total)
                if (task is Task.Download) {
                    downloadDao.markDone(videoId, System.currentTimeMillis(), finalBytes)
                }
                if (total == null) {
                    trackDao.setSelectedStream(
                        videoId,
                        resolved.itag,
                        track?.selectedMime,
                        track?.selectedBitrate,
                        finalBytes,
                    )
                }
                return true
            } catch (e: PriorityTaskManager.PriorityTooLowException) {
                // playback took the channel: wait politely, then resume from cache
                statusBus.set(
                    StatusBus.Key.Transfer,
                    StatusBus.Entry(
                        text = StatusBus.Text.Res(R.string.download_state_waiting_channel),
                        severity = StatusBus.Severity.Info,
                    ),
                )
                runInterruptible(Dispatchers.IO) {
                    chain.priorityTaskManager.proceed(C.PRIORITY_DOWNLOAD)
                }
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                if (e.responseCode == 403 || e.responseCode == 410) {
                    urlStore.invalidate(key) // next loop re-resolves
                } else {
                    attempt++
                    backoff(attempt)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: InterruptedException) {
                throw kotlinx.coroutines.CancellationException("transfer interrupted")
            } catch (e: Exception) {
                attempt++
                if (task is Task.Download && attempt >= MAX_DOWNLOAD_ATTEMPTS) {
                    downloadDao.setState(videoId, DownloadState.ERROR, e.errorMarker())
                    return true // move on; round-robin sweep will retry later
                }
                backoff(attempt)
            }
        }
    }

    private fun Exception.errorMarker(): String = when (this) {
        is SocketTimeoutException -> "TIMEOUT"
        is HttpDataSource.InvalidResponseCodeException -> "HTTP_$responseCode"
        else -> "NETWORK"
    }

    private suspend fun markUnavailable(task: Task, err: app.liteaudio.extractor.ExtractorError) {
        trackDao.setUnavailable(task.videoId, err.marker)
        if (task is Task.Download) {
            downloadDao.setState(task.videoId, DownloadState.ERROR, err.marker)
        } else {
            sessionSkip += task.videoId
        }
    }

    private suspend fun backoff(attempt: Int) {
        val delayMs = InfiniteRetryPolicy.backoffMs(attempt)
        statusBus.set(
            StatusBus.Key.Transfer,
            StatusBus.Entry(
                text = StatusBus.Text.Res(R.string.status_waiting_network),
                severity = StatusBus.Severity.Warning,
                countdownToElapsedRealtime = SystemClock.elapsedRealtime() + delayMs,
            ),
        )
        delay(delayMs)
    }

    private fun publishStatus(task: Task, title: String, bytes: Long, total: Long, bytesPerSec: Long) {
        val fraction = if (total > 0 && total != C.LENGTH_UNSET.toLong()) {
            (bytes.toFloat() / total).coerceIn(0f, 1f)
        } else null
        val text = when (task) {
            is Task.CompleteCurrent -> StatusBus.Text.Res(R.string.status_completing_current)
            is Task.Prefetch -> StatusBus.Text.Res(R.string.status_prefetching, listOf(title))
            is Task.Download -> StatusBus.Text.Res(
                R.string.status_downloading,
                listOf(task.position, task.ofTotal, title),
            )
        }
        statusBus.set(
            StatusBus.Key.Transfer,
            StatusBus.Entry(
                text = text,
                severity = StatusBus.Severity.Progress,
                progress = fraction,
                indeterminate = fraction == null,
                detail = StatusBus.Text.Literal(formatSpeed(bytesPerSec)),
            ),
        )
    }

    private var lastProgressWrite = 0L
    private fun throttledProgress(videoId: String, bytes: Long, total: Long?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProgressWrite < 1_000) return
        lastProgressWrite = now
        scope.launch(Dispatchers.IO) {
            downloadDao.setProgress(videoId, bytes, total)
        }
    }

    private fun ensureService() {
        runCatching {
            context.startForegroundService(Intent(context, TransferService::class.java))
        }
    }

    private class SpeedMeter {
        private var windowStart = SystemClock.elapsedRealtime()
        private var windowBytes = 0L
        private var lastRate = 0L

        fun add(bytes: Long) {
            windowBytes += bytes
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - windowStart
            if (elapsed >= 3_000) {
                lastRate = windowBytes * 1000 / elapsed
                windowStart = now
                windowBytes = 0
            }
        }

        fun bytesPerSec(): Long = lastRate
    }

    companion object {
        const val MAX_DOWNLOAD_ATTEMPTS = 3

        fun formatSpeed(bytesPerSec: Long): String = when {
            bytesPerSec <= 0 -> ""
            bytesPerSec < 1024 -> "$bytesPerSec Б/с"
            bytesPerSec < 1024 * 1024 -> "%.1f КБ/с".format(bytesPerSec / 1024f)
            else -> "%.1f МБ/с".format(bytesPerSec / (1024f * 1024f))
        }
    }
}
