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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import java.time.LocalDate
import java.time.ZoneId
import org.openstrap.app.R
import org.openstrap.app.data.DetailRoutes
import org.openstrap.app.data.WidgetKind
import org.openstrap.app.ui.glow.draw.EdgeKind
import org.openstrap.app.ui.glow.draw.EdgeStatus
import org.openstrap.app.ui.glow.draw.GlowMood
import org.openstrap.app.ui.glow.draw.drawStageBar
import org.openstrap.app.ui.glow.draw.renderToBitmap
import org.openstrap.app.ui.glow.draw.edgeLight
import org.openstrap.app.ui.glow.draw.stageColor
import org.openstrap.app.ui.theme.AppIcons

/**
 * The Sleep widget (widgets spec 3.2): last night's score, time asleep and stages, as the Sleep tab's hero tells them.
 * The Wide adds bedtime to waking and the stage legend, the Large the tab's sentence, how often you woke and the
 * time in each stage. An edge card lit from the top like a night sky, in Sleep's ink and always the night mood,
 * as the Sleep tab. The whole widget opens the Sleep score's detail for that night.
 */
class SleepWidget : LoadingWidget<SleepWidgetModel>(WidgetKind.Sleep) {
    override fun shown(data: WidgetData, context: Context): SleepWidgetModel = load(data)

    override fun sample(): SleepWidgetModel = ZoneId.systemDefault().let { WidgetSamples.sleep(LocalDate.now(it), it) }

    @Composable
    override fun Content(data: SleepWidgetModel) = SleepContent(data)

    companion object {
        /**
         * This morning's night as the Sleep tab reads it ([WidgetSnapshot.gather]), and yesterday's while this
         * morning's isn't synced. "After your first night" until the strap has stored a sleep.
         */
        internal fun load(data: WidgetData): SleepWidgetModel {
            val read = checkNotNull(data.sleep) { "not read for Sleep" }.getOrNull()
                ?: return SleepWidgetModel.from(null, null, firstNight = false, napsToday = false, zone = data.zone)
            return SleepWidgetModel.from(read.tonight, read.yesterday, read.firstNight, read.napsToday, data.zone, read.deleted)
        }
    }
}

/** The Sleep widget's receiver. The first one placed stops Moment leading with last night (spec 3.1). */
class SleepWidgetReceiver : PlacedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SleepWidget()
}

/* ------------------------------------------------------------------------------------------------ */
/* Layout                                                                                            */
/* ------------------------------------------------------------------------------------------------ */

/** Sleep's hue in the rings theme. */
private val Ink = RingWidget.Sleep

/** The legend's labels: Caption size and weight, in the theme's muted grey. */
private val LegendStyle = WidgetType.caption.copy(color = ColorProvider(RingWidget.Muted))

/**
 * The widget at the size Glance lays out, in the rings theme: the flat card, the title and the score's word, and
 * Sleep's one ring with the score in the middle ([RingContent]). The Wide adds time asleep and how often you woke beside
 * the ring with the stage bar under it; the Large the time in each stage. A night opens its Sleep score; while waiting,
 * the Sleep screen.
 */
@Composable
private fun SleepContent(m: SleepWidgetModel) {
    val context = LocalContext.current
    val open = if (m.date != null) openMetric(context, DetailRoutes.SLEEP_SCORE, m.date) else openMetric(context, DetailRoutes.SLEEP, null)
    EdgeWidget(edgeLight(EdgeStatus.Usual, EdgeKind.Sleep), Ink, GlowMood.Night, open, BITMAP_EXTRA) { frame ->
        if (m.waiting != null) WaitingContent(m.title, m.waiting, frame) else RingsContent(m, frame)
    }
}

/** The ring layout at [f]'s size. */
@Composable
private fun ColumnScope.RingsContent(m: SleepWidgetModel, f: WidgetFrame) {
    val share = (m.score?.toIntOrNull() ?: 0) / 100f
    val number = m.score ?: "–"
    val lines = listOfNotNull(m.asleep?.let { RingLine(it) }, m.woke?.let { RingLine(it) })
    when (f.bucket) {
        WidgetSizes.Small -> {
            // The word with time asleep, "Fair · 8 h 26", as the mockup's.
            val word = listOfNotNull(m.word, m.asleep).joinToString(" · ").ifEmpty { null }
            RingContent(m.title, null, word, RingWidget.SleepText, Ink, share, number, m.scoreDescription, AppIcons.MoonFill, f)
        }
        WidgetSizes.Wide -> if (m.totals.isEmpty()) {
            RingContent(m.title, m.note, m.word, RingWidget.SleepText, Ink, share, number, m.scoreDescription, AppIcons.MoonFill, f, lines)
        } else {
            RingContent(m.title, m.note, m.word, RingWidget.SleepText, Ink, share, number, m.scoreDescription, AppIcons.MoonFill, f, lines, SleepLayout.BAR_DP) {
                StageBar(m, SleepLayout.BAR_DP, f)
            }
        }
        else -> when {
            m.stages.isNotEmpty() -> RingContent(
                m.title, m.note, m.word, RingWidget.SleepText, Ink, share, number, m.scoreDescription, AppIcons.MoonFill, f, lines,
                SleepLayout.stagesHeight(m.stages.size, f.fontScale),
            ) { StageRows(m.stages, f) }
            m.totals.isNotEmpty() -> RingContent(
                m.title, m.note, m.word, RingWidget.SleepText, Ink, share, number, m.scoreDescription, AppIcons.MoonFill, f, lines, SleepLayout.BAR_DP,
            ) { StageBar(m, SleepLayout.BAR_DP, f) }
            else -> RingContent(m.title, m.note, m.word, RingWidget.SleepText, Ink, share, number, m.scoreDescription, AppIcons.MoonFill, f, lines)
        }
    }
}

