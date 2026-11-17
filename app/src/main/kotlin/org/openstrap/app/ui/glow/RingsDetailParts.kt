package org.openstrap.app.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.NumberFamily

/*
 * The rings design's detail pieces (T-Detail): the metric's one thick ring, the card of key figures, solid cards for
 * charts and lists, and status words as pills. Every detail page builds from these, so they read as one family.
 */

/** A detail's card: solid, 22 dp corners, no border. */
internal val RingDetailShape = RoundedCornerShape(22.dp)

/** Dark ink on a metric's hue (the ring's mark, an advice card). */
internal val RingOnHue = Color(0xFF0A0A0C)

/** Figures are bold: the rings design's number type at [size]. */
internal fun ringFigure(size: TextUnit, weight: FontWeight = FontWeight.ExtraBold) = TextStyle(
    fontFamily = NumberFamily, fontWeight = weight, fontSize = size, lineHeight = size * 1.25f,
    textDirection = TextDirection.ContentOrLtr, fontFeatureSettings = "tnum",
)

/** A card title inside a detail's card: 14 sp bold. */
internal val RingCardTitle: TextStyle get() = GlowType.body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)

/** A small label over a figure: 12 sp in the muted ink. */
internal val RingLabel: TextStyle get() = GlowType.secondary.copy(fontSize = 12.sp, lineHeight = 16.sp)

/**
 * The metric's one thick ring (T-Detail): a track in [hue] at 20%, [share] of it from the top, clockwise, round capped,
 * sweeping in from empty. [icon] sits on the ring's start in dark ink. [content] is centred inside. [life] is how the
 * ring lives once in ([DetailRingLife], by the metric's hue): its tip glows, and Energy shimmers, Recovery beats, Sleep
 * breathes under twinkling stars, Steps has a dot lapping its track. All of it is read in the canvas or a layer only.
 */
@Composable
internal fun DetailRing(
    share: Float,
    hue: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    diameter: Dp = 200.dp,
    stroke: Dp = 22.dp,
    life: DetailRingLife = detailRingLife(hue),
    content: @Composable ColumnScope.() -> Unit,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(share) { anim.animateTo(share.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing)) }
    val glow = rememberPulse(if (life == DetailRingLife.Breathe) 6_000 else 2_400)
    val loop: State<Float>? = when (life) {
        DetailRingLife.Shimmer -> rememberLoop(3_600)
        DetailRingLife.Heartbeat -> rememberLoop(920)
        DetailRingLife.Orbit -> rememberLoop(6_400)
        DetailRingLife.Breathe -> rememberLoop(9_000)
        DetailRingLife.Glow -> null
    }
    val light = lerp(hue, Color.White, 0.6f)
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Box(
            Modifier.matchParentSize().graphicsLayer {
                val s = when (life) {
                    DetailRingLife.Heartbeat -> 1f + 0.03f * detailHeartbeat(loop?.value ?: 0f)
                    DetailRingLife.Breathe -> 1f + 0.018f * glow.value
                    else -> 1f
                }
                scaleX = s
                scaleY = s
            },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val w = stroke.toPx()
                val d = size.minDimension - w
                val tl = Offset(w / 2f, w / 2f)
                val arc = Size(d, d)
                val r = d / 2f
                val c = center
                val breath = if (life == DetailRingLife.Breathe) 0.06f * glow.value else 0f
                drawArc(hue.copy(alpha = 0.2f + breath), 0f, 360f, false, tl, arc, style = Stroke(w))
                val sweep = 360f * anim.value
                if (sweep > 0f) {
                    drawArc(hue, -90f, sweep, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
                    // Energy's shimmer: a short bright run travelling from the ring's start to its tip.
                    if (life == DetailRingLife.Shimmer && loop != null && sweep > 24f) {
                        val head = -90f + sweep * loop.value
                        for (k in 0 until 5) {
                            val len = 7f
                            val from = (head - (k + 1) * len).coerceAtLeast(-90f)
                            val span = (head - k * len) - from
                            if (span > 0f) drawArc(
                                light.copy(alpha = 0.34f * (1f - k / 5f)), from, span, false, tl, arc, style = Stroke(w * 0.42f, cap = StrokeCap.Round),
                            )
                        }
                    }
                    // The tip's soft pulsing glow.
                    val a = Math.toRadians((-90f + sweep).toDouble())
                    val tip = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                    val g = (0.3f + 0.35f * glow.value) * if (life == DetailRingLife.Breathe) 0.7f else 1f
                    val gr = w * 1.35f
                    drawCircle(Brush.radialGradient(listOf(hue.copy(alpha = g), Color.Transparent), tip, gr), gr, tip)
                }
                // Steps: a small dot lapping the track, dark over the filled arc and light over the track.
                if (life == DetailRingLife.Orbit && loop != null) {
                    val deg = 360f * loop.value
                    val a = Math.toRadians((-90f + deg).toDouble())
                    val at = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                    drawCircle(if (deg <= sweep) RingOnHue.copy(alpha = 0.55f) else light.copy(alpha = 0.9f), w * 0.15f, at)
                }
                // Sleep: tiny stars round the ring, each twinkling on its own beat.
                if (life == DetailRingLife.Breathe && loop != null) {
                    for (i in 0 until 7) {
                        val ang = Math.toRadians((i * 51.4 + 23.0))
                        val rr = r + w * (0.95f + (i % 3) * 0.18f)
                        val tw = (sin(2f * PI.toFloat() * (loop.value * 3f + i / 7f)) * 0.5f + 0.5f)
                        val at = Offset(c.x + rr * cos(ang).toFloat(), c.y + rr * sin(ang).toFloat())
                        drawCircle(light.copy(alpha = 0.12f + 0.7f * tw * tw), (1f + (i % 2) * 0.7f).dp.toPx() * (0.7f + 0.5f * tw), at)
                    }
                }
            }
            if (icon != null) {
                val mark = (stroke * 0.66f)
                Icon(
                    icon, null, tint = RingOnHue,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = (stroke - mark) / 2).size(mark),
                )
            }
        }
        Column(
            Modifier.padding(horizontal = stroke + 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center, content = content,
        )
    }
}

