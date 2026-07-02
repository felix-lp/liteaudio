package app.liteaudio.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import app.liteaudio.ui.design.theme.Lite

/** Raised glossy panel — the standard container. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Lite.colors
    Box(
        modifier = modifier
            .glossSurface(colors, PanelShape)
            .clip(PanelShape),
        content = content,
    )
}

/**
 * Glass panel: translucent surface over content with a top gloss line.
 * True background blur is a post-v1 progressive enhancement (API 31+);
 * the base look is translucency + gloss, uniform on all supported devices.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Lite.colors
    Box(
        modifier = modifier
            .background(colors.glass)
            .background(
                Brush.verticalGradient(
                    0f to colors.gloss,
                    0.35f to androidx.compose.ui.graphics.Color.Transparent,
                ),
            ),
        content = content,
    )
}

@Composable
fun LiteDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Lite.colors.divider),
    )
}
