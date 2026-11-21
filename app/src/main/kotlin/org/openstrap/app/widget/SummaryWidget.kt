package org.openstrap.app.widget

import android.content.Context
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import org.openstrap.app.R
import org.openstrap.app.data.WidgetKind
import org.openstrap.app.i18n.Strings
import org.openstrap.app.ui.glow.draw.renderToBitmap
import java.time.LocalDate
import java.time.ZoneId

/**
 * The summary widget in the fill look: Energy, Recovery and Sleep side by side, Energy at the start, each a column of
 * its card with its liquid as high as its number, hairlines between them, no title. Compact (a Small): each column's
 * icon at the top and its number centred at the bottom. From the Wide on: each one's name and line at the top start
 * and its number at the bottom end, "%" after Energy and Recovery. It reads what the Energy, Recovery and Sleep
 * widgets read, so its numbers are theirs. The whole widget opens the app.
 */
class SummaryWidget : LoadingWidget<SummaryWidget.Shown>(WidgetKind.Summary) {
    override fun shown(data: WidgetData, context: Context): Shown = load(data)

    override fun sample(): Shown {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val energy = WidgetSamples.energy(today)
        val recovery = WidgetSamples.recovery(today)
        val sleep = WidgetSamples.sleep(today, zone)
        return Shown(
            row(R.string.widget_energy_name, energy.now, FillMetric.Energy, energy.word),
            row(R.string.widget_recovery_name, recovery.score?.toIntOrNull(), FillMetric.Recovery, recovery.word),
            row(R.string.widget_sleep_name, sleep.score?.toIntOrNull(), FillMetric.Sleep, sleep.asleepTime),
        )
    }

    @Composable
    override fun Content(data: Shown) = SummaryContent(data)

    /** One column: its [label], its [value] out of 100 (null before there's one), its [metric] and the [status] under its name. */
    data class Row(val label: String, val value: Int?, val metric: FillMetric, val status: String?) {
        val hue: Color get() = metric.hue

        /** How high its liquid stands ([FillLevel.percent]). */
        val level: Float get() = FillLevel.percent(value)

        /** Energy and Recovery are shares, so the Wide writes "%" after them; Sleep's is a score. */
        val unit: String? get() = if (metric == FillMetric.Sleep) null else "%"
    }

    /** The three columns, from the start. */
    data class Shown(val energy: Row, val recovery: Row, val sleep: Row) {
        val rows: List<Row> get() = listOf(energy, recovery, sleep)
        val title: String get() = Strings.app.get(R.string.widget_summary_name)
        val description: String
            get() = title + ", " + rows.joinToString(", ") { r -> r.label + " " + (r.value?.toString() ?: "–") }
    }

    companion object {
        private fun row(label: Int, value: Int?, metric: FillMetric, status: String?) =
            Row(Strings.app.get(label), value, metric, status.takeIf { value != null })

        internal fun load(data: WidgetData): Shown {
            val today: LocalDate = data.now.atZone(data.zone).toLocalDate()
            val energy = data.energy?.let { EnergyWidgetModel.from(today, it.charge) }
            val recovery = data.recovery?.let { RecoveryWidgetModel.from(it.today, it.yesterday) }
            val sleep = data.sleep?.let { SleepWidget.load(data) }
            return Shown(
                row(R.string.widget_energy_name, energy?.now, FillMetric.Energy, energy?.word),
                row(R.string.widget_recovery_name, recovery?.score?.toIntOrNull(), FillMetric.Recovery, recovery?.word),
                row(R.string.widget_sleep_name, sleep?.score?.toIntOrNull(), FillMetric.Sleep, sleep?.asleepTime),
            )
        }
    }
}

/** The summary widget's receiver. */
class SummaryWidgetReceiver : PlacedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryWidget()
}

/** The summary's measures, dp and sp, from the mockups (the 4 × 2's «أ» and the 2 × 2's «icon on top»). */
internal object SummaryLook {
    /** The Wide's column: inset, the name and its line (the sizes the name steps down through), the number's baseline. */
    const val SIDE_DP = 12f
    const val TOP_DP = 14f
    val NAME_SP = listOf(13f, 12f, 11f)
    const val STATUS_SP = 11f
    const val LINE_GAP_DP = 2f
    const val BASELINE_DP = 16f

