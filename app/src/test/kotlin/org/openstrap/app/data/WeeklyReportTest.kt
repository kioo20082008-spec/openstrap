package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.WeeklyReport.Advice
import java.time.LocalDate

class WeeklyReportTest {
    private val last = LocalDate.of(2026, 11, 6)
    private val thisWeek = WeeklyReport.week(last)
    private val lastWeek = WeeklyReport.week(last.minusDays(7))

    private fun <T> series(week: List<LocalDate>, vararg v: T): Map<LocalDate, T> = week.zip(v.toList()).toMap()

    private fun report(
        ready: Map<LocalDate, Int> = emptyMap(), sleep: Map<LocalDate, Int> = emptyMap(), asleep: Map<LocalDate, Int> = emptyMap(),
        rhr: Map<LocalDate, Float> = emptyMap(), hrv: Map<LocalDate, Float> = emptyMap(), steps: Map<LocalDate, Int> = emptyMap(),
        load: Map<LocalDate, Float> = emptyMap(), need: Int = 480,
    ) = WeeklyReport.build(last, need, ready, sleep, asleep, rhr, hrv, steps, load)

    @Test fun `the week is seven days ending on the last one`() {
        assertEquals(7, thisWeek.size)
        assertEquals(last, thisWeek.last())
        assertEquals(last.minusDays(6), thisWeek.first())
        assertEquals(last.minusDays(7), lastWeek.last())
    }

    @Test fun `a figure is the mean of this week against the week before`() {
        val r = report(steps = series(thisWeek, 9000, 8000, 7000, 10000, 6000, 9000, 9000) + series(lastWeek, 6000, 6000, 6000, 6000, 6000, 6000, 6000))
        assertEquals(8285.714f, r.steps.value!!, 0.01f)
        assertEquals(6000f, r.steps.before!!, 0.01f)
        assertEquals(2285.714f, r.steps.change!!, 0.01f)
    }

    @Test fun `fewer than three days says nothing`() {
        val r = report(steps = series(thisWeek.take(2), 9000, 8000))
        assertNull(r.steps.value)
        assertNull(r.steps.change)
    }

    @Test fun `best and worst days come from Readiness`() {
        val r = report(ready = series(thisWeek, 60, 80, 50, 70, 65, 55, 62))
        assertEquals(thisWeek[1] to 80, r.best)
        assertEquals(thisWeek[2] to 50, r.worst)
    }

    @Test fun `no spread no best day`() {
        val r = report(ready = series(thisWeek, 70, 70, 70, 70, 70, 70, 70))
        assertNull(r.best)
        assertNull(r.worst)
    }

    @Test fun `nothing kept gives no advice`() {
        assertEquals(Advice.NoData, report().advice)
    }

    @Test fun `a low Readiness week is an easy one`() {
        assertEquals(Advice.Ease, report(ready = series(thisWeek, 50, 52, 48, 55, 51, 49, 53)).advice)
    }

    @Test fun `resting heart rate up with HRV down is an easy one even when Readiness held`() {
        val r = report(
            ready = series(thisWeek, 70, 70, 70, 70, 70, 70, 70),
            rhr = series(thisWeek, 60f, 60f, 60f, 60f, 60f, 60f, 60f) + series(lastWeek, 57f, 57f, 57f, 57f, 57f, 57f, 57f),
            hrv = series(thisWeek, 40f, 40f, 40f, 40f, 40f, 40f, 40f) + series(lastWeek, 50f, 50f, 50f, 50f, 50f, 50f, 50f),
        )
        assertEquals(Advice.Ease, r.advice)
    }

    @Test fun `short nights are about sleep`() {
        val r = report(ready = series(thisWeek, 62, 62, 62, 62, 62, 62, 62), asleep = series(thisWeek, 390, 400, 380, 395, 385, 400, 390))
        assertEquals(Advice.Sleep, r.advice)
    }

    @Test fun `a ready week with room in the load can build`() {
        val r = report(
            ready = series(thisWeek, 75, 72, 70, 78, 69, 74, 71),
            load = series(thisWeek, 40f, 40f, 40f, 40f, 40f, 40f, 40f) + series(lastWeek, 38f, 38f, 38f, 38f, 38f, 38f, 38f),
        )
        assertEquals(Advice.Build, r.advice)
    }

    @Test fun `a ready week that already climbed in load holds`() {
        val r = report(
            ready = series(thisWeek, 75, 72, 70, 78, 69, 74, 71),
            load = series(thisWeek, 60f, 60f, 60f, 60f, 60f, 60f, 60f) + series(lastWeek, 40f, 40f, 40f, 40f, 40f, 40f, 40f),
        )
        assertEquals(Advice.Hold, r.advice)
    }

    @Test fun `the report names its days`() {
        val r = report(ready = series(thisWeek, 60, 80, 50, 70, 65, 55, 62))
        assertEquals(last.minusDays(6), r.first)
        assertEquals(last, r.last)
        assertEquals(480, r.sleepNeed)
        assertNotNull(r.best)
        assertTrue(r.readiness.value!! in 60f..65f)
    }
}
