package org.openstrap.app.data.ailearn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.ai.AiDay
import java.time.DayOfWeek

/** The history squeezed to days, weeks and months. */
class LearnHistoryTest {
    private val today = LEARN_TODAY

    @Test fun `ninety days are rows of their own, a year of weeks and months beyond`() {
        val rows = LearnHistory.compress(learnDays(730), today)
        assertEquals(LearnHistory.DAILY_DAYS, rows.daily.size)
        assertTrue(rows.weekly.size in 38..40)
        assertTrue(rows.monthly.size in 11..13)
        // Oldest first, and no day is in two tiers: together the rows stand for every day exactly once.
        assertEquals(today.minusDays(89).toString().drop(5), rows.daily.first().label)
        assertEquals(today.toString().drop(5), rows.daily.last().label)
        val weeklyDays = rows.weekly.sumOf { it.days }
        val monthlyDays = rows.monthly.sumOf { it.days }
        assertEquals(730, rows.daily.sumOf { it.days } + weeklyDays + monthlyDays)
    }

    @Test fun `a week is the mean of its days and is labelled by its Monday`() {
        val days = (0 until 14).map { AiDay(date = today.minusDays(200L + it), sleepMinutes = 400 + it, restingHr = 60) }
        val rows = LearnHistory.compress(days, today)
        assertTrue(rows.weekly.isNotEmpty())
        rows.weekly.forEach { assertEquals(DayOfWeek.MONDAY, java.time.LocalDate.parse(it.label).dayOfWeek) }
        val col = LearnHistory.COLUMNS.indexOfFirst { it.key == "sleep" }
        val full = rows.weekly.first { it.days == 7 }
        assertTrue((full.values[col] as Int) in 400..413)
    }

    @Test fun `a column with no value in a row is null, and a row with no value at all is left out`() {
        val days = listOf(
            AiDay(date = today.minusDays(1), sleepMinutes = 420),
            AiDay(date = today.minusDays(2)),
        )
        val rows = LearnHistory.compress(days, today)
        assertEquals(1, rows.daily.size)
        val r = rows.daily.single()
        assertEquals(420, r.values[LearnHistory.COLUMNS.indexOfFirst { it.key == "sleep" }])
        assertNull(r.values[LearnHistory.COLUMNS.indexOfFirst { it.key == "hrv" }])
    }

    @Test fun `figures are rounded to what is worth sending`() {
        val days = listOf(AiDay(date = today.minusDays(1), hrv = 55.2345f, skinTemp = 33.12345f, breathRate = 14.56f, load = 41.6f))
        val v = LearnHistory.compress(days, today).daily.single().values
        fun at(key: String) = v[LearnHistory.COLUMNS.indexOfFirst { it.key == key }]
        assertEquals(55.2, (at("hrv") as Number).toDouble(), 0.0001)
        assertEquals(33.12, (at("temp") as Number).toDouble(), 0.0001)
        assertEquals(14.6, (at("br") as Number).toDouble(), 0.0001)
        assertEquals(42, at("load"))
    }

    @Test fun `days after today are ignored and the input may come in any order`() {
        val days = listOf(
            AiDay(date = today.plusDays(3), sleepMinutes = 999),
            AiDay(date = today.minusDays(2), sleepMinutes = 400),
            AiDay(date = today.minusDays(5), sleepMinutes = 410),
        )
        assertEquals(listOf(today.minusDays(5), today.minusDays(2)).map { it.toString().drop(5) }, LearnHistory.compress(days, today).daily.map { it.label })
    }

    @Test fun `the JSON names its columns once and a row is an array`() {
        val j = LearnHistory.toJson(LearnHistory.compress(learnDays(10), today).daily, "d")
        assertEquals(2 + LearnHistory.COLUMNS.size, j.getJSONArray("cols").length())
        assertEquals(10, j.getJSONArray("rows").length())
        assertEquals(2 + LearnHistory.COLUMNS.size, j.getJSONArray("rows").getJSONArray(0).length())
    }
}
