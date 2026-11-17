package org.openstrap.app.ui.glow

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.components.rememberAnimationsOn
import org.openstrap.app.ui.theme.AppColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/*
 * The workout pages' share of the alive kit ([AmbientAura] and the rest live in Alive.kt): a heart that beats at the
 * strap's own bpm, the zone's colour washing slowly behind a live screen, the clock's tick, and a burst of sparks for a
 * personal best. Everything is read in draw or layer blocks, so a running animation never composes the screen again.
 */

/**
 * Where the heart is in its beat, 0 to 1, advancing frame by frame at [bpm]: a new reading changes the pace from here
 * on instead of jumping the beat. Still (0) with no reading or with the system's animations off.
 */
@Composable
internal fun rememberBeatPhase(bpm: Int?): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    val rate by rememberUpdatedState(bpm ?: 0)
    val animationsOn = rememberAnimationsOn()
    val live = bpm != null && bpm > 0 && animationsOn
    LaunchedEffect(live) {
        if (!live) {
            phase.floatValue = 0f
            return@LaunchedEffect
        }
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                // A long frame (the screen coming back) doesn't skip whole beats.
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                phase.floatValue = (phase.floatValue + dt * rate.coerceIn(30, 220) / 60f) % 1f
            }
        }
    }
    return phase
}

/** A beat's swell at [phase]: the lub, then a smaller dub, then rest (0 to 1). */
internal fun lubDub(phase: Float): Float {
    fun bump(c: Float, w: Float): Float {
        val x = (1f - abs(phase - c) / w).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
    return max(bump(0.08f, 0.08f), 0.6f * bump(0.27f, 0.09f))
}

/** Grows by [amount] on each beat of [phase] ([rememberBeatPhase]), in the layer. */
internal fun Modifier.beating(phase: State<Float>, amount: Float = 0.03f): Modifier = graphicsLayer {
    val k = 1f + amount * lubDub(phase.value)
    scaleX = k
    scaleY = k
}

/**
 * The live screen's backdrop: [AmbientAura] in the zone's [color], easing to a new zone's colour over a second or so
 * rather than switching, so the page drifts and warms as the effort climbs.
 */
@Composable
internal fun LiveZoneWash(color: Color, modifier: Modifier = Modifier, strength: Float = 0.9f) {
    val hue by animateColorAsState(color, tween(1400), label = "zone wash")
    AmbientAura(hue, modifier, strength)
}

/** Pops a little each time [key] changes after the first (a clock's second), springing back in the layer. */
@Composable
internal fun Modifier.tickOn(key: Long, amount: Float = 0.03f): Modifier {
    val t = remember { Animatable(0f) }
    val first = remember { booleanArrayOf(true) }
    val on = rememberAnimationsOn()
    LaunchedEffect(key) {
        if (first[0] || !on) {
            first[0] = false
            return@LaunchedEffect
        }
        t.snapTo(1f)
        t.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        val k = 1f + amount * t.value
        scaleX = k
        scaleY = k
    }
}

/**
 * A soft round light in [hue] behind a hero, breathing slowly; it fades to nothing at its own edge, so it can sit in
 * any box without showing one. Drawn past the box it's given ([reach] of its smaller side).
 */
@Composable
internal fun HeroHalo(hue: Color, modifier: Modifier = Modifier, reach: Float = 0.78f, alpha: Float = 0.16f) {
    val breathe = rememberPulse(6_000)
    Canvas(modifier) {
        val r = size.minDimension * reach * (1f + 0.05f * breathe.value)
        val a = alpha * (0.75f + 0.25f * breathe.value)
        drawCircle(Brush.radialGradient(listOf(hue.copy(alpha = a), Color.Transparent), center, r), r, center)
    }
}

/**
 * A burst of sparks flying out from the edge of the box it's given, in [color] and the page's ink, while [progress]
 * runs from 0 to 1 (a personal best). Nothing is drawn outside that run.
 */
@Composable
internal fun SparkBurst(progress: () -> Float, color: Color, modifier: Modifier = Modifier) {
    val sparks = remember {
        List(SPARKS) { i ->
            floatArrayOf(i * 360f / SPARKS + Random.nextFloat() * 14f, 0.6f + Random.nextFloat() * 0.7f, Random.nextFloat())
        }
    }
    val ink = AppColors.ink
    Canvas(modifier) {
        val p = progress()
        // The burst starts at the hero's ring, a little inside the box's edge.
        if (p <= 0f || p >= 1f) return@Canvas
        val r0 = size.minDimension / 2f
        val reach = 52.dp.toPx()
        val out = 1f - (1f - p) * (1f - p) * (1f - p)
        val fade = (1f - p).coerceIn(0f, 1f)
        sparks.forEach { s ->
            val a = Math.toRadians(s[0].toDouble())
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            val d = r0 + reach * s[1] * out
            val tail = 10.dp.toPx() * s[1] * fade
            val tip = Offset(center.x + dx * d, center.y + dy * d)
            val back = Offset(tip.x - dx * tail, tip.y - dy * tail)
            val c = if (s[2] > 0.65f) ink else color
            drawLine(c.copy(alpha = 0.85f * fade), back, tip, strokeWidth = 2.5.dp.toPx() * (0.5f + 0.5f * fade), cap = StrokeCap.Round)
        }
    }
}

private const val SPARKS = 18

/**
 * [value] counted up from zero when first shown, if it's a plain number ("42", "5.21"); anything else ("42:10",
 * "1,204") is shown as it is. Keeps its decimals while counting.
 */
@Composable
internal fun rememberCountUpText(value: String, durationMs: Int = 1100): String {
    val m = remember(value) { Regex("""(\d+)(?:\.(\d+))?""").matchEntire(value) }
    val a = remember(value) { Animatable(if (m == null) 1f else 0f) }
    val on = rememberAnimationsOn()
    LaunchedEffect(value, on) {
        if (!on) a.snapTo(1f) else a.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    if (m == null || a.value >= 1f) return value
    val decimals = m.groupValues[2].length
    val v = (value.toDoubleOrNull() ?: return value) * a.value
    return if (decimals == 0) v.toLong().toString() else String.format(java.util.Locale.US, "%.${decimals}f", v)
}
