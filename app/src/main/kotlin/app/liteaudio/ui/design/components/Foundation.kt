package app.liteaudio.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.liteaudio.ui.design.theme.Lite
import app.liteaudio.ui.design.theme.LiteColors

/** BasicText with theme defaults; the app's only text primitive. */
@Composable
fun LiteText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val base = style ?: Lite.type.body
    val resolved = if (color != Color.Unspecified) base.copy(color = color)
    else if (base.color == Color.Unspecified) base.copy(color = Lite.colors.textPrimary)
    else base
    BasicText(
        text = text,
        modifier = modifier,
        style = resolved,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** Click without ripple — skeuomorphic pressed states are drawn by the component itself. */
fun Modifier.liteClickable(
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    onClick = onClick,
)

@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * Raised glossy surface: vertical gradient, 1px light bevel on top, dark bevel below.
 * The core of the PS Vita look.
 */
fun Modifier.glossSurface(
    colors: LiteColors,
    shape: Shape,
    pressed: Boolean = false,
    base: Color? = null,
): Modifier {
    val body = base ?: if (pressed) colors.surfacePressed else colors.surfaceRaised
    val top = if (pressed) body else lerpToWhite(body, 0.10f)
    val bottom = if (pressed) lerpToBlack(body, 0.25f) else lerpToBlack(body, 0.18f)
    return this
        .background(
            brush = Brush.verticalGradient(
                0f to top,
                0.5f to body,
                1f to bottom,
            ),
            shape = shape,
        )
        .border(
            width = Dp.Hairline,
            brush = Brush.verticalGradient(
                0f to (if (pressed) colors.bevelBottom else colors.bevelTop),
                1f to (if (pressed) colors.bevelTop else colors.bevelBottom),
            ),
            shape = shape,
        )
}

fun lerpToWhite(c: Color, t: Float): Color = Color(
    red = c.red + (1f - c.red) * t,
    green = c.green + (1f - c.green) * t,
    blue = c.blue + (1f - c.blue) * t,
    alpha = c.alpha,
)

fun lerpToBlack(c: Color, t: Float): Color = Color(
    red = c.red * (1f - t),
    green = c.green * (1f - t),
    blue = c.blue * (1f - t),
    alpha = c.alpha,
)

val PanelShape = RoundedCornerShape(10.dp)
val ControlShape = RoundedCornerShape(8.dp)
