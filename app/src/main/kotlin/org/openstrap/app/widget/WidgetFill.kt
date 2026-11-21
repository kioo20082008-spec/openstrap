package org.openstrap.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.text.TextPaint
import android.util.LruCache
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
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * The «fill» look for the metric widgets (the owner's choice, 2026-11): a dark card tinted with the metric, liquid in
 * the metric's colour standing as high as the value in the room under the title, the title and its line at the top
 * start and the number large and light at the bottom end. The liquid is one bitmap a size and level (FillBitmaps),
 * its waves always the same shape so a refresh never moves them.
 */

/** A metric's colours in the fill look: its [hue] (the app's, [MetricColors]), the [card] behind and the liquid's [deep] end. */
enum class FillMetric(val hue: Color, val card: Color, val deep: Color, val lightFill: Boolean) {
    Energy(MetricColors.charge, Color(0xFF2C130F), Color(0xFFD9442A), false),

    /** Lime: the only liquid light enough that white doesn't read on it ([FillLevel.darkInk]). */
    Recovery(MetricColors.readiness, Color(0xFF1A2408), Color(0xFF7FA312), true),
    Sleep(MetricColors.sleepDeep, Color(0xFF1A1640), Color(0xFF6C5CE7), false),
    Steps(MetricColors.steps, Color(0xFF0A2530), Color(0xFF0E9CC4), false),
    ;

    /** The compact summary's icon over the column: a bolt, a heart, a moon. */
    val icon: ImageVector
        get() = when (this) {
            Energy -> AppIcons.Lightning
            Recovery -> AppIcons.Heart
            Sleep -> AppIcons.Moon
            Steps -> AppIcons.SneakerMoveFill
        }
}

/** The fill look's measures, dp and sp, from the mockups (a 182 px card drawn at about 1 px a dp). */
internal object FillLook {
    /** The content's inset: the sides and the top. The bottom is the number's own ([BASELINE_DP]). */
    const val SIDE_DP = 18f
    const val TOP_DP = 16f

    /** The line under the title ("Fair 8 h 11"), and the gap over each line under the title row. */
    const val STATUS_SP = 12f
    const val ROW_GAP_DP = 2f

    /** Between the lines at the top and the room the liquid stands in, and before a Large's chart. */
    const val FILL_GAP_DP = 10f
    const val BELOW_GAP_DP = 10f

    /** The number's baseline above the card's bottom, and the least liquid room over the number. */
    const val BASELINE_DP = 18f
    const val ROOM_ABOVE_DP = 12f

    /** The number's sizes, largest first: a Small's, a Wide's, a Large's. */
    val NUMERAL_SMALL = listOf(44f, 38f, 32f, 28f)
    val NUMERAL_WIDE = listOf(52f, 44f, 38f, 32f)
    val NUMERAL_LARGE = listOf(64f, 54f, 46f, 38f)

    /** Inter's cap height (the digits' height), the room over it for round digits and under it for a comma, em. */
    const val CAP = 0.7275f
    const val OVERSHOOT = 0.02f
    const val DESCENT = 0.14f

    /** The number's weight (Inter Light), tracking, and its "%" at this share of its size, at [UNIT_ALPHA]. */
    const val WEIGHT = 300
    const val TRACKING = -0.02f
    const val UNIT_SHARE = 0.36f
    const val UNIT_GAP = 0.04f
    const val UNIT_ALPHA = 0.7f

    /** The number's ink on the lime liquid; white everywhere else. */
    val DarkInk = Color(0xFF14210A)

    /** The line under the title, and a figure's label, in white at these alphas. */
    const val STATUS_ALPHA = 0.75f
    const val LABEL_ALPHA = 0.6f

    /** The back wave's alpha over the card (the mockup's 35%). */
    const val BACK_ALPHA = 0.35f

    /** The summary columns' hairline between them: black at 35%, half a dp on each column's edge. */
    const val HAIRLINE_ALPHA = 0.35f
    const val HAIRLINE_DP = 0.5f

    /** The space between a line and a figure beside it on one row. */
    const val BESIDE_GAP_DP = 12f
}

/* ------------------------------------------------------------------------------------------------ */
/* The plan: what fits, and where the liquid starts                                                  */
/* ------------------------------------------------------------------------------------------------ */

/** A row under the title: the [status] line at the start, a figure [line] at the end, either or both. */
internal data class FillRow(val status: String?, val line: RingLine?)

/**
 * A fill widget's layout at its cell: the title row [top] (the [note] or the strap's button at its end), the [rows]
 * under it, whether the Large's chart ([below]) shows, the number's size [numeral] and where the liquid's room starts,
 * [fillTop] dp from the card's top.
 */
internal data class FillPlan(
    val top: TopRow,
    val note: String?,
    val rows: List<FillRow>,
    val below: Boolean,
    val numeral: Float,
    val fillTop: Float,
    val rowHeight: Float,
    val width: Float,
)

/**
 * The layout for [f]'s cell (fitted at the real cell's smallest size, [WidgetFrame.fitWidth]): the title and [status]
 * always; on the Wide and Large the [note] on the title row and the [lines] at the end of the rows under it; on the
 * Large the chart under them ([belowHeight] dp, null for none). What doesn't fit goes, in this order: lines past the
 * first, the chart, the last line, then the number steps down, then the status line, so nothing is cut at the
 * smallest cell.
 */
internal fun fillPlan(f: WidgetFrame, title: String, note: String?, status: String?, lines: List<RingLine>, belowHeight: Float?): FillPlan {
    val small = f.bucket == WidgetSizes.Small
    val width = f.fitWidth - 2 * FillLook.SIDE_DP
    val top = TopRow.of(title, if (small) null else note, f.status, width, f.fontScale, withWords = !small, titleSizes = RingWidget.TITLE_SIZES)
    val rowH = WidgetText.line(FillLook.STATUS_SP, f.fontScale)
    val shown = if (small) emptyList() else lines
    val ladder = when (f.bucket) {
        WidgetSizes.Small -> FillLook.NUMERAL_SMALL
        WidgetSizes.Wide -> FillLook.NUMERAL_WIDE
        else -> FillLook.NUMERAL_LARGE
    }
    fun lineWidth(l: RingLine) = WidgetText.fitWidth(l.label, FillLook.STATUS_SP, f.fontScale) +
        (l.value?.let { 4f + WidgetText.fitWidth(it, FillLook.STATUS_SP, f.fontScale) } ?: 0f)
    fun rows(s: String?, n: Int): List<FillRow> {
        var rest = shown.take(n)
        val out = mutableListOf<FillRow>()
        if (s != null) {
            val beside = rest.firstOrNull()?.takeIf {
                WidgetText.fitWidth(s, FillLook.STATUS_SP, f.fontScale) + FillLook.BESIDE_GAP_DP + lineWidth(it) <= width
            }
            out += FillRow(s, beside)
            if (beside != null) rest = rest.drop(1)
        }
        rest.forEach { out += FillRow(null, it) }
        return out
    }
    fun plan(s: String?, n: Int, below: Boolean, numeral: Float): FillPlan {
        val r = rows(s, n)
        val block = top.height + r.size * (rowH + FillLook.ROW_GAP_DP) +
            (if (below && belowHeight != null) FillLook.BELOW_GAP_DP + belowHeight else 0f)
        return FillPlan(top, note.takeIf { !small }, r, below && belowHeight != null, numeral, FillLook.TOP_DP + block + FillLook.FILL_GAP_DP, rowH, width)
    }
    fun fits(p: FillPlan) = p.fillTop + FillLook.ROOM_ABOVE_DP + p.numeral * FillLook.CAP + FillLook.BASELINE_DP <= f.fitHeight + 0.5f
    val all = shown.size
    val tries = listOf(all to true, minOf(all, 1) to true, all to false, minOf(all, 1) to false, 0 to false).distinct()
        .filter { (_, b) -> !b || belowHeight != null }
    for (numeral in ladder) for ((n, below) in tries) plan(status, n, below, numeral).takeIf(::fits)?.let { return it }
    for (numeral in ladder) plan(null, 0, false, numeral).takeIf(::fits)?.let { return it }
    return plan(null, 0, false, ladder.last())
}

/* ------------------------------------------------------------------------------------------------ */
/* Layout                                                                                            */
/* ------------------------------------------------------------------------------------------------ */

/**
 * A metric widget in the fill look: [metric]'s card with its liquid at [level] where [plan] leaves room, or the card
 * alone while there's no number ([plan] null). The whole widget opens [open].
 */
@Composable
internal fun FillWidget(
    metric: FillMetric, level: Float, open: Intent, bitmapExtra: Double,
    plan: (WidgetFrame) -> FillPlan?,
    content: @Composable ColumnScope.(WidgetFrame, FillPlan?) -> Unit,
) = FillCardWidget(
    open, bitmapExtra, FillLook.SIDE_DP, FillLook.TOP_DP, 0f,
    background = { f, corner ->
        val p = plan(f)
        FillBitmaps.get(metric, FillBitmaps.Shape.Card, f.drawWidth, f.drawHeight, corner, p?.fillTop ?: f.drawHeight, if (p == null) 0f else level, f.density)
    },
) { f -> content(f, plan(f)) }

/**
 * The fill look's content for [p]: the title row, the line under it and the figures at the end, the Large's [below],
 * then [number] (with [unit], "%", small after it) at the bottom end, read out as [description], in dark ink when it
 * sits on the lime liquid at [level].
 */
@Composable
internal fun ColumnScope.FillContent(
    metric: FillMetric, p: FillPlan, f: WidgetFrame, title: String, level: Float,
    number: String, unit: String?, description: String?,
    below: (@Composable () -> Unit)? = null,
) {
    TopRowView(title, p.note, p.top, f, above = 0f)
    // Every block one child of the card's column: Glance drops a column's children past ten.
    if (p.rows.isNotEmpty()) {
        Column(GlanceModifier.fillMaxWidth()) {
            p.rows.forEach { r ->
                Spacer(GlanceModifier.height(FillLook.ROW_GAP_DP.dp))
                FillRowView(r, p.width, p.rowHeight)
            }
        }
    }
    if (p.below && below != null) {
        Column(GlanceModifier.fillMaxWidth()) {
            Spacer(GlanceModifier.height(FillLook.BELOW_GAP_DP.dp))
            below()
        }
    }
    Spacer(GlanceModifier.defaultWeight())
    val context = LocalContext.current
    val fit = FillNumeral.measure(context, number, unit, p.numeral, p.width)
    val dark = FillLevel.darkInk(metric.lightFill, level, FillLook.BASELINE_DP + fit.cap / 2, f.drawHeight - p.fillTop)
    NumeralRow(fit, number, unit, if (dark) FillLook.DarkInk else Color.White, f.density, description, atEnd = true)
    Spacer(GlanceModifier.height((FillLook.BASELINE_DP - fit.descent).coerceAtLeast(0f).dp))
}

/** A row under the title: the line in white at 75% at the start, the figure (label, then its value) at the end. */
@Composable
private fun FillRowView(r: FillRow, width: Float, height: Float) {
    val status: @Composable () -> Unit = {
        r.status?.let { FontText(it, FillLook.STATUS_SP, Color.White.copy(alpha = FillLook.STATUS_ALPHA), medium = true, maxWidthDp = width) }
    }
    val lineRoom = if (r.status != null) width / 2 - FillLook.BESIDE_GAP_DP / 2 else width
    val line: @Composable () -> Unit = { r.line?.let { FigureLine(it, lineRoom) } }
    Row(GlanceModifier.fillMaxWidth().height(height.dp), verticalAlignment = Alignment.CenterVertically) {
        if (WidgetRtl) { line(); Spacer(GlanceModifier.defaultWeight()); status() }
        else { status(); Spacer(GlanceModifier.defaultWeight()); line() }
    }
}

/** A figure at the end of a row: its label muted, its value in white, the value at the far end. */
@Composable
private fun FigureLine(l: RingLine, room: Float) {
    val valueW = l.value?.let { WidgetText.fitWidth(it, FillLook.STATUS_SP) + 6f } ?: 0f
    val label: @Composable () -> Unit = {
        FontText(l.label, FillLook.STATUS_SP, Color.White.copy(alpha = FillLook.LABEL_ALPHA), maxWidthDp = (room - valueW).coerceAtLeast(24f))
    }
    val value: @Composable () -> Unit = { l.value?.let { FontText(it, FillLook.STATUS_SP, Color.White, medium = true, maxWidthDp = valueW.coerceAtLeast(24f)) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (WidgetRtl) { value(); if (l.value != null) Spacer(GlanceModifier.width(4.dp)); label() }
        else { label(); if (l.value != null) Spacer(GlanceModifier.width(4.dp)); value() }
    }
}

/**
 * The number drawn at [fit] in [ink], read out as [description]: at the row's end ([atEnd], the left in Arabic, as the
 * mockups place it), at its start, or centred.
 */
@Composable
internal fun NumeralRow(
    fit: FillNumeral.Fit, number: String, unit: String?, ink: Color, density: Float, description: String?,
    atEnd: Boolean, centred: Boolean = false,
) {
    val context = LocalContext.current
    val image: @Composable () -> Unit = {
        Image(
            ImageProvider(FillNumeral.bitmap(context, number, unit, fit, ink, density)),
            contentDescription = description,
            modifier = GlanceModifier.size(fit.width.dp, fit.height.dp),
            contentScale = ContentScale.FillBounds,
        )
    }
    if (centred) {
        Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.Bottom) { image() }
        return
    }
    // In Arabic with a left to right launcher the row is mirrored by hand: the end is the left.
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        if (atEnd == WidgetRtl) { image(); Spacer(GlanceModifier.defaultWeight()) }
        else { Spacer(GlanceModifier.defaultWeight()); image() }
    }
}

