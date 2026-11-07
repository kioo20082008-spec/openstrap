package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CheckInTest {
    private val start = LocalDate.of(2026, 10, 1)

    @Test fun `answers survive a round trip and a damaged piece is skipped`() {
        val all = mapOf(
            start to CheckIn.Entry(4, 82, 76),
            start.plusDays(1) to CheckIn.Entry(2, null, null),
        )
        assertEquals(all, CheckIn.decode(CheckIn.encode(all)))
        assertEquals(1, CheckIn.decode("2026-10-01=3,50,60;oops;2026-10-02=9,1,1").size)
        assertTrue(CheckIn.decode(null).isEmpty())
    }

    @Test fun `old days are let go and the same day is replaced`() {
        val today = start.plusDays(200)
        val all = CheckIn.with(mapOf(start to CheckIn.Entry(3, 1, 1)), today, CheckIn.Entry(5, 90, 80), today)
        assertEquals(setOf(today), all.keys)
        assertEquals(2, CheckIn.with(all, today, CheckIn.Entry(2, 40, 30), today)[today]!!.rating - 0)
    }

    @Test fun `a perfectly following number gives one and an opposite one minus one`() {
        val up = (1..5).map { it.toFloat() }
        assertEquals(1f, CheckIn.spearman(up, up.map { it * 10 })!!, 1e-4f)
        assertEquals(-1f, CheckIn.spearman(up, up.map { -it })!!, 1e-4f)
        assertNull(CheckIn.spearman(up, listOf(3f, 3f, 3f, 3f, 3f)))
    }

    @Test fun `ties share a rank`() {
        val r = CheckIn.spearman(listOf(1f, 1f, 2f, 3f), listOf(10f, 20f, 30f, 40f))
        assertNotNull(r)
        assertTrue(r!! > 0.8f)
    }

    @Test fun `a fit waits for enough days and then follows the feeling`() {
        val few = (0 until 5).associate { start.plusDays(it.toLong()) to CheckIn.Entry(it % 5 + 1, 20 * (it % 5 + 1), null) }
        assertNull(CheckIn.fit(few).energyR)
        val many = (0 until 20).associate { start.plusDays(it.toLong()) to CheckIn.Entry(it % 5 + 1, 20 * (it % 5 + 1), null) }
        val fit = CheckIn.fit(many)
        assertEquals(20, fit.days)
        assertEquals(1f, fit.energyR!!, 1e-3f)
        assertNull(fit.readinessR)
    }
}
