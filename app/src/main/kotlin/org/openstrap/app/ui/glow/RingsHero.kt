package org.openstrap.app.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.NumberFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The rings design's metric hues: one thick ring a metric, the same hue wherever that metric shows. */
internal object RingColors {
    val Energy = Color(0xFFFF5C39)
    val Recovery = Color(0xFFC8FF3D)
    val Sleep = Color(0xFF8B7BFF)
    val Steps = Color(0xFF3DD6FF)
}

private val Muted: Color get() = AppColors.muted

/** A ring [inset] from the canvas edge: a faint full track, then [share] of it from the top, clockwise, round capped. */
private fun DrawScope.ring(share: Float, color: Color, stroke: Float, inset: Float) {
    val d = size.minDimension - 2 * inset - stroke
    if (d <= 0f) return
    val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
    val arc = Size(d, d)
    drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, tl, arc, style = Stroke(stroke))
    val sweep = 360f * share.coerceIn(0f, 1f)
    if (sweep > 0f) drawArc(color, -90f, sweep, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
}

/** [target] (0 to 1) sweeping in from empty once the tab is first seen; read its value in a draw block. */
@Composable
private fun animatedShare(target: Float, delayMs: Int = 120): Animatable<Float, AnimationVector1D> =
    rememberSeenShare(target, delayMs = delayMs)

/* The rings' life: every piece below is drawn inside the ring's canvas from the tab's clock, so nothing recomposes. */

/** Where [share] of a ring [inset] from the edge ends; [Offset.Unspecified] when the ring doesn't fit (no allocation: it runs every frame). */
private fun DrawScope.ringPoint(share: Float, stroke: Float, inset: Float): Offset {
    val d = size.minDimension - 2 * inset - stroke
    if (d <= 0f) return Offset.Unspecified
    val r = d / 2f
    val a = ((-90f + 360f * share) * PI / 180f).toFloat()
    return Offset(center.x + r * cos(a), center.y + r * sin(a))
}

/** A soft halo round the ring's tip, brighter as [glow] (0 to 1) rises, with a small light core on the tip itself. */
private fun DrawScope.tipGlow(share: Float, color: Color, stroke: Float, inset: Float, glow: Float) {
    if (share <= 0.005f) return
    val p = ringPoint(share, stroke, inset)
    if (p.isUnspecified) return
    val reach = stroke * (1.15f + 0.45f * glow)
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.34f + 0.3f * glow), Color.Transparent), p, reach), reach, p)
}

/** The tip's light core, drawn over the ring. */
private fun DrawScope.tipCore(share: Float, color: Color, stroke: Float, inset: Float, glow: Float) {
    if (share <= 0.005f) return
    val p = ringPoint(share, stroke, inset)
    if (p.isUnspecified) return
    drawCircle(lerp(color, Color.White, 0.6f).copy(alpha = 0.35f + 0.45f * glow), stroke * (0.14f + 0.05f * glow), p)
}

/** A short light travelling along the filled arc, start to tip, fading in and out on its way: once every 3.6 s. */
private fun DrawScope.shimmer(share: Float, color: Color, stroke: Float, inset: Float, t: Float) {
    val sweep = 360f * share.coerceIn(0f, 1f)
    if (sweep < 24f) return
    val d = size.minDimension - 2 * inset - stroke
    if (d <= 0f) return
    val pos = lap(t, 3.6f)
    val mid = -90f + sweep * pos
    val from = maxOf(-90f, mid - 14f)
    val to = minOf(-90f + sweep, mid + 14f)
    if (to <= from) return
    val alpha = 0.42f * sin(PI.toFloat() * pos)
    drawArc(
        lerp(color, Color.White, 0.65f).copy(alpha = alpha), from, to - from, false,
        Offset((size.width - d) / 2f, (size.height - d) / 2f), Size(d, d), style = Stroke(stroke * 0.42f, cap = StrokeCap.Round),
    )
}

/** A small light lapping the ring's track, once every [period] seconds, with a faint tail behind it. */
private fun DrawScope.orbit(color: Color, stroke: Float, inset: Float, t: Float, period: Float) {
    val at = lap(t, period)
    val light = lerp(color, Color.White, 0.55f)
    for (k in 0..4) {
        val p = ringPoint(at - k * 0.012f, stroke, inset)
        if (p.isUnspecified) return
        drawCircle(light.copy(alpha = 0.85f * (1f - k / 5f)), stroke * (0.2f - k * 0.025f), p)
    }
}

