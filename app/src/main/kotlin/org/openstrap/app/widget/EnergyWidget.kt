package org.openstrap.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.Text
import org.openstrap.app.data.DetailRoutes
import org.openstrap.app.data.WidgetKind
import org.openstrap.app.ui.glow.draw.EdgeKind
import org.openstrap.app.ui.glow.draw.EdgeStatus
import org.openstrap.app.ui.glow.draw.GlowMood
import org.openstrap.app.ui.glow.draw.edgeLight
import org.openstrap.app.ui.glow.draw.renderToBitmap
import org.openstrap.app.ui.theme.MetricColors
import java.time.LocalDate

/**
 * The Energy widget (widgets spec 3.6): Energy now, the body battery Recovery works out minute by minute, with a
 * battery bar; the Wide adds Energy at waking, the Large what the day recharged and drained. An edge card whose light
 * fills across to the level, in Energy's ink and the moment's mood, as Today's Energy card. The whole widget opens
 * the Energy detail.
 */
class EnergyWidget : LoadingWidget<EnergyWidget.Shown>(WidgetKind.Energy) {
    override fun shown(data: WidgetData, context: Context): Shown = load(data)

    override fun sample(): Shown = Shown(WidgetSamples.energy(LocalDate.now()), GlowMood.Day)

    @Composable
    override fun Content(data: Shown) = EnergyContent(data)

    /** What the widget shows and the light it's lit in. */
    data class Shown(val model: EnergyWidgetModel, val mood: GlowMood)

    companion object {
        /**
         * Today's Energy from the same Recovery result Today reads ([org.openstrap.app.data.HealthRepository.recovery]),
         * so the widget and the app show one number. The mood from the moment now ([widgetMood]).
         */
        internal fun load(data: WidgetData): Shown {
            val read = checkNotNull(data.energy) { "not read for Energy" }
            val today = data.now.atZone(data.zone).toLocalDate()
            return Shown(EnergyWidgetModel.from(today, read.charge), widgetMood(data, read.usualBedtime ?: data.usualBedtime))
        }
    }
}

/** The Energy widget's receiver. */
class EnergyWidgetReceiver : PlacedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EnergyWidget()
}

/* ------------------------------------------------------------------------------------------------ */
/* Layout                                                                                            */
/* ------------------------------------------------------------------------------------------------ */

/** Energy's ink, as Today's Energy card and the Energy detail. */
private val Ink = MetricColors.charge

/** The bar's track (as Steps' goal bar) and its cap, white at 10% and 30%; the cap half the body's height. */
private const val TRACK_ALPHA = 0.10f
private const val CAP_ALPHA = 0.30f
private const val CAP_HEIGHT = 0.5f

/**
 * The widget at the size Glance lays out, its light filling across to the level as Today's Energy card
 * ([EdgeKind.Level]), from the right in Arabic as the background is mirrored.
 */
@Composable
private fun EnergyContent(data: EnergyWidget.Shown) {
    val m = data.model
    val open = openMetric(LocalContext.current, DetailRoutes.ENERGY, m.date)
    EdgeWidget(edgeLight(EdgeStatus.Usual, EdgeKind.Level), Ink, data.mood, open, BITMAP_EXTRA, level = m.level) { frame ->
        val (w, h) = frame.contentWidth to frame.contentHeight
        when {
            m.now == null -> WaitingContent(m.title, m.waiting, frame)
            frame.bucket == WidgetSizes.Small -> SmallContent(m, EnergyLayout.small(m, w, h, frame.fontScale, frame.status), frame)
            frame.bucket == WidgetSizes.Wide -> WideContent(m, EnergyLayout.wide(m, w, h, frame.fontScale, frame.status), frame)
            else -> LargeContent(m, EnergyLayout.large(m, w, h, frame.fontScale, frame.status), frame)
        }
    }
}

/** Small: the top row, the number with "left" under it, the battery bar. */
@Composable
private fun ColumnScope.SmallContent(m: EnergyWidgetModel, fit: EnergyLayout.Fit, f: WidgetFrame) {
    Edge(fit.spread, f)
    TopRowView(m.title, null, fit.top, f, fit.spread.edge(f.drawContentHeight))
    Gap()
    // Each block one child of the column: Glance drops a column's children past ten.
    Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = if (WidgetRtl) Alignment.End else Alignment.Start) {
        m.number?.let { DotNumber(it, fit.dot, f.density, m.description) }
        if (fit.word) {
            Spacer(GlanceModifier.height(EnergyLayout.SMALL_WORD_GAP_DP.dp))
            if (WidgetRtl) FontText(m.word, WidgetText.BODY_SP, Color.White.copy(alpha = 0.72f))
            else Text(m.word, style = WidgetType.word, maxLines = 1)
        }
    }
    Gap()
    BatteryBar(m.level, f)
    Edge(fit.spread, f)
}

