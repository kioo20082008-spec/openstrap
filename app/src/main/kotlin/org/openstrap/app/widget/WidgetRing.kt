package org.openstrap.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import org.openstrap.app.ui.glow.draw.drawIcon
import org.openstrap.app.ui.glow.draw.renderToBitmap

/*
 * The rings theme for widgets: a flat near-black card, no light, no gradient, and a metric's one thick ring in its hue
 * with the number bold in the middle and the metric's icon on the ring's start. Text in the card is white and muted grey.
 */

/** The rings theme's widget colours. Widgets stay dark whatever the app's theme, as the mockups draw them. */
internal object RingWidget {
    /** The widget's card. */
    val Page = Color(0xFF0A0A0C)

    /** Tiles and the status button inside the card. */
    val Card = Color(0xFF17171B)

    /** Secondary text. */
    val Muted = Color(0xFFA7A7B0)

    /** Dark ink, on a hue. */
    val Ink = Color(0xFF0A0A0C)

    /** The metric hues ([org.openstrap.app.ui.glow.RingColors]). */
    val Energy = Color(0xFFFF5C39)
    val Recovery = Color(0xFFC8FF3D)
    val Sleep = Color(0xFF8B7BFF)
    val SleepText = Color(0xFFB3A8FF)
    val Steps = Color(0xFF3DD6FF)

    /** The ring's stroke as a share of its diameter (the mockup's 13 in 100) and its track's alpha. */
    const val STROKE = 0.13f
    const val TRACK_ALPHA = 0.30f

    /** Stands for the day's summary, which wears no one metric's hue. */
    val SummaryHue = Color(0xFFFFB067)

    /**
     * The card's colours for [hue]: a deep shade of the metric's own colour at the top start, deeper at the bottom end,
     * so each widget wears its metric (Energy red, Recovery green, Sleep violet, Steps blue) rather than black.
     */
    fun card(hue: Color): Pair<Color, Color> = when (hue) {
        Recovery -> Color(0xFF4B6D10) to Color(0xFF1B2A06)
        Sleep -> Color(0xFF4C3FAE) to Color(0xFF1A1542)
        Steps -> Color(0xFF106B86) to Color(0xFF082A34)
        SummaryHue -> Color(0xFF45306F) to Color(0xFF3F1C1A)
        else -> Color(0xFF99361B) to Color(0xFF3D140C)
    }

    /** The title's sizes, largest first, and the word under it. */
    val TITLE_SIZES = listOf(15f, 13f, 12f)
    const val WORD_SP = 12f
    const val LINE_LABEL_SP = 12f
    const val LINE_VALUE_SP = 13f
    const val LINE_GAP_DP = 6f
    const val RING_GAP_DP = 12f
    const val BELOW_GAP_DP = 10f
    const val MIN_RING_DP = 48f
    const val MAX_RING_DP = 140f
}

/** A row beside the ring: a [label] in grey with its [value] bold at the far end, or the label alone; [dot] marks it. */
internal data class RingLine(val label: String, val value: String? = null, val dot: Color? = null)

/**
 * A metric widget in the rings theme at any of the three sizes: the top row ([title], [note] or the strap's status),
 * [word] in [wordColor] under it, then the ring ([share] of it in [hue], [number] in the middle, [icon] on its start)
 * at the far end. The Wide and Large put [lines] beside the ring; the Large adds [below] ([belowHeight] dp) under them.
 */
