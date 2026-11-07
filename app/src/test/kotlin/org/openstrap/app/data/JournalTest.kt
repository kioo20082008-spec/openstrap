package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.Insights.Metric
import org.openstrap.app.data.Journal.Tag
import java.time.LocalDate

class JournalTest {
    private val today = LocalDate.of(2026, 11, 7)

    @Test fun `tags survive the round trip in order`() {
        val days = mapOf(
            today.minusDays(1) to setOf(Tag.HardDay, Tag.LateCaffeine),
            today.minusDays(3) to setOf(Tag.LongNap),
        )
        val raw = Journal.encode(days)
        assertEquals("2026-11-04=nap;2026-11-06=caffeine,stress", raw)
        assertEquals(days, Journal.decode(raw))
    }

    @Test fun `damaged pieces are skipped`() {
        assertEquals(emptyMap<LocalDate, Set<Tag>>(), Journal.decode(null))
        assertEquals(emptyMap<LocalDate, Set<Tag>>(), Journal.decode(""))
        val got = Journal.decode("junk;2026-11-06=caffeine,unknown;2026-13-40=nap;2026-11-05=")
        assertEquals(mapOf(LocalDate.of(2026, 11, 6) to setOf(Tag.LateCaffeine)), got)
    }

    @Test fun `toggling adds, removes and forgets old days`() {
        val once = Journal.toggled(emptyMap(), today, Tag.HeavyDinner, today)
        assertEquals(setOf(Tag.HeavyDinner), once[today])
        val twice = Journal.toggled(once, today, Tag.LateWorkout, today)
        assertEquals(setOf(Tag.HeavyDinner, Tag.LateWorkout), twice[today])
        val off = Journal.toggled(twice, today, Tag.HeavyDinner, today)
        assertEquals(setOf(Tag.LateWorkout), off[today])
        assertTrue(Journal.toggled(off, today, Tag.LateWorkout, today).isEmpty())
        val old = today.minusDays(Journal.KEEP_DAYS + 1L)
        assertNull(Journal.toggled(mapOf(old to setOf(Tag.LongNap)), today, Tag.HardDay, today)[old])
    }

    // Twelve days from the 1st: coffee on the even ones. Mornings after coffee days are worse, with a little wobble.
    private val start = LocalDate.of(2026, 10, 1)
    private val coffeeDays = (0 until 12).filter { it % 2 == 0 }.map { start.plusDays(it.toLong()) }
    private val tags = coffeeDays.associateWith { setOf(Tag.LateCaffeine) }
    private val now = start.plusDays(12)

    private fun mornings(withCoffee: Float, without: Float, wobble: Float = 1.5f): Map<LocalDate, Float> =
        (0 until 12).associate { i ->
            val wiggle = (if (i % 3 == 0) wobble else if (i % 3 == 1) -wobble else 0f)
            start.plusDays(i + 1L) to (if (i % 2 == 0) withCoffee else without) + wiggle
        }

    @Test fun `a clear drop in HRV after a habit is named`() {
        val r = Insights.analyze(tags, mapOf(Metric.Hrv to mornings(40f, 50f)), now)
        val f = r.findings.single()
        assertEquals(Tag.LateCaffeine, f.tag)
        assertEquals(Metric.Hrv, f.metric)
        assertEquals(false, f.good)
        assertEquals(6, f.withDays)
        assertEquals(6, f.withoutDays)
        assertTrue(f.percent in 18..22)
        assertEquals(12, r.loggedDays)
        assertEquals(6, r.tagDays[Tag.LateCaffeine])
    }

    @Test fun `a lower resting heart rate counts as good`() {
        val f = Insights.analyze(tags, mapOf(Metric.RestingHr to mornings(55f, 58f)), now).findings.single()
        assertEquals(true, f.good)
    }

    @Test fun `a small difference is not named`() {
        assertTrue(Insights.analyze(tags, mapOf(Metric.Hrv to mornings(49f, 50f)), now).findings.isEmpty())
        assertTrue(Insights.analyze(tags, mapOf(Metric.SleepScore to mornings(80f, 82f)), now).findings.isEmpty())
    }

    @Test fun `noise bigger than the difference is not named`() {
        assertTrue(Insights.analyze(tags, mapOf(Metric.Hrv to mornings(45f, 50f, wobble = 12f)), now).findings.isEmpty())
    }

    @Test fun `too few days of a habit is not enough`() {
        val few = tags.entries.take(3).associate { it.key to it.value }
        assertTrue(Insights.analyze(few, mapOf(Metric.Hrv to mornings(40f, 50f)), now).findings.isEmpty())
    }

    @Test fun `today's habit has no morning yet`() {
        val r = Insights.analyze(mapOf(now to setOf(Tag.HardDay)), mapOf(Metric.Hrv to mornings(40f, 50f)), now)
        assertTrue(r.findings.isEmpty())
        assertEquals(0, r.loggedDays)
    }

    @Test fun `nothing logged is an empty result`() {
        assertEquals(Insights.Result.EMPTY, Insights.analyze(emptyMap(), emptyMap(), now))
    }
}
