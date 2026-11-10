package org.openstrap.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.openstrap.app.data.db.CheckInRow
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.settings.UserProfile
import java.time.LocalDate
import java.time.ZoneId

/**
 * Replays the engine over the mornings the user answered the check-in, as [Backtest] does, and hands the results to
 * [EnergyCalibration] and [ReadinessCalibration]. Each answered morning costs one pass over the data ([RecoveryEngine.replay],
 * a few hundred ms) and then one in-memory Charge run per [EnergyCalibration.GRID] entry, so it runs off the main thread, at
 * most every [CalibrationResult.STALE_DAYS] days, and not at all before either calibration has the mornings it needs. The
 * replay uses only the last [REPLAY_DAYS] days.
 */
class CalibrationRunner(private val dao: HealthDao, private val engine: RecoveryEngine = RecoveryEngine(dao)) {

    suspend fun run(today: LocalDate, profile: UserProfile, zone: ZoneId, nowSeconds: Long): CalibrationResult {
        val answers = dao.checkInsOnce(today.minusDays(REPLAY_DAYS - 1L), today)
            .filter { it.feeling in MorningFeel.MIN_RATING..MorningFeel.MAX_RATING }
            .sortedBy { it.day }
        val energyFrom = today.minusDays(EnergyCalibration.MAX_DAYS - 1L)
        val energyDays = answers.count { it.day >= energyFrom }
        val doEnergy = energyDays >= EnergyCalibration.MIN_DAYS
        val doReadiness = answers.size >= ReadinessCalibration.MIN_DAYS
        // Too few answers for either: say so without replaying a day.
        if (!doEnergy && !doReadiness) {
            return CalibrationResult(
                today, EnergyCalibration.Outcome(CalibrationStatus.Learning, energyDays),
                ReadinessCalibration.Outcome(CalibrationStatus.Learning, answers.size),
            )
        }

        val samples = ArrayList<EnergyCalibration.Sample>()
        val days = ArrayList<ReadinessCalibration.Day>()
        for (row in answers) {
            val wantEnergy = doEnergy && row.day >= energyFrom
            if (!wantEnergy && !doReadiness) continue
            currentCoroutineContext().ensureActive()
            // As of the end of that morning's day, so the Charge run covers all of it; today's stops at the present.
            val now = minOf(nowSeconds, DayWindow.of(row.day, zone).to + 1)
            val replay = catching { engine.replay(row.day, profile, zone, now) }.getOrNull() ?: continue
            if (doReadiness) {
                val r = replay.readiness
                if (r.score != null) {
                    days += ReadinessCalibration.Day(row.day, row.feeling, r.contributors.associate { it.factor to it.score }, r.strainAlert)
                }
            }
            if (wantEnergy) sample(row, replay.charge)?.let(samples::add)
        }
        return CalibrationResult(today, EnergyCalibration.fit(samples), ReadinessCalibration.fit(days))
    }

    /**
     * The Energy at wake the morning had under every tuning of [EnergyCalibration.GRID]. Null when the published run has no
     * Energy at wake worth comparing with: no sleep seen yet, or the strap off overnight (an estimated wake).
     */
    private fun sample(row: CheckInRow, inputs: ChargeInputs): EnergyCalibration.Sample? {
        val wake = EnergyCalibration.GRID.map { tuning ->
            val day = Recovery.charge(
                inputs.minutes, inputs.params.copy(tuning = tuning), inputs.window, start = 50f,
                sampleSeconds = ONE_DAY, mainSleepEnd = inputs.mainSleepEnd,
            )
            if (tuning.isDefault && (!day.anchored || day.strapOffOvernight)) return null
            day.atWake
        }
        return EnergyCalibration.Sample(row.day, row.feeling, wake)
    }

    companion object {
        /** How far back the replay goes: the Energy fit uses the last [EnergyCalibration.MAX_DAYS] of these. */
        const val REPLAY_DAYS = 60
        private const val ONE_DAY = 86_400L
    }
}
