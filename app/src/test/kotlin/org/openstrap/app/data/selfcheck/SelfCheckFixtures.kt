package org.openstrap.app.data.selfcheck

import org.openstrap.app.data.BodySignals
import org.openstrap.app.data.ChargeInputs
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.db.SleepStage
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Days built minute by minute for the self-check tests: a few ordinary days to settle the model, then the day under test.
 * Heart rate, steps and sleep come from the [Day] each test describes; everything else is the engine's own.
 */
object SelfCheckFixtures {
    val date: LocalDate = LocalDate.of(2026, 11, 18)
    private val zone = ZoneOffset.UTC

    fun at(d: LocalDate, h: Int, m: Int = 0): Long = d.atTime(LocalTime.of(h, m)).atZone(zone).toEpochSecond()

    /** What the strap saw in one minute. */
    class Spec(val hr: Int?, val steps: Int = 0, val stage: Int? = null, val workout: Boolean = false)

    /** A day's routine: [night] hours slept from [bedtime] to [wake], then [awake] decides each awake minute (minute of the day). */
    class Day(
        val bedtime: Int = 22,
        val wake: Int = 8,
        val sleepHr: Int = 56,
        val awake: (Int) -> Spec,
    )

    /** An ordinary day: still at about [still] bpm, a walk of [walkSteps] steps a minute for 20 minutes at 10, 13 and 17 hours. */
    fun ordinary(still: Int = 66, walkSteps: Int = 100, walkHr: Int = 105) = Day { m ->
        val h = m / 60
        when {
            (h == 10 || h == 13 || h == 17) && m % 60 < 20 -> Spec(walkHr, walkSteps)
            else -> Spec(still + (m % 7))
        }
    }

    /**
     * The owner's day: ten hours asleep, then a still day (975 steps in all) with the heart rate up at 112 bpm from 16:30 to
     * 21:50, dipping for a minute every [dipEvery] minutes, as a body that is coming down with something does.
     */
    fun ownersDay(dipEvery: Int = 80) = Day(bedtime = 22, wake = 8) { m ->
        val raised = m in (16 * 60 + 30)..(21 * 60 + 50)
        val steps = if (m % 55 == 0) 25 else 0
        when {
            raised && (m - (16 * 60 + 30)) % dipEvery == dipEvery - 1 -> Spec(78, steps)
            raised -> Spec(112 + (m % 3), steps)
            else -> Spec(64 + (m % 5), steps)
        }
    }

    class Built(val inputs: ChargeInputs, val date: LocalDate)

    /**
     * [days] ordinary days before [date], then [day] on it. The night that ends on [date]'s morning is [day]'s; the nights
     * before are the ordinary ones. [alert] puts the body-signal Alert on every night from [date] minus that many.
     */
    fun build(day: Day, days: Int = 4, alert: Int = 0, restingHr: Float = 62f, maxHr: Float = 185f, tuning: ChargeTuning = ChargeTuning.Default): Built {
        val first = date.minusDays(days.toLong())
        val from = at(first, 0)
        val to = at(date.plusDays(1), 0)
        val ordinary = ordinary()
        fun routine(d: LocalDate) = if (d == date) day else ordinary.let { Day(awake = it.awake) }
        val sleeps = ArrayList<LongRange>()
        val ends = ArrayList<Long>()
        val signals = ArrayList<Pair<Long, BodySignals.State>>()
        var d = first
        while (d <= date) {
            val r = routine(d)
            val start = at(d.minusDays(1), r.bedtime)
            val end = at(d, r.wake)
            sleeps += start..end
            ends += end
            signals += start to if (alert > 0 && d > date.minusDays(alert.toLong())) BodySignals.State.Alert else BodySignals.State.Normal
            d = d.plusDays(1)
        }
        val minutes = ArrayList<Recovery.Minute>()
        var t = from
        while (t < to) {
            val d0 = LocalDate.ofEpochDay(t / 86_400)
            val minuteOfDay = ((t - at(d0, 0)) / 60).toInt()
            val night = sleeps.firstOrNull { t in it }
            val r = routine(night?.let { LocalDate.ofEpochDay(it.last / 86_400) } ?: d0)
            val spec = if (night != null) Spec(r.sleepHr + (minuteOfDay % 3), stage = if ((minuteOfDay / 90) % 2 == 0) SleepStage.LIGHT else SleepStage.DEEP) else r.awake(minuteOfDay)
            minutes += Recovery.Minute(t, spec.hr, spec.steps, null, spec.stage, if (spec.workout) Recovery.WorkoutPhase.Active else null)
            t += 60
        }
        val params = Recovery.ChargeParams(
            restingHr, maxHr, nightRestingHr = 55f, hrvScore = 70, bodySignals = signals, mainSleepEnds = ends, usualSleep = sleeps, tuning = tuning,
        )
        return Built(ChargeInputs(minutes, params, at(date, 0)..(at(date.plusDays(1), 0) - 1), at(date, day.wake)), date)
    }

    fun trace(b: Built, tuning: ChargeTuning = ChargeTuning.Default): DayTrace = SelfFacts.trace(b.inputs, tuning, b.date)

    // ------------------------------------------------------------------------------------------------
    // Traces written by hand, for the checks that read a day and nothing behind it
    // ------------------------------------------------------------------------------------------------

    private val midnight = at(date, 0)

    /** [count] awake minutes from [startMinute] of [date], each at [value], in [state], with [steps] each. */
    fun run(startMinute: Int, count: Int, value: Float, state: Recovery.State = Recovery.State.Rest, steps: Int = 0, workout: Boolean = false, hrr: Float? = 0.1f, asleep: Boolean = false) =
        (0 until count).map { TraceMinute(midnight + (startMinute + it) * 60L, value, state, steps, hrr, asleep, workout) }

    /** A minute-by-minute fall from [from] to [to] over [count] minutes, in Activity (effort without a step). */
    fun fall(startMinute: Int, count: Int, from: Float, to: Float, state: Recovery.State = Recovery.State.Activity, steps: Int = 0) =
        (0 until count).map { TraceMinute(midnight + (startMinute + it) * 60L, from + (to - from) * it / (count - 1), state, steps, 0.35f, false, false) }

    fun day(
        minutes: List<TraceMinute>,
        atWake: Float? = 90f,
        wakeAsleep: Int? = 600,
        alertNights: Int = 0,
        offOvernight: Boolean = false,
        steps: Int = 6000,
        falseSteps: Int = 0,
        peakHr: Int? = 130,
        peakMinutes: Int = 0,
        gaps: List<Gap> = emptyList(),
        partial: Boolean = false,
        date: LocalDate = SelfCheckFixtures.date,
    ) = DayTrace(date, minutes, atWake, wakeAsleep, alertNights, offOvernight, steps, falseSteps, 62f, 185f, peakHr, peakMinutes, gaps, partial)

    fun night(day: LocalDate, asleep: Int = 460, awake: Int? = 30, deep: Int? = 80, rem: Int? = 100, score: Int? = 80, strap: Int? = 80, midpoint: Int? = 180) =
        NightFact(day, asleep, awake, deep, rem, score, strap, midpoint)
}