    /** The Wide's number: this share of the cell's height, at most [NUMBER_MAX_DP]. */
    const val NUMBER_SHARE = 0.22f
    const val NUMBER_MAX_DP = 46f

    /** The compact column: the icon's top and size, the number's share of the height (at most 30 dp) and baseline. */
    const val ICON_TOP_DP = 12f
    const val ICON_DP = 18f
    const val COMPACT_SHARE = 0.15f
    const val COMPACT_MAX_DP = 30f
    const val COMPACT_BASELINE_DP = 14f

    /** Between what's at the top and the liquid's room. */
    const val FILL_GAP_DP = 10f

    /** Where the liquid's room starts in a Wide's column, dp from the top: under the name and its line. */
    fun wideTop(fontScale: Float): Float =
        TOP_DP + WidgetText.line(NAME_SP.first(), fontScale) + LINE_GAP_DP + WidgetText.line(STATUS_SP, fontScale) + FILL_GAP_DP

    /** And in a compact column: under the icon. */
    const val COMPACT_TOP_DP = ICON_TOP_DP + ICON_DP + FILL_GAP_DP
}

/** The three columns at the size Glance lays out, compact on a Small, the strap's button in the last column. */
@Composable
private fun SummaryContent(m: SummaryWidget.Shown) {
    val open = openToday(LocalContext.current)
    FillCardWidget(open, BITMAP_EXTRA, 0f, 0f, 0f, background = { _, _ -> page }) { frame ->
        val compact = frame.bucket == WidgetSizes.Small
        // Energy at the start: the right in Arabic. A right to left launcher mirrors the row itself.
        val columns = if (WidgetRtl) m.rows.reversed() else m.rows
        Row(GlanceModifier.fillMaxSize()) {
            columns.forEach { r ->
                SummaryColumn(
                    r, frame, compact, frame.status.takeIf { r.metric == FillMetric.Sleep },
                    GlanceModifier.defaultWeight().fillMaxHeight(),
                )
            }
        }
    }
}

/** Under the columns, which cover it all: the page's colour, a pixel stretched (the card's corners clip it). */
private val page by lazy { renderToBitmap(1f, 1f) { it.drawColor(RingWidget.Page.toArgb()) } }

/** One column: its card and liquid behind ([FillBitmaps]), then what's on it, compact or not. */
@Composable
private fun SummaryColumn(r: SummaryWidget.Row, f: WidgetFrame, compact: Boolean, status: StatusLine?, modifier: GlanceModifier) {
    val top = if (compact) SummaryLook.COMPACT_TOP_DP else SummaryLook.wideTop(f.fontScale)
    val shape = if (compact) FillBitmaps.Shape.Compact else FillBitmaps.Shape.Strip
    val bitmap = FillBitmaps.get(r.metric, shape, f.drawWidth / 3f, f.drawHeight, 0f, top, r.level, f.density)
    Box(modifier.background(ImageProvider(bitmap), ContentScale.FillBounds)) {
        if (compact) CompactColumn(r, f, top, status) else WideColumn(r, f, top, status)
    }
}

