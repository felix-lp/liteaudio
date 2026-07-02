package app.liteaudio.ui.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.liteaudio.ui.design.theme.Lite

/**
 * Thin determinate/indeterminate progress bar with an optional secondary
 * (cache) layer behind the primary one — used everywhere: status strip,
 * download rows, mini-player hairline.
 */
@Composable
fun ProgressStrip(
    progress: Float?, // null => indeterminate
    modifier: Modifier = Modifier,
    secondaryProgress: Float = 0f,
    height: Dp = 3.dp,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
) {
    val colors = Lite.colors
    val bar = if (color != Color.Unspecified) color else colors.accent
    val track = if (trackColor != Color.Unspecified) trackColor else colors.surfacePressed
    val secondary = bar.copy(alpha = 0.30f)

    val indeterminatePhase: Float = if (progress == null) {
        val transition = rememberInfiniteTransition(label = "progress")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "phase",
        )
        phase
    } else 0f

    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(color = track, cornerRadius = r)
        if (progress == null) {
            val w = size.width * 0.28f
            val x = (size.width + w) * indeterminatePhase - w
            drawRoundRect(
                color = bar,
                topLeft = Offset(x.coerceAtLeast(0f), 0f),
                size = Size(
                    width = (x + w).coerceAtMost(size.width) - x.coerceAtLeast(0f),
                    height = size.height,
                ),
                cornerRadius = r,
            )
        } else {
            if (secondaryProgress > 0f) {
                drawRoundRect(
                    color = secondary,
                    size = Size(size.width * secondaryProgress.coerceIn(0f, 1f), size.height),
                    cornerRadius = r,
                )
            }
            drawRoundRect(
                color = bar,
                size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                cornerRadius = r,
            )
        }
    }
}
