package app.liteaudio.media.playback

import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import app.liteaudio.R
import app.liteaudio.core.StatusBus

/**
 * "Connection timeouts do not exist": every load error is retried forever with
 * bounded exponential backoff. Each scheduled retry surfaces on the status
 * strip with a live countdown.
 */
@UnstableApi
class InfiniteRetryPolicy(
    private val statusBus: StatusBus,
) : LoadErrorHandlingPolicy {

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
    ): LoadErrorHandlingPolicy.FallbackSelection? = null

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val delay = backoffMs(loadErrorInfo.errorCount)
        statusBus.set(
            StatusBus.Key.Network,
            StatusBus.Entry(
                text = StatusBus.Text.Res(R.string.status_waiting_network),
                severity = StatusBus.Severity.Warning,
                countdownToElapsedRealtime = SystemClock.elapsedRealtime() + delay,
            ),
        )
        return delay
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = Int.MAX_VALUE

    companion object {
        fun backoffMs(errorCount: Int): Long {
            val exp = (errorCount - 1).coerceIn(0, 5) // 2s..64s cap below
            return (2_000L shl exp).coerceAtMost(60_000L)
        }
    }
}
