package org.openstrap.app.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/*
 * The "alive" kit of the rings design: small motions shared by every screen so the flat near-black pages breathe.
 * Everything here is cheap (graphicsLayer or one canvas) and reads state inside draw/layer blocks only.
 */

/** A value that swings 0..1..0 forever, one full swing every [periodMs]. Read it in a draw or layer block. */
@Composable
internal fun rememberPulse(periodMs: Int, delayMs: Int = 0): State<Float> {
    val t = rememberInfiniteTransition(label = "pulse")
    return t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(periodMs / 2, delayMillis = delayMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
}

/** A value that runs 0..1 and wraps, one lap every [periodMs]. Read it in a draw or layer block. */
@Composable
internal fun rememberLoop(periodMs: Int): State<Float> {
    val t = rememberInfiniteTransition(label = "loop")
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "loop")
}

/** [target] counted up from zero on first show (and eased to any later change). */
@Composable
internal fun rememberCountUp(target: Int, durationMs: Int = 1100): Int {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing)) }
    return a.value.toInt()
}

/**
 * Rises into place once, when first shown: fades in and slides up 22dp, [index] steps (70 ms each) after the first so a
 * column of cards cascades in.
 */
@Composable
internal fun Modifier.enterRise(index: Int = 0): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 10) * 70).toLong())
        p.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    val rise = with(LocalDensity.current) { 22.dp.toPx() }
    return graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * rise
    }
}

/** A click that presses the thing in: it shrinks a little under the finger and springs back, with no ripple. */
@Composable
internal fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * A slow living wash of [hue] behind a hero: three soft blobs drifting on their own slow loops, faint enough that the
 * page stays near-black and the ring stays the star. Put it as the first child of the hero's Box.
 */
@Composable
internal fun AmbientAura(hue: Color, modifier: Modifier = Modifier, strength: Float = 1f) {
    val a = rememberLoop(26_000)
    val b = rememberLoop(38_000)
    val breathe = rememberPulse(7_000)
    Box(modifier.clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val r = maxOf(w, h) * 0.62f
            val k = (0.16f + 0.07f * breathe.value) * strength
            val ta = a.value * 6.2831855f
            val tb = b.value * 6.2831855f
            fun blob(cx: Float, cy: Float, radius: Float, alpha: Float) {
                drawCircle(
                    Brush.radialGradient(listOf(hue.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), radius),
                    radius, Offset(cx, cy),
                )
            }
            blob(w * (0.5f + 0.28f * cos(ta)), h * (0.34f + 0.12f * sin(ta)), r, k)
            blob(w * (0.5f + 0.34f * sin(tb)), h * (0.5f + 0.16f * cos(tb)), r * 0.8f, k * 0.7f)
            blob(w * (0.5f + 0.2f * cos(tb + 2f)), h * (0.22f + 0.1f * sin(ta + 1f)), r * 0.6f, k * 0.6f)
        }
    }
}
