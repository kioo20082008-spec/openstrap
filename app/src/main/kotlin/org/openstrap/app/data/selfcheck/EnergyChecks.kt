package org.openstrap.app.data.selfcheck

import org.openstrap.app.data.Recovery

/*
 * The Energy checks: Energy is a body-battery, and a few things about it cannot be true however the day went. Each is
 * one small class, and each Search plan lets the planner turn Energy's own knobs until the day reads plausibly again.
 */

/** Minutes of one stretch of [TraceMinute]s, by index into the day's list. */
internal class Run(val from: Int, val to: Int) {
    val length: Int get() = to - from + 1
}

internal object Traces {
    /** A gap between two minutes of more than this many seconds ends a run. */
    private const val BREAK_SECONDS = 120L

    /** The longest run of consecutive minutes of [minutes] that all satisfy [hit], or null when none do. */
    fun longest(minutes: List<TraceMinute>, hit: (TraceMinute) -> Boolean): Run? {
        var best: Run? = null
        var i = 0
        while (i < minutes.size) {
            if (!hit(minutes[i])) {
                i++
                continue
            }
            var j = i
            while (j + 1 < minutes.size && hit(minutes[j + 1]) && minutes[j + 1].t - minutes[j].t <= BREAK_SECONDS) j++
            if (best == null || j - i + 1 > best.length) best = Run(i, j)
            i = j + 1
        }
        return best
    }

    fun steps(minutes: List<TraceMinute>, from: Int, to: Int): Int = (from..to).sumOf { minutes[it].steps }

    /** Minutes inside a recorded workout whose time is within [from, to] (epoch seconds). */
    fun workoutMinutes(minutes: List<TraceMinute>, from: Long, to: Long): Int = minutes.count { it.workout && it.t in from..to }

    fun stepsBetween(minutes: List<TraceMinute>, from: Long, to: Long): Int = minutes.filter { it.t in from..to }.sumOf { it.steps }

    fun active(state: Recovery.State) = state == Recovery.State.Exercise || state == Recovery.State.Activity
}

private val TUNABLE = listOf(Knob.Alert, Knob.Active, Knob.Rest, Knob.Cap, Knob.Resting)

/**
 * Energy at 0 for hours while awake. A drained battery after a long day is real; after a night's sleep, with few steps
 * and no long workout, it is the model, not the body (a day with 0 from 16:30 to 21:47 on 975 steps).
 * nums: minutes at 0, steps in them, minutes asleep the night before (-1 unknown).
 */
class EnergyStuckEmpty : DayCheck {
    override val id = "energy.stuck_empty"
    override val domain = Domain.Energy

    override fun inspectDay(day: DayTrace): Finding? {
        val run = Traces.longest(day.minutes) { !it.asleep && it.value <= EMPTY } ?: return null
        if (run.length < MIN_MINUTES) return null
        val start = day.minutes[run.from].t
        val end = day.minutes[run.to].t
        // A very hard day can really empty the tank: a long workout or very many steps in the hours up to it, or a day
        // of walking whose steps are not all in those hours.
        val lead = start - LEAD_SECONDS
        if (day.steps >= HARD_DAY_STEPS) return null
        if (Traces.workoutMinutes(day.minutes, lead, end) >= HARD_WORKOUT_MINUTES || Traces.stepsBetween(day.minutes, lead, end) >= HARD_STEPS) return null
        val severity = if (run.length >= HIGH_MINUTES) Severity.High else Severity.Medium
        return Finding(
            id, domain, severity, day.date,
            listOf(run.length.toFloat(), Traces.steps(day.minutes, run.from, run.to).toFloat(), (day.wakeAsleepMinutes ?: -1).toFloat()),
            Plan.Search(TUNABLE, Direction.Soften),
        )
    }

    companion object {
        const val EMPTY = 1f
        const val MIN_MINUTES = 180
        const val HIGH_MINUTES = 300
        private const val LEAD_SECONDS = 3 * 3600L
        private const val HARD_WORKOUT_MINUTES = 90
        private const val HARD_STEPS = 8_000
        private const val HARD_DAY_STEPS = 15_000
    }
}

/**
 * Energy full for most of a day that was spent moving: the drain is too weak. nums: minutes at the top, steps in them.
 */
class EnergyStuckFull : DayCheck {
    override val id = "energy.stuck_full"
    override val domain = Domain.Energy

    override fun inspectDay(day: DayTrace): Finding? {
        val run = Traces.longest(day.minutes) { !it.asleep && it.value >= FULL } ?: return null
        if (run.length < MIN_MINUTES) return null
        val steps = Traces.steps(day.minutes, run.from, run.to)
        if (steps < MIN_STEPS) return null
        return Finding(
            id, domain, Severity.Medium, day.date, listOf(run.length.toFloat(), steps.toFloat()),
            Plan.Search(listOf(Knob.Active, Knob.Rest), Direction.Strengthen),
        )
    }

    companion object {
        const val FULL = 99f
        const val MIN_MINUTES = 360
        const val MIN_STEPS = 4_000
    }
}

/**
 * Energy at wake under [LOW_WAKE] after a night of [MIN_NIGHT_MINUTES] or more. Zepp wakes above 80 after such a night; the
 * model's slowest recharge (a poor HRV night, a raised heart rate) still gets past the mid fifties. nums: Energy at wake,
 * minutes asleep, whether an Alert was on (1 or 0).
 */
class EnergyLowWake : DayCheck {
    override val id = "energy.low_wake"
    override val domain = Domain.Energy

