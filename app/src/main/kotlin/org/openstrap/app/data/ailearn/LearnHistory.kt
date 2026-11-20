package org.openstrap.app.data.ailearn

import org.json.JSONArray
import org.json.JSONObject
import org.openstrap.app.data.ai.AiDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * The whole history in a form small enough to send every day: the last [DAILY_DAYS] days as one row each, the days up to a
 * year back as one row per week, and anything older as one row per month, all of them means of the existing daily summaries
 * ([AiDay]). A row is an array in the order of [COLUMNS], so no key is repeated. Pure.
 */
object LearnHistory {
    /** Days kept as rows of their own, counting today. */
    const val DAILY_DAYS = 90

    /** Days back from today where weeks end and months begin. */
    const val WEEKLY_DAYS = 365

    private const val PREFERRED_DECIMALS = 10.0
    private const val CENTI = 100.0

    /** One column: its name in the JSON, the decimals it is rounded to and where it comes from. */
    class Column(val key: String, val decimals: Int, val of: (AiDay) -> Number?)

    /**
     * The columns, in order: minutes asleep, bedtime (minutes after 18:00), wake (minutes after midnight), sleep score,
     * Recovery, HRV, resting heart rate, skin temperature, breathing rate, load, steps, Energy at wake.
     */
    val COLUMNS: List<Column> = listOf(
        Column("sleep", 0) { it.sleepMinutes },
        Column("bed", 0) { it.bedMinutes },
        Column("wake", 0) { it.wakeMinutes },
        Column("ss", 0) { it.sleepScore },
        Column("rec", 0) { it.recovery },
        Column("hrv", 1) { it.hrv },
        Column("rhr", 0) { it.restingHr },
        Column("temp", 2) { it.skinTemp },
        Column("br", 1) { it.breathRate },
        Column("load", 0) { it.load },
        Column("steps", 0) { it.steps },
        Column("ew", 0) { it.energyAtWake },
    )

    /** A row: its label (a day, the Monday of a week, a month), how many days it stands for, then one value per column. */
    class Row(val label: String, val days: Int, val values: List<Number?>)

    /** The history as rows, each list oldest first. */
    class Rows(val daily: List<Row>, val weekly: List<Row>, val monthly: List<Row>) {
        val size: Int get() = daily.size + weekly.size + monthly.size
    }

    private val dayLabel = DateTimeFormatter.ofPattern("MM-dd")

    /**
     * [days] (any order, any length) as rows to [today]: the last [DAILY_DAYS] days one by one, the rest by week back to
     * [WEEKLY_DAYS] days and by month beyond. A day with no value at all gives no row; a week or month gives one when any
     * of its days has a value.
     */
    fun compress(days: List<AiDay>, today: LocalDate): Rows {
        val dailyFrom = today.minusDays(DAILY_DAYS - 1L)
        val weeklyFrom = today.minusDays(WEEKLY_DAYS - 1L)
        val sorted = days.filter { it.date <= today }.sortedBy { it.date }
        val daily = sorted.filter { it.date >= dailyFrom }.mapNotNull { d ->
            val values = COLUMNS.map { c -> round(c, c.of(d)?.toDouble()) }
            if (values.all { it == null }) null else Row(d.date.format(dayLabel), 1, values)
        }
        val older = sorted.filter { it.date < dailyFrom }
        val weekly = older.filter { it.date >= weeklyFrom }
            .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .toSortedMap().mapNotNull { (monday, group) -> mean(monday.toString(), group) }
        val monthly = older.filter { it.date < weeklyFrom }
            .groupBy { YearMonth.from(it.date) }
            .toSortedMap().mapNotNull { (month, group) -> mean(month.toString(), group) }
        return Rows(daily, weekly, monthly)
    }

    /** The mean of each column over [group], null where it has none; null when no column has a value. */
    private fun mean(label: String, group: List<AiDay>): Row? {
        val values = COLUMNS.map { c ->
            val xs = group.mapNotNull { c.of(it)?.toDouble() }
            round(c, if (xs.isEmpty()) null else xs.average())
        }
        if (values.all { it == null }) return null
        return Row(label, group.count { d -> COLUMNS.any { it.of(d) != null } }, values)
    }

    private fun round(c: Column, v: Double?): Number? {
        if (v == null || !v.isFinite()) return null
        return when (c.decimals) {
            0 -> v.roundToInt()
            1 -> (v * PREFERRED_DECIMALS).roundToInt() / PREFERRED_DECIMALS
            else -> (v * CENTI).roundToInt() / CENTI
        }
    }

    /** The rows as `{"cols":[...],"rows":[[label, days, values...], ...]}`; [leading] names the first column. */
    fun toJson(rows: List<Row>, leading: String): JSONObject = JSONObject().apply {
        put("cols", JSONArray(listOf(leading, "n") + COLUMNS.map { it.key }))
        put(
            "rows",
            JSONArray().apply {
                rows.forEach { r ->
                    put(
                        JSONArray().apply {
                            put(r.label)
                            put(r.days)
                            r.values.forEach { put(it ?: JSONObject.NULL) }
                        },
                    )
                }
            },
        )
    }
}
