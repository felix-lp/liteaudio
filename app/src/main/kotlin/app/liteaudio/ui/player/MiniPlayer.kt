package app.liteaudio.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import app.liteaudio.di.AppGraph
import app.liteaudio.ui.design.components.LiteIconButton
import app.liteaudio.ui.design.components.LiteIcons
import app.liteaudio.ui.design.components.LiteText
import app.liteaudio.ui.design.components.ProgressStrip
import app.liteaudio.ui.design.components.liteClickable
import app.liteaudio.ui.design.theme.Lite
import coil3.compose.AsyncImage

/** One-line mini-player above the tab bar; hairline two-layer progress below. */
@Composable
fun MiniPlayer(graph: AppGraph, onOpen: () -> Unit) {
    val state by graph.playerController.state.collectAsState()
    if (state.queue.isEmpty()) return

    val colors = Lite.colors
    val current = state.queue.getOrNull(state.currentIndex)

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.bevelTop),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(Lite.dimens.miniPlayerHeight)
                .background(
                    Brush.verticalGradient(
                        0f to colors.surfaceRaised,
                        1f to colors.surface,
                    ),
                )
                .liteClickable(onClick = onOpen)
                .padding(horizontal = Lite.dimens.spacing3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = current?.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surfacePressed),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Spacer(Modifier.width(Lite.dimens.spacing3))
            Column(Modifier.weight(1f)) {
                LiteText(
                    text = current?.title ?: "",
                    style = Lite.type.body,
                    modifier = Modifier.basicMarquee(),
                )
                LiteText(
                    text = current?.uploader ?: "",
                    style = Lite.type.caption,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.width(Lite.dimens.spacing2))
            LiteIconButton(
                icon = if (state.isPlaying) LiteIcons.Pause else LiteIcons.Play,
                onClick = { graph.playerController.playPause() },
                raised = true,
            )
            Spacer(Modifier.width(Lite.dimens.spacing1))
            LiteIconButton(
                icon = LiteIcons.Next,
                onClick = { graph.playerController.next() },
            )
        }
        // hairline: position + cached
        val positionFraction = if (state.durationMs > 0) {
            state.positionMs.toFloat() / state.durationMs
        } else 0f
        val bufferedFraction = if (state.durationMs > 0) {
            state.bufferedMs.toFloat() / state.durationMs
        } else 0f
        ProgressStrip(
            progress = positionFraction,
            secondaryProgress = bufferedFraction,
            height = 2.dp,
        )
    }
}
