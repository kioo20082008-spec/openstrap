package org.openstrap.app.widget

import org.openstrap.app.R
import org.openstrap.app.data.DetailRoutes
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.layout.Alignment
import androidx.compose.ui.graphics.Color
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.Text
import org.openstrap.app.data.WidgetKind
import org.openstrap.app.ui.glow.draw.EdgeKind
import org.openstrap.app.ui.glow.draw.EdgeStatus
import org.openstrap.app.ui.glow.draw.GlowMood
import org.openstrap.app.ui.glow.draw.WeekBarsLook
import org.openstrap.app.ui.glow.draw.drawWeekBars
import org.openstrap.app.ui.glow.draw.renderToBitmap
import org.openstrap.app.ui.glow.draw.weekBars
import org.openstrap.app.ui.glow.draw.edgeLight
import org.openstrap.app.ui.theme.AppIcons
import java.time.LocalDate

/**
 * The Recovery widget (widgets spec 3.4): this morning's score and word in the same spot every day, with what's
 * behind it on the Wide and the sentence and the last seven days on the Large. An edge card lit from the left, about
 * earlier, in Recovery's ink and the moment's mood, as Today's Recovery card after the morning. The whole widget opens
 * the Recovery screen.
 */
class RecoveryWidget : LoadingWidget<RecoveryWidget.Shown>(WidgetKind.Recovery) {
    override fun shown(data: WidgetData, context: Context): Shown = load(data)

    override fun sample(): Shown = Shown(WidgetSamples.recovery(LocalDate.now()), GlowMood.Day)

    @Composable
    override fun Content(data: Shown) = RecoveryContent(data)

    /** What the widget shows and the light it's lit in. */
    data class Shown(val model: RecoveryWidgetModel, val mood: GlowMood)

    companion object {
        /**
         * Today's Recovery, worked out as the app does ([org.openstrap.app.data.HealthRepository.recovery]), and
         * yesterday's while this morning's isn't in. The mood from the moment then ([widgetMood]).
         */
        internal fun load(data: WidgetData): Shown {
            val read = checkNotNull(data.recovery) { "not read for Recovery" }
            val model = RecoveryWidgetModel.from(read.today, read.yesterday)
            return Shown(model, widgetMood(data, read.today?.let { read.todayResult?.usualBedtime }))
        }
    }
}

/** The Recovery widget's receiver. The first one placed stops Moment leading with Recovery (spec 3.1). */
class RecoveryWidgetReceiver : PlacedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecoveryWidget()
}

/* ------------------------------------------------------------------------------------------------ */
/* Layout                                                                                            */
/* ------------------------------------------------------------------------------------------------ */

/** Recovery's hue in the rings theme. */
private val Ink = RingWidget.Recovery

/**
 * The widget at the size Glance lays out, in the rings theme: the flat card, the title and the word, and Recovery's one
 * ring with the score in the middle ([RingContent]). The Wide adds the three figures beside the ring; the Large the
 * sentence and the last seven days under it. Any size the launcher gives lands on one of the three ([WidgetSizes.bucket]).
 */
@Composable
private fun RecoveryContent(data: RecoveryWidget.Shown) {
    val model = data.model
    val open = openMetric(LocalContext.current, DetailRoutes.RECOVERY, model.date)
    EdgeWidget(edgeLight(EdgeStatus.Usual, EdgeKind.Earlier), Ink, data.mood, open, BITMAP_EXTRA) { frame ->
        if (model.score == null) WaitingContent(model.title, model.waiting, frame) else RingsContent(model, frame)
    }
}

/** The ring layout at [f]'s size, the Large with the sentence and the week under the ring. */
@Composable
private fun ColumnScope.RingsContent(m: RecoveryWidgetModel, f: WidgetFrame) {
    val share = (m.score?.toIntOrNull() ?: 0) / 100f
    val lines = m.figures.map { RingLine(it.label, it.value) }
    if (f.bucket != WidgetSizes.Large) {
        RingContent(m.title, m.note, m.word, Ink, Ink, share, m.score, m.scoreDescription, AppIcons.HeartFill, f, lines)
        return
    }
    val sentenceLines = m.sentence?.let { WidgetText.lines(it, WidgetText.SECONDARY_SP, f.contentWidth, f.fontScale).coerceAtMost(2) } ?: 0
    val sentenceH = sentenceLines * WidgetText.line(WidgetText.SECONDARY_SP, f.fontScale)
    val week = m.week.isNotEmpty()
    val weekH = if (week) WeekBarsLook.HEIGHT_DP + 4f + WidgetText.line(WidgetText.CAPTION_SP, f.fontScale) else 0f
    val belowH = sentenceH + weekH + (if (sentenceLines > 0 && week) RingWidget.BELOW_GAP_DP else 0f)
    RingContent(m.title, m.note, m.word, Ink, Ink, share, m.score, m.scoreDescription, AppIcons.HeartFill, f, lines, belowH) {
        // One child of the card's column: Glance drops a column's children past ten.
        Column(GlanceModifier.fillMaxWidth()) {
            if (sentenceLines > 0) m.sentence?.let { Text(it, style = WidgetType.secondary, maxLines = sentenceLines) }
            if (week) {
                if (sentenceLines > 0) Spacer(GlanceModifier.height(RingWidget.BELOW_GAP_DP.dp))
                Week(m.week, WeekBarsLook.column(m.week.size, f.contentWidth), f.density)
            }
        }
    }
}

/** The last seven days as solid bars, the day shown in Recovery's ink, each day's letter under its bar. */
@Composable
private fun Week(week: List<RecoveryWeekDay>, column: Float, density: Float) {
    val width = WeekBarsLook.width(week.size, column)
    val bars = weekBars(RecoveryLayout.barValues(week), density, column)
    val bitmap = renderToBitmap(width * density, WeekBarsLook.HEIGHT_DP * density) { drawWeekBars(it, bars, week.indexOfFirst { d -> d.shown }, Ink) }
    val description = LocalContext.current.getString(R.string.system_widget_recovery_week_description)
    WeekChart(ImageProvider(bitmap), description, week.map { it.letter to it.shown }, column)
}

/* ------------------------------------------------------------------------------------------------ */
/* Bitmaps                                                                                           */
/* ------------------------------------------------------------------------------------------------ */

/** Room for the dots and the week beside the backgrounds, in the bitmap budget. */
private const val BITMAP_EXTRA = 1.25
