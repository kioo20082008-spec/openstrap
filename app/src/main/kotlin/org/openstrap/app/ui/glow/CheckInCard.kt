package org.openstrap.app.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.MorningFeel

private const val STRONG_R = 0.5f
private const val MIDDLE_R = 0.3f

/**
 * The line under the morning check-in strip: how many mornings have been answered, and, from [MorningFeel.MIN_DAYS] on,
 * how well the Energy at wake followed how you felt ([MorningFeel.fit]), the check on whether the formula suits you.
 */
@Composable
internal fun morningFitNote(answers: Map<java.time.LocalDate, MorningFeel.Entry>): String? {
    if (answers.isEmpty()) return null
    val fit = MorningFeel.fit(answers)
    val r = fit.energyR ?: return stringResource(R.string.checkin_learning, fit.days, MorningFeel.MIN_DAYS)
    val how = when {
        r >= STRONG_R -> R.string.checkin_fit_strong
        r >= MIDDLE_R -> R.string.checkin_fit_middle
        else -> R.string.checkin_fit_weak
    }
    return stringResource(R.string.checkin_fit, stringResource(how), fit.days)
}
