package org.openstrap.app.data

import org.openstrap.app.data.Recovery.Factor
import java.time.LocalDate

/**
 * Personal Readiness weights from the morning check-ins (docs/algorithms/readiness-calibration.md, "Personal weights").
 * [fit] asks one question: would re-weighting the contributors the score already has follow how you felt better on
 * mornings the weights were not fitted on? Each weight may move within [ReadinessWeights.MIN]..[ReadinessWeights.MAX] of
 * its default and a ridge pulls it back towards the default. The new weights are used only when the held-out Spearman
 * improves by [MIN_GAIN] to at least [EnergyCalibration.MIN_AFTER], and the published weights do not already reach
 * [EnergyCalibration.GOOD_ENOUGH]. Nothing else of the score changes: the contributors, the red-flag cap and the Alert cap
 * stay. Pure: the replay that produces the [Day]s is [CalibrationRunner]'s.
 */
object ReadinessCalibration {
    /** Mornings needed (six weeks) before anything is changed. */
    const val MIN_DAYS = 42

    const val MIN_GAIN = EnergyCalibration.MIN_GAIN

    /** Ridge: each multiplier pays [RIDGE] times its squared distance from 1 out of the fitted Spearman. */
    const val RIDGE = 0.1f

    /** Passes of the coordinate search over the factors. */
    private const val PASSES = 3

    /** The values one weight's multiplier tries. */
    val STEPS = listOf(ReadinessWeights.MIN, 0.75f, 1f, 1.25f, ReadinessWeights.MAX)

    /** One morning: the feeling (1-5), the contributors that day's Readiness had (score each) and whether it was an Alert. */
    data class Day(val date: LocalDate, val feeling: Int, val parts: Map<Factor, Int>, val alert: Boolean)

    /** As [EnergyCalibration.Outcome]: the held-out Spearman of the published weights and of the new ones. */
    data class Outcome(
        val status: CalibrationStatus,
        val days: Int,
        val before: Float? = null,
        val after: Float? = null,
        val weights: ReadinessWeights = ReadinessWeights.Default,
    )

    /** The score the day would have shown under [weights]: the published fusion with its caps. */
    fun score(day: Day, weights: ReadinessWeights): Int =
        Recovery.fusedScore(Factor.entries.mapNotNull { f -> day.parts[f]?.let { f to it } }, day.alert, weights).first

    fun fit(days: List<Day>): Outcome {
        // A day with neither HRV nor resting HR had no score to compare with.
        val usable = days
            .filter { it.feeling in MorningFeel.MIN_RATING..MorningFeel.MAX_RATING }
            .filter { Factor.HrvBalance in it.parts || Factor.RestingHr in it.parts }
            .sortedBy { it.date }
        val n = usable.size
        if (n < MIN_DAYS) return Outcome(CalibrationStatus.Learning, n)

        val split = n * 2 / 3
        val train = usable.subList(0, split)
        val test = usable.subList(split, n)
        fun r(rows: List<Day>, w: ReadinessWeights) =
            MorningFeel.spearman(rows.map { it.feeling.toFloat() }, rows.map { score(it, w).toFloat() })

        val before = r(test, ReadinessWeights.Default)
        if (before != null && before >= EnergyCalibration.GOOD_ENOUGH) return Outcome(CalibrationStatus.NotNeeded, n, before)

        // Coordinate search from the published weights: each pass tries every step for each factor in turn and keeps
        // the best on the fitting mornings, ridge included. A tie keeps what it has, which is the nearer to 1.
        val multiplier = HashMap<Factor, Float>()
        fun objective(m: Map<Factor, Float>): Float {
            val fit = r(train, ReadinessWeights.of(m)) ?: NO_FIT
            return fit - RIDGE * Factor.entries.sumOf { f -> ((m[f] ?: 1f) - 1f).let { (it * it).toDouble() } }.toFloat()
        }
        var current = objective(multiplier)
        repeat(PASSES) {
            for (f in Factor.entries) {
                var bestValue = multiplier[f] ?: 1f
                for (step in STEPS) {
                    if (step == bestValue) continue
                    val score = objective(multiplier + (f to step))
                    if (score > current + EPSILON) {
                        current = score
                        bestValue = step
                    }
                }
                multiplier[f] = bestValue
            }
        }
        val weights = ReadinessWeights.of(multiplier)
        if (weights.isDefault) return Outcome(CalibrationStatus.NoGain, n, before)
        val after = r(test, weights)
        return if (before != null && after != null && after - before >= MIN_GAIN && after >= EnergyCalibration.MIN_AFTER) {
            Outcome(CalibrationStatus.Active, n, before, after, weights)
        } else {
            Outcome(CalibrationStatus.NoGain, n, before, after)
        }
    }

    private const val NO_FIT = -1f
    private const val EPSILON = 1e-6f
}