/* ------------------------------------------------------------------------------------------------ */
/* The number                                                                                        */
/* ------------------------------------------------------------------------------------------------ */

/** The fill look's number: Inter Light, Western digits, left to right whatever the layout, "%" small after it. */
internal object FillNumeral {
    private const val REFERENCE = 100f

    /** A number's measures, dp: its font [size], the image's [width] and [height], the digits' [cap] and the room under them. */
    data class Fit(val size: Float, val width: Float, val height: Float, val cap: Float, val descent: Float)

    private fun paint(context: Context, size: Float, alpha: Float = 1f, ink: Color = Color.White): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = WidgetFonts.inter(context)
            setFontVariationSettings("'wght' ${FillLook.WEIGHT}")
            textSize = size
            color = ink.copy(alpha = ink.alpha * alpha).toArgb()
        }

    /** [number] with [unit] at [sizeDp], made smaller until it's no wider than [maxWidth] dp. */
    fun measure(context: Context, number: String, unit: String?, sizeDp: Float, maxWidth: Float): Fit {
        // Measured at 100 px and scaled: advances at a few px are rounded, at 100 they're near enough exact.
        val n = paint(context, REFERENCE).apply { letterSpacing = FillLook.TRACKING }.measureText(number)
        val u = unit?.let { REFERENCE * FillLook.UNIT_GAP + paint(context, REFERENCE * FillLook.UNIT_SHARE).measureText(it) } ?: 0f
        fun widthAt(size: Float): Float = (n + u) * size / REFERENCE
        val w = widthAt(sizeDp)
        val size = if (w > maxWidth && w > 0f) sizeDp * maxWidth / w else sizeDp
        val cap = size * FillLook.CAP
        val descent = size * FillLook.DESCENT
        return Fit(size, ceil(widthAt(size) + 2f), size * FillLook.OVERSHOOT + cap + descent, cap, descent)
    }

    /** The number at [fit] in [ink], drawn at [density] px a dp, its baseline over [Fit.descent]. */
    fun bitmap(context: Context, number: String, unit: String?, fit: Fit, ink: Color, density: Float): Bitmap {
        val size = fit.size * density
        val w = (fit.width * density).coerceAtLeast(1f)
        val h = (fit.height * density).coerceAtLeast(1f)
        val baseline = (fit.height - fit.descent) * density
        val n = paint(context, size, ink = ink).apply { letterSpacing = FillLook.TRACKING }
        val bmp = Bitmap.createBitmap(ceil(w).toInt(), ceil(h).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawText(number, density, baseline, n)
        unit?.let {
            val u = paint(context, size * FillLook.UNIT_SHARE, FillLook.UNIT_ALPHA, ink)
            c.drawText(it, density + n.measureText(number) + size * FillLook.UNIT_GAP, baseline, u)
        }
        return bmp
    }
}

