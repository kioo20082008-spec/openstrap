package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openstrap.app.data.db.DailyAggregate
import org.openstrap.app.data.db.FalseStepsDay
import java.time.LocalDate
import java.time.ZoneOffset

/** The stored false steps ([FalseStepHistory]): kept per local hour, taken off the raw daily and hourly sums. */
class FalseStepHistoryTest {
    private val day = LocalDate.of(2026, 1, 15)

    private fun hours(vararg at: Pair<Int, Int>): List<Int> = MutableList(24) { 0 }.also { l -> at.forEach { (h, s) -> l[h] = s } }

    @Test fun `minutes become steps per local hour`() {
        val noon = 12 * 3600L
        val minutes = mapOf(noon to 10, noon + 60 to 12, noon + 3600 to 5)
        val byHour = FalseStepHistory.byHour(minutes) { (it / 3600).toInt() }
        assertEquals(24, byHour.size)
        assertEquals(22, byHour[12])
        assertEquals(5, byHour[13])
        assertEquals(27, byHour.sum())
    }

    @Test fun `an hour outside 0 to 23 is ignored`() {
        assertEquals(List(24) { 0 }, FalseStepHistory.byHour(mapOf(0L to 9)) { 24 })
    }

    @Test fun `format and parse round trip, and empty or damaged text is not worked out`() {
        val h = hours(10 to 360, 11 to 7)
        assertEquals(h, FalseStepHistory.parse(FalseStepHistory.format(h)))
        assertEquals("", FalseStepHistory.format(emptyList()))
        assertEquals("", FalseStepHistory.format(listOf(1, 2, 3)))
        assertNull(FalseStepHistory.parse(""))
        assertNull(FalseStepHistory.parse("1,2,3"))
        assertNull(FalseStepHistory.parse(List(24) { if (it == 5) "x" else "0" }.joinToString(",")))
        assertNull(FalseStepHistory.parse(List(24) { if (it == 5) "-3" else "0" }.joinToString(",")))
    }

    @Test fun `daily sums lose the stored steps, never below zero, and unknown days stay raw`() {
        val raw = listOf(
            DailyAggregate("2026-01-14", 5000.0, 0.0, 90.0, 400),
            DailyAggregate("2026-01-15", 1360.0, 0.0, 100.0, 40),
            DailyAggregate("2026-01-16", 100.0, 0.0, 30.0, 10),
        )
        val stored = listOf(
            FalseStepsDay(day, FalseStepHistory.format(hours(10 to 360))),
            FalseStepsDay(day.plusDays(1), FalseStepHistory.format(hours(3 to 500))),
        )
        val out = FalseStepHistory.daily(raw, stored)
        assertEquals(listOf(5000.0, 1000.0, 0.0), out.map { it.value })
        // Only the sum moves; the minute extremes and the count stay as the raw read gave them.
        assertEquals(raw[1].copy(value = 1000.0), out[1])
    }

    @Test fun `nothing stored leaves the raw sums as they were`() {
        val raw = listOf(DailyAggregate("2026-01-15", 1360.0, 0.0, 100.0, 40))
        assertEquals(raw, FalseStepHistory.daily(raw, emptyList()))
        assertEquals(raw, FalseStepHistory.daily(raw, listOf(FalseStepsDay(day, "damaged"))))
    }

    @Test fun `hourly steps lose the stored hours`() {
        val raw = mapOf(day to List(24) { if (it == 10) 400f else 0f }, day.minusDays(1) to List(24) { 7f })
        val stored = listOf(FalseStepsDay(day, FalseStepHistory.format(hours(10 to 360, 11 to 20))))
        val out = FalseStepHistory.hourly(raw, stored)
        assertEquals(40f, out.getValue(day)[10])
        assertEquals(0f, out.getValue(day)[11])
        assertEquals(raw.getValue(day.minusDays(1)), out.getValue(day.minusDays(1)))
    }

    @Test fun `an open span clamps to the dates a day table can hold`() {
        val r = FalseStepHistory.datesOf(0L, Long.MAX_VALUE / 2, ZoneOffset.UTC)
        assertEquals(LocalDate.of(1970, 1, 1), r.start)
        assertEquals(LocalDate.of(9999, 12, 31), r.endInclusive)
        val d = FalseStepHistory.datesOf(day.atStartOfDay(ZoneOffset.UTC).toEpochSecond(), day.plusDays(2).atStartOfDay(ZoneOffset.UTC).toEpochSecond() - 1, ZoneOffset.UTC)
        assertEquals(day, d.start)
        assertEquals(day.plusDays(1), d.endInclusive)
    }
}
