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
import org.openstrap.app.ui.theme.AppIcons
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
private val Ink = RingWidget.Energy

/**
 * The widget at the size Glance lays out, in the rings theme: the flat card, the title and "left", and Energy's one
 * ring filled to the level with the number in the middle ([RingContent]). The Wide adds Energy at waking beside the
 * ring; the Large what the day recharged and drained too.
 */
@Composable
private fun EnergyContent(data: EnergyWidget.Shown) {
    val m = data.model
    val open = openMetric(LocalContext.current, DetailRoutes.ENERGY, m.date)
    EdgeWidget(edgeLight(EdgeStatus.Usual, EdgeKind.Level), Ink, data.mood, open, BITMAP_EXTRA, level = m.level) { frame ->
        if (m.now == null) WaitingContent(m.title, m.waiting, frame)
        else {
            val lines = listOfNotNull(m.waking?.let { RingLine(it) }) +
                (if (frame.bucket == WidgetSizes.Large) m.figures.map { RingLine(it.label, it.value) } else emptyList())
            RingContent(m.title, null, m.word, RingWidget.Muted, Ink, m.level, m.number, m.description, AppIcons.Lightning, frame, lines)
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */
/* Bitmaps                                                                                           */
/* ------------------------------------------------------------------------------------------------ */

/** Room for the dots and the bar beside the backgrounds, in the bitmap budget. */
private const val BITMAP_EXTRA = 1.25
