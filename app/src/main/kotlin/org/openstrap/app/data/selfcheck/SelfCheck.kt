package org.openstrap.app.data.selfcheck

import org.openstrap.app.data.ChargeTuning
import java.time.LocalDate

/*
 * The self-check (docs/algorithms/self-check.md): the app looks at its own readings for the ones that cannot be true,
 * learns a small, bounded correction where one exists, and says so. This file holds the vocabulary: what a check looks
 * at ([Facts]), what it reports ([Finding]) and what may be changed about it ([Knob]). Pure Kotlin, no Android, so
 * every rule is unit tested.
 */

/** The part of the app a check reads. */
enum class Domain { Energy, Sleep, Readiness, HeartRate, Steps, Calories, Strap }

/**
 * How sure a finding is. [Low] is listed on the self-learning page and nothing more; [Medium] may be notified when
 * something was adjusted; [High] is notified even when nothing could be.
 */
enum class Severity { Low, Medium, High }

/** Which way a search moves a [Knob]: [Soften] gives Energy less to lose, [Strengthen] more. */
enum class Direction { Soften, Strengthen }

/**
 * One number self-check may change, with the bounds it may never leave. All of them are parts of Energy's model
 * ([ChargeTuning]); [default] is the published value, which every knob falls back to within [SelfLearning.DECAY_DAYS] days
 * of its last evidence. [soft] and [strong] are the ends a search may go to in each [Direction]; the knob never goes
 * past [min]..[max].
 */
enum class Knob(
    val key: String,
    val default: Float,
    val min: Float,
    val max: Float,
    private val quantum: Double,
    val soft: Float,
    val strong: Float,
) {
    /** How much of the body-signal Alert's effect on Energy is kept: 1 the published x0.75 and x1.25, 0 none. */
    Alert("alert", 1f, ChargeTuning.ALERT_MIN, ChargeTuning.ALERT_MAX, 0.05, soft = 0f, strong = 1f),

    /** Scale on the drain of Activity and Exercise minutes. */
    Active("active", 1f, ChargeTuning.ACTIVE_MIN, ChargeTuning.ACTIVE_MAX, 0.05, soft = ChargeTuning.ACTIVE_MIN, strong = ChargeTuning.ACTIVE_MAX),

    /** Scale on the drain of a still, awake Rest minute. */
    Rest("rest", 1f, ChargeTuning.REST_MIN, ChargeTuning.REST_MAX, 0.05, soft = ChargeTuning.REST_MIN, strong = ChargeTuning.REST_MAX),

    /** The longest run of raised heart rate without steps counted as exercise, in minutes. */
    Cap("cap", ChargeTuning.CAP_MAX.toFloat(), ChargeTuning.CAP_MIN.toFloat(), ChargeTuning.CAP_MAX.toFloat(), 5.0, soft = ChargeTuning.CAP_MIN.toFloat(), strong = ChargeTuning.CAP_MAX.toFloat()),

    /** Beats a minute added to the daytime resting heart rate Energy measures reserve against. */
    Resting("rhr", 0f, ChargeTuning.OFFSET_MIN, ChargeTuning.OFFSET_MAX, 0.5, soft = ChargeTuning.OFFSET_MAX, strong = 0f),
    ;

    /** [v] forced into the bounds and onto the knob's grid (0.05 for the scales, 5 minutes, half a beat). */
    fun snap(v: Float): Float = (Math.round(v.coerceIn(min, max).toDouble() / quantum) * quantum).toFloat().coerceIn(min, max)

    /** This knob's value in [t]. */
    fun read(t: ChargeTuning): Float = when (this) {
        Alert -> t.alert
        Active -> t.active
        Rest -> t.rest
        Cap -> t.sustainedCap.toFloat()
        Resting -> t.restingOffset
    }

    /** [t] with this knob set to [v], bounded and snapped. */
    fun write(t: ChargeTuning, v: Float): ChargeTuning = snap(v).let { s ->
        when (this) {
            Alert -> t.copy(alert = s)
            Active -> t.copy(active = s)
            Rest -> t.copy(rest = s)
            Cap -> t.copy(sustainedCap = s.toInt())
            Resting -> t.copy(restingOffset = s)
        }
    }

    /** How far [v] is from the default, as a share of the whole allowed range (0 to 1): the "size" of a change. */
    fun distance(v: Float): Float = Math.abs(v - default) / (max - min)

    /** The values a search tries for this knob: the default, halfway to the end [dir] points at, and that end. */
    fun candidates(dir: Direction): List<Float> {
        val end = if (dir == Direction.Soften) soft else strong
        return listOf(default, snap((default + end) / 2f), snap(end)).distinct()
    }

    companion object {
        fun of(key: String): Knob? = entries.firstOrNull { it.key == key }
    }
}

/** What to do about a finding. */
sealed interface Plan {
    /** Nothing can be adjusted: the finding is only recorded, and told when it is [Severity.High]. */
    data object Inform : Plan

