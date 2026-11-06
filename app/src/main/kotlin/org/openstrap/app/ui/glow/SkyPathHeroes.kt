package org.openstrap.app.ui.glow

import androidx.compose.animation.core.Animatable
import org.openstrap.app.ui.theme.AppColors
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.theme.NumberFamily
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private val Muted: Color get() = AppColors.muted
private val Track: Color get() = AppColors.ink.copy(alpha = 0.15f)
private val SleepBlue = Color(0xFF8FB4FF)

/** One stage's line on the Sky hero: its name, its share of the longest stage, its time, its colour. */
@androidx.compose.runtime.Immutable
internal data class StageLine(val name: String, val share: Float, val time: String, val color: Color)

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float)

private val Stars = Random(7).let { rnd -> List(34) { Star(rnd.nextFloat(), rnd.nextFloat() * 0.8f, 1f + rnd.nextFloat() * 0.9f, rnd.nextFloat()) } }

/**
 * The redesign's Sleep hero, "Sky": stars twinkling over the night, the moon travelling along an arc from bedtime to
 * waking, how long you slept, the score's word, and each stage's bar filling in.
 */
@Composable
internal fun SkyHero(
    hours: Long, minutes: Long, units: Pair<String, String>, line: String, start: String, end: String,
    stages: List<StageLine>, onOpen: () -> Unit,
) {
    val sky = rememberInfiniteTransition(label = "sky")
    val twinkle by sky.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "twinkle")
    val moon = remember { Animatable(0f) }
    LaunchedEffect(Unit) { moon.animateTo(1f, tween(2600, easing = FastOutSlowInEasing)) }
    val fill = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fill.animateTo(1f, tween(1200, 400, FastOutSlowInEasing)) }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onOpen).padding(vertical = 6.dp)) {
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Column {
                Canvas(Modifier.fillMaxWidth().aspectRatio(360f / 190f)) {
                    val w = size.width
                    val h = size.height
                    Stars.forEach { s ->
                        val a = 0.2f + 0.8f * (0.5f + 0.5f * sin(2 * PI * (twinkle + s.phase)).toFloat())
                        drawCircle(AppColors.ink.copy(alpha = a), s.r.dp.toPx(), Offset(s.x * w, s.y * h))
                    }
                    // Bedtime at the start of the line (the right in Arabic), waking at its end.
                    val p0 = Offset(w * (if (rtl) 330 else 30) / 360, h * 175 / 190)
                    val c = Offset(w * 180 / 360, -h * 10 / 190)
                    val p1 = Offset(w * (if (rtl) 30 else 330) / 360, h * 175 / 190)
                    val arc = Path().apply { moveTo(p0.x, p0.y); quadraticTo(c.x, c.y, p1.x, p1.y) }
                    drawPath(
                        arc, AppColors.ink.copy(alpha = 0.25f),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))),
                    )
                    val t = moon.value
                    val u = 1 - t
                    val at = Offset(u * u * p0.x + 2 * u * t * c.x + t * t * p1.x, u * u * p0.y + 2 * u * t * c.y + t * t * p1.y)
                    val r = 15.dp.toPx()
                    drawCircle(AppColors.ink, r, at)
                    // The crescent's cut in the sky's own colour, so it reads as a moon on every theme.
                    drawCircle(AppColors.current.skyMid, r, at + Offset(r * (if (rtl) -0.47f else 0.47f), -r * 0.33f))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Text(start, color = Muted, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(end, color = Muted, style = MaterialTheme.typography.labelMedium)
                }
        }
        val big = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraLight, fontSize = 92.sp, lineHeight = 96.sp, letterSpacing = (-4).sp)
        Text(
            buildAnnotatedString {
                append(hours.toString()); withStyle(SpanStyle(fontSize = 22.sp, color = Muted)) { append(" ${units.first}  ") }
                append(minutes.toString()); withStyle(SpanStyle(fontSize = 22.sp, color = Muted)) { append(" ${units.second}") }
            },
            Modifier.fillMaxWidth().padding(top = 6.dp), color = AppColors.ink, style = big, textAlign = TextAlign.Center,
        )
        Text(line, Modifier.fillMaxWidth(), color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        if (stages.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(GlassFill).border(1.dp, GlassEdge, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                stages.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, Modifier.width(52.dp), color = AppColors.ink, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        Ltr {
                            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(AppColors.ink.copy(alpha = 0.1f))) {
                                Box(Modifier.fillMaxWidth((s.share * fill.value).coerceIn(0.02f, 1f)).height(3.dp).clip(RoundedCornerShape(2.dp)).background(s.color))
                            }
                        }
                        Text(
                            s.time, Modifier.width(64.dp), color = AppColors.ink, textAlign = TextAlign.End,
                            style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.Light, fontSize = 16.sp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The redesign's Activity hero, "Path": a winding trail and a walker moving along it to the day's share of the step
 * goal, a flag at the end, the steps counting up over it and what's left under them. In Arabic the trail starts on the right.
 */
@Composable
internal fun PathHero(steps: Int, goal: Int, stepsWord: String, line: String, onOpen: () -> Unit) {
    val share = if (goal > 0) (steps / goal.toFloat()).coerceIn(0f, 1f) else 0f
    val walk = remember { Animatable(0f) }
    LaunchedEffect(share) { walk.animateTo(share, tween(2200, easing = FastOutSlowInEasing)) }
    val count = remember { Animatable(0f) }
    LaunchedEffect(steps) { count.animateTo(steps.toFloat(), tween(1500, easing = FastOutSlowInEasing)) }
    val mirror = LocalLayoutDirection.current == LayoutDirection.Rtl
    val n = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(360f / 320f)) {
            val sx = size.width / 360f
            val sy = size.height / 320f
            fun p(x: Float, y: Float) = Offset((if (mirror) 360 - x else x) * sx, y * sy)
            val path = Path().apply {
                val a = p(40f, 300f); moveTo(a.x, a.y)
                listOf(
                    Triple(p(40f, 220f), p(320f, 240f), p(320f, 170f)),
                    Triple(p(320f, 100f), p(40f, 120f), p(60f, 60f)),
                    Triple(p(80f, 0f), p(300f, 40f), p(320f, 20f)),
                ).forEach { (c1, c2, e) -> cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y) }
            }
            val wide = 2.5.dp.toPx()
            drawPath(path, AppColors.ink.copy(alpha = 0.12f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            val measure = PathMeasure().apply { setPath(path, false) }
            val len = measure.length
            val upTo = len * walk.value
            if (upTo > 0f) {
                val done = Path()
                measure.getSegment(0f, upTo, done, true)
                drawPath(done, androidx.compose.ui.graphics.Brush.linearGradient(listOf(AppColors.ink, Color(0xFF7FE3C0))), style = Stroke(wide, cap = StrokeCap.Round))
            }
            val flag = p(320f, 20f)
            drawCircle(Color(0xFF7FE3C0), 3.5.dp.toPx(), flag, style = Stroke(1.3.dp.toPx()))
            val at = measure.getPosition(upTo)
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x997FE3C0), Color.Transparent), at, 16.dp.toPx()), 16.dp.toPx(), at)
            drawCircle(Color(0xFFF4FFF9), 4.5.dp.toPx(), at)
        }
        val shade = androidx.compose.ui.graphics.Shadow(AppColors.page, blurRadius = 18f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                n.format(count.value.toInt()), color = AppColors.ink,
                style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraLight, fontSize = 72.sp, lineHeight = 76.sp, letterSpacing = (-3).sp, shadow = shade),
            )
            Text("$stepsWord · $line", color = Muted, style = MaterialTheme.typography.bodyMedium.copy(shadow = shade))
        }
    }
}
