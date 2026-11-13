package app.wiis.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Placeholder tokens for Milestone 0. The full design system (iteration 2) replaces these values;
 * components already read from tokens, so the swap won't touch feature code.
 */
@Immutable
data class WiisTokens(
    val focusRing: Color,
    val tileSurface: Color,
    val tileSurfaceFocused: Color,
    val accent: Color,
    val muted: Color,
)

private val LightTokens = WiisTokens(
    focusRing = Color(0xFF2F7BF6),
    tileSurface = Color(0xFFFFFFFF),
    tileSurfaceFocused = Color(0xFFEAF2FF),
    accent = Color(0xFFE8453C),
    muted = Color(0xFF7A7F8C),
)

private val DarkTokens = WiisTokens(
    focusRing = Color(0xFF6FA8FF),
    tileSurface = Color(0xFF232838),
    tileSurfaceFocused = Color(0xFF2E3650),
    accent = Color(0xFFFF6B5E),
    muted = Color(0xFF9AA0AE),
)

val LocalWiisTokens = staticCompositionLocalOf { LightTokens }

private val WiisShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

private val WiisTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
)

@Composable
fun WiisTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = DarkTokens.focusRing,
            secondary = DarkTokens.accent,
            background = Color(0xFF14161F),
            surface = Color(0xFF1B1F2B),
            onBackground = Color(0xFFF1F2F6),
            onSurface = Color(0xFFF1F2F6),
        )
    } else {
        lightColorScheme(
            primary = LightTokens.focusRing,
            secondary = LightTokens.accent,
            background = Color(0xFFF6F3EE),
            surface = Color(0xFFFFFFFF),
            onBackground = Color(0xFF1C1E26),
            onSurface = Color(0xFF1C1E26),
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalWiisTokens provides if (dark) DarkTokens else LightTokens) {
        MaterialTheme(colorScheme = scheme, shapes = WiisShapes, typography = WiisTypography, content = content)
    }
}