    /** Look for the smallest change to [knobs], moved [direction], that makes the finding's day plausible again. */
    data class Search(val knobs: List<Knob>, val direction: Direction) : Plan

    /** Set [to] directly: the evidence itself names the value. */
    data class Set(val to: Map<Knob, Float>) : Plan
}

/**
 * One implausible reading a [Check] found. [nums] are the numbers behind it, in the order the check documents, which
 * the copy reads to say it in words and which are stored with the incident. [day] is the day the reading was on.
 */
data class Finding(
    val check: String,
    val domain: Domain,
    val severity: Severity,
    val day: LocalDate,
    val nums: List<Float>,
    val plan: Plan,
)

/** A plausibility check: one small class per rule, listed in [SelfChecks.all]. */
interface Check {
    /** The check's name, stable because it is stored: `domain.what`, lower case. */
    val id: String
    val domain: Domain

    /** Every implausible reading in [facts], oldest day first; empty when everything is as it should be. */
    fun inspect(facts: Facts): List<Finding>
}

/** A [Check] that reads a single day's [DayTrace], so the search can ask it again of a replayed day. */
interface DayCheck : Check {
    fun inspectDay(day: DayTrace): Finding?

    override fun inspect(facts: Facts): List<Finding> = facts.days.mapNotNull(::inspectDay)
}

/** One minute of a day's Energy, as the checks read it. */
class TraceMinute(
    val t: Long,
    /** Energy after this minute, 0 to 100. */
    val value: Float,
    val state: org.openstrap.app.data.Recovery.State,
    val steps: Int,
    /** Heart-rate reserve used (0 to 1.2), null without a heart rate. */
    val hrr: Float?,
    val asleep: Boolean,
    /** Inside a recorded workout. */
    val workout: Boolean,
)

/** A stretch without heart rate while awake, and whether the strap's own state explains it (off the wrist, or off over the night). */
class Gap(val minutes: Int, val explained: Boolean)

/**
 * One day as the model saw it: its Energy minute by minute with the evidence beside it (steps, heart-rate reserve,
 * sleep), the heart-rate baselines it was measured against, and the gaps in the strap's data. Built by [SelfFacts] from
 * a real day or from a replay of it under another tuning.
 */
class DayTrace(
    val date: LocalDate,
    /** Worn minutes with an Energy value, ascending, one per minute. */
    val minutes: List<TraceMinute>,
    /** Energy when the main sleep ended; null when the strap was off overnight or no sleep has been seen. */
    val atWake: Float?,
    /** Minutes asleep in the night that [atWake] follows; null without one. */
    val wakeAsleepMinutes: Int?,
    /** Nights in a row, up to this day's, in the body-signal Alert state; 0 when none. */
    val alertNights: Int,
    val offOvernight: Boolean,
    val steps: Int,
    val falseSteps: Int,
    val restingHr: Float,
    val maxHr: Float,
    /** The highest minute heart rate of the day, and minutes at or above [maxHr] + [PEAK_OVER]. */
    val peakHr: Int?,
    val peakMinutes: Int,
    val gaps: List<Gap>,
    /** The day is not over: hours so far. */
    val partial: Boolean,
) {
    companion object {
        /** bpm over the maximum heart rate that no honest minute reaches. */
        const val PEAK_OVER = 10
    }
}

/** One night, as the sleep checks read it. */
class NightFact(
    val day: LocalDate,
    val asleepMinutes: Int,
    val awakeMinutes: Int?,
    val deepMinutes: Int?,
    val remMinutes: Int?,
    /** OpenStrap's own sleep score, and the strap's, 0 to 100. */
    val score: Int?,
    val strapScore: Int?,
    /** The sleep's midpoint, minutes after midnight. */
    val midpointMinutes: Int?,
)

/** A day's Readiness and the three contributors that explain a jump in it, each 0 to 100. */
class ReadinessFact(val day: LocalDate, val score: Int?, val hrv: Int?, val restingHr: Int?, val sleep: Int?)

/** A day's calories as the calories checks read them. */
class CaloriesFact(val day: LocalDate, val activeKcal: Float, val totalKcal: Float, val steps: Int, val partial: Boolean, val palImplausible: Boolean)

/** The strap as last seen: its battery and whether it is on the charger. */
class StrapFacts(val battery: Int?, val charging: Boolean)

/** Everything the checks may read: the last days, oldest first, and the readings that don't belong to one day. */
class Facts(
    val today: LocalDate,
    val days: List<DayTrace> = emptyList(),
    val nights: List<NightFact> = emptyList(),
    val readiness: List<ReadinessFact> = emptyList(),
    /** The Readiness of the 7 days ending today, oldest first, null where there was none. */
    val readinessWeek: List<Int?> = emptyList(),
    val calories: List<CaloriesFact> = emptyList(),
    val strap: StrapFacts? = null,
)