    override fun inspectDay(day: DayTrace): Finding? {
        val wake = day.atWake ?: return null
        val asleep = day.wakeAsleepMinutes ?: return null
        if (day.offOvernight || asleep < MIN_NIGHT_MINUTES || wake >= LOW_WAKE) return null
        return Finding(
            id, domain, if (wake < VERY_LOW_WAKE) Severity.High else Severity.Medium, day.date,
            listOf(wake, asleep.toFloat(), if (day.alertNights > 0) 1f else 0f),
            Plan.Search(TUNABLE, Direction.Soften),
        )
    }

    companion object {
        const val LOW_WAKE = 55f
        const val VERY_LOW_WAKE = 40f
        const val MIN_NIGHT_MINUTES = 8 * 60
    }
}

/**
 * Energy drained by movement far faster than the day's steps and workouts can account for: in some [WINDOW_MINUTES] window,
 * the points lost in Activity and Exercise minutes exceed [BASE] plus [PER_1000_STEPS] a thousand steps plus [PER_WORKOUT_MINUTE]
 * a workout minute. The [BASE] is one unrecorded ride or row, which the sustained-effort rule really counts.
 * nums: points lost, steps in the window, minutes in it, minutes of it counted as effort without a step.
 */
class EnergyDrainFast : DayCheck {
    override val id = "energy.drain_fast"
    override val domain = Domain.Energy

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun inspectDay(day: DayTrace): Finding? {
        val m = day.minutes
        val n = m.size
        if (n < MIN_WINDOW_MINUTES) return null
        // Prefix sums: points lost in effort minutes, steps, workout minutes, effort minutes without a step.
        val drop = FloatArray(n + 1)
        val steps = IntArray(n + 1)
        val workout = IntArray(n + 1)
        val still = IntArray(n + 1)
        for (i in 0 until n) {
            val lost = if (i > 0 && !m[i].asleep && Traces.active(m[i].state)) maxOf(0f, m[i - 1].value - m[i].value) else 0f
            drop[i + 1] = drop[i] + lost
            steps[i + 1] = steps[i] + m[i].steps
            workout[i + 1] = workout[i] + if (m[i].workout) 1 else 0
            still[i + 1] = still[i] + if (!m[i].workout && m[i].steps == 0 && Traces.active(m[i].state)) 1 else 0
        }
        var worst: Finding? = null
        var worstExcess = 0f
        var j = 0
        for (i in 0 until n) {
            while (j + 1 < n && m[j + 1].t < m[i].t + WINDOW_MINUTES * 60L) j++
            if (j - i + 1 < MIN_WINDOW_MINUTES) break
            val lost = drop[j + 1] - drop[i]
            val s = steps[j + 1] - steps[i]
            val allowed = BASE + PER_1000_STEPS * s / 1000f + PER_WORKOUT_MINUTE * (workout[j + 1] - workout[i])
            val excess = lost - allowed
            if (lost >= MIN_LOST && excess > worstExcess) {
                worstExcess = excess
                worst = Finding(
                    id, domain, if (lost >= HIGH_LOST) Severity.High else Severity.Medium, day.date,
                    listOf(lost, s.toFloat(), (j - i + 1).toFloat(), (still[j + 1] - still[i]).toFloat()),
                    Plan.Search(listOf(Knob.Active, Knob.Cap, Knob.Resting, Knob.Alert), Direction.Soften),
                )
            }
        }
        return worst
    }

    companion object {
        const val WINDOW_MINUTES = 120
        const val MIN_WINDOW_MINUTES = 100
        const val BASE = 25f
        const val PER_1000_STEPS = 4f
        const val PER_WORKOUT_MINUTE = 0.6f
        const val MIN_LOST = 28f
        const val HIGH_LOST = 45f
    }
}

/**
 * The body-signal Alert in force for [MIN_NIGHTS] nights or more in a row. The Alert is a yes or no for "something is off
 * tonight"; a week of it is far more likely a baseline that drifted than a week of illness, and it halves recharging and
 * raises drain all the while. The Alert's strength on Energy is lowered by what the run asks ([strengthFor]); the Alert
 * itself, its notification and its cap on Readiness are not touched. nums: nights in a row.
 */
class EnergyAlertStreak : Check {
    override val id = "energy.alert_streak"
    override val domain = Domain.Energy

    override fun inspect(facts: Facts): List<Finding> {
        val day = facts.days.lastOrNull() ?: return emptyList()
        val nights = day.alertNights
        if (nights < MIN_NIGHTS) return emptyList()
        return listOf(
            Finding(
                id, domain, if (nights >= HIGH_NIGHTS) Severity.High else Severity.Medium, day.date, listOf(nights.toFloat()),
                Plan.Set(mapOf(Knob.Alert to strengthFor(nights))),
            ),
        )
    }

    companion object {
        const val MIN_NIGHTS = 4
        const val HIGH_NIGHTS = 7

        /** How much of the Alert's effect on Energy stays after [nights] nights of it: half from the fourth, a quarter from the seventh. */
        fun strengthFor(nights: Int): Float = if (nights >= HIGH_NIGHTS) 0.25f else 0.5f
    }
}

/** The Energy checks the planner asks again of a replayed day: each reads one day, so a trial tuning can be judged by them. */
internal val TunableEnergyChecks: List<DayCheck> = listOf(EnergyStuckEmpty(), EnergyStuckFull(), EnergyLowWake(), EnergyDrainFast(), SittingAsEffort())
