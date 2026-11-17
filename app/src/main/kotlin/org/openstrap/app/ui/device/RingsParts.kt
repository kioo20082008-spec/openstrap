package org.openstrap.app.ui.device

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import org.openstrap.app.ui.glow.rememberLoop
import org.openstrap.app.ui.glow.rememberPulse
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.R
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.glow.BackButton
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageHeaderHeight
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.FunnelDisplay
import org.openstrap.app.ui.theme.FunnelSans
import org.openstrap.app.ui.theme.NumberFamily

/*
 * The rings theme for the You tab and the settings pages (You, Strap and what it opens, Alarms, Firmware, Zepp):
 * flat solid cards with no edge, rows with a hairline between them, status words as pills, figures in bold, and
 * the chosen segment filled with the ink. The same names as the page parts they stand in for, prefixed Rings, so a
 * page swaps one for the other without changing what it shows.
 */

/** A card's corners in the rings theme. */
val RingsCorner: Dp = 22.dp
val RingsShape = RoundedCornerShape(RingsCorner)

/** How far a card's rows sit in from its sides. */
val RingsRowInset: Dp = 16.dp

/** Dark ink on a card or pill filled with a metric's hue. */
val RingsOnHue = Color(0xFF0A0A0C)

/** The rings theme's type for these pages. */
object RingsType {
    /** A page title: ExtraBold 26. */
    val title get() = TextStyle(textDirection = TextDirection.ContentOrLtr, fontFamily = FunnelDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp)

    /** A card's title: Bold 17. */
    val cardTitle get() = TextStyle(textDirection = TextDirection.ContentOrLtr, fontFamily = FunnelDisplay, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp)

    /** A section's name over a card: Medium 13, muted. */
    val section get() = TextStyle(textDirection = TextDirection.ContentOrLtr, fontFamily = FunnelSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)

    /** A pill's word: Bold 12. */
    val pill get() = TextStyle(textDirection = TextDirection.ContentOrLtr, fontFamily = FunnelSans, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp)

    /** A chosen segment's word: Bold 13. */
    val segmentOn get() = TextStyle(textDirection = TextDirection.ContentOrLtr, fontFamily = FunnelSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp)

    /** Another segment's word: Regular 13. */
    val segmentOff get() = TextStyle(textDirection = TextDirection.ContentOrLtr, fontFamily = FunnelSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)

    /** A figure, bold, [size] tall, left to right. */
    fun figure(size: TextUnit = 26.sp) = TextStyle(
        textDirection = TextDirection.Ltr, fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = size,
        lineHeight = size * 1.15f, fontFeatureSettings = "tnum",
    )
}

/**
 * The page header in the rings theme: the round back button and the [title] in ExtraBold, a small [subtitle] under it.
 * Same parameters as the glow PageHeader it stands in for.
 */
