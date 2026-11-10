package org.openstrap.app.data

import org.openstrap.app.data.Recovery.Factor
import java.time.LocalDate

/**
 * What the personal calibrations ([EnergyCalibration], [ReadinessCalibration]) concluded on [day], kept in settings so the
 * replay behind them runs about every [STALE_DAYS] days rather than at every start. Only an [CalibrationStatus.Active]
 * calibration changes a score: [tuning] and [weights] are the published ones otherwise.
 */
data class CalibrationResult(
    val day: LocalDate,
    val energy: EnergyCalibration.Outcome,
    val readiness: ReadinessCalibration.Outcome,
) {
    val tuning: ChargeTuning
        get() = if (energy.status == CalibrationStatus.Active) energy.tuning.clamped() else ChargeTuning.Default

    val weights: ReadinessWeights
        get() = if (readiness.status == CalibrationStatus.Active) readiness.weights else ReadinessWeights.Default

    /** One line of text: `1;2026-11-10;E,active,24,0.31,0.52,1.3,1.0,0.7;R,learning,12,,,1.0,...` (nine weights). */
    fun encode(): String = listOf(
        VERSION, day.toString(),
        listOf("E", energy.status.key, energy.days, energy.before ?: "", energy.after ?: "", energy.tuning.sleep, energy.tuning.exercise, energy.tuning.carry)
            .joinToString(","),
        (listOf("R", readiness.status.key, readiness.days, readiness.before ?: "", readiness.after ?: "") +
            Factor.entries.map { readiness.weights.multiplier(it) }).joinToString(","),
    ).joinToString(";")

    companion object {
        private const val VERSION = "1"

        /** A cached result older than this many days is worked out again. */
        const val STALE_DAYS = 3L

        /** What [encode] wrote; null for anything else, so a damaged value just means a fresh calibration. */
        fun decode(raw: String?): CalibrationResult? {
            val parts = raw?.split(';') ?: return null
            if (parts.size != 4 || parts[0] != VERSION) return null
            val day = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
            val e = parts[2].split(',')
            val r = parts[3].split(',')
            if (e.size != 8 || e[0] != "E" || r.size != 5 + Factor.entries.size || r[0] != "R") return null
            val eStatus = statusOf(e[1]) ?: return null
            val rStatus = statusOf(r[1]) ?: return null
            val tuning = ChargeTuning(e[5].toFloatOrNull() ?: 1f, e[6].toFloatOrNull() ?: 1f, e[7].toFloatOrNull() ?: 1f).clamped()
            val weights = ReadinessWeights.of(
                Factor.entries.withIndex().associate { (i, f) -> f to (r[5 + i].toFloatOrNull() ?: 1f) },
            )
            return CalibrationResult(
                day,
                EnergyCalibration.Outcome(eStatus, e[2].toIntOrNull() ?: 0, e[3].toFloatOrNull(), e[4].toFloatOrNull(), tuning),
                ReadinessCalibration.Outcome(rStatus, r[2].toIntOrNull() ?: 0, r[3].toFloatOrNull(), r[4].toFloatOrNull(), weights),
            )
        }

        /** Whether [cached] should be worked out again on [today]. */
        fun due(cached: CalibrationResult?, today: LocalDate): Boolean {
            if (cached == null || cached.day > today || cached.day.plusDays(STALE_DAYS) <= today) return true
            // Still collecting costs one query, not a replay, so it looks again each day.
            return cached.day < today && cached.energy.status == CalibrationStatus.Learning &&
                cached.readiness.status == CalibrationStatus.Learning
        }

        private fun statusOf(key: String): CalibrationStatus? = CalibrationStatus.entries.firstOrNull { it.key == key }
    }
}

private val CalibrationStatus.key: String get() = name.lowercase()
