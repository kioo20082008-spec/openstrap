package org.openstrap.app.data

import java.time.LocalDate

/** What a calibration decided, and why. Only [Active] changes a score. */
enum class CalibrationStatus {
    /** Fewer mornings than the calibration needs: the published formula stands, and it is still collecting. */
    Learning,

    /** The published formula already follows how you feel ([EnergyCalibration.GOOD_ENOUGH]): nothing to adjust. */
    NotNeeded,

    /** Enough mornings, but no adjustment beat the published formula on mornings it hadn't seen: it stands. */
    NoGain,

    /** An adjustment beat the published formula on held-out mornings, and is used. */
    Active,
}

/**
 * Personal calibration of Energy from the morning check-ins (docs/algorithms/calculations.md, section 7). No body-battery
 * has a published validation, so the only check there is, is yours: how rested you said you felt against the Energy at
 * wake. [fit] looks for a small set of multipliers on the sleep recharge, the exercise drain and the next-night carry-over
 * ([ChargeTuning]) that make Energy at wake follow your answers better, and keeps them only when they do so on mornings
 * they were not fitted on. Pure: the replay that produces the [Sample]s is [CalibrationRunner]'s.
 */
object EnergyCalibration {
    /** Mornings needed before anything is changed (three weeks), and the most recent mornings used. */
    const val MIN_DAYS = 21
    const val MAX_DAYS = 42

    /** The held-out Spearman must improve by this much to accept a change... */
    const val MIN_GAIN = 0.05f

    /**
     * ... and must itself reach this on those mornings: turning a poor fit into a slightly less poor one (or noise into a
     * lucky draw) is not a calibration.
     */
    const val MIN_AFTER = 0.2f

    /** A published formula that already follows the answers this well is left alone. */
    const val GOOD_ENOUGH = 0.6f

    /** Ridge: each fitted multiplier pays [RIDGE] times its squared distance from 1 out of the fitted Spearman. */
    const val RIDGE = 1.0f

    /** The values each multiplier tries: the ends of its allowed range, and the default between them. */
    val STEPS = listOf(1f, ChargeTuning.MIN, ChargeTuning.MAX)

    /** Every combination of [STEPS], the default first. [Sample.wake] follows this order. */
    val GRID: List<ChargeTuning> = STEPS.flatMap { s -> STEPS.flatMap { e -> STEPS.map { c -> ChargeTuning(s, e, c) } } }

    /** One morning: the feeling (1-5) and the Energy at wake the engine gives under each tuning of [GRID], same order. */
    data class Sample(val date: LocalDate, val feeling: Int, val wake: List<Float?>)

    /**
     * [status], the mornings used ([days]), the held-out Spearman of the published formula ([before]) and of the
     * best adjustment ([after]) when they were worked out, and the [tuning] to use (the default unless [Active]).
     */
    data class Outcome(
        val status: CalibrationStatus,
        val days: Int,
        val before: Float? = null,
        val after: Float? = null,
        val tuning: ChargeTuning = ChargeTuning.Default,
    )

    fun fit(samples: List<Sample>): Outcome {
        val usable = samples
            .filter { it.feeling in MorningFeel.MIN_RATING..MorningFeel.MAX_RATING && it.wake.size == GRID.size && it.wake.none { w -> w == null } }
            .sortedBy { it.date }
            .takeLast(MAX_DAYS)
        val n = usable.size
        if (n < MIN_DAYS) return Outcome(CalibrationStatus.Learning, n)

        // The first two thirds of the mornings fit, the last third checks.
        val split = n * 2 / 3
        val train = usable.subList(0, split)
        val test = usable.subList(split, n)
        fun r(rows: List<Sample>, i: Int) =
            MorningFeel.spearman(rows.map { it.feeling.toFloat() }, rows.map { it.wake[i] ?: 0f })

        val before = r(test, 0)
        if (before != null && before >= GOOD_ENOUGH) return Outcome(CalibrationStatus.NotNeeded, n, before)

        var best = 0
        var bestScore = (r(train, 0) ?: NO_FIT) - penalty(GRID[0])
        for (i in 1 until GRID.size) {
            val score = (r(train, i) ?: continue) - penalty(GRID[i])
            if (score > bestScore) {
                best = i
                bestScore = score
            }
        }
        val after = r(test, best)
        val accepted = best != 0 && before != null && after != null && after - before >= MIN_GAIN && after >= MIN_AFTER
        return if (accepted) {
            Outcome(CalibrationStatus.Active, n, before, after, GRID[best])
        } else {
            Outcome(CalibrationStatus.NoGain, n, before, after.takeIf { best != 0 })
        }
    }

    private const val NO_FIT = -1f

    private fun penalty(t: ChargeTuning): Float =
        RIDGE * ((t.sleep - 1f) * (t.sleep - 1f) + (t.exercise - 1f) * (t.exercise - 1f) + (t.carry - 1f) * (t.carry - 1f))
}
