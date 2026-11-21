package org.openstrap.app.widget

import org.openstrap.app.R
import org.openstrap.app.data.DetailRoutes
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import org.openstrap.app.data.WidgetKind
import org.openstrap.app.ui.glow.draw.GlowMood
import org.openstrap.app.ui.glow.draw.WeekBarsLook
import org.openstrap.app.ui.glow.draw.drawWeekBars
import org.openstrap.app.ui.glow.draw.renderToBitmap
import org.openstrap.app.ui.glow.draw.weekBars
import java.time.LocalDate

/**
 * The Recovery widget (widgets spec 3.4): this morning's score and word in the same spot every day, with what's
 * behind it on the Wide and the last seven days on the Large, in the fill look: lime liquid as high as the score
 * ([FillContent]). The whole widget opens the Recovery screen.
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

/** Recovery's hue, as the app's. */
private val Ink = RingWidget.Recovery

/**
 * The widget at the size Glance lays out, in the fill look: Recovery's card with lime liquid as high as the score,
 * the title and the word at the top, the score at the bottom end ([FillContent]). The Wide adds the note and the
 * figures at the end of the rows; the Large the last seven days under them. Any size the launcher gives lands on one
 * of the three ([WidgetSizes.bucket]).
 */
@Composable
private fun RecoveryContent(data: RecoveryWidget.Shown) {
    val model = data.model
    val open = openMetric(LocalContext.current, DetailRoutes.RECOVERY, model.date)
    val level = FillLevel.percent(model.score?.toIntOrNull())
    val lines = model.figures.map { RingLine(it.label, it.value) }
    val week = model.week.isNotEmpty()
    FillWidget(FillMetric.Recovery, level, open, BITMAP_EXTRA, plan = { f ->
        if (model.score == null) null
        else fillPlan(f, model.title, model.note, model.word, lines, if (week && f.bucket == WidgetSizes.Large) weekHeight(f) else null)
    }) { frame, p ->
        if (model.score == null || p == null) WaitingContent(model.title, model.waiting, frame)
        else FillContent(FillMetric.Recovery, p, frame, model.title, level, model.score, "%", model.scoreDescription) {
            Week(model.week, WeekBarsLook.column(model.week.size, frame.contentWidth), frame.density)
        }
    }
}

/** The week's height: its bars and the letters under them. */
private fun weekHeight(f: WidgetFrame): Float = WeekBarsLook.HEIGHT_DP + 4f + WidgetText.line(WidgetText.CAPTION_SP, f.fontScale)

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