@Composable
internal fun ColumnScope.RingContent(
    title: String, note: String?, word: String?, wordColor: Color, hue: Color, share: Float, number: String?,
    description: String?, icon: ImageVector, f: WidgetFrame,
    lines: List<RingLine> = emptyList(),
    belowHeight: Float = 0f,
    below: (@Composable () -> Unit)? = null,
    ringImage: (@Composable (Float) -> Unit)? = null,
) {
    val ring: @Composable (Float) -> Unit = { d -> if (ringImage != null) ringImage(d) else RingImage(share, hue, number, icon, d, f.density, description) }
    val small = f.bucket == WidgetSizes.Small
    val top = TopRow.of(title, if (small) null else note, f.status, f.contentWidth, f.fontScale, withWords = !small, titleSizes = RingWidget.TITLE_SIZES)
    TopRowView(title, if (small) null else note, top, f, above = 0f)
    val wordH = if (word != null) WidgetText.line(RingWidget.WORD_SP, f.fontScale) else 0f
    if (small) {
        word?.let { Spacer(GlanceModifier.height(2.dp)); RingText(it, RingWidget.WORD_SP, wordColor, f.contentWidth) }
        val d = (f.contentHeight - top.height - wordH - 6f).coerceAtMost(f.contentWidth * 0.8f).coerceIn(RingWidget.MIN_RING_DP, RingWidget.MAX_RING_DP)
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            // The ring at the far end: the left in Arabic, as the mockups place it.
            if (WidgetRtl) { ring(d); Spacer(GlanceModifier.defaultWeight()) }
            else { Spacer(GlanceModifier.defaultWeight()); ring(d) }
        }
        return
    }
    val room = f.contentHeight - top.height - 8f - (if (below != null) belowHeight + RingWidget.BELOW_GAP_DP else 0f)
    val d = room.coerceAtMost(f.contentWidth * 0.45f).coerceIn(RingWidget.MIN_RING_DP, RingWidget.MAX_RING_DP)
    val textWidth = f.contentWidth - d - RingWidget.RING_GAP_DP
    val lineH = WidgetText.line(RingWidget.LINE_VALUE_SP, f.fontScale) + RingWidget.LINE_GAP_DP
    val shown = lines.take(((d - wordH - RingWidget.LINE_GAP_DP) / lineH).toInt().coerceIn(0, 4))
    Spacer(GlanceModifier.defaultWeight())
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val text: @Composable () -> Unit = {
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = if (WidgetRtl) Alignment.End else Alignment.Start) {
                word?.let { RingText(it, RingWidget.WORD_SP, wordColor, textWidth) }
                shown.forEach { l ->
                    Spacer(GlanceModifier.height(RingWidget.LINE_GAP_DP.dp))
                    LineRow(l, textWidth, f.density)
                }
            }
        }
        if (WidgetRtl) {
            ring(d)
            Spacer(GlanceModifier.width(RingWidget.RING_GAP_DP.dp))
            text()
        } else {
            text()
            Spacer(GlanceModifier.width(RingWidget.RING_GAP_DP.dp))
            ring(d)
        }
    }
    if (below != null) {
        Spacer(GlanceModifier.defaultWeight())
        below()
    }
}

/** [text] at [sp] in [color], in the app's font, no wider than [maxWidth] dp. */
@Composable
internal fun RingText(text: String, sp: Float, color: Color, maxWidth: Float, strong: Boolean = false) =
    FontText(text, sp, color, strong = strong, maxWidthDp = maxWidth.coerceAtLeast(24f))

/** A row beside the ring: the dot and label at the start, the value bold at the end. */
@Composable
private fun LineRow(l: RingLine, width: Float, density: Float) {
    val valueW = l.value?.let { WidgetText.fitWidth(it, RingWidget.LINE_VALUE_SP) + 8f } ?: 0f
    val labelW = (width - valueW - (if (l.dot != null) 14f else 0f)).coerceAtLeast(24f)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val dot: @Composable () -> Unit = {
            l.dot?.let {
                Image(ImageProvider(dotBitmap(it, density)), contentDescription = null, modifier = GlanceModifier.size(8.dp), contentScale = ContentScale.FillBounds)
                Spacer(GlanceModifier.width(6.dp))
            }
        }
        if (WidgetRtl) {
            l.value?.let { RingText(it, RingWidget.LINE_VALUE_SP, Color.White, valueW, strong = true) }
            Spacer(GlanceModifier.defaultWeight())
            RingText(l.label, RingWidget.LINE_LABEL_SP, RingWidget.Muted, labelW)
            if (l.dot != null) { Spacer(GlanceModifier.width(6.dp)); Image(ImageProvider(dotBitmap(l.dot, density)), contentDescription = null, modifier = GlanceModifier.size(8.dp), contentScale = ContentScale.FillBounds) }
        } else {
            dot()
            RingText(l.label, RingWidget.LINE_LABEL_SP, RingWidget.Muted, labelW)
            Spacer(GlanceModifier.defaultWeight())
            l.value?.let { RingText(it, RingWidget.LINE_VALUE_SP, Color.White, valueW, strong = true) }
        }
    }
}

