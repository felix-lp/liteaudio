package app.liteaudio.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import app.liteaudio.R
import app.liteaudio.data.repo.CacheStatus
import app.liteaudio.di.AppGraph
import app.liteaudio.ui.design.components.LiteIconButton
import app.liteaudio.ui.design.components.LiteIcons
import app.liteaudio.ui.design.components.LiteSlider
import app.liteaudio.ui.design.components.LiteText
import app.liteaudio.ui.design.components.Panel
import app.liteaudio.ui.design.components.formatDuration
import app.liteaudio.ui.design.theme.Lite
import app.liteaudio.ui.nav.Routes
import coil3.compose.AsyncImage

/** Full player: artwork, two-layer seek, transport, stream info line. */
@Composable
fun PlayerScreen(graph: AppGraph, navController: NavHostController) {
    val state by graph.playerController.state.collectAsState()
    val colors = Lite.colors
    val current = state.queue.getOrNull(state.currentIndex)
    val cacheStatus by (
        current?.let { graph.cacheStatus.observe(it.videoId) }
            ?: kotlinx.coroutines.flow.flowOf(null)
        ).collectAsState(initial = null)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(Lite.dimens.spacing4),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LiteIconButton(icon = LiteIcons.ChevronDown, onClick = { navController.popBackStack() })
            Spacer(Modifier.weight(1f))
            LiteIconButton(
                icon = LiteIcons.Queue,
                onClick = { navController.navigate(Routes.QUEUE) },
                raised = true,
            )
        }
        Spacer(Modifier.height(Lite.dimens.spacing4))

        Panel(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            AsyncImage(
                model = current?.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(Lite.dimens.spacing4))

        LiteText(
            text = current?.title ?: "",
            style = Lite.type.title,
            modifier = Modifier.basicMarquee(),
        )
        LiteText(
            text = current?.uploader ?: "",
            style = Lite.type.secondary,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(Lite.dimens.spacing4))

        // two-layer seek: position + cached bytes
        val duration = state.durationMs
        val positionFraction = if (duration > 0) state.positionMs.toFloat() / duration else 0f
        val cacheFraction = when (val cs = cacheStatus) {
            is CacheStatus.Full -> 1f
            is CacheStatus.Partial -> cs.fraction
            else -> if (duration > 0) state.bufferedMs.toFloat() / duration else 0f
        }
        LiteSlider(
            position = positionFraction,
            cacheFill = cacheFraction,
            onSeek = { f ->
                if (duration > 0) {
                    if (f > cacheFraction) {
                        graph.statusBus.toast(
                            app.liteaudio.core.StatusBus.Text.Res(R.string.status_beyond_buffer),
                            app.liteaudio.core.StatusBus.Severity.Info,
                        )
                    }
                    graph.playerController.seekTo((duration * f).toLong())
                }
            },
        )
        Row(Modifier.fillMaxWidth()) {
            LiteText(
                formatDuration((state.positionMs / 1000).toInt()),
                style = Lite.type.mono,
                color = colors.textTertiary,
            )
            Spacer(Modifier.weight(1f))
            LiteText(
                formatDuration((duration / 1000).toInt()),
                style = Lite.type.mono,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.height(Lite.dimens.spacing3))

        // transport
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiteIconButton(
                icon = LiteIcons.Shuffle,
                onClick = { graph.playerController.toggleShuffle() },
                tint = if (state.shuffleOn) colors.accent else colors.textSecondary,
            )
            Spacer(Modifier.size(Lite.dimens.spacing3))
            LiteIconButton(
                icon = LiteIcons.Prev,
                onClick = { graph.playerController.previous() },
                size = 48.dp,
                iconSize = 26.dp,
                raised = true,
            )
            Spacer(Modifier.size(Lite.dimens.spacing3))
            LiteIconButton(
                icon = if (state.isPlaying) LiteIcons.Pause else LiteIcons.Play,
                onClick = { graph.playerController.playPause() },
                size = 64.dp,
                iconSize = 32.dp,
                raised = true,
                tint = colors.accent,
            )
            Spacer(Modifier.size(Lite.dimens.spacing3))
            LiteIconButton(
                icon = LiteIcons.Next,
                onClick = { graph.playerController.next() },
                size = 48.dp,
                iconSize = 26.dp,
                raised = true,
            )
            Spacer(Modifier.size(Lite.dimens.spacing3))
            Box {
                LiteIconButton(
                    icon = LiteIcons.Repeat,
                    onClick = { graph.playerController.cycleRepeat() },
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) colors.accent else colors.textSecondary,
                )
                if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                    LiteText(
                        "1",
                        style = Lite.type.caption,
                        color = colors.accent,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
        Spacer(Modifier.height(Lite.dimens.spacing4))

        // stream data line: source + cached share (buffering lives in the strip)
        val sourceInfo = when (val cs = cacheStatus) {
            is CacheStatus.Full -> stringResource(R.string.player_source_cache)
            is CacheStatus.Partial ->
                "${stringResource(R.string.player_source_network)} · ${(cs.fraction * 100).toInt()}%"
            else -> stringResource(R.string.player_source_network)
        }
        LiteText(
            text = if (current != null) sourceInfo else "",
            style = Lite.type.caption,
            color = colors.textTertiary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
