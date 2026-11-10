package org.openstrap.app.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.CalibrationResult
import org.openstrap.app.data.CalibrationStatus
import org.openstrap.app.data.MorningFeel

private const val STRONG_R = 0.5f
private const val MIDDLE_R = 0.3f

/**
 * The line under the morning check-in strip: how many mornings have been answered, and, from [MorningFeel.MIN_DAYS] on,
 * how well the Energy at wake followed how you felt ([MorningFeel.fit]), the check on whether the formula suits you.
 * Then, from there on, what the personal calibration made of it ([calibration]): still collecting, not needed, nothing
 * better found, or active.
 */
@Composable
internal fun morningFitNote(answers: Map<java.time.LocalDate, MorningFeel.Entry>, calibration: CalibrationResult? = null): String? {
    if (answers.isEmpty()) return null
    val fit = MorningFeel.fit(answers)
    val r = fit.energyR ?: return stringResource(R.string.checkin_learning, fit.days, MorningFeel.MIN_DAYS)
    val how = when {
        r >= STRONG_R -> R.string.checkin_fit_strong
        r >= MIDDLE_R -> R.string.checkin_fit_middle
        else -> R.string.checkin_fit_weak
    }
    val line = stringResource(R.string.checkin_fit, stringResource(how), fit.days)
    return calibration?.let { "$line ${calibrationNote(it)}" } ?: line
}

/** What the personal calibration did, in a sentence ([CalibrationResult]); Recovery's weights are added when they are tuned too. */
@Composable
private fun calibrationNote(c: CalibrationResult): String {
    val energy = when (c.energy.status) {
        CalibrationStatus.Learning -> stringResource(R.string.checkin_cal_learning, c.energy.days, org.openstrap.app.data.EnergyCalibration.MIN_DAYS)
        CalibrationStatus.NotNeeded -> stringResource(R.string.checkin_cal_not_needed)
        CalibrationStatus.NoGain -> stringResource(R.string.checkin_cal_no_gain)
        CalibrationStatus.Active -> stringResource(R.string.checkin_cal_active, twoDecimals(c.energy.after), twoDecimals(c.energy.before))
    }
    return if (c.readiness.status == CalibrationStatus.Active) "$energy ${stringResource(R.string.checkin_cal_readiness)}" else energy
}

/** A correlation to two decimals, in Western digits. */
private fun twoDecimals(r: Float?): String = r?.let { "%.2f".format(java.util.Locale.ROOT, it) } ?: "-"