/** A few tiny stars just outside the ring, each twinkling on its own slow beat. */
private fun DrawScope.stars(color: Color, stroke: Float, t: Float) {
    val r = size.minDimension / 2f
    val light = lerp(color, Color.White, 0.7f)
    Stars.forEach { (deg, gap, dot, period) ->
        val a = (deg * PI / 180f).toFloat()
        val dist = r + gap * density
        val p = Offset(center.x + dist * cos(a), center.y + dist * sin(a))
        val k = wave(t, period, deg / 360f)
        val rad = dot * density * (0.7f + 0.5f * k)
        drawCircle(Brush.radialGradient(listOf(light.copy(alpha = 0.35f * k), Color.Transparent), p, rad * 4f), rad * 4f, p)
        drawCircle(light.copy(alpha = 0.2f + 0.75f * k), rad, p)
    }
}

/** The stars round Sleep's ring: angle (degrees from the right, clockwise), dp outside the ring, size in dp, period in s. */
private val Stars = listOf(
    floatArrayOf(-58f, 14f, 1.6f, 3.4f), floatArrayOf(-18f, 22f, 1.1f, 4.6f), floatArrayOf(28f, 10f, 1.4f, 3.9f),
    floatArrayOf(96f, 18f, 1.2f, 5.2f), floatArrayOf(152f, 12f, 1.7f, 4.2f), floatArrayOf(204f, 24f, 1.0f, 3.1f),
    floatArrayOf(246f, 9f, 1.3f, 4.8f),
)

/** A bar's fill growing from the start edge, [grow] (0 to 1) of the way. */
@Composable
private fun Modifier.growFromStart(grow: () -> Float): Modifier {
    val origin = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TransformOrigin(1f, 0.5f) else TransformOrigin(0f, 0.5f)
    return graphicsLayer { transformOrigin = origin; scaleX = grow().coerceIn(0f, 1f) }
}

/** The [i]th of [n] bars growing one after another as [grow] runs 0 to 1. */
private fun staged(grow: Float, i: Int, n: Int): Float = (grow * n - i).coerceIn(0f, 1f)

/** A metric's mark on its ring, where the ring starts at the top: [top] is how far down the ring's middle sits. */
@Composable
private fun BoxScope.RingMark(icon: ImageVector, top: Dp, size: Dp = 14.dp) {
    Icon(icon, null, tint = Color(0xFF0A0A0C), modifier = Modifier.align(Alignment.TopCenter).padding(top = top - size / 2).size(size))
}

/**
 * A figure in the ring's number type. [value] is a lambda read here, so a count-up recomposes this text alone, and not
 * the hero round it, on each step.
 */
@Composable
private fun RingNumber(value: () -> String, unit: String?, size: TextUnit) {
    val text = value()
    Text(
        buildAnnotatedString {
            append(text)
            if (unit != null) withStyle(SpanStyle(fontSize = size * 0.4f, color = Muted, fontWeight = FontWeight.Bold)) { append(unit) }
        },
        color = AppColors.ink, maxLines = 1,
        style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = size, textDirection = TextDirection.Ltr),
    )
}

/**
 * Today's hero in the rings design: Energy, Recovery and Sleep as three concentric rings, Energy outermost and its
 * level in the middle, with Energy's own line under them. Alive: the rings sweep in one after another, Energy's level
 * counts up, a light runs along Energy's arc and each tip glows on its metric's beat, over a faint wash of Energy's hue.
 */