/** The stage bar, 8 dp, with its legend under it on [legendRows] rows (none at 0). */
@Composable
private fun Stages(m: SleepWidgetModel, legendRows: Int, f: WidgetFrame) {
    // One child of the widget's column: Glance drops a column's children past ten.
    Column(GlanceModifier.fillMaxWidth()) {
        StageBar(m, SleepLayout.BAR_DP, f, described = legendRows == 0)
        if (legendRows > 0) {
            Spacer(GlanceModifier.height(SleepLayout.BAR_GAP_DP.dp))
            Legend(m.legend, legendRows)
        }
    }
}

/**
 * The legend: one row of every stage spread across the width, or two rows of two, each item in half the width, so a
 * label never wraps ([SleepLayout.legendRows] decides).
 */
@Composable
private fun Legend(items: List<SleepLegendItem>, rows: Int) {
    if (rows == 1) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, item ->
                if (i > 0) Spacer(GlanceModifier.defaultWeight())
                LegendItem(item)
            }
        }
        return
    }
    Column(GlanceModifier.fillMaxWidth()) {
        items.chunked(2).forEachIndexed { r, pair ->
            if (r > 0) Spacer(GlanceModifier.height(SleepLayout.LEGEND_ROW_GAP_DP.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                pair.forEach { item -> Box(GlanceModifier.defaultWeight()) { LegendItem(item) } }
                if (pair.size == 1) Box(GlanceModifier.defaultWeight()) {}
            }
        }
    }
}

/** An 8 dp colour mark and the stage's total, "Deep 1 h 32". */
@Composable
private fun LegendItem(item: SleepLegendItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.size(SleepLayout.MARK_DP.dp).cornerRadius(2.dp).background(ColorProvider(stageColor(item.stage))),
        ) {}
        Spacer(GlanceModifier.width(SleepLayout.MARK_GAP_DP.dp))
        Text(item.label, style = LegendStyle, maxLines = 1)
    }
}

/** The stage bar [heightDp] high across the content: the Sleep tab's [drawStageBar]. Read out when there's no legend. */
@Composable
private fun StageBar(m: SleepWidgetModel, heightDp: Float, f: WidgetFrame, described: Boolean = true) {
    val w = f.drawContentWidth * f.density
    val bitmap = renderToBitmap(w, heightDp * f.density) { c -> drawStageBar(c, m.totals, 0f, 0f, w, f.density, heightDp) }
        .let { if (WidgetRtl) mirrored(it) else it }
    Image(
        ImageProvider(bitmap),
        contentDescription = if (described) m.legend.joinToString(", ") { it.label } else null,
        modifier = GlanceModifier.fillMaxWidth().height(heightDp.dp),
        contentScale = ContentScale.FillBounds,
    )
}

/**
 * The time in each stage, as the Sleep score detail lists it: the name, a bar for its share of the night in the stage's
 * colour on a white 10% track, the time on the right. One child of the column, a row a stage.
 */
@Composable
private fun StageRows(rows: List<SleepStageRow>, f: WidgetFrame) {
    val track = f.drawContentWidth - SleepLayout.STAGE_NAME_DP - SleepLayout.STAGE_TIME_DP - 2 * SleepLayout.STAGE_GAP_DP
    Column(GlanceModifier.fillMaxWidth()) {
        rows.forEachIndexed { i, r ->
            if (i > 0) Spacer(GlanceModifier.height(SleepLayout.STAGE_ROW_GAP_DP.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, style = WidgetType.secondary, maxLines = 1, modifier = GlanceModifier.width(SleepLayout.STAGE_NAME_DP.dp))
                Spacer(GlanceModifier.width(SleepLayout.STAGE_GAP_DP.dp))
                Image(
                    ImageProvider(stageRowBar(r, track, f.density)),
                    contentDescription = LocalContext.current.getString(R.string.system_widget_sleep_stage_description, r.name, r.time),
                    modifier = GlanceModifier.defaultWeight().height(SleepLayout.STAGE_BAR_DP.dp),
                    contentScale = ContentScale.FillBounds,
                )
                Spacer(GlanceModifier.width(SleepLayout.STAGE_GAP_DP.dp))
                Text(
                    r.time,
                    style = WidgetType.secondaryShown.copy(textAlign = TextAlign.End),
                    maxLines = 1,
                    modifier = GlanceModifier.width(SleepLayout.STAGE_TIME_DP.dp),
                )
            }
        }
    }
}

/** A stage row's bar [widthDp] wide: the white 10% track, the stage's share filled in its colour, both fully round. */
private fun stageRowBar(r: SleepStageRow, widthDp: Float, density: Float): Bitmap {
    val w = (widthDp * density).coerceAtLeast(1f)
    val h = SleepLayout.STAGE_BAR_DP * density
    return renderToBitmap(w, h) { c ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Ink.copy(alpha = RingWidget.TRACK_ALPHA).toArgb()
        c.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, paint)
        paint.color = stageColor(r.stage).toArgb()
        val fill = (w * r.share).coerceIn(h, w)
        c.drawRoundRect(0f, 0f, fill, h, h / 2, h / 2, paint)
    }
}

/* ------------------------------------------------------------------------------------------------ */
/* Bitmaps                                                                                           */
/* ------------------------------------------------------------------------------------------------ */

/** Room for the dots, the stage bar and the stage rows' bars beside the backgrounds, in the bitmap budget. */
private const val BITMAP_EXTRA = 1.35
