package org.openstrap.app.data

import org.openstrap.app.data.db.DailyAggregate
import org.openstrap.app.data.db.FalseStepsDay
import org.openstrap.app.data.db.HealthDao
import java.time.LocalDate
import java.time.ZoneId

/**
 * The step history without arm movement at a desk ([Recovery.falseStepMinutes]). The rule is worked out once per day,
 * when the day's calories are ([DailyCaloriesHistory], [RecoveryEngine.calories]), and the steps it took off each local
 * hour are kept in the day's row ([org.openstrap.app.data.db.DailyCaloriesRow.falseSteps]). The SQL daily and hourly
 * step sums stay raw; every reader of them takes the stored hours off with [daily], [hourly] or [dailyStepsCorrectedOnce],
 * so the history charts, your usual steps, Habit Age, the rewards, the weekly report and the widgets agree with the
 * day's own page. A day not worked out yet has no stored hours and reads raw. Raw samples are never changed.
 */
object FalseStepHistory {
    const val HOURS = 24

    /** [minutes] (minute start to steps taken off) as steps per local hour, 24 of them; [hourOf] gives a minute's hour. */
    fun byHour(minutes: Map<Long, Int>, hourOf: (Long) -> Int): List<Int> {
        val out = IntArray(HOURS)
        for ((t, s) in minutes) hourOf(t).takeIf { it in 0 until HOURS }?.let { out[it] += s }
        return out.toList()
    }

    /** The text a row keeps for [hours]; empty (not worked out) unless it has all 24. */
    fun format(hours: List<Int>): String = if (hours.size == HOURS) hours.joinToString(",") else ""

    /** The 24 hours of a stored [text], or null when the day was never worked out or the text is damaged. */
    fun parse(text: String): List<Int>? {
        if (text.isEmpty()) return null
        val parts = text.split(',').map { it.toIntOrNull()?.takeIf { n -> n >= 0 } }
        return if (parts.size == HOURS && parts.none { it == null }) parts.map { it!! } else null
    }

    /** [raw] daily step sums with the stored false steps of [stored] taken off each day's value, never below zero. */
    fun daily(raw: List<DailyAggregate>, stored: List<FalseStepsDay>): List<DailyAggregate> {
        if (stored.isEmpty()) return raw
        val taken = stored.mapNotNull { r -> parse(r.falseSteps)?.let { r.day.toString() to it.sum() } }.toMap()
        if (taken.isEmpty()) return raw
        return raw.map { r -> taken[r.day]?.let { r.copy(value = maxOf(0.0, r.value - it)) } ?: r }
    }

    /** [raw] steps per hour by date with the stored false steps of [stored] taken off each hour, never below zero. */
    fun hourly(raw: Map<LocalDate, List<Float>>, stored: List<FalseStepsDay>): Map<LocalDate, List<Float>> {
        if (stored.isEmpty()) return raw
        val taken = stored.mapNotNull { r -> parse(r.falseSteps)?.let { r.day to it } }.toMap()
        if (taken.isEmpty()) return raw
        return raw.mapValues { (day, hours) ->
            val t = taken[day] ?: return@mapValues hours
            hours.mapIndexed { h, v -> if (h < HOURS) maxOf(0f, v - t[h]) else v }
        }
    }

    /** The first and last date a span of epoch seconds [from] to [to] touches in [zone]; an open end ([Long.MAX_VALUE] / 2) is clamped. */
    fun datesOf(from: Long, to: Long, zone: ZoneId): ClosedRange<LocalDate> {
        fun date(t: Long) = Days.date(t.coerceIn(0L, MAX_EPOCH), zone)
        return date(from)..date(to)
    }

    private const val MAX_EPOCH = 253_402_214_400L // 9999-12-31
}

/**
 * Daily step sums from [from] to [to] (epoch seconds, as [HealthDao.dailyStepsOnce]) less each day's false steps, for the
 * readers that sum days once (Habit Age, the rewards). Same days and fields as the raw read.
 */
suspend fun HealthDao.dailyStepsCorrectedOnce(from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): List<DailyAggregate> {
    val dates = FalseStepHistory.datesOf(from, to, zone)
    return FalseStepHistory.daily(dailyStepsOnce(from, to), falseStepsOnce(dates.start, dates.endInclusive))
}
