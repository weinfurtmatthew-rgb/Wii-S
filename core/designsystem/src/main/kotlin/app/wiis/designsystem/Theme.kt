package app.wiis.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Downloaded font families; [FontFamily.Default] until they arrive (or when "System" is chosen). */
@Immutable
data class WiisFonts(
    val display: FontFamily = FontFamily.Default,
    val body: FontFamily = FontFamily.Default,
)

/**
 * A [ThemeSpec] resolved into Compose values. Created once per theme change, read through
 * [LocalWiis]. Display font: wordmark, tabs, shelf titles, bottom-screen title.
 * Body font: everything else.
 */
@Immutable
class WiisStyle(val spec: ThemeSpec, val fonts: WiisFonts) {
    private val c = spec.colors
    private val defaults = ColorTokens()

    val background = c.background.toColorOr(defaults.background.toColorOr(Color.Black))
    val backgroundTop = c.backgroundTop.toColorOr(background)
    val glowA = c.glowA.toColorOr(Color.Transparent)
    val glowB = c.glowB.toColorOr(Color.Transparent)
    val surface = c.surface.toColorOr(background)
    val tile = c.tile.toColorOr(surface)
    val tileFocused = c.tileFocused.toColorOr(tile)
    val text = c.text.toColorOr(Color.White)
    val textMuted = c.textMuted.toColorOr(text.copy(alpha = 0.7f))
    val accent = c.accent.toColorOr(Color(0xFFFF6B4A))
    val focusRing = c.focusRing.toColorOr(accent)
    val focusGlow = c.focusGlow.toColorOr(focusRing)
    val tabActiveFill = c.tabActiveFill.toColorOr(accent)
    val tabActiveText = c.tabActiveText.toColorOr(background)
    val tabIdleText = c.tabIdleText.toColorOr(textMuted)
    val hintBadge = c.hintBadge.toColorOr(surface)
    val hintBadgeText = c.hintBadgeText.toColorOr(text)
    val messageBar = c.messageBar.toColorOr(surface)
    val tileBorder = spec.shape.tileBorderColor.toColorOr(Color.Transparent)
    val shadowColor = spec.shape.shadowColor.toColorOr(Color.Black)
    val companionBackground = spec.companion.backgroundColor.toColorOr(background)

    private val g = spec.glass
    val glassFill = g.fill.toColorOr(Color.White).copy(alpha = g.opacity.coerceIn(0f, 1f))
    val glassBorder = g.border.toColorOr(Color.White).copy(alpha = g.borderOpacity.coerceIn(0f, 1f))
    val glassText = g.text.toColorOr(Color(0xFF1E1B27))
    val glassTextMuted = g.textMuted.toColorOr(glassText.copy(alpha = 0.7f))
    val glassShape = RoundedCornerShape(g.radius.dp)

    fun systemColor(systemId: String): Color = spec.systemColor(systemId)

    fun hintColor(button: String): Color = when (button) {
        "A" -> spec.hints.colorA
        "B" -> spec.hints.colorB
        "X" -> spec.hints.colorX
        "Y" -> spec.hints.colorY
        else -> spec.hints.colorOther
    }.toColorOr(hintBadge)

    private val t = spec.type
    val wordmark = TextStyle(fontFamily = fonts.display, fontWeight = FontWeight(t.wordmarkWeight.w()), fontSize = t.wordmarkSize.sp, color = accent)
    val tab = TextStyle(fontFamily = fonts.display, fontWeight = FontWeight(t.tabWeight.w()), fontSize = t.tabSize.sp)
    val shelfTitle = TextStyle(
        fontFamily = fonts.display,
        fontWeight = FontWeight(t.shelfTitleWeight.w()),
        fontSize = t.shelfTitleSize.sp,
        letterSpacing = t.shelfTitleTracking.sp,
    )
    val tileTitle = TextStyle(fontFamily = fonts.body, fontWeight = FontWeight(t.tileTitleWeight.w()), fontSize = t.tileTitleSize.sp, color = text)
    val label = TextStyle(fontFamily = fonts.body, fontWeight = FontWeight(t.labelWeight.w()), fontSize = t.labelSize.sp, color = textMuted)
    val clock = TextStyle(fontFamily = fonts.body, fontWeight = FontWeight(t.clockWeight.w()), fontSize = t.clockSize.sp, color = text)
    val body = TextStyle(fontFamily = fonts.body, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = text)
    val heading = TextStyle(fontFamily = fonts.display, fontWeight = FontWeight(t.wordmarkWeight.w()), fontSize = (t.wordmarkSize * 0.9f).sp, color = text)

    fun shelfTitleText(raw: String): String = when (t.shelfTitleCase) {
        "upper" -> raw.uppercase()
        "lower" -> raw.lowercase()
        else -> raw
    }

    val tileShape = RoundedCornerShape(spec.shape.tileRadius.dp)
    val cardShape = RoundedCornerShape(spec.shape.cardRadius.dp)
    val dialogShape = RoundedCornerShape(spec.shape.dialogRadius.dp)
    val tabShape = RoundedCornerShape(spec.shape.tabRadius.dp)
    val badgeShape = RoundedCornerShape(spec.shape.badgeRadius.dp)

    private fun Int.w() = coerceIn(100, 900)
}

val LocalWiis = staticCompositionLocalOf { WiisStyle(ThemeSpec(), WiisFonts()) }

@Composable
fun WiisTheme(
    spec: ThemeSpec = ThemeSpec(),
    fonts: WiisFonts = WiisFonts(),
    feedback: UiFeedback = UiFeedback.None,
    content: @Composable () -> Unit,
) {
    val style = remember(spec, fonts) { WiisStyle(spec, fonts) }
    val scheme = remember(style) {
        darkColorScheme(
            primary = style.focusRing,
            onPrimary = style.background,
            secondary = style.accent,
            background = style.background,
            onBackground = style.text,
            surface = style.surface,
            onSurface = style.text,
            surfaceContainerHigh = style.surface,
            onSurfaceVariant = style.textMuted,
        )
    }
    val typography = remember(style) {
        Typography(
            headlineMedium = style.heading,
            titleMedium = style.tileTitle,
            bodyMedium = style.body,
            labelMedium = style.label,
        )
    }
    val shapes = remember(style) {
        Shapes(small = style.tileShape, medium = style.cardShape, large = style.dialogShape, extraLarge = style.dialogShape)
    }
    CompositionLocalProvider(LocalWiis provides style, LocalUiFeedback provides feedback) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
