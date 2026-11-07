package org.openstrap.app.ui.glow

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import org.openstrap.app.R
import org.openstrap.app.data.WeeklyReport
import org.openstrap.app.presentation.DayWords
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.dashboard.WeeklyReportViewModel
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.AppIcons

private const val SECONDS_A_MINUTE = 60L

/** Wraps [text] so a sign or a figure keeps its left-to-right order inside Arabic. */
private fun ltr(text: String) = "⁦$text⁩"

private fun signed(n: Int) = String.format(Locale.ROOT, "%+d", n)

/**
 * The week report: the last seven days against the seven before, a line on what to do next week, and the figures
 * under it. Flat panels and thin numerals like the tabs, and a share button with the same lines as text.
 */
@Composable
fun WeeklyReportPage(vm: WeeklyReportViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader(stringResource(R.string.report_title), onBack) }
        when (val s = state) {
            WeeklyReportViewModel.State.Loading -> item { Note(stringResource(R.string.report_loading)) }
            WeeklyReportViewModel.State.Unavailable -> item { Note(stringResource(R.string.report_unavailable)) }
            is WeeklyReportViewModel.State.Ready -> reportItems(s.report)
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = AppColors.ink.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.body, modifier = Modifier.padding(horizontal = 4.dp))
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportItems(r: WeeklyReport.Report) {
    item { Range(r.first, r.last) }
    item { AdviceCard(r) }
    item { Trio(r) }
    item { Figures(r) }
    item { ShareButton(r) }
    item { PageNote(stringResource(R.string.report_foot)) }
}

@Composable
private fun Range(first: LocalDate, last: LocalDate) {
    Text(
        stringResource(R.string.report_range, DayWords.short(first), DayWords.short(last)),
        color = AppColors.ink.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.body, modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(FlatSurface).border(1.dp, FlatHairline, shape).padding(horizontal = 20.dp, vertical = 18.dp)) {
        content()
    }
}

/** What to do next week, in one line with its reason. */
@Composable
private fun adviceText(r: WeeklyReport.Report): Pair<String, String> = when (r.advice) {
    WeeklyReport.Advice.NoData -> stringResource(R.string.report_advice_nodata_title) to stringResource(R.string.report_advice_nodata_body)
    WeeklyReport.Advice.Ease -> stringResource(R.string.report_advice_ease_title) to stringResource(R.string.report_advice_ease_body)
    WeeklyReport.Advice.Sleep -> {
        val short = (r.sleepNeed - (r.asleepMinutes.value ?: r.sleepNeed.toFloat())).roundToInt().coerceAtLeast(0)
        stringResource(R.string.report_advice_sleep_title) to stringResource(R.string.report_advice_sleep_body, Units.duration(short * SECONDS_A_MINUTE))
    }
    WeeklyReport.Advice.Build -> stringResource(R.string.report_advice_build_title) to stringResource(R.string.report_advice_build_body)
    WeeklyReport.Advice.Hold -> stringResource(R.string.report_advice_hold_title) to stringResource(R.string.report_advice_hold_body)
}

@Composable
private fun AdviceCard(r: WeeklyReport.Report) {
    val (title, body) = adviceText(r)
    Panel {
        Text(stringResource(R.string.report_next_week), color = AppColors.muted, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Text(title, color = AppColors.ink, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
        Spacer(Modifier.height(6.dp))
        Text(body, color = AppColors.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Recovery, sleep score and steps, large and thin, each with how it moved. */
@Composable
private fun Trio(r: WeeklyReport.Report) {
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Big(stringResource(R.string.report_readiness), r.readiness.value?.roundToInt()?.toString(), r.readiness.change?.roundToInt()?.let(::signed))
            Big(stringResource(R.string.report_sleep_score), r.sleepScore.value?.roundToInt()?.toString(), r.sleepScore.change?.roundToInt()?.let(::signed))
            Big(stringResource(R.string.report_steps), r.steps.value?.roundToInt()?.let { Units.count(it) }, r.steps.change?.roundToInt()?.let(::signed))
        }
    }
}

@Composable
private fun Big(label: String, value: String?, change: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Ltr { Text(value ?: "–", color = AppColors.ink, style = GlowType.storyStat, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(2.dp))
        Text(label, color = AppColors.muted, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        if (change != null) Ltr { Text(change, color = AppColors.muted, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun Figures(r: WeeklyReport.Report) {
    val min = stringResource(R.string.report_min)
    val asleep = r.asleepMinutes
    val asleepText = asleep.value?.let { Units.duration(it.roundToInt() * SECONDS_A_MINUTE) + change(asleep.change, min) }
    PageRows {
        PageRow(stringResource(R.string.report_asleep), asleepText, below = true, divider = false, icon = AppIcons.Moon)
        PageRow(stringResource(R.string.report_rhr), figure(r.restingHr), below = true, icon = AppIcons.Heartbeat)
        PageRow(stringResource(R.string.report_hrv), figure(r.hrv), below = true, icon = AppIcons.ChartLineUp)
        PageRow(stringResource(R.string.report_load), figure(r.load), below = true, icon = AppIcons.ChartBar)
        r.best?.let { (d, v) ->
            PageRow(stringResource(R.string.report_best), stringResource(R.string.report_day_score, DayWords.short(d), v), below = true, icon = AppIcons.Target)
        }
        r.worst?.let { (d, v) ->
            PageRow(stringResource(R.string.report_worst), stringResource(R.string.report_day_score, DayWords.short(d), v), below = true, icon = AppIcons.FlagCheckered)
        }
    }
}

/** A figure with how it moved against the week before, "58 (-1)"; null without the figure. */
private fun figure(stat: WeeklyReport.Stat): String? = stat.value?.roundToInt()?.let { it.toString() + change(stat.change, "") }

/** " (+3)" in its own left-to-right run, with [unit] after the number; nothing without a change. */
private fun change(by: Float?, unit: String): String = by?.roundToInt()?.let { " " + ltr("(" + signed(it) + unit + ")") } ?: ""

@Composable
private fun ShareButton(r: WeeklyReport.Report) {
    val context = LocalContext.current
    val (title, body) = adviceText(r)
    val text = stringResource(
        R.string.report_share, DayWords.short(r.first), DayWords.short(r.last),
        r.readiness.value?.roundToInt()?.toString() ?: "–", r.sleepScore.value?.roundToInt()?.toString() ?: "–",
        r.steps.value?.roundToInt()?.let { Units.count(it) } ?: "–", title, body,
    )
    val chooser = stringResource(R.string.report_share_title)
    PageButton(
        stringResource(R.string.report_share_button),
        {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, chooser))
        },
        modifier = Modifier.fillMaxWidth(), icon = AppIcons.UploadSimple,
    )
}