/* ------------------------------------------------------------------------------------------------ */
/* The liquid                                                                                        */
/* ------------------------------------------------------------------------------------------------ */

/**
 * The cards with their liquid, cached by metric, shape, pixel size, corner, where the liquid's room starts and level
 * (in 1% steps, [FillLevel.bucket]), so a refresh that changes nothing draws nothing. Widgets stay dark on every app
 * theme, so the theme isn't part of the key. A cached bitmap is never recycled, here or by a caller: RemoteViews may
 * still be sending it when the cache lets it go, so an evicted one is left to the garbage collector.
 */
internal object FillBitmaps {
    /** A whole card, rounded; a summary column (a Wide's or a compact one), square, with the hairline on its sides. */
    enum class Shape(val amp: Float, val rounded: Boolean) { Card(1f, true), Strip(0.9f, false), Compact(0.8f, false) }

    private data class Key(val metric: FillMetric, val shape: Shape, val w: Int, val h: Int, val corner: Int, val top: Int, val level: Int)

    /** At most a sixteenth of the heap, and 24 MB: about every size of every widget at once on a big screen. */
    private val maxBytes: Int = (Runtime.getRuntime().maxMemory() / 16).coerceAtMost(24L shl 20).toInt()

    private val cache = object : LruCache<Key, Bitmap>(maxBytes) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    /**
     * [metric]'s card [w] by [h] dp with [corner] dp corners at [density], the liquid at [level] in the room from
     * [top] dp down; no liquid at 0.
     */
    fun get(metric: FillMetric, shape: Shape, w: Float, h: Float, corner: Float, top: Float, level: Float, density: Float): Bitmap {
        val key = Key(
            metric, shape,
            ceil(w * density).toInt().coerceAtLeast(1), ceil(h * density).toInt().coerceAtLeast(1),
            if (shape.rounded) (corner * density).roundToInt() else 0, (top * density).roundToInt(), FillLevel.bucket(level),
        )
        cache.get(key)?.takeUnless { it.isRecycled }?.let { return it }
        return draw(key, density).also { cache.put(key, it) }
    }

