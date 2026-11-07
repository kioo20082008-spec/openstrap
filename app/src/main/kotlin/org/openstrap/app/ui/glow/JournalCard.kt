package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.Insights
import org.openstrap.app.data.Journal
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.AppPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** The journal for the screens: every day's habits, what they have been found to do, and how to switch one. */
@Immutable
class JournalUi(
    val tags: Map<LocalDate, Set<Journal.Tag>>,
    val insights: Insights.Result,
    val toggle: (LocalDate, Journal.Tag) -> Unit,
)

val LocalJournal = compositionLocalOf<JournalUi?> { null }

/** The evening starts here, and the day still counts as the evening until this hour of the night. */
private const val EVENING_FROM_HOUR = 16
private const val NIGHT_UNTIL_HOUR = 5
private const val CHIPS_A_ROW = 2

/**
 * What shaped your day: the habits in one tap, in the evening, and under them what your own mornings have shown about
 * each ([Insights]). A habit logged after midnight still belongs to the evening before, so it is read against the
 * morning it leads to.
 */
// The card reads the clock it is given, not its own.
@Composable
internal fun JournalCard(now: Long, zone: ZoneId, isToday: Boolean) {
    val journal = LocalJournal.current ?: return
    val time = Instant.ofEpochSecond(now).atZone(zone)
    val day = time.minusHours(NIGHT_UNTIL_HOUR.toLong()).toLocalDate()
    val mine = journal.tags[day].orEmpty()
    val evening = time.hour >= EVENING_FROM_HOUR || time.hour < NIGHT_UNTIL_HOUR
    if (!isToday || !(evening || mine.isNotEmpty())) return
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(shape).background(AppColors.glass).border(1.dp, AppColors.hairline, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(stringResource(R.string.journal_title), color = AppColors.ink, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.journal_hint), color = AppColors.muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Journal.Tag.entries.chunked(CHIPS_A_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tag -> Chip(tagName(tag), tag in mine, Modifier.weight(1f)) { journal.toggle(day, tag) } }
                repeat(CHIPS_A_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        val result = journal.insights
        if (result.findings.isEmpty()) {
            if (result.loggedDays > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.journal_learning, result.loggedDays, Insights.MIN_DAYS),
                    color = AppColors.muted, style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.hairline))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.journal_found), color = AppColors.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            result.findings.forEach { f ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 7.dp).size(9.dp).clip(CircleShape).background(if (f.good) Color(0xFF5FD3A6) else AppPalette.Coral))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.journal_finding, tagName(f.tag), effect(f), f.withDays, f.withoutDays),
                        color = AppColors.ink, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.journal_caveat), color = AppColors.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (on) AppPalette.Coral else AppColors.ink
    Box(
        modifier.clip(CircleShape).background(tint.copy(alpha = if (on) 0.16f else 0.06f))
            .border(1.dp, if (on) AppPalette.Coral.copy(alpha = 0.6f) else AppColors.hairline, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (on) AppPalette.Coral else AppColors.ink,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
        )
    }
}

@Composable
private fun tagName(tag: Journal.Tag): String = stringResource(
    when (tag) {
        Journal.Tag.LateCaffeine -> R.string.journal_tag_caffeine
        Journal.Tag.HeavyDinner -> R.string.journal_tag_dinner
        Journal.Tag.LateWorkout -> R.string.journal_tag_workout
        Journal.Tag.LateScreens -> R.string.journal_tag_screens
        Journal.Tag.HardDay -> R.string.journal_tag_stress
        Journal.Tag.LongNap -> R.string.journal_tag_nap
    },
)

/** What the habit did to the next morning, in words with the size: "HRV lower by 9%". */
@Composable
private fun effect(f: Insights.Finding): String {
    val lower = f.diff < 0
    return when (f.metric) {
        Insights.Metric.Hrv -> stringResource(if (lower) R.string.journal_effect_hrv_down else R.string.journal_effect_hrv_up, f.percent)
        Insights.Metric.RestingHr ->
            stringResource(if (lower) R.string.journal_effect_rhr_down else R.string.journal_effect_rhr_up, abs(f.diff).roundToInt().coerceAtLeast(1))
        Insights.Metric.SleepScore ->
            stringResource(if (lower) R.string.journal_effect_sleep_down else R.string.journal_effect_sleep_up, abs(f.diff).roundToInt().coerceAtLeast(1))
    }
}
