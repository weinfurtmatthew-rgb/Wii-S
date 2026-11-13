package app.wiis.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Gamepad buttons, handled explicitly so behaviour doesn't depend on the OEM's key fallbacks.
 * A = confirm, Y = secondary action (e.g. pick emulator).
 */
fun Modifier.gamepadActions(onConfirm: () -> Unit, onSecondary: (() -> Unit)? = null): Modifier =
    onKeyEvent { e ->
        if (e.type != KeyEventType.KeyUp) return@onKeyEvent false
        when (e.key) {
            Key.ButtonA -> { onConfirm(); true }
            Key.ButtonY -> onSecondary?.let { it(); true } ?: false
            else -> false
        }
    }

/**
 * The basic focusable unit: springs up when focused, presses in when activated, gives a haptic
 * tick on focus. Works with touch, D-pad and gamepad.
 */
@Composable
fun WiisTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSecondary: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalWiisTokens.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(focused) { if (focused) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    val scale by animateFloatAsState(
        targetValue = when { pressed -> 0.96f; focused -> 1.06f; else -> 1f },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tileScale",
    )
    val shape = MaterialTheme.shapes.medium
    Surface(
        shape = shape,
        color = if (focused) tokens.tileSurfaceFocused else tokens.tileSurface,
        shadowElevation = if (focused) 10.dp else 2.dp,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.border(3.dp, tokens.focusRing, shape) else Modifier)
            .gamepadActions(onClick, onSecondary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(content = content)
    }
}
