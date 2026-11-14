package app.wiis.designsystem

import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

/**
 * The focusable unit for both screens, driven entirely by theme tokens:
 * focus scale/lift/spring, idle bob, focus ring (width, gap, colour), glow, soft or hard shadow,
 * press-in, fill colours and border. Works with touch, D-pad and gamepad.
 *
 * Gamepad: A confirms, Y is the secondary action. D-pad moves focus; when there is nowhere to go
 * it plays the "bump" feedback instead of doing nothing silently.
 *
 * @param clickSound feedback for a confirm; null when the caller emits its own (e.g. a game
 *   launch reports Launch or Error once the outcome is known).
 * @param ring false for elements that show focus another way (e.g. tabs).
 */
@Composable
fun WiisTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSecondary: (() -> Unit)? = null,
    shape: Shape? = null,
    fill: Color? = null,
    fillFocused: Color? = null,
    clickSound: UiEvent? = UiEvent.Select,
    ring: Boolean = true,
    elevated: Boolean = true,
    /** Corner radius of the focus ring's tile; the theme's tile radius when null. */
    ringRadius: androidx.compose.ui.unit.Dp? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val style = LocalWiis.current
    val spec = style.spec
    val motion = spec.motion
    val feedback = LocalUiFeedback.current
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val touchPressed by interaction.collectIsPressedAsState()
    var padPressed by remember { mutableStateOf(false) }
    val pressed = touchPressed || padPressed
    val tileShape = shape ?: style.tileShape

    LaunchedEffect(focused) { if (focused) feedback.emit(UiEvent.Move) }

    val reduce = motion.reduceMotion
    val focusProgress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reduce) tween(120) else spring(motion.damping.coerceIn(0.05f, 2f), motion.stiffness.coerceIn(10f, 20_000f)),
        label = "focus",
    )
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (reduce) tween(60) else spring(1f, 1_500f),
        label = "press",
    )
    val bob = if (focused && motion.idleBob && !reduce) {
        val transition = rememberInfiniteTransition(label = "bob")
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(motion.idleBobPeriodMs.coerceAtLeast(200) / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bobValue",
        ).value
    } else {
        0f
    }

    val ringWidth = spec.shape.focusRingWidth.dp
    val ringGap = spec.shape.focusRingGap.dp
    val radius = ringRadius ?: spec.shape.tileRadius.dp
    val baseFill = fill ?: style.tile
    val focusFill = fillFocused ?: style.tileFocused

    Box(
        modifier
            .graphicsLayer {
                // A big card grows by at most focusGrowMax dp per side; small tiles grow by focusScale.
                val longest = maxOf(size.width, size.height)
                val growCap = if (longest > 0f) 1f + 2f * motion.focusGrowMax.coerceAtLeast(0).dp.toPx() / longest else motion.focusScale
                val focusScale = minOf(motion.focusScale, growCap)
                val scale = (1f + (focusScale - 1f) * focusProgress) * (1f - (1f - motion.pressScale) * pressProgress)
                scaleX = scale
                scaleY = scale
                translationY = (-motion.focusLift * focusProgress + motion.idleBobAmplitude * bob).dp.toPx()
            }
            .drawWithContent {
                drawContent()
                if (ring && ringWidth.value > 0f && focusProgress > 0.01f) {
                    val outset = ringGap.toPx() + ringWidth.toPx() / 2
                    val r = radius.toPx() + outset
                    drawRoundRect(
                        color = style.focusRing.copy(alpha = style.focusRing.alpha * focusProgress.coerceIn(0f, 1f)),
                        topLeft = Offset(-outset, -outset),
                        size = Size(size.width + outset * 2, size.height + outset * 2),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(ringWidth.toPx()),
                    )
                }
            }
            .onKeyEvent { e ->
                when (e.key) {
                    Key.ButtonA -> {
                        if (e.type == KeyEventType.KeyDown) padPressed = true
                        if (e.type == KeyEventType.KeyUp) { padPressed = false; clickSound?.let(feedback::emit); onClick() }
                        true
                    }
                    Key.ButtonY -> {
                        if (onSecondary == null) return@onKeyEvent false
                        if (e.type == KeyEventType.KeyUp) { feedback.emit(UiEvent.Select); onSecondary() }
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val direction = when (e.key) {
                            Key.DirectionLeft -> FocusDirection.Left
                            Key.DirectionRight -> FocusDirection.Right
                            Key.DirectionUp -> FocusDirection.Up
                            else -> FocusDirection.Down
                        }
                        if (!focusManager.moveFocus(direction)) feedback.emit(UiEvent.Bump)
                        true
                    }
                    else -> false
                }
            }
            .clickable(interactionSource = interaction, indication = null) {
                clickSound?.let(feedback::emit)
                onClick()
            },
        // A fixed-size tile (caller sets width/height) hands its size to the content; a free-sized
        // one (a tab, a button) wraps its content.
        propagateMinConstraints = true,
    ) {
        if (elevated && spec.shape.shadow == "hard") {
            val off = spec.shape.shadowOffset.dp
            Box(Modifier.matchParentSize().offset(off, off).background(style.shadowColor, tileShape))
        }
        val glowStrength = spec.colors.focusGlowStrength.coerceIn(0f, 1f)
        if (elevated && glowStrength > 0f && focusProgress > 0.01f) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = glowStrength * focusProgress.coerceIn(0f, 1f)
                        val blur = 22.dp.toPx()
                        renderEffect = BlurEffect(blur, blur, TileMode.Decal)
                        scaleX = 1.04f
                        scaleY = 1.06f
                    }
                    .background(style.focusGlow, tileShape),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .then(
                    if (elevated && spec.shape.shadow == "soft") {
                        Modifier.softShadow(tileShape, style.shadowColor) { focusProgress }
                    } else {
                        Modifier
                    },
                )
                .clip(tileShape)
                .background(lerp(baseFill, focusFill, focusProgress.coerceIn(0f, 1f)))
                .then(
                    if (spec.shape.tileBorderWidth > 0) Modifier.border(spec.shape.tileBorderWidth.dp, style.tileBorder, tileShape) else Modifier,
                ),
        )
        Box(Modifier.clip(tileShape)) { content(focused) }
    }
}

/**
 * The workbench's tile shadow, `0 (e/2)dp (1.6e)dp` in [color] at half strength, where e grows
 * from 3 to 12 with focus. Drawn only outside the tile's outline (like a CSS box-shadow), so it
 * never darkens a see-through glass tile from underneath, which an elevation shadow would.
 */
fun Modifier.softShadow(shape: Shape, color: Color, progress: () -> Float): Modifier = drawBehind {
    val e = 3f + 9f * progress().coerceIn(0f, 1f)
    drawBoxShadow(shape, (e * 0.5f).dp.toPx(), (e * 1.6f).dp.toPx(), color.copy(alpha = color.alpha * 0.5f))
}
