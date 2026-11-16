package org.openstrap.app.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.NumberFamily

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

/** [target] (0 to 1) filling in from empty; read its value in a draw block so only the canvas redraws. */
@Composable
private fun animatedShare(target: Float): Animatable<Float, AnimationVector1D> {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target.coerceIn(0f, 1f), tween(1200, easing = FastOutSlowInEasing)) }
    return a
}

@Composable
private fun RingNumber(value: String, unit: String?, size: TextUnit) {
    Text(
        buildAnnotatedString {
            append(value)
            if (unit != null) withStyle(SpanStyle(fontSize = size * 0.4f, color = Muted, fontWeight = FontWeight.Bold)) { append(unit) }
        },
        color = AppColors.ink, maxLines = 1,
        style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = size, textDirection = TextDirection.Ltr),
    )
}

/**
 * Today's hero in the rings design: Energy, Recovery and Sleep as three concentric rings, Energy outermost and its
 * level in the middle, with Energy's own line under them.
 */
@Composable
internal fun TripleRings(energy: FlaskModel, recovery: Int?, sleep: Int?, modifier: Modifier = Modifier, word: String? = null) {
    val e = animatedShare(energy.fill)
    val r = animatedShare((recovery ?: 0) / 100f)
    val s = animatedShare((sleep ?: 0) / 100f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(250.dp)) {
                val w = 20.dp.toPx()
                val step = w + 5.dp.toPx()
                ring(e.value, RingColors.Energy, w, 0f)
                ring(r.value, RingColors.Recovery, w, step)
                ring(s.value, RingColors.Sleep, w, step * 2)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RingNumber(energy.level?.toString() ?: "–", if (energy.level != null) "%" else null, 40.sp)
                if (word != null) Text(word, color = AppColors.readable(RingColors.Energy), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/**
 * Recovery's hero in the rings design, taking what [PulseHero] took: the score in one thick ring with its word, the
 * three zones filled up to where you are, then the resting line and the sentence.
 */
@Composable
internal fun RingPulseHero(
    score: Int, word: String, line: String, sentence: String?, zones: Triple<String, String, String>,
    @Suppress("UNUSED_PARAMETER") restingHr: Int?, onOpen: () -> Unit, info: @Composable () -> Unit,
) {
    val share = animatedShare(score / 100f)
    val hue = RingColors.Recovery
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onOpen).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.align(Alignment.TopEnd)) { info() }
            Box(Modifier.align(Alignment.Center).padding(top = 8.dp).size(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(220.dp)) { ring(share.value, hue, 24.dp.toPx(), 0f) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RingNumber(score.toString(), "%", 56.sp)
                    Text(word, color = AppColors.readable(hue), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        // The zones run start to end, so in Arabic rest sits on the right and the fill grows leftwards.
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { i ->
                val filled = ((score - i * 100f / 3f) / (100f / 3f)).coerceIn(0f, 1f)
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(hue.copy(alpha = 0.2f))) {
                    if (filled > 0f) Box(Modifier.fillMaxWidth(filled).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(hue))
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
            RingAdviceCard(word, sentence, hue)
        }
        if (line.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                line, Modifier.fillMaxWidth().clip(RingCard).background(AppColors.glass).padding(horizontal = 20.dp, vertical = 14.dp),
                color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Sleep's hero in the rings design, taking what [SkyHero] took plus the night's [score] for the ring: how long you
 * slept in the middle, the score's line, bedtime and waking, then each stage's bar.
 */
@Composable
internal fun RingSkyHero(
    hours: Long, minutes: Long, units: Pair<String, String>, line: String, start: String, end: String,
    stages: List<StageLine>, onOpen: () -> Unit, score: Int? = null,
) {
    val share = animatedShare((score ?: 0) / 100f)
    val hue = RingColors.Sleep
    val glass = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onOpen).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 8.dp).size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp)) { ring(share.value, hue, 24.dp.toPx(), 0f) }
            Text(
                buildAnnotatedString {
                    append(hours.toString()); withStyle(SpanStyle(fontSize = 14.sp, color = Muted, fontWeight = FontWeight.Normal)) { append(" ${units.first}  ") }
                    append(minutes.toString()); withStyle(SpanStyle(fontSize = 14.sp, color = Muted, fontWeight = FontWeight.Normal)) { append(" ${units.second}") }
                },
                color = AppColors.ink, maxLines = 1,
                style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(line, Modifier.fillMaxWidth(), color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(glass).background(GlassFill).border(1.dp, GlassEdge, glass).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(start, color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            Text(end, color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        if (stages.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(glass).background(GlassFill).border(1.dp, GlassEdge, glass).padding(horizontal = 16.dp, vertical = 8.dp)) {
                // The night in one bar, each stage as wide as its share.
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp).height(22.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    stages.forEach { s -> Box(Modifier.weight(s.share.coerceAtLeast(0.02f)).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(s.color)) }
                }
                stages.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                        Spacer(Modifier.width(10.dp))
                        Text(s.name, Modifier.width(52.dp), color = AppColors.ink, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(AppColors.ink.copy(alpha = 0.1f))) {
                            Box(Modifier.fillMaxWidth(s.share.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(s.color))
                        }
                        Text(
                            s.time, Modifier.width(72.dp), color = AppColors.ink, textAlign = TextAlign.End,
                            style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp),
                        )
                    }
                }
            }
        }
    }
}

/** Activity's hero in the rings design, taking what [PathHero] took: the day's steps in a ring of the step goal. */
@Composable
internal fun RingPathHero(steps: Int, goal: Int, stepsWord: String, line: String, onOpen: () -> Unit) {
    val share = animatedShare(if (goal > 0) steps / goal.toFloat() else 0f)
    val n = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onOpen).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(230.dp)) { ring(share.value, RingColors.Steps, 24.dp.toPx(), 0f) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RingNumber(n.format(steps), null, 50.sp)
                Text(stepsWord, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(line, color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
    }
}

private val RingCard = RoundedCornerShape(22.dp)
private val OnHue = Color(0xFF0A0A0C)

/** One of Today's three figures as a solid card: its name, the number, and a bar of it in the metric's hue. */
@Composable
internal fun RingStatCard(label: String, value: Int?, hue: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(AppColors.glass).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        RingNumber(value?.toString() ?: "–", null, 26.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(hue.copy(alpha = 0.2f))) {
            val filled = (value ?: 0).coerceIn(0, 100) / 100f
            if (filled > 0f) Box(Modifier.fillMaxWidth(filled).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(hue))
        }
    }
}

/** The day's Energy as one bar: where it stood at waking, faint, and where it stands now, solid, with Energy's line. */
@Composable
internal fun EnergyDayCard(energy: FlaskModel, startLabel: String, modifier: Modifier = Modifier) {
    val now = energy.level ?: return
    val hue = RingColors.Energy
    val bar = RoundedCornerShape(7.dp)
    Column(modifier.fillMaxWidth().clip(RingCard).background(AppColors.glass).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(energy.line, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelMedium)
            energy.atWake?.let { Text("$startLabel $it", color = AppColors.ink, style = MaterialTheme.typography.labelMedium) }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(14.dp).clip(bar).background(AppColors.ink.copy(alpha = 0.1f))) {
            energy.atWake?.let { Box(Modifier.fillMaxWidth((it.coerceIn(0, 100) / 100f).coerceAtLeast(0.02f)).fillMaxHeight().clip(bar).background(hue.copy(alpha = 0.35f))) }
            Box(Modifier.fillMaxWidth((now.coerceIn(0, 100) / 100f).coerceAtLeast(0.02f)).fillMaxHeight().clip(bar).background(hue))
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
