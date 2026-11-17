package org.openstrap.app.ui.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.NumberFamily

/*
 * The rings design on the workout pages (T-Workouts, T-Theme): solid cards without an edge, bold figures, round pills,
 * one thick ring for the hero, and the one main call to action filled in a hue with dark ink.
 */

/** Dark ink on a filled hue (the main button, a status pill). */
internal val RingInk = Color(0xFF0A0A0C)

/** A card: 22 dp corners, no edge. */
internal val RingCardShape = RoundedCornerShape(22.dp)

/** A status pill. */
internal val RingPillShape = RoundedCornerShape(12.dp)

/** A sport's or a metric's mark: its icon in a 40 dp tile of its hue, dimmed. */
internal val RingTileShape = RoundedCornerShape(14.dp)

/** The colour of starting a workout: Energy's, as the theme's main button has it. */
internal val StartHue: Color get() = org.openstrap.app.ui.glow.RingColors.Energy

/** A figure on the workout pages: the number face, bold, left to right. */
internal fun ringFigure(size: TextUnit, weight: FontWeight = FontWeight.ExtraBold): TextStyle = TextStyle(
    fontFamily = NumberFamily, fontWeight = weight, fontSize = size, lineHeight = size * 1.12f,
    fontFeatureSettings = "tnum", textDirection = TextDirection.Ltr,
)

/** A page's title in the rings design: ExtraBold. */
internal val RingPageTitle: TextStyle get() = GlowType.pageTitle.copy(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp)

/** A card's title: bold, a step under the page's. */
internal val RingCardTitle: TextStyle get() = GlowType.cardTitle.copy(fontWeight = FontWeight.Bold)

/**
 * One thick ring, round capped, filling [share] (0 to 1) of itself clockwise from the top in [hue] over its track (the
 * hue at 20%), drawing in when it first shows. [icon] sits on the ring where it starts; [content] in the middle.
 */
@Composable
internal fun WorkoutRing(
    share: Float,
    hue: Color,
    modifier: Modifier = Modifier,
    ringSize: Dp = 200.dp,
    stroke: Dp = 22.dp,
    icon: ImageVector? = null,
    /** A hero's ring: a softly pulsing light at its tip and a slow shimmer travelling along the arc once it's drawn. */
    alive: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(share) { a.animateTo(share.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing)) }
    val tip = if (alive) org.openstrap.app.ui.glow.rememberPulse(2_400) else null
    val shimmer = if (alive) org.openstrap.app.ui.glow.rememberLoop(4_200) else null
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ringSize)) {
            val w = stroke.toPx()
            val d = size.minDimension - w
            if (d > 0f) {
                val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                drawArc(hue.copy(alpha = 0.2f), 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
                val sweep = 360f * a.value
                if (sweep > 0f) drawArc(hue, -90f, sweep, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
                // The shimmer: a short pale stretch running from the start to the tip, only along what's filled.
                if (shimmer != null && sweep > 40f) {
                    val len = 26f
                    val at = -90f + (sweep - len) * shimmer.value
                    drawArc(
                        Color.White.copy(alpha = 0.22f * kotlin.math.sin(shimmer.value * Math.PI).toFloat()), at, len, false, tl,
                        Size(d, d), style = Stroke(w * 0.5f, cap = StrokeCap.Round),
                    )
                }
                // The tip's light, breathing.
                if (tip != null && sweep > 0f) {
                    val rad = Math.toRadians((-90f + sweep).toDouble())
                    val c = Offset(center.x + d / 2f * kotlin.math.cos(rad).toFloat(), center.y + d / 2f * kotlin.math.sin(rad).toFloat())
                    val glow = w * (0.85f + 0.35f * tip.value)
                    drawCircle(
                        androidx.compose.ui.graphics.Brush.radialGradient(listOf(hue.copy(alpha = 0.55f * (0.6f + 0.4f * tip.value)), Color.Transparent), c, glow),
                        glow, c,
                    )
                }
            }
        }
        if (icon != null) {
            val mark = (stroke * 0.66f).coerceAtMost(16.dp)
            Icon(icon, null, tint = RingInk, modifier = Modifier.align(Alignment.TopCenter).padding(top = stroke / 2 - mark / 2).size(mark))
        }
        content()
    }
}

/** A thin bar of [share] in [hue] over its track, as the theme's metric card has under its number. */
@Composable
internal fun RingBar(share: Float, hue: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val shape = RoundedCornerShape(height / 2)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(hue.copy(alpha = 0.2f))) {
        val s = share.coerceIn(0f, 1f)
        if (s > 0f) Box(Modifier.fillMaxWidth(s).fillMaxHeight().clip(shape).background(hue))
    }
}

/** The time in each zone as one segmented bar (T-Workouts' rows): each zone as wide as its share, 3 dp apart. */
@Composable
internal fun ZoneSegments(seconds: List<Int>, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val total = seconds.sum()
    if (total <= 0) return
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        seconds.forEachIndexed { i, s ->
            if (s > 0) Box(
                Modifier.weight(s.toFloat() / total).fillMaxHeight().clip(RoundedCornerShape(height / 2))
                    .background(ZONE_COLORS.getOrElse(i) { ZONE_COLORS.last() }),
            )
        }
    }
}

/** A status word as a pill filled in [hue] with dark ink. */
@Composable
internal fun RingStatusPill(text: String, hue: Color, modifier: Modifier = Modifier) {
    Text(
        text, modifier.clip(RingPillShape).background(hue).padding(horizontal = 12.dp, vertical = 4.dp),
        color = RingInk, style = GlowType.secondary.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), maxLines = 1,
    )
}

/** A sport's mark: its [icon] in its [hue] on a 40 dp tile of the hue at 20%. */
@Composable
internal fun RingSportTile(icon: ImageVector, hue: Color, modifier: Modifier = Modifier, tile: Dp = 40.dp) {
    Box(modifier.size(tile).clip(RingTileShape).background(hue.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = AppColors.readable(hue), modifier = Modifier.size(tile * 0.5f))
    }
}

/** Room between a mark and its words in a row. */
internal val RingRowGap: Dp = 12.dp

@Composable
internal fun RingGap() = Spacer(Modifier.width(RingRowGap))
