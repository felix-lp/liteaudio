package app.liteaudio.ui.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.liteaudio.ui.design.theme.Lite

/** Glossy raised button that visibly "presses in" — accent or neutral. */
@Composable
fun PressableButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val colors = Lite.colors
    val source = rememberPressSource()
    val pressed by source.collectIsPressedAsState()
    val base = if (accent) colors.accent else null
    val contentColor = when {
        !enabled -> colors.textTertiary
        accent -> colors.onAccent
        else -> colors.textPrimary
    }
    Row(
        modifier = modifier
            .height(if (compact) 30.dp else Lite.dimens.buttonHeight)
            .glossSurface(colors, ControlShape, pressed = pressed, base = base)
            .liteClickable(interactionSource = source, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                painter = rememberVectorPainter(icon),
                contentDescription = null,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                colorFilter = ColorFilter.tint(contentColor),
            )
            Spacer(Modifier.width(6.dp))
        }
        LiteText(
            text = text,
            style = if (compact) Lite.type.secondary else Lite.type.heading,
            color = contentColor,
        )
    }
}

/** Icon-only pressable button; transparent until pressed unless [raised]. */
@Composable
fun LiteIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    raised: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = Lite.colors
    val source = rememberPressSource()
    val pressed by source.collectIsPressedAsState()
    val resolvedTint = when {
        !enabled -> colors.textTertiary
        tint != Color.Unspecified -> tint
        else -> colors.textPrimary
    }
    val base = Modifier.size(size)
    val surfaced = if (raised || pressed) {
        base.glossSurface(colors, ControlShape, pressed = pressed)
    } else base
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .then(surfaced)
            .liteClickable(interactionSource = source, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(resolvedTint),
        )
    }
}

@Composable
fun LiteIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 20.dp,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(if (tint != Color.Unspecified) tint else Lite.colors.textPrimary),
    )
}