@Composable
internal fun TripleRings(energy: FlaskModel, recovery: Int?, sleep: Int?, modifier: Modifier = Modifier, word: String? = null) {
    val e = animatedShare(energy.fill, 120)
    val r = animatedShare((recovery ?: 0) / 100f, 280)
    val s = animatedShare((sleep ?: 0) / 100f, 440)
    val clock = rememberLiveClock()
    val level = energy.level
    val shown = rememberSeenCount(level ?: 0)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
            LiveAura(RingColors.Energy, clock, Modifier.requiredSize(400.dp), strength = 0.9f)
            Canvas(Modifier.size(250.dp).graphicsLayer()) {
                val t = clock.value
                val w = 20.dp.toPx()
                val step = w + 5.dp.toPx()
                val eg = wave(t, 2.4f)
                val rg = heartbeat(t)
                val sg = wave(t, 6f)
                tipGlow(e.value, RingColors.Energy, w, 0f, eg)
                tipGlow(r.value, RingColors.Recovery, w, step, rg)
                tipGlow(s.value, RingColors.Sleep, w, step * 2, sg)
                ring(e.value, RingColors.Energy, w, 0f)
                ring(r.value, RingColors.Recovery, w, step)
                ring(s.value, RingColors.Sleep, w, step * 2)
                shimmer(e.value, RingColors.Energy, w, 0f, t)
                tipCore(e.value, RingColors.Energy, w, 0f, eg)
                tipCore(r.value, RingColors.Recovery, w, step, rg)
                tipCore(s.value, RingColors.Sleep, w, step * 2, sg)
            }
            RingMark(AppIcons.Lightning, 10.dp)
            RingMark(AppIcons.HeartFill, 35.dp)
            RingMark(AppIcons.MoonFill, 60.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RingNumber({ if (level != null) shown.value.toString() else "–" }, if (level != null) "%" else null, 40.sp)
                if (word != null) Text(word, color = AppColors.readable(RingColors.Energy), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp), maxLines = 1)
            }
        }
    }
}

/**
 * Recovery's hero in the rings design, taking what [PulseHero] took: the score in one thick ring with its word, the
 * three zones filled up to where you are, then the resting line and the sentence. Alive: the ring and its heart beat
 * in a lub-dub at about 64 bpm, the tip glowing on each beat, the score counts up and the zones fill one by one.
 */
