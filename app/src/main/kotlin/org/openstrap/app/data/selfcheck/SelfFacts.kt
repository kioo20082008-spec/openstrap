package org.openstrap.app.data.selfcheck

import org.openstrap.app.data.BodySignals
import org.openstrap.app.data.ChargeInputs
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.charge
import org.openstrap.app.data.chargeResting
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.hrr
import java.time.LocalDate

/**
 * Builds the checks' [Facts] from what the engine already worked out: a day's Energy run replayed minute by minute under a
 * given tuning ([trace]), and the nights, Readiness and calories of the last days' [RecoveryResult]s. Pure, so the same
 * trace serves the live detection and the planner's trial runs.
 */
object SelfFacts {
    private const val MINUTE = 60L

    /** The longest night looked back over for the minutes asleep before a wake, in seconds. */
    private const val NIGHT_LOOKBACK = 16 * 3600L

    /**
     * [date]'s Energy under [tuning], minute by minute with its evidence. [falseSteps] are the day's steps left out as arm
     * movement (the minutes in [inputs] already have them removed), [partial] whether the day is still going.
     */
    fun trace(inputs: ChargeInputs, tuning: ChargeTuning, date: LocalDate, falseSteps: Int = 0, partial: Boolean = false): DayTrace {
        val params = inputs.params.copy(tuning = tuning)
        val day = Recovery.charge(inputs.minutes, params, inputs.window, start = 50f, sampleSeconds = MINUTE, mainSleepEnd = inputs.mainSleepEnd)
        val byTime = inputs.minutes.associateBy { it.t }
        val rest = Recovery.chargeResting(params)
        val worn = ArrayList<TraceMinute>(day.points.size)
        for (p in day.points) {
            if (p.state == Recovery.State.NoData) continue
            val m = byTime[p.t] ?: continue
            worn += TraceMinute(
                p.t, p.value, p.state, m.steps, m.hr?.let { Recovery.hrr(it, rest, params.maxHr) },
                asleep = m.sleepStage != null, workout = m.workout == Recovery.WorkoutPhase.Active,
            )
        }
        val inDay = inputs.minutes.filter { it.t in inputs.window }
        val peakLine = params.maxHr + DayTrace.PEAK_OVER
        val hrs = inDay.mapNotNull { it.hr }
        return DayTrace(
            date = date,
            minutes = worn,
            atWake = day.atWake,
            wakeAsleepMinutes = wakeAsleep(inputs),
            alertNights = alertNights(params, inputs.window.last),
            offOvernight = day.strapOffOvernight,
            steps = inDay.sumOf { it.steps },
            falseSteps = falseSteps,
            restingHr = params.restingHr,
            maxHr = params.maxHr,
            peakHr = hrs.maxOrNull(),
            peakMinutes = hrs.count { it >= peakLine },
            gaps = gaps(inputs, params),
            partial = partial,
        )
    }

    /** Minutes asleep in the night the main sleep that ended at [ChargeInputs.mainSleepEnd] was; null without one. */
    private fun wakeAsleep(inputs: ChargeInputs): Int? {
        val end = inputs.mainSleepEnd ?: return null
        val n = inputs.minutes.count { it.sleepStage != null && it.sleepStage != SleepStage.AWAKE && it.t in (end - NIGHT_LOOKBACK)..end }
        return n.takeIf { it > 0 }
    }

    /** Nights in a row, ending with the last sleep that started by [until], whose body-signal state is the Alert. */
    private fun alertNights(params: Recovery.ChargeParams, until: Long): Int {
        var n = 0
        for ((_, state) in params.bodySignals.filter { it.first <= until }.sortedByDescending { it.first }) {
            if (state != BodySignals.State.Alert) break
            n++
        }
        return n
    }

    /** The stretches of the day, [Recovery.NO_WEAR_GAP_MINUTES] or longer, with neither heart rate nor sleep, and whether wear or the usual night explains each. */
    private fun gaps(inputs: ChargeInputs, params: Recovery.ChargeParams): List<Gap> {
        val ms = inputs.minutes
        val out = ArrayList<Gap>()
        var i = 0
        while (i < ms.size) {
            if (ms[i].hr != null || ms[i].sleepStage != null) {
                i++
                continue
            }
            var j = i
            while (j < ms.size && ms[j].hr == null && ms[j].sleepStage == null) j++
            // A gap still open at the end of the data is a sync not yet made, not a hole.
            val len = j - i
            if (j < ms.size && len >= Recovery.NO_WEAR_GAP_MINUTES && ms[i].t in inputs.window) {
                val off = (i until j).count { ms[it].worn == false || ms[it].offWrist }
                val first = ms[i].t
                val last = ms[j - 1].t + MINUTE
                val night = params.usualSleep.any { u -> (minOf(last, u.last) - maxOf(first, u.first)) * 2 >= last - first }
                out += Gap(len, explained = off * 2 >= len || night)
            }
            i = j
        }
        return out
    }

    /** The main sleeps of [results] as the sleep checks read them, oldest first. */
    fun nights(results: List<RecoveryResult>): List<NightFact> = results.sortedBy { it.date }.mapNotNull { r ->
        val n = r.tonight ?: return@mapNotNull null
        NightFact(
            r.date, n.asleepMinutes, n.awakeMinutes, n.deepMinutes, n.remMinutes,
            score = r.sleepScores[r.date] ?: r.sleepScore?.total, strapScore = n.strapScore, midpointMinutes = n.midpointMinutes,
        )
    }

    /** Each day's Readiness and its HRV, resting heart rate and sleep contributors, oldest first. */
    fun readiness(results: List<RecoveryResult>): List<ReadinessFact> = results.sortedBy { it.date }.map { r ->
        fun part(f: Recovery.Factor) = r.readiness.contributors.firstOrNull { it.factor == f }?.score
        ReadinessFact(r.date, r.readiness.score, part(Recovery.Factor.HrvBalance), part(Recovery.Factor.RestingHr), part(Recovery.Factor.Sleep))
    }

    /** Each day's calories, oldest first; a day without them (no weight, height or age) is left out. */
    fun calories(results: List<RecoveryResult>): List<CaloriesFact> = results.sortedBy { it.date }.mapNotNull { r ->
        val c = r.calories ?: return@mapNotNull null
        CaloriesFact(r.date, c.activeKcal, c.totalKcal, r.activity?.steps ?: 0, c.partial, c.palImplausible)
    }
}
