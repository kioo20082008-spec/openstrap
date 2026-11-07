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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.CheckIn
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.AppPalette
import java.time.Instant
import java.time.ZoneId

private const val MORNING_FROM_HOUR = 4
private const val MORNING_UNTIL_HOUR = 14
private const val STRONG_R = 0.5f
private const val MIDDLE_R = 0.3f

/**
 * "How rested do you feel?", 1 to 5, asked in the morning before the numbers are looked at. Each answer is kept with
 * the Energy and Recovery shown that morning, so after [CheckIn.MIN_DAYS] answers the card can say how well they follow
 * how you actually felt ([CheckIn.fit]): the only way to know the computed readings are right for you.
 */
// The card reads the clock it is given, not its own.
@Composable
internal fun CheckInCard(now: Long, zone: ZoneId, isToday: Boolean, energyAtWake: Int?, readiness: Int?) {
    val journal = LocalJournal.current ?: return
    val time = Instant.ofEpochSecond(now).atZone(zone)
    if (!isToday || time.hour !in MORNING_FROM_HOUR until MORNING_UNTIL_HOUR) return
    val day = time.toLocalDate()
    val mine = journal.checkIns[day]
    val fit = CheckIn.fit(journal.checkIns)
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(shape).background(AppColors.glass).border(1.dp, AppColors.hairline, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(stringResource(R.string.checkin_title), color = AppColors.ink, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.checkin_hint), color = AppColors.muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (rating in CheckIn.MIN_RATING..CheckIn.MAX_RATING) {
                val on = mine?.rating == rating
                Box(
                    Modifier.weight(1f).height(48.dp).clip(CircleShape)
                        .background((if (on) AppPalette.Coral else AppColors.ink).copy(alpha = if (on) 0.16f else 0.06f))
                        .border(1.dp, if (on) AppPalette.Coral.copy(alpha = 0.6f) else AppColors.hairline, CircleShape)
                        .clickable { journal.checkIn(day, CheckIn.Entry(rating, energyAtWake, readiness)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        rating.toString(), color = if (on) AppPalette.Coral else AppColors.ink,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.checkin_low), color = AppColors.muted, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.checkin_high), color = AppColors.muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        val energyR = fit.energyR
        if (energyR == null) {
            Text(
                stringResource(R.string.checkin_learning, fit.days, CheckIn.MIN_DAYS),
                color = AppColors.muted, style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(9.dp).clip(CircleShape).background(AppPalette.Coral))
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(R.string.checkin_fit, stringResource(strength(energyR)), fit.days),
                    color = AppColors.ink, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun strength(r: Float): Int = when {
    r >= STRONG_R -> R.string.checkin_fit_strong
    r >= MIDDLE_R -> R.string.checkin_fit_middle
    else -> R.string.checkin_fit_weak
}
