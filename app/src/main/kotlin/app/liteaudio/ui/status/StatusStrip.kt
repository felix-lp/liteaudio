package app.liteaudio.ui.status

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.liteaudio.R
import app.liteaudio.core.NetworkMonitor
import app.liteaudio.core.StatusBus
import app.liteaudio.core.rank
import app.liteaudio.ui.design.components.LiteDivider
import app.liteaudio.ui.design.components.LiteIcon
import app.liteaudio.ui.design.components.LiteIcons
import app.liteaudio.ui.design.components.LiteText
import app.liteaudio.ui.design.components.ProgressStrip
import app.liteaudio.ui.design.components.liteClickable
import app.liteaudio.ui.design.theme.Lite
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp

@Composable
fun statusText(text: StatusBus.Text): String = when (text) {
    is StatusBus.Text.Literal -> text.value
    is StatusBus.Text.Res -> stringResource(text.id, *text.args.toTypedArray())
}

/**
 * The always-visible strip under the system status bar.
 * Shows the highest-priority live status; tap to expand all active slots.
 */
@Composable
fun StatusStrip(
    slots: Map<StatusBus.Key, StatusBus.Entry>,
    network: NetworkMonitor.State,
    modifier: Modifier = Modifier,
) {
    val colors = Lite.colors
    var expanded by remember { mutableStateOf(false) }

    val headline = slots.values.minByOrNull { it.rank() }

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.background)
            .liteClickable { if (slots.isNotEmpty()) expanded = !expanded },
    ) {
        AnimatedContent(
            targetState = headline,
            transitionSpec = {
                (slideInVertically { it } togetherWith slideOutVertically { -it })
            },
            label = "status",
        ) { entry ->
            if (entry == null) {
                IdleLine(network)
            } else {
                StatusLine(entry)
            }
        }
        if (expanded && slots.size > 1) {
            LiteDivider()
            slots.entries
                .sortedBy { it.value.rank() }
                .drop(1)
                .forEach { (_, entry) -> StatusLine(entry) }
        }
        LiteDivider()
    }
}

@Composable
private fun IdleLine(network: NetworkMonitor.State) {
    val colors = Lite.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(Lite.dimens.statusStripHeight)
            .padding(horizontal = Lite.dimens.spacing3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (dotColor, label) = when (network) {
            is NetworkMonitor.State.Online -> colors.ok to stringResource(R.string.status_idle)
            NetworkMonitor.State.Offline -> colors.textTertiary to stringResource(R.string.status_offline)
        }
        Box(
            Modifier
                .size(6.dp)
                .background(dotColor, androidx.compose.foundation.shape.CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        LiteText(label, style = Lite.type.caption, color = colors.textTertiary)
    }
}

@Composable
private fun StatusLine(entry: StatusBus.Entry) {
    val colors = Lite.colors
    val tint = when (entry.severity) {
        StatusBus.Severity.Error -> colors.error
        StatusBus.Severity.Warning -> colors.warning
        StatusBus.Severity.Progress -> colors.accent
        StatusBus.Severity.Info -> colors.textSecondary
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(Lite.dimens.statusStripHeight)
                .padding(horizontal = Lite.dimens.spacing3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.severity == StatusBus.Severity.Error) {
                LiteIcon(LiteIcons.Error, tint = tint, size = 12.dp)
                Spacer(Modifier.width(6.dp))
            }
            LiteText(
                text = statusText(entry.text),
                style = Lite.type.caption,
                color = tint,
                modifier = Modifier.weight(1f),
            )
            entry.countdownToElapsedRealtime?.let { target ->
                CountdownLabel(target)
            }
            entry.action?.let { action ->
                Spacer(Modifier.width(8.dp))
                LiteText(
                    text = statusText(action.label),
                    style = Lite.type.caption,
                    color = colors.accent,
                    modifier = Modifier.liteClickable { action.run() },
                )
            }
        }
        if (entry.indeterminate || entry.progress != null) {
            ProgressStrip(
                progress = if (entry.indeterminate) null else entry.progress,
                height = 2.dp,
                color = tint,
            )
        }
    }
}

@Composable
private fun CountdownLabel(targetElapsedRealtime: Long) {
    var secondsLeft by remember { mutableLongStateOf(0L) }
    LaunchedEffect(targetElapsedRealtime) {
        while (true) {
            val left = (targetElapsedRealtime - SystemClock.elapsedRealtime()) / 1000
            secondsLeft = left.coerceAtLeast(0)
            if (left <= 0) break
            delay(250)
        }
    }
    if (secondsLeft > 0) {
        LiteText(
            text = stringResource(R.string.status_retry_in, secondsLeft),
            style = Lite.type.mono,
            color = Lite.colors.textSecondary,
        )
    }
}
