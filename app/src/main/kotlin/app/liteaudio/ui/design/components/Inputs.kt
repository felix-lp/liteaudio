package app.liteaudio.ui.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.liteaudio.ui.design.theme.Lite
import app.liteaudio.ui.design.theme.LiteColors

/** Inset "engraved" text field — the inverse of the raised button. */
@Composable
fun LiteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    singleLine: Boolean = true,
) {
    val colors = Lite.colors
    Box(
        modifier = modifier
            .height(38.dp)
            .background(
                Brush.verticalGradient(
                    0f to lerpToBlack(colors.surfacePressed, 0.3f),
                    0.2f to colors.surfacePressed,
                    1f to colors.surface,
                ),
                ControlShape,
            )
            .border(1.dp, colors.bevelBottom, ControlShape)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty() && hint.isNotEmpty()) {
            LiteText(hint, style = Lite.type.body, color = colors.textTertiary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = Lite.type.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Skeuomorphic toggle: engraved groove, glossy sliding knob. */
@Composable
fun LiteSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Lite.colors
    val knobOffset by animateFloatAsState(if (checked) 1f else 0f, label = "switch")
    val trackWidth = 40.dp
    val knobSize = 18.dp
    Box(
        modifier = modifier
            .width(trackWidth)
            .height(22.dp)
            .background(
                Brush.verticalGradient(
                    0f to lerpToBlack(if (checked) colors.accent else colors.surfacePressed, 0.35f),
                    1f to (if (checked) colors.accentDim else colors.surface),
                ),
                CircleShape,
            )
            .border(1.dp, colors.bevelBottom, CircleShape)
            .liteClickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = 2.dp + (trackWidth - knobSize - 4.dp) * knobOffset)
                .size(knobSize)
                .background(
                    Brush.verticalGradient(
                        0f to lerpToWhite(colors.surfaceRaised, 0.35f),
                        1f to colors.surfaceRaised,
                    ),
                    CircleShape,
                )
                .border(Dp.Hairline, colors.bevelTop, CircleShape),
        )
    }
}

/**
 * Two-layer seek slider: engraved groove, cache-fill layer behind the position
 * layer, metallic thumb. Tap or drag to seek.
 */
@Composable
fun LiteSlider(
    position: Float,
    cacheFill: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    thumbVisible: Boolean = true,
) {
    val colors: LiteColors = Lite.colors
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = (dragFraction ?: position).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        last = (offset.x / size.width).coerceIn(0f, 1f)
                        dragFraction = last
                    },
                    onHorizontalDrag = { change, _ ->
                        last = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragFraction = last
                    },
                    onDragEnd = {
                        dragFraction = null
                        onSeek(last)
                    },
                    onDragCancel = { dragFraction = null },
                )
            },
    ) {
        val grooveH = 5.dp.toPx()
        val cy = size.height / 2f
        val r = CornerRadius(grooveH / 2f)
        drawRoundRect(
            color = colors.surfacePressed,
            topLeft = Offset(0f, cy - grooveH / 2f),
            size = Size(size.width, grooveH),
            cornerRadius = r,
        )
        drawRoundRect(
            color = colors.bevelBottom,
            topLeft = Offset(0f, cy - grooveH / 2f),
            size = Size(size.width, 1.5f),
            cornerRadius = r,
        )
        if (cacheFill > 0f) {
            drawRoundRect(
                color = colors.textTertiary.copy(alpha = 0.5f),
                topLeft = Offset(0f, cy - grooveH / 2f),
                size = Size(size.width * cacheFill.coerceIn(0f, 1f), grooveH),
                cornerRadius = r,
            )
        }
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to lerpToWhite(colors.accent, 0.25f),
                1f to colors.accent,
            ),
            topLeft = Offset(0f, cy - grooveH / 2f),
            size = Size(size.width * fraction, grooveH),
            cornerRadius = r,
        )
        if (thumbVisible) {
            val cx = size.width * fraction
            val thumbR = 8.dp.toPx()
            drawCircle(
                brush = Brush.verticalGradient(
                    0f to lerpToWhite(colors.surfaceRaised, 0.5f),
                    1f to lerpToBlack(colors.surfaceRaised, 0.1f),
                ),
                radius = thumbR,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = colors.bevelTop,
                radius = thumbR,
                center = Offset(cx, cy),
                style = Stroke(width = 1f),
            )
        }
    }
}
