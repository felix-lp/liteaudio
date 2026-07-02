package app.liteaudio.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liteaudio.ui.design.components.LiteIcon
import app.liteaudio.ui.design.components.LiteText
import app.liteaudio.ui.design.components.liteClickable
import app.liteaudio.ui.design.components.rememberPressSource
import app.liteaudio.ui.design.theme.Lite

data class TabSpec(
    val route: String,
    val icon: ImageVector,
    val label: String,
    val badge: Int = 0,
)

/** Bottom tab bar: compact, glossy top edge, accent glow under the active tab. */
@Composable
fun TabBar(
    tabs: List<TabSpec>,
    activeRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Lite.colors
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.bevelTop),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(Lite.dimens.tabBarHeight)
                .background(
                    Brush.verticalGradient(
                        0f to colors.surface,
                        1f to colors.background,
                    ),
                ),
        ) {
            tabs.forEach { tab ->
                TabItem(
                    tab = tab,
                    active = tab.route == activeRoute,
                    onClick = { onSelect(tab.route) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: TabSpec,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Lite.colors
    val source = rememberPressSource()
    val pressed by source.collectIsPressedAsState()
    val tint = when {
        active -> colors.accent
        pressed -> colors.textPrimary
        else -> colors.textSecondary
    }
    Box(modifier.liteClickable(interactionSource = source, onClick = onClick)) {
        if (active) {
            // soft accent glow rising from the bottom — the "lit key" effect
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to colors.accent.copy(alpha = 0.16f),
                        ),
                    ),
            )
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                LiteIcon(tab.icon, tint = tint, size = 20.dp)
                if (tab.badge > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-4).dp)
                            .size(14.dp)
                            .background(colors.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        LiteText(
                            text = if (tab.badge > 9) "9+" else tab.badge.toString(),
                            style = Lite.type.caption.copy(fontSize = 9.sp, lineHeight = 10.sp),
                            color = colors.onAccent,
                        )
                    }
                }
            }
            LiteText(
                text = tab.label,
                style = Lite.type.caption,
                color = tint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