@Composable
internal fun RingPulseHero(
    score: Int, word: String, line: String, sentence: String?, zones: Triple<String, String, String>,
    @Suppress("UNUSED_PARAMETER") restingHr: Int?, onOpen: () -> Unit, info: @Composable () -> Unit,
) {
    val share = animatedShare(score / 100f)
    val shown = rememberSeenCount(score)
    val grow = rememberSeenShare(1f, durationMs = 1100, delayMs = 420)
    val clock = rememberLiveClock()
    val hue = RingColors.Recovery
    Column(
        Modifier.fillMaxWidth().bounceClick(onClick = onOpen).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.align(Alignment.Center).padding(top = 8.dp).size(220.dp), contentAlignment = Alignment.Center) {
                LiveAura(hue, clock, Modifier.requiredSize(380.dp), strength = 0.75f)
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val k = 1f + 0.03f * heartbeat(clock.value)
                        scaleX = k
                        scaleY = k
                    },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 24.dp.toPx()
                        val g = heartbeat(clock.value)
                        tipGlow(share.value, hue, w, 0f, g)
                        ring(share.value, hue, w, 0f)
                        tipCore(share.value, hue, w, 0f, g)
                    }
                    RingMark(AppIcons.HeartFill, 12.dp, 16.dp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RingNumber({ shown.value.toString() }, "%", 56.sp)
                    Text(word, color = AppColors.readable(hue), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
            Box(Modifier.align(Alignment.TopEnd)) { info() }
        }
        Spacer(Modifier.height(20.dp))
        // The zones run start to end, so in Arabic rest sits on the right and the fill grows leftwards.
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { i ->
                val filled = ((score - i * 100f / 3f) / (100f / 3f)).coerceIn(0f, 1f)
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(hue.copy(alpha = 0.2f))) {
                    if (filled > 0f) Box(
                        Modifier.fillMaxWidth(filled).fillMaxHeight().growFromStart { staged(grow.value, i, 3) }
                            .clip(RoundedCornerShape(4.dp)).background(hue),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 6.dp, end = 6.dp)) {
            listOf(zones.first, zones.second, zones.third).forEach {
                Text(it, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
        if (sentence != null) {
            Spacer(Modifier.height(16.dp))
            RingAdviceCard(word, sentence, hue, Modifier.riseOnShow(3))
        }
        if (line.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                line, Modifier.riseOnShow(4).fillMaxWidth().clip(RingCard).background(AppColors.glass).padding(horizontal = 20.dp, vertical = 14.dp),
                color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Sleep's hero in the rings design, taking what [SkyHero] took plus the night's [score] for the ring: how long you
 * slept in the middle, the score's line, bedtime and waking, then each stage's bar. Alive: the ring breathes slowly
 * (one breath in 6 s), a few tiny stars twinkle round it, the score counts up and the stage bars grow in.
 */
@Composable
internal fun RingSkyHero(
    hours: Long, minutes: Long, units: Pair<String, String>, line: String, start: String, end: String,
    stages: List<StageLine>, onOpen: () -> Unit, score: Int? = null,
) {
    val share = animatedShare((score ?: 0) / 100f)
    val shown = rememberSeenCount(score ?: 0)
    val grow = rememberSeenShare(1f, durationMs = 1300, delayMs = 380)
    val clock = rememberLiveClock()
    val hue = RingColors.Sleep
    val glass = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().bounceClick(onClick = onOpen).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 8.dp).size(220.dp), contentAlignment = Alignment.Center) {
            LiveAura(hue, clock, Modifier.requiredSize(380.dp), strength = 0.85f)
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val k = 1f + 0.025f * wave(clock.value, 6f)
                    scaleX = k
                    scaleY = k
                },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val t = clock.value
                    val w = 24.dp.toPx()
                    val g = wave(t, 6f)
                    stars(hue, w, t)
                    tipGlow(share.value, hue, w, 0f, g)
                    ring(share.value, hue, w, 0f)
                    tipCore(share.value, hue, w, 0f, g)
                }
                RingMark(AppIcons.MoonFill, 12.dp, 16.dp)
            }
            val asleep = buildAnnotatedString {
                append(hours.toString()); withStyle(SpanStyle(color = Muted, fontWeight = FontWeight.Medium)) { append(" ${units.first}  ") }
                append(minutes.toString()); withStyle(SpanStyle(color = Muted, fontWeight = FontWeight.Medium)) { append(" ${units.second}") }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The night's score leads when there is one; the time asleep sits under it, or alone without a score.
                if (score != null) RingNumber({ shown.value.toString() }, null, 56.sp)
                Text(
                    asleep, color = AppColors.ink, maxLines = 1,
                    style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = if (score != null) 17.sp else 38.sp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(line, Modifier.fillMaxWidth(), color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.riseOnShow(2).fillMaxWidth().clip(glass).background(AppColors.glass).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(start, color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            Text(end, color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        if (stages.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.riseOnShow(3).fillMaxWidth().clip(glass).background(AppColors.glass).padding(horizontal = 16.dp, vertical = 8.dp)) {
                // The night in one bar, each stage as wide as its share, unrolling from the start.
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp).height(22.dp).growFromStart { grow.value },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    stages.forEach { s -> Box(Modifier.weight(s.share.coerceAtLeast(0.02f)).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(s.color)) }
                }
                stages.forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                        Spacer(Modifier.width(10.dp))
                        Text(s.name, Modifier.widthIn(min = 68.dp, max = 104.dp), color = AppColors.ink, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(AppColors.ink.copy(alpha = 0.1f))) {
                            Box(
                                Modifier.fillMaxWidth(s.share.coerceIn(0.02f, 1f)).fillMaxHeight()
                                    .growFromStart { staged(grow.value, i, stages.size) }
                                    .clip(RoundedCornerShape(4.dp)).background(s.color),
                            )
                        }
                        Text(
                            s.time, Modifier.widthIn(min = 72.dp), color = AppColors.ink, textAlign = TextAlign.End, maxLines = 1,
                            style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Activity's hero in the rings design, taking what [PathHero] took: the day's steps in a ring of the step goal. Alive:
 * the steps count up, a small light laps the ring's track every 8 s and the tip glows softly.
 */
@Composable
internal fun RingPathHero(steps: Int, goal: Int, stepsWord: String, line: String, onOpen: () -> Unit) {
    val share = animatedShare(if (goal > 0) steps / goal.toFloat() else 0f)
    val shown = rememberSeenCount(steps, durationMs = 1400)
    val clock = rememberLiveClock()
    val hue = RingColors.Steps
    val n = remember { java.text.NumberFormat.getIntegerInstance(java.util.Locale.US) }
    Column(
        Modifier.fillMaxWidth().bounceClick(onClick = onOpen).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
            LiveAura(hue, clock, Modifier.requiredSize(390.dp), strength = 0.75f)
            Canvas(Modifier.size(230.dp).graphicsLayer()) {
                val t = clock.value
                val w = 24.dp.toPx()
                val g = wave(t, 2.8f)
                tipGlow(share.value, hue, w, 0f, g)
                ring(share.value, hue, w, 0f)
                orbit(hue, w, 0f, t, 8f)
                tipCore(share.value, hue, w, 0f, g)
            }
            RingMark(AppIcons.SneakerMoveFill, 12.dp, 16.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Sized by the final figure, so the number doesn't jump size while it counts up.
                val size = n.format(steps).length
                RingNumber({ n.format(shown.value) }, null, if (size >= 6) 34.sp else if (size == 5) 40.sp else 50.sp)
                Text(stepsWord, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(line, color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
    }
}

private val RingCard = RoundedCornerShape(22.dp)
private val OnHue = Color(0xFF0A0A0C)

/**
 * One of Today's three figures as a solid card: its name, the number, a bar of it in the metric's hue, and a word for
 * the level so the colour never says it alone ([word]); while there's no figure, [empty] says why in its place.
 */
@Composable
internal fun RingStatCard(
    label: String, value: Int?, hue: Color, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null,
    word: String? = null, empty: String? = null,
) {
    val shown = rememberSeenCount(value ?: 0)
    val grow = rememberSeenShare(1f, durationMs = 1000, delayMs = 300)
    Column(modifier.bounceClick(onClick = onClick).clip(RoundedCornerShape(20.dp)).background(AppColors.glass).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = AppColors.readable(hue), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(label, color = Muted, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(2.dp))
        RingNumber({ if (value != null) shown.value.toString() else "–" }, null, 26.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(hue.copy(alpha = 0.2f))) {
            val filled = (value ?: 0).coerceIn(0, 100) / 100f
            if (filled > 0f) Box(Modifier.fillMaxWidth(filled).fillMaxHeight().growFromStart { grow.value }.clip(RoundedCornerShape(3.dp)).background(hue))
        }
        val line = if (value != null) word else empty
        if (line != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                line, color = if (value != null) AppColors.readable(hue) else Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp, fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal),
            )
        }
    }
}

/** The day's Energy as one bar: where it stood at waking, faint, and where it stands now, solid, with Energy's line. */
@Composable
internal fun EnergyDayCard(energy: FlaskModel, startLabel: String, modifier: Modifier = Modifier) {
    val said = listOfNotNull(energy.word, energy.line.takeIf { it.isNotEmpty() }).joinToString("، ")
    val now = energy.level ?: return
    val hue = RingColors.Energy
    val bar = RoundedCornerShape(7.dp)
    val grow = rememberSeenShare(1f, durationMs = 1200, delayMs = 360)
    Column(modifier.fillMaxWidth().clip(RingCard).background(AppColors.glass).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(said, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp))
            energy.atWake?.let { Text("$startLabel $it", color = AppColors.ink, style = MaterialTheme.typography.labelMedium) }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(14.dp).clip(bar).background(AppColors.ink.copy(alpha = 0.1f))) {
            energy.atWake?.let { Box(Modifier.fillMaxWidth((it.coerceIn(0, 100) / 100f).coerceAtLeast(0.02f)).fillMaxHeight().growFromStart { grow.value }.clip(bar).background(hue.copy(alpha = 0.35f))) }
            Box(Modifier.fillMaxWidth((now.coerceIn(0, 100) / 100f).coerceAtLeast(0.02f)).fillMaxHeight().growFromStart { grow.value * grow.value }.clip(bar).background(hue))
        }
    }
}

/** What to do with the day, on a card filled with the metric's [hue], in dark ink. */
@Composable
internal fun RingAdviceCard(title: String, note: String, hue: Color, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(RingCard).background(hue).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, color = OnHue, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(2.dp))
        Text(note, color = OnHue, style = MaterialTheme.typography.bodyMedium)
    }
}
