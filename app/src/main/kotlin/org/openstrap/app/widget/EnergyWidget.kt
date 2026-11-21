package org.openstrap.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import org.openstrap.app.data.DetailRoutes
import org.openstrap.app.data.WidgetKind
import org.openstrap.app.ui.glow.draw.GlowMood
import java.time.LocalDate

/**
 * The Energy widget (widgets spec 3.6): Energy now, the body battery Recovery works out minute by minute, with a
 * liquid as high as the level ([FillContent]); the Wide adds Energy at waking, the Large what the day recharged and
 * drained. The whole widget opens the Energy detail.
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

/**
 * The widget at the size Glance lays out, in the fill look: Energy's card with its liquid as high as the level, the
 * title and "left" at the top, the number at the bottom end ([FillContent]). The Wide adds Energy at waking at the end
 * of the row; the Large what the day recharged and drained under it.
 */
@Composable
private fun EnergyContent(data: EnergyWidget.Shown) {
    val m = data.model
    val open = openMetric(LocalContext.current, DetailRoutes.ENERGY, m.date)
    val level = FillLevel.percent(m.now)
    FillWidget(FillMetric.Energy, level, open, BITMAP_EXTRA, plan = { f ->
        if (m.now == null) null
        else {
            val lines = listOfNotNull(m.waking?.let { RingLine(it) }) +
                (if (f.bucket == WidgetSizes.Large) m.figures.map { RingLine(it.label, it.value) } else emptyList())
            fillPlan(f, m.title, null, m.word, lines, null)
        }
    }) { frame, p ->
        val number = m.number
        if (number == null || p == null) WaitingContent(m.title, m.waiting, frame)
        else FillContent(FillMetric.Energy, p, frame, m.title, level, number, "%", m.description)
    }
}

/* ------------------------------------------------------------------------------------------------ */
/* Bitmaps                                                                                           */
/* ------------------------------------------------------------------------------------------------ */

/** Room for the dots and the bar beside the backgrounds, in the bitmap budget. */
private const val BITMAP_EXTRA = 1.25
