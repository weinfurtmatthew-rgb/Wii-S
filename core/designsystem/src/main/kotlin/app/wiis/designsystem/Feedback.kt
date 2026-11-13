package app.wiis.designsystem

import androidx.compose.foundation.background
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Interface events that can make a sound and/or a haptic tick. Keys match [SOUND_EVENTS]. */
enum class UiEvent(val key: String) {
    Move("move"),
    Select("select"),
    Back("back"),
    Tab("tab"),
    Launch("launch"),
    Error("error"),
    Bump("bump"),
}

fun interface UiFeedback {
    fun emit(event: UiEvent)

    companion object {
        val None = UiFeedback { }
    }
}

val LocalUiFeedback = staticCompositionLocalOf { UiFeedback.None }

/** Screen background as the theme describes it: solid, vertical gradient, or gradient plus two light pools. */
fun Modifier.wiisBackground(style: WiisStyle, solidOverride: Color? = null): Modifier {
    if (solidOverride != null) return background(solidOverride)
    return when (style.spec.colors.backgroundStyle) {
        "solid" -> background(style.background)
        "gradient" -> background(Brush.verticalGradient(listOf(style.backgroundTop, style.background)))
        else -> background(Brush.verticalGradient(listOf(style.backgroundTop, style.background))).drawBehind {
            val strength = style.spec.colors.glowStrength.coerceIn(0f, 1f)
            if (strength <= 0f) return@drawBehind
            drawRect(
                Brush.radialGradient(
                    listOf(style.glowA.copy(alpha = strength), Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.05f),
                    radius = size.width * 0.55f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(style.glowB.copy(alpha = strength), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 1.0f),
                    radius = size.width * 0.5f,
                ),
            )
        }
    }
}
