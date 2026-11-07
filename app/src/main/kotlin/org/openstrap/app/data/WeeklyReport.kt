package org.openstrap.app.data

import java.time.LocalDate

/**
 * The week report: the last seven days against the seven before, from what the app already keeps. Every figure is a
 * mean over the days that have one, and is left out when fewer than [MIN_DAYS] days do, so a week the strap was mostly
 * off says less rather than something wrong. The advice for the coming week follows from the same numbers, by rules
 * that are written out in [advice].
 */
object WeeklyReport {
    /** A figure for this week and the week before (null where there was too little to say). */
    data class Stat(val value: Float?, val before: Float?) {
        /** This week minus the week before, when both exist. */
        val change: Float? get() = if (value != null && before != null) value - before else null
    }

    enum class Advice { NoData, Ease, Sleep, Build, Hold }

    data class Report(
        val first: LocalDate,
        val last: LocalDate,
        val readiness: Stat,
        val sleepScore: Stat,
        val asleepMinutes: Stat,
        val restingHr: Stat,
        val hrv: Stat,
        val steps: Stat,
        val load: Stat,
        /** The days with the highest and the lowest readiness; null with fewer than [MIN_DAYS] scored days or no spread. */
        val best: Pair<LocalDate, Int>?,
        val worst: Pair<LocalDate, Int>?,
        /** Minutes of sleep the week should have averaged ([Recovery.SLEEP_NEED_MINUTES] or the user's own). */
        val sleepNeed: Int,
        val advice: Advice,
    )

    const val MIN_DAYS = 3
    private const val EASE_READINESS = 55f
    private const val BUILD_READINESS = 67f
    private const val SHORT_SLEEP_MINUTES = 45
    private const val RHR_UP_BPM = 2f
    private const val HRV_DOWN_SHARE = 0.08f
    private const val LOAD_ROOM_SHARE = 1.1f

    /** The seven days ending on [last], oldest first. */
    fun week(last: LocalDate): List<LocalDate> = (6L downTo 0L).map { last.minusDays(it) }

    // Each series is keyed by the day it is about; the two weeks are cut from the same maps.
    @Suppress("LongParameterList")
    fun build(
        last: LocalDate, sleepNeed: Int,
        readiness: Map<LocalDate, Int>, sleepScore: Map<LocalDate, Int>, asleepMinutes: Map<LocalDate, Int>,
        restingHr: Map<LocalDate, Float>, hrv: Map<LocalDate, Float>, steps: Map<LocalDate, Int>, load: Map<LocalDate, Float>,
    ): Report {
        val now = week(last)
        val before = week(last.minusDays(7))
        fun <T : Number> stat(series: Map<LocalDate, T>) = Stat(mean(now, series), mean(before, series))
        val ready = stat(readiness)
        val scored = now.mapNotNull { d -> readiness[d]?.let { d to it } }
        val spread = scored.size >= MIN_DAYS && scored.maxOf { it.second } != scored.minOf { it.second }
        val stats = Stats(ready, stat(sleepScore), stat(asleepMinutes), stat(restingHr), stat(hrv), stat(steps), stat(load))
        return Report(
            first = now.first(), last = last, readiness = ready, sleepScore = stats.sleepScore, asleepMinutes = stats.asleep,
            restingHr = stats.restingHr, hrv = stats.hrv, steps = stats.steps, load = stats.load,
            best = if (spread) scored.maxBy { it.second } else null,
            worst = if (spread) scored.minBy { it.second } else null,
            sleepNeed = sleepNeed, advice = advice(stats, sleepNeed),
        )
    }

    private class Stats(val readiness: Stat, val sleepScore: Stat, val asleep: Stat, val restingHr: Stat, val hrv: Stat, val steps: Stat, val load: Stat)

    private fun <T : Number> mean(days: List<LocalDate>, series: Map<LocalDate, T>): Float? =
        days.mapNotNull { series[it]?.toDouble() }.takeIf { it.size >= MIN_DAYS }?.average()?.toFloat()

    /**
     * Ease when the week's Readiness averaged under 55, or resting heart rate rose 2 or more while HRV fell 8% or more.
     * Sleep when the nights averaged 45 minutes or more under the need. Build when Readiness averaged 67 or more and the
     * load did not climb past 10% over the week before. Hold otherwise.
     */
    private fun advice(s: Stats, need: Int): Advice {
        val ready = s.readiness.value
        if (ready == null && s.sleepScore.value == null) return Advice.NoData
        val hrvDown = s.hrv.change?.let { c -> s.hrv.before?.let { b -> b > 0f && -c >= HRV_DOWN_SHARE * b } } == true
        val rhrUp = (s.restingHr.change ?: 0f) >= RHR_UP_BPM
        val short = s.asleep.value?.let { need - it >= SHORT_SLEEP_MINUTES } == true
        val loadOk = s.load.value == null || s.load.before == null || s.load.value <= LOAD_ROOM_SHARE * s.load.before
        return when {
            (ready != null && ready < EASE_READINESS) || (rhrUp && hrvDown) -> Advice.Ease
            short -> Advice.Sleep
            ready != null && ready >= BUILD_READINESS && loadOk -> Advice.Build
            else -> Advice.Hold
        }
    }
}