/** The compact column: the icon (or the strap's button) at the top centre, the number centred at the bottom. */
@Composable
private fun CompactColumn(r: SummaryWidget.Row, f: WidgetFrame, top: Float, status: StatusLine?) {
    val context = LocalContext.current
    val width = f.fitWidth / 3f
    val number = r.value?.toString() ?: "–"
    val size = (f.fitHeight * SummaryLook.COMPACT_SHARE).coerceAtMost(SummaryLook.COMPACT_MAX_DP)
    val fit = FillNumeral.measure(context, number, null, size, width - 8f)
    val dark = FillLevel.darkInk(r.metric.lightFill, r.level, SummaryLook.COMPACT_BASELINE_DP + fit.cap / 2, f.drawHeight - top)
    Column(
        GlanceModifier.fillMaxSize().padding(top = (if (status != null) 6f else SummaryLook.ICON_TOP_DP).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (status != null) SummaryStatus(status, f)
        else Image(
            ImageProvider(iconBitmap(r.metric.icon, Color.White, SummaryLook.ICON_DP, f.density)),
            contentDescription = null,
            modifier = GlanceModifier.size(SummaryLook.ICON_DP.dp),
            contentScale = ContentScale.FillBounds,
        )
        Spacer(GlanceModifier.defaultWeight())
        NumeralRow(fit, number, null, if (dark) FillLook.DarkInk else Color.White, f.density, r.label + " " + number, atEnd = true, centred = true)
        Spacer(GlanceModifier.height((SummaryLook.COMPACT_BASELINE_DP - fit.descent).coerceAtLeast(0f).dp))
    }
}

/** The Wide's column: the name and its line at the top start (the strap's button at the end), the number at the bottom end. */
@Composable
private fun WideColumn(r: SummaryWidget.Row, f: WidgetFrame, top: Float, status: StatusLine?) {
    val context = LocalContext.current
    val width = f.fitWidth / 3f - 2 * SummaryLook.SIDE_DP
    val textWidth = width - (if (status != null) StatusButton.SIZE_DP + 4f else 0f)
    val number = r.value?.toString() ?: "–"
    val unit = r.unit.takeIf { r.value != null }
    val size = (f.fitHeight * SummaryLook.NUMBER_SHARE).coerceAtMost(SummaryLook.NUMBER_MAX_DP)
    val fit = FillNumeral.measure(context, number, unit, size, width)
    val dark = FillLevel.darkInk(r.metric.lightFill, r.level, SummaryLook.BASELINE_DP + fit.cap / 2, f.drawHeight - top)
    val align = if (WidgetRtl) Alignment.End else Alignment.Start
    Column(
        GlanceModifier.fillMaxSize().padding(start = SummaryLook.SIDE_DP.dp, end = SummaryLook.SIDE_DP.dp, top = SummaryLook.TOP_DP.dp),
        horizontalAlignment = align,
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            val texts: @Composable () -> Unit = {
                Column(horizontalAlignment = align) {
                    val nameSp = fittingSp(context, r.label, strong = true, SummaryLook.NAME_SP, textWidth)
                    FontText(r.label, nameSp, Color.White, strong = true, maxWidthDp = textWidth)
                    r.status?.let {
                        Spacer(GlanceModifier.height(SummaryLook.LINE_GAP_DP.dp))
                        FontText(it, SummaryLook.STATUS_SP, Color.White.copy(alpha = FillLook.STATUS_ALPHA), medium = true, maxWidthDp = textWidth)
                    }
                }
            }
            val button: @Composable () -> Unit = { status?.let { SummaryStatus(it, f) } }
            if (WidgetRtl) { button(); Spacer(GlanceModifier.defaultWeight()); texts() }
            else { texts(); Spacer(GlanceModifier.defaultWeight()); button() }
        }
        Spacer(GlanceModifier.defaultWeight())
        NumeralRow(fit, number, unit, if (dark) FillLook.DarkInk else Color.White, f.density, r.label + " " + number, atEnd = true)
        Spacer(GlanceModifier.height((SummaryLook.BASELINE_DP - fit.descent).coerceAtLeast(0f).dp))
    }
}

/** The strap's button (spec 5) on the summary, which has no title row: its own tap, read out as the whole line. */
@Composable
private fun SummaryStatus(line: StatusLine, f: WidgetFrame) {
    val action = statusAction(line.status)
    val size = GlanceModifier.size(StatusButton.SIZE_DP.dp)
    Box(
        (if (action != null) size.clickable(action) else size).semantics { contentDescription = line.full },
        contentAlignment = Alignment.Center,
    ) { StatusButtonView(line, f) }
}

/** The first of [sizes] (sp, largest first) at which [text] in the widgets' Alexandria is no wider than [maxWidth] dp. */
private fun fittingSp(context: Context, text: String, strong: Boolean, sizes: List<Float>, maxWidth: Float): Float {
    val d = context.resources.displayMetrics.density
    val paint = TextPaint().apply { typeface = WidgetFonts.text(context, strong) }
    val scale = context.resources.configuration.fontScale
    return sizes.firstOrNull { sp -> paint.apply { textSize = sp * scale * d }.measureText(text) <= maxWidth * d } ?: sizes.last()
}

/** Room for the columns' liquid, the icons and the numbers beside the backgrounds, in the bitmap budget. */
private const val BITMAP_EXTRA = 1.25
