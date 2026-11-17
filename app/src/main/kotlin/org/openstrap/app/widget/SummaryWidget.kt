package org.openstrap.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.layout.ContentScale
import androidx.glance.layout.size
import org.openstrap.app.R
import org.openstrap.app.data.WidgetKind
import org.openstrap.app.i18n.Strings
import org.openstrap.app.ui.glow.draw.EdgeKind
import org.openstrap.app.ui.glow.draw.EdgeStatus
import org.openstrap.app.ui.glow.draw.GlowMood
import org.openstrap.app.ui.glow.draw.edgeLight
import org.openstrap.app.ui.theme.AppIcons
import java.time.LocalDate

/**
 * The summary widget: the day's three rings nested, Energy outside, then Recovery, then Sleep, in the app's colours,
 * with each one's number beside them on the Wide and the Large. It reads what the Energy, Recovery and Sleep widgets
 * read, so its numbers are theirs. The whole widget opens the app.
 */
class SummaryWidget : LoadingWidget<SummaryWidget.Shown>(WidgetKind.Summary) {
    override fun shown(data: WidgetData, context: Context): Shown = load(data)

    override fun sample(): Shown = Shown(row(R.string.widget_energy_name, 77, RingWidget.Energy), row(R.string.widget_recovery_name, 66, RingWidget.Recovery), row(R.string.widget_sleep_name, 75, RingWidget.Sleep))

    @Composable
    override fun Content(data: Shown) = SummaryContent(data)

    /** One ring: its [label], its [value] out of 100 (null before there's one) and its [hue]. */
    data class Row(val label: String, val value: Int?, val hue: Color)

    /** The three rings, outside in. */
    data class Shown(val energy: Row, val recovery: Row, val sleep: Row) {
        val rows: List<Row> get() = listOf(energy, recovery, sleep)
        val title: String get() = Strings.app.get(R.string.widget_summary_name)
        val description: String
            get() = title + ", " + rows.joinToString(", ") { r -> r.label + " " + (r.value?.toString() ?: "–") }
    }

    companion object {
        private fun row(label: Int, value: Int?, hue: Color) = Row(Strings.app.get(label), value, hue)

        internal fun load(data: WidgetData): Shown {
            val today: LocalDate = data.now.atZone(data.zone).toLocalDate()
            val energy = data.energy?.let { EnergyWidgetModel.from(today, it.charge).now }
            val recovery = data.recovery?.let { RecoveryWidgetModel.from(it.today, it.yesterday).score?.toIntOrNull() }
            val sleep = data.sleep?.let { SleepWidget.load(data).score?.toIntOrNull() }
            return Shown(
                row(R.string.widget_energy_name, energy, RingWidget.Energy),
                row(R.string.widget_recovery_name, recovery, RingWidget.Recovery),
                row(R.string.widget_sleep_name, sleep, RingWidget.Sleep),
            )
        }
    }
}

/** The summary widget's receiver. */
class SummaryWidgetReceiver : PlacedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryWidget()
}

/** The card at the size Glance lays out: the title, the three rings, and from the Wide on a line for each beside them. */
@Composable
private fun SummaryContent(m: SummaryWidget.Shown) {
    val open = openToday(LocalContext.current)
    EdgeWidget(edgeLight(EdgeStatus.Usual, EdgeKind.Level), RingWidget.SummaryHue, GlowMood.Day, open, BITMAP_EXTRA) { frame ->
        val lines = if (frame.bucket == WidgetSizes.Small) emptyList() else m.rows.map { RingLine(it.label, it.value?.toString() ?: "–", it.hue) }
        RingContent(
            m.title, null, null, RingWidget.Muted, RingWidget.SummaryHue, 0f, null, m.description, AppIcons.Lightning, frame, lines,
            ringImage = { d -> NestedRings(m, d, frame.density) },
        )
    }
}

@Composable
private fun NestedRings(m: SummaryWidget.Shown, d: Float, density: Float) {
    val bitmap = nestedRingsBitmap(m.rows.map { (it.value ?: 0) / 100f }, m.rows.map { it.hue }, d, density)
    Image(ImageProvider(bitmap), contentDescription = m.description, modifier = GlanceModifier.size(d.dp), contentScale = ContentScale.FillBounds)
}

/** Room for the rings beside the backgrounds, in the bitmap budget. */
private const val BITMAP_EXTRA = 1.25