/** Wide: the top row, the number with "left" and Energy at waking, the battery bar. */
@Composable
private fun ColumnScope.WideContent(m: EnergyWidgetModel, fit: EnergyLayout.Fit, f: WidgetFrame) {
    Edge(fit.spread, f)
    TopRowView(m.title, null, fit.top, f, fit.spread.edge(f.drawContentHeight))
    Gap()
    NumberRow(m, fit, f)
    Gap()
    BatteryBar(m.level, f)
    Edge(fit.spread, f)
}

/** Large: the Wide plus what the day recharged and drained. */
@Composable
private fun ColumnScope.LargeContent(m: EnergyWidgetModel, fit: EnergyLayout.Fit, f: WidgetFrame) {
    Edge(fit.spread, f)
    TopRowView(m.title, null, fit.top, f, fit.spread.edge(f.drawContentHeight))
    Gap()
    NumberRow(m, fit, f)
    Gap()
    BatteryBar(m.level, f)
    if (fit.figures) {
        Gap()
        Figures(m.figures)
    }
    Edge(fit.spread, f)
}

/** The number in dots with "left" beside it when it fits, Energy at waking on the right (on the left in Arabic). */
@Composable
private fun NumberRow(m: EnergyWidgetModel, fit: EnergyLayout.Fit, f: WidgetFrame) {
    val number = m.number ?: return
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        if (WidgetRtl) {
            m.waking?.let { FontText(it, WidgetText.SECONDARY_SP, Color.White.copy(alpha = 0.72f)) }
            Spacer(GlanceModifier.defaultWeight())
            if (fit.word) {
                FontText(m.word, WidgetText.BODY_SP, Color.White.copy(alpha = 0.72f))
                Spacer(GlanceModifier.width(EnergyLayout.WORD_GAP_DP.dp))
            }
            DotNumber(number, fit.dot, f.density, m.description)
            return@Row
        }
        DotNumber(number, fit.dot, f.density, m.description)
        if (fit.word) {
            Spacer(GlanceModifier.width(EnergyLayout.WORD_GAP_DP.dp))
            Text(m.word, style = WidgetType.word, maxLines = 1)
        }
        Spacer(GlanceModifier.defaultWeight())
        m.waking?.let { Text(it, style = WidgetType.secondary, maxLines = 1) }
    }
}

/** Recharged and Drained side by side, each a Caption over a Body value, Recharged first in reading order. */
@Composable
private fun Figures(figures: List<RecoveryFigure>) {
    Row(GlanceModifier.fillMaxWidth()) {
        (if (WidgetRtl) figures.reversed() else figures).forEachIndexed { i, f ->
            if (i > 0) Spacer(GlanceModifier.width(12.dp))
            FigureCell(f.label, f.value, GlanceModifier.defaultWeight())
        }
    }
}

/** The battery bar across the content, mirrored in Arabic so it fills from the right, as Steps' goal bar. */
@Composable
private fun BatteryBar(level: Float, f: WidgetFrame) {
    Image(
        ImageProvider(batteryBar(level, f.drawContentWidth, f.density).let { if (WidgetRtl) mirrored(it) else it }),
        // The dotted number already reads the level out.
        contentDescription = null,
        modifier = GlanceModifier.fillMaxWidth().height(EnergyLayout.BAR_DP.dp),
        contentScale = ContentScale.FillBounds,
    )
}

/* ------------------------------------------------------------------------------------------------ */
/* Bitmaps                                                                                           */
/* ------------------------------------------------------------------------------------------------ */

/** Room for the dots and the bar beside the backgrounds, in the bitmap budget. */
private const val BITMAP_EXTRA = 1.25

/**
 * The battery bar [width] dp wide at [density]: a body with soft corners, its track white at 10% and Energy's ink as
 * far as [level], and the cap at the full end, so it reads as a battery rather than a goal bar.
 */
private fun batteryBar(level: Float, width: Float, density: Float): Bitmap {
    val w = width * density
    val h = EnergyLayout.BAR_DP * density
    val r = EnergyLayout.BAR_CORNER_DP * density
    val capW = EnergyLayout.CAP_WIDTH_DP * density
    val capH = h * CAP_HEIGHT
    val body = (w - capW - EnergyLayout.CAP_GAP_DP * density).coerceAtLeast(h)
    return renderToBitmap(w, h) { c ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawRoundRect(0f, 0f, body, h, r, r, paint.apply { color = Color.White.copy(alpha = TRACK_ALPHA).toArgb() })
        val fill = body * level.coerceIn(0f, 1f)
        if (fill > 0f) c.drawRoundRect(0f, 0f, fill.coerceAtLeast(2 * r), h, r, r, paint.apply { color = Ink.toArgb() })
        paint.color = Color.White.copy(alpha = CAP_ALPHA).toArgb()
        c.drawRoundRect(w - capW, (h - capH) / 2, w, (h + capH) / 2, capW / 2, capW / 2, paint)
    }
}