    private fun draw(k: Key, density: Float): Bitmap {
        val w = k.w.toFloat()
        val h = k.h.toFloat()
        val bmp = Bitmap.createBitmap(k.w, k.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(k.metric.card.toArgb())
        val level = FillLevel.ofBucket(k.level)
        if (level > 0f) {
            val a = density * k.shape.amp
            val y = FillLevel.surface(k.top.toFloat(), h, level)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = k.metric.hue.copy(alpha = FillLook.BACK_ALPHA).toArgb()
            c.drawPath(wave(w, h, y, a, BACK), paint)
            paint.color = android.graphics.Color.WHITE
            paint.shader = LinearGradient(0f, y - FRONT[1] * a / 2, 0f, h, k.metric.hue.toArgb(), k.metric.deep.toArgb(), Shader.TileMode.CLAMP)
            c.drawPath(wave(w, h, y, a, FRONT), paint)
        }
        if (k.shape == Shape.Card) {
            // The corners cut with an edge as soft as the launcher's own clip.
            val r = k.corner.toFloat()
            val outside = Path().apply {
                addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW)
                fillType = Path.FillType.INVERSE_WINDING
            }
            c.drawPath(outside, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
        } else {
            // Half the hairline on each side: two columns side by side make the whole line, whichever way they're laid.
            val line = Paint().apply { color = Color.Black.copy(alpha = FillLook.HAIRLINE_ALPHA).toArgb() }
            val hw = (FillLook.HAIRLINE_DP * density).coerceAtLeast(1f)
            c.drawRect(0f, 0f, hw, h, line)
            c.drawRect(w - hw, 0f, w, h, line)
        }
        return bmp
    }

    /** The waves' heights at the start, the first bend, the middle and the end, in [amp] units (the mockup's px). */
    private val BACK = floatArrayOf(4f, -8f, 2f, -2f)
    private val FRONT = floatArrayOf(0f, 9f, 0f, 3f)

    /**
     * A wave whose surface stands at [y] across [w], filled down to [h]: a quadratic bend to the middle and its mirror
     * on to the end (SVG's Q then T), heights [s] times [a] px, the same shape at every refresh.
     */
    private fun wave(w: Float, h: Float, y: Float, a: Float, s: FloatArray): Path = Path().apply {
        moveTo(0f, y + s[0] * a)
        quadTo(w * 0.25f, y + s[1] * a, w * 0.5f, y + s[2] * a)
        quadTo(w * 0.75f, y + (2 * s[2] - s[1]) * a, w, y + s[3] * a)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
}
