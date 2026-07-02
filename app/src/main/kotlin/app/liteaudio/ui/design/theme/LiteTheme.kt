package app.liteaudio.ui.design.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fully custom dark-only design system. No Material dependency.
 * Visual language: PS Vita LiveArea — gloss, depth, glass.
 */
@Immutable
data class LiteColors(
    val accent: Color,
    // layered surfaces, darkest at the back
    val background: Color = Color(0xFF0E0E10),
    val surface: Color = Color(0xFF17171A),
    val surfaceRaised: Color = Color(0xFF202024),
    val surfacePressed: Color = Color(0xFF121214),
    // bevels & gloss
    val bevelTop: Color = Color(0x28FFFFFF),
    val bevelBottom: Color = Color(0x66000000),
    val gloss: Color = Color(0x1FFFFFFF),
    val glossStrong: Color = Color(0x38FFFFFF),
    // text tiers
    val textPrimary: Color = Color(0xFFEAEAEE),
    val textSecondary: Color = Color(0xFF9B9BA6),
    val textTertiary: Color = Color(0xFF5E5E68),
    // semantic
    val error: Color = Color(0xFFE5484D),
    val warning: Color = Color(0xFFE2A336),
    val ok: Color = Color(0xFF46A758),
    // misc
    val divider: Color = Color(0x14FFFFFF),
    val scrim: Color = Color(0xB3000000),
    val glass: Color = Color(0xCC1A1A1E),
) {
    val accentDim: Color get() = accent.copy(alpha = 0.6f)
    val onAccent: Color get() = Color(0xFF120A02)
}

@Immutable
data class LiteTypography(
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    ),
    val heading: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    val secondary: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 15.sp,
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp,
    ),
    /** Timers / byte counters: tabular figures so digits don't jitter. */
    val mono: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontFeatureSettings = "tnum",
    ),
)

@Immutable
data class LiteDimens(
    val spacing1: Dp = 4.dp,
    val spacing2: Dp = 8.dp,
    val spacing3: Dp = 12.dp,
    val spacing4: Dp = 16.dp,
    val buttonHeight: Dp = 36.dp,
    val rowHeight: Dp = 52.dp,
    val playlistRowHeight: Dp = 60.dp,
    val tabBarHeight: Dp = 50.dp,
    val miniPlayerHeight: Dp = 56.dp,
    val statusStripHeight: Dp = 24.dp,
    val thumbSmall: Dp = 40.dp,
    val thumbMedium: Dp = 48.dp,
    val cornerSmall: Dp = 6.dp,
    val cornerMedium: Dp = 10.dp,
)

val LocalLiteColors = staticCompositionLocalOf { LiteColors(accent = Color(0xFFFF7A00)) }
val LocalLiteTypography = staticCompositionLocalOf { LiteTypography() }
val LocalLiteDimens = staticCompositionLocalOf { LiteDimens() }

object Lite {
    val colors: LiteColors
        @Composable get() = LocalLiteColors.current
    val type: LiteTypography
        @Composable get() = LocalLiteTypography.current
    val dimens: LiteDimens
        @Composable get() = LocalLiteDimens.current
}

@Composable
fun LiteTheme(
    accent: Color,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLiteColors provides LiteColors(accent = accent),
        LocalLiteTypography provides LiteTypography(),
        LocalLiteDimens provides LiteDimens(),
        content = content,
    )
}
