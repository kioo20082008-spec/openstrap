package org.openstrap.app.data

import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Whether last night was a heavy one (a late bed, a short sleep, HRV down, resting heart rate up, warmer skin), read from
 * the night's own numbers against your usual. A late bed or a short sleep alone is not a verdict: at least one body
 * signal has to have moved with it. Several causes together make it heavier.
 */
object LateNight {
    enum class Reason { LateBed, ShortSleep, HrvDown, HeartUp, SkinWarm }
    enum class Level { None, Rough, Heavy }

    /** [value]: minutes late, minutes short, percent down, bpm up, or tenths of a degree up. */
    data class Cause(val reason: Reason, val value: Int) {
        val body: Boolean get() = reason == Reason.HrvDown || reason == Reason.HeartUp || reason == Reason.SkinWarm
    }

    data class Verdict(val level: Level, val causes: List<Cause>)

    const val LATE_MINUTES = 90
    const val SHORT_MINUTES = 60
    const val HRV_DROP_PERCENT = 10
    const val HEART_UP_BPM = 4f
    const val SKIN_UP_DEGREES = 0.5f
    private const val HEAVY_CAUSES = 3
    private const val ROUGH_CAUSES = 2
    private const val HALF_DAY = 720
    private const val DAY = 1440

    /** How late [bed] is against [usual], in minutes, around midnight (00:30 against 23:00 is 90). */
    fun minutesLate(bed: LocalTime, usual: LocalTime): Int {
        val d = (bed.toSecondOfDay() - usual.toSecondOfDay()) / 60
        return ((d + HALF_DAY).mod(DAY)) - HALF_DAY
    }

    // Each reading is optional: a missing one just isn't a cause.
    @Suppress("LongParameterList")
    fun read(
        bedStart: LocalTime?, usualBed: LocalTime?, asleepMinutes: Int?, needMinutes: Int?,
        hrvVsUsualPercent: Int?, restingHrVsUsual: Float?, skinVsUsual: Float?,
    ): Verdict {
        val causes = buildList {
            if (bedStart != null && usualBed != null) {
                val late = minutesLate(bedStart, usualBed)
                if (late >= LATE_MINUTES) add(Cause(Reason.LateBed, late))
            }
            if (asleepMinutes != null && needMinutes != null && needMinutes - asleepMinutes >= SHORT_MINUTES) {
                add(Cause(Reason.ShortSleep, needMinutes - asleepMinutes))
            }
            if (hrvVsUsualPercent != null && hrvVsUsualPercent <= -HRV_DROP_PERCENT) add(Cause(Reason.HrvDown, -hrvVsUsualPercent))
            if (restingHrVsUsual != null && restingHrVsUsual >= HEART_UP_BPM) add(Cause(Reason.HeartUp, restingHrVsUsual.roundToInt()))
            if (skinVsUsual != null && skinVsUsual >= SKIN_UP_DEGREES) add(Cause(Reason.SkinWarm, (skinVsUsual * 10).roundToInt()))
        }
        val level = when {
            causes.none { it.body } -> Level.None
            causes.size >= HEAVY_CAUSES -> Level.Heavy
            causes.size >= ROUGH_CAUSES -> Level.Rough
            else -> Level.None
        }
        return Verdict(level, if (level == Level.None) emptyList() else causes)
    }
}

/** Tonight's bedtime: your need plus part of the debt, and more after a heavy night. */
object BedtimePlan {
    const val DEBT_CAP_MINUTES = 60
    const val HEAVY_EXTRA_MINUTES = 30

    /** The minutes of sleep to plan for: [need], up to an hour of [debt], and half an hour more after a [heavy] night. */
    fun plannedMinutes(need: Int, debt: Int, heavy: Boolean): Int =
        need + debt.coerceIn(0, DEBT_CAP_MINUTES) + if (heavy) HEAVY_EXTRA_MINUTES else 0

    fun bedtime(wake: LocalTime, need: Int, debt: Int, heavy: Boolean): LocalTime =
        wake.minusMinutes(plannedMinutes(need, debt, heavy).toLong())
}

/**
 * A short nap, offered only when it helps: Charge is low (or low after a short or heavy night), it is the early
 * afternoon, there is no nap yet today, and it ends early enough not to cost tonight's sleep.
 */
object NapPlan {
    /** [latestStart] is the last time to lie down; [minutes] how long to sleep. */
    data class Plan(val latestStart: LocalTime, val minutes: Int)

    val EARLIEST: LocalTime = LocalTime.of(12, 0)
    val LATEST: LocalTime = LocalTime.of(16, 30)
    const val HOURS_BEFORE_BED = 7L
    const val LOW_CHARGE = 40
    const val MID_CHARGE = 55
    const val VERY_LOW_CHARGE = 30
    const val SHORT_NIGHT_MINUTES = 60
    const val DEEP_DEBT_MINUTES = 120
    const val NAP_MINUTES = 20
    const val LONG_NAP_MINUTES = 25

    // Each return is a reason not to offer one.
    @Suppress("ReturnCount", "LongParameterList")
    fun suggest(
        now: LocalTime, charge: Int?, debtMinutes: Int, shortMinutes: Int, heavyNight: Boolean,
        napToday: Boolean, recording: Boolean, bedtime: LocalTime?,
    ): Plan? {
        if (charge == null || napToday || recording || now < EARLIEST) return null
        val tired = charge <= LOW_CHARGE || (charge <= MID_CHARGE && (shortMinutes >= SHORT_NIGHT_MINUTES || heavyNight))
        if (!tired) return null
        // Seven hours before bed at the latest, never after the afternoon dip is over.
        val byBed = bedtime?.minusHours(HOURS_BEFORE_BED)
        val latest = if (byBed != null && byBed >= EARLIEST && byBed < LATEST) byBed else LATEST
        if (now > latest) return null
        val long = charge <= VERY_LOW_CHARGE && debtMinutes >= DEEP_DEBT_MINUTES
        return Plan(latest, if (long) LONG_NAP_MINUTES else NAP_MINUTES)
    }
}