@Composable
fun RingsHeader(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    subtitle: String? = null,
    onTitle: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val switchLabel = stringResource(R.string.glow_page_switch)
    Row(Modifier.fillMaxWidth().riseIn().heightIn(min = PageHeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        BackButton(onBack, backEnabled)
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                .let { if (onTitle != null) it.clickable(onClickLabel = switchLabel, role = Role.Button, onClick = onTitle) else it },
        ) {
            Text(title, color = AppColors.ink, style = RingsType.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = AppColors.muted, style = GlowType.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A section's name over its card, muted, a little in from the card's edge. */
@Composable
fun RingsSection(text: String, modifier: Modifier = Modifier, inset: Dp = 4.dp) {
    Text(
        text, modifier.riseIn().padding(start = inset, top = 10.dp), color = AppColors.muted, style = RingsType.section,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** A flat solid card in the rings theme: no edge, no glow, [RingsShape]. */
@Composable
fun RingsCard(
    modifier: Modifier = Modifier,
    color: Color = AppColors.glass,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.riseIn().fillMaxWidth().clip(RingsShape)
            .let { if (onClick != null) it.pressClickable(onClick, RingsShape, 0.98f) else it }
            .background(color, RingsShape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** A card of rows (PageRow, PageSwitchRow and the like) with the rows' own hairlines between them. */
@Composable
fun RingsRows(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.riseIn().fillMaxWidth().clip(RingsShape).background(AppColors.glass, RingsShape)
            .padding(horizontal = RingsRowInset, vertical = 2.dp),
        content = content,
    )
}

/** A card for free content with an optional bold [title] and a muted [note] beside it. */
@Composable
fun RingsPanel(modifier: Modifier = Modifier, title: String? = null, note: String? = null, content: @Composable ColumnScope.() -> Unit) =
    RingsCard(modifier) {
        if (title != null || note != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title.orEmpty(), Modifier.weight(1f), color = AppColors.ink, style = RingsType.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (note != null) {
                Spacer(Modifier.width(12.dp))
                Text(note, color = AppColors.muted, style = GlowType.secondary, maxLines = 1)
            }
        }
        content()
    }

/** A status word as a pill: filled with [color] in dark ink, or, with no colour, a quiet pill on the card. */
@Composable
fun RingsPill(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.clip(shape).background(color ?: AppColors.ink.copy(alpha = 0.10f), shape).padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (color != null) RingsOnHue else AppColors.ink, style = RingsType.pill, maxLines = 1)
    }
}

/**
 * A choice among a few: pills in a row, the chosen one filled with the ink and its word in [AppColors.onInk], the rest
 * outlined. Same parameters as the GlowSegments it stands in for; the pills share the width.
 */
@Composable
fun RingsSegments(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            RingsChoice(label, i == selected, { onSelect(i) }, Modifier.weight(1f), role)
        }
    }
}

/** One pill of a choice: filled with the ink when [on], outlined otherwise, 44 dp tall. */
@Composable
fun RingsChoice(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, role: Role = Role.RadioButton) {
    // The chosen fill swells in from the pill's middle while the outline fades, and drains back out when it moves on.
    val fill by animateFloatAsState(if (on) 1f else 0f, tween(300, easing = FastOutSlowInEasing), label = "choice fill")
    val word by animateColorAsState(if (on) AppColors.onInk else AppColors.ink, tween(300), label = "choice word")
    val ink = AppColors.ink
    Box(
        modifier.height(44.dp).clip(CircleShape)
            .pressClickable(onClick, CircleShape, 0.97f)
            .drawBehind {
                val f = fill
                val line = 1.dp.toPx()
                if (f < 1f) drawRoundRect(
                    ink.copy(alpha = 0.18f * (1f - f)), Offset(line / 2, line / 2), Size(size.width - line, size.height - line),
                    CornerRadius((size.height - line) / 2), style = Stroke(line),
                )
                if (f > 0f) {
                    val k = 0.55f + 0.45f * f
                    val w = size.width * k
                    val h = size.height * (0.7f + 0.3f * f)
                    drawRoundRect(ink.copy(alpha = f), Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h), CornerRadius(h / 2))
                }
            }
            .semantics { this.role = role; this.selected = on }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = word, style = if (on) RingsType.segmentOn else RingsType.segmentOff,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A thin bar in [hue] on its 20% track, filled to [fraction]. */
@Composable
fun RingsBar(fraction: Float, hue: Color, modifier: Modifier = Modifier.fillMaxWidth(), thickness: Dp = 6.dp) {
    // Fills in from empty on first show and eases to any later value; read only in draw.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(fraction) { shown.animateTo(fraction.coerceIn(0f, 1f), tween(1000, 150, FastOutSlowInEasing)) }
    Canvas(modifier.height(thickness)) {
        val r = size.height / 2
        drawLine(hue.copy(alpha = 0.2f), Offset(r, r), Offset(size.width - r, r), size.height, StrokeCap.Round)
        val f = shown.value
        if (f > 0f) {
            // From the start edge, so it fills right to left in Arabic.
            val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
            val len = (size.width - 2 * r) * f
            val from = if (rtl) size.width - r else r
            val to = if (rtl) from - len else from + len
            drawLine(hue, Offset(from, r), Offset(to, r), size.height, StrokeCap.Round)
        }
    }
}

/**
 * One thick ring in [hue] (round caps, from the top), its 20% track under it; the [content] sits in the middle. The
 * ring sweeps in from empty and eases to each new value. With [shimmer] (work in progress) a bright band travels along
 * the filled arc and its tip glows softly.
 */
@Composable
fun RingsGauge(
    fraction: Float,
    hue: Color,
    modifier: Modifier,
    stroke: Dp = 12.dp,
    shimmer: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(fraction) { shown.animateTo(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing)) }
    val run: State<Float> = if (shimmer) rememberLoop(1600) else StillState
    val glow: State<Float> = if (shimmer) rememberPulse(1400) else StillState
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val d = size.minDimension - s
            if (d > 0f) {
                val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                drawArc(hue.copy(alpha = 0.2f), 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
                val sweep = 360f * shown.value
                if (sweep > 0f) {
                    drawArc(if (shimmer) hue.copy(alpha = 0.72f) else hue, -90f, sweep, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
                    if (shimmer) {
                        // The band: 44 degrees wide, running from before the start to past the tip, cut to the arc.
                        val band = 44f
                        val from = -90f - band + (sweep + band) * run.value
                        val a = maxOf(from, -90f)
                        val b = minOf(from + band, -90f + sweep)
                        if (b > a) drawArc(hue, a, b - a, false, tl, Size(d, d), style = Stroke(s))
                        val tip = Math.toRadians((-90f + sweep).toDouble())
                        val c = Offset(center.x + (d / 2f) * kotlin.math.cos(tip).toFloat(), center.y + (d / 2f) * kotlin.math.sin(tip).toFloat())
                        drawCircle(hue.copy(alpha = 0.10f + 0.16f * glow.value), s * (0.8f + 0.35f * glow.value), c)
                        drawCircle(hue, s / 2f, c)
                    }
                }
            }
        }
        content()
    }
}

/** A resting value for motions that aren't running. */
private val StillState: State<Float> = mutableFloatStateOf(0f)
