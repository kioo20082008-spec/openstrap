package org.openstrap.app.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.openstrap.app.ui.theme.MetricColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The detail pages' share of the alive kit ([enterRise], [rememberPulse] and friends in Alive.kt): each metric's ring
 * has its own life, cards cascade in down a page, charts grow in, presses bounce, and a met goal bursts. All of it is
 * read in draw or layer blocks, so a running motion redraws a canvas and never recomposes the page.
 */

/** How a metric's ring lives (the alive brief): each metric moves its own way. */
internal enum class DetailRingLife {
    /** A soft pulsing glow at the ring's tip: any metric without its own life. */
    Glow,
    /** Energy: the tip glows and a slow shimmer travels along the arc. */
    Shimmer,
    /** Recovery: the ring beats, lub-dub, about 65 a minute, with the tip glow. */
    Heartbeat,
    /** Sleep: the ring breathes over 6 s, and a few tiny stars twinkle round it. */
    Breathe,
    /** Steps: a small dot laps the ring's track, with the tip glow. */
    Orbit,
}

/** The life of the ring in [hue]: the four metrics of the rings design by their hue, any other a tip glow. */
internal fun detailRingLife(hue: Color): DetailRingLife = when (hue) {
    RingColors.Energy, MetricColors.charge -> DetailRingLife.Shimmer
    RingColors.Recovery, MetricColors.readiness -> DetailRingLife.Heartbeat
    RingColors.Sleep, MetricColors.sleepDeep -> DetailRingLife.Breathe
    RingColors.Steps, MetricColors.steps -> DetailRingLife.Orbit
    else -> DetailRingLife.Glow
}

/** One beat of a heart, 0..1 over a beat's [t] (0..1): a strong lub, then a softer dub, then rest. */
internal fun detailHeartbeat(t: Float): Float {
    fun bump(a: Float, b: Float) = if (t in a..b) sin(PI.toFloat() * (t - a) / (b - a)) else 0f
    return bump(0f, 0.14f) + 0.6f * bump(0.2f, 0.34f)
}

/**
 * A page's cascade: the cards composed in the page's first moments rise one after another, 70 ms apart; a card that
 * comes in later (scrolled to) rises at once.
 */
internal class DetailCascade {
    private val opened = System.nanoTime()
    private var slot = 0

    fun nextDelay(): Int {
        val since = ((System.nanoTime() - opened) / 1_000_000L).toInt()
        if (since > 900) return 0
        val at = (slot++).coerceAtMost(9) * 70
        return (at - since).coerceAtLeast(0)
    }
}

/** The page's cascade, provided by a page's root; without one each card rises on its own. */
internal val LocalDetailCascade = compositionLocalOf<DetailCascade?> { null }

/** Rises into place once, its turn in the page's [LocalDetailCascade]: fades in and slides up 18 dp. */
@Composable
internal fun Modifier.detailRise(): Modifier {
    val cascade = LocalDetailCascade.current
    val wait = remember { cascade?.nextDelay() ?: 0 }
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (wait > 0) delay(wait.toLong())
        p.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    return graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * rise
    }
}

/** A bar chart growing up from its foot on first show. */
@Composable
internal fun Modifier.detailGrow(delayMs: Int = 120): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)
        scaleY = 0.02f + 0.98f * p.value
        alpha = (p.value * 3f).coerceAtMost(1f)
    }
}

/**
 * A chart drawn in on first show: from the left, as charts read left to right (`Ltr`), or with [fromStart] from the
 * layout's start (a bar that fills from the right in Arabic).
 */
@Composable
internal fun Modifier.detailReveal(delayMs: Int = 120, fromStart: Boolean = false): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    return drawWithContent {
        val v = p.value
        when {
            v >= 1f -> drawContent()
            fromStart && layoutDirection == LayoutDirection.Rtl -> clipRect(left = size.width * (1f - v)) { this@drawWithContent.drawContent() }
            else -> clipRect(right = size.width * v) { this@drawWithContent.drawContent() }
        }
    }
}

/**
 * A click that presses the thing in ([bounceClick] in Alive.kt) and keeps a row's semantics: [onClickLabel] and [role]
 * are read out as before. [scale] is how far it gives: cards 0.96, full width rows less.
 */
@Composable
internal fun Modifier.detailPress(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    scale: Float = 0.96f,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s = animateFloatAsState(
        if (pressed) scale else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press",
    )
    return graphicsLayer { scaleX = s.value; scaleY = s.value }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
}