/** A status word as a pill (rounded 12 dp): the metric's hue faint behind, the word in it, bold. */
@Composable
internal fun DetailStatusPill(word: String, hue: Color, modifier: Modifier = Modifier) {
    Text(
        word,
        modifier.clip(RoundedCornerShape(12.dp)).background(hue.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 5.dp),
        color = AppColors.readable(hue), style = GlowType.secondary.copy(fontWeight = FontWeight.Bold), maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The card of a detail's key figures (T-Detail): side by side, each its label over the figure in bold, a hairline
 * between them. A figure without a value shows [missing] dimmed.
 */
@Composable
internal fun RingFiguresCard(figures: List<Pair<String?, String>>, modifier: Modifier = Modifier, missing: String = "–") {
    if (figures.isEmpty()) return
    Row(
        modifier.detailRise().fillMaxWidth().clip(RingDetailShape).background(AppColors.glass).height(IntrinsicSize.Min).padding(vertical = 14.dp),
    ) {
        figures.forEachIndexed { i, (value, label) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(AppColors.hairline))
            Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = AppColors.muted, style = RingLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(
                    value ?: missing, color = if (value != null) AppColors.ink else AppColors.ink.copy(alpha = 0.3f),
                    style = ringFigure(20.sp), maxLines = 1, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A solid card for a detail's chart or block (T-Detail): [title] in bold at its top with an optional [note] at its
 * end, then [content].
 */
@Composable
internal fun DetailCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    note: String? = null,
    spacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.detailRise().fillMaxWidth().clip(RingDetailShape).background(AppColors.glass).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (title != null || note != null) Row(Modifier.fillMaxWidth().padding(bottom = (12.dp - spacing).coerceAtLeast(0.dp)), verticalAlignment = Alignment.CenterVertically) {
            Text(title.orEmpty(), Modifier.weight(1f), color = AppColors.ink, style = RingCardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, color = AppColors.muted, style = RingLabel, maxLines = 1)
        }
        content()
    }
}