/** The ring [d] dp across as an image, read out as [description]. */
@Composable
internal fun RingImage(share: Float, hue: Color, number: String?, icon: ImageVector?, d: Float, density: Float, description: String?) {
    val context = LocalContext.current
    Image(
        ImageProvider(ringBitmap(context, share, hue, number, icon, d, density)),
        contentDescription = description,
        modifier = GlanceModifier.size(d.dp),
        contentScale = ContentScale.FillBounds,
    )
}

/**
 * One thick ring [d] dp across at [density]: the track in [hue] at 20%, then [share] of it in [hue] from the top,
 * clockwise, round capped, [icon] in dark ink on its start and [number] bold and white in the middle.
 */
internal fun ringBitmap(context: Context, share: Float, hue: Color, number: String?, icon: ImageVector?, d: Float, density: Float): Bitmap {
    val s = d * density
    val stroke = s * RingWidget.STROKE
    return renderToBitmap(s, s) { c ->
        val rect = RectF(stroke / 2, stroke / 2, s - stroke / 2, s - stroke / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
        paint.color = hue.copy(alpha = RingWidget.TRACK_ALPHA).toArgb()
        c.drawArc(rect, 0f, 360f, false, paint)
        val sweep = 360f * share.coerceIn(0f, 1f)
        if (sweep > 0f) {
            paint.color = hue.toArgb()
            paint.strokeCap = Paint.Cap.ROUND
            c.drawArc(rect, -90f, sweep, false, paint)
        }
        if (icon != null && stroke >= 8f * density) {
            val i = stroke * 0.72f
            drawIcon(c, icon, if (sweep > 0f) RingWidget.Ink else hue, s / 2 - i / 2, stroke / 2 - i / 2, i)
        }
        if (number != null) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = WidgetFonts.number(context)
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = s * (if (number.length <= 2) 0.32f else 0.26f)
            }
            val room = (s - 2 * stroke) * 0.82f
            val w = text.measureText(number)
            if (w > room) text.textSize *= room / w
            val fm = text.fontMetrics
            c.drawText(number, s / 2, s / 2 - (fm.ascent + fm.descent) / 2, text)
        }
    }
}

/** The rings theme's flat card [w] by [h] dp with [corner] dp corners at [density]. */
internal fun flatBackground(w: Float, h: Float, corner: Float, density: Float, hue: Color = RingWidget.Energy): Bitmap {
    val pw = w * density
    val ph = h * density
    val r = corner * density
    val (from, to) = RingWidget.card(hue)
    return renderToBitmap(pw, ph) { c ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(0f, 0f, pw, ph, from.toArgb(), to.toArgb(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, pw, ph, r, r, paint)
        // A soft light of the hue in the top corner, so the card isn't a flat fill.
        val light = Paint(Paint.ANTI_ALIAS_FLAG)
        light.shader = android.graphics.RadialGradient(
            pw * 0.8f, 0f, pw * 0.9f, hue.copy(alpha = 0.28f).toArgb(), hue.copy(alpha = 0f).toArgb(), android.graphics.Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(0f, 0f, pw, ph, r, r, light)
    }
}

/** The day's three rings nested, Energy outside, then Recovery, then Sleep, [d] dp across at [density]: tracks at 30%, round arcs. */
internal fun nestedRingsBitmap(shares: List<Float>, hues: List<Color>, d: Float, density: Float): Bitmap {
    val s = d * density
    val stroke = s * 0.105f
    val gap = s * 0.035f
    return renderToBitmap(s, s) { c ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
        hues.forEachIndexed { i, hue ->
            val inset = stroke / 2 + i * (stroke + gap)
            val rect = RectF(inset, inset, s - inset, s - inset)
            paint.strokeCap = Paint.Cap.BUTT
            paint.color = hue.copy(alpha = RingWidget.TRACK_ALPHA).toArgb()
            c.drawArc(rect, 0f, 360f, false, paint)
            val sweep = 360f * shares.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            if (sweep > 0f) {
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = hue.toArgb()
                c.drawArc(rect, -90f, sweep, false, paint)
            }
        }
    }
}