/** A 0..1 that runs once on first show, after [delayMs]. Read it in a draw or layer block. */
@Composable
internal fun rememberDetailOnce(durationMs: Int, delayMs: Int = 0): State<Float> {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        p.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    return p.asState()
}

private val NumberText = Regex("""(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d+))?""")

/**
 * [value] counted up from zero on first show, kept in its own form (grouping commas, the same decimals). Anything that
 * isn't a plain number ("7h 30m", "–") shows as it is. Later changes (a scrub) show at once.
 */
@Composable
internal fun detailCountUp(value: String, durationMs: Int = 1100): String {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    // Worked out once a value, not on every frame of the count; and the text is a derived state, so the reader recomposes
    // only when the shown text itself changes.
    val counter = remember(value) {
        val m = NumberText.matchEntire(value)
        val whole = m?.groupValues?.get(1)
        val decimals = m?.groupValues?.get(2)?.length ?: 0
        val target = if (m == null) null else (whole!!.replace(",", "") + if (decimals > 0) "." + m.groupValues[2] else "").toDoubleOrNull()
        if (target == null) null else {
            val f = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).apply {
                isGroupingUsed = whole!!.contains(',')
                minimumFractionDigits = decimals
                maximumFractionDigits = decimals
            }
            target to f
        }
    }
    if (counter == null) return value
    val shown = remember(value, counter) {
        derivedStateOf {
            val at = p.value
            if (at >= 1f) value else counter.second.format(counter.first * at)
        }
    }
    return shown.value
}

/**
 * A short celebration on one canvas: a ring bursting out from the middle, then sparks in [hue] and its lighter shade
 * flying out and fading, once, about 1.4 s, after [delayMs]. Put it with matchParentSize behind the thing celebrated;
 * it draws past its bounds.
 */
@Composable
internal fun DetailBurst(hue: Color, modifier: Modifier = Modifier, delayMs: Int = 0) {
    val t = rememberDetailOnce(1400, delayMs)
    val sparks = remember {
        val r = kotlin.random.Random(7)
        List(30) { floatArrayOf(r.nextFloat() * 2f * PI.toFloat(), 0.55f + r.nextFloat() * 0.6f, 1.5f + r.nextFloat() * 2.5f, r.nextFloat()) }
    }
    val light = lerp(hue, Color.White, 0.55f)
    Canvas(modifier) {
        val p = t.value
        if (p <= 0f || p >= 1f) return@Canvas
        val c = center
        val base = size.minDimension / 2f
        // Two rings racing out, the second a beat behind.
        for (k in 0..1) {
            val q = ((p - k * 0.12f) / 0.6f).coerceIn(0f, 1f)
            if (q <= 0f || q >= 1f) continue
            val e = 1f - (1f - q) * (1f - q)
            drawCircle(
                hue.copy(alpha = 0.55f * (1f - q)), base * (0.7f + 0.75f * e), c,
                style = Stroke((10.dp.toPx() * (1f - q)).coerceAtLeast(1f)),
            )
        }
        // Sparks: out fast, slowing, falling a little, fading.
        sparks.forEach { s ->
            val q = ((p - s[3] * 0.15f) / 0.85f).coerceIn(0f, 1f)
            if (q <= 0f || q >= 1f) return@forEach
            val e = 1f - (1f - q) * (1f - q) * (1f - q)
            val dist = base * (0.55f + s[1] * e)
            val fall = 24.dp.toPx() * q * q
            val at = Offset(c.x + cos(s[0]) * dist, c.y + sin(s[0]) * dist + fall)
            val tail = Offset(c.x + cos(s[0]) * (dist - 14.dp.toPx() * (1f - q)), c.y + sin(s[0]) * (dist - 14.dp.toPx() * (1f - q)) + fall)
            val col = if (s[3] > 0.5f) light else hue
            val a = (1f - q).coerceIn(0f, 1f)
            drawLine(col.copy(alpha = a * 0.7f), tail, at, s[2].dp.toPx() * 0.7f, cap = StrokeCap.Round)
            drawCircle(col.copy(alpha = a), s[2].dp.toPx(), at)
        }
        // A soft flash at the start.
        val flash = (1f - p / 0.3f).coerceIn(0f, 1f)
        if (flash > 0f) drawCircle(Brush.radialGradient(listOf(hue.copy(alpha = 0.35f * flash), Color.Transparent), c, base * 1.2f), base * 1.2f, c)
    }
}
