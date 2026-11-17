package org.openstrap.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The simple forecast in the payload: load trend, sleep shortfall and the expected Recovery for tomorrow. */
class AiForecastTest {
    private fun withLoads(recent: Float, before: Float) = aiInput(days = 14).let { input ->
        // days run from 14 days ago (index 0) to yesterday (index 13) once sorted
        val sorted = input.days.sortedBy { it.date }
        input.copy(days = sorted.mapIndexed { i, d -> d.copy(load = if (i >= 7) recent else before) })
    }

    @Test fun `load is rising, steady or falling by this week against the one before`() {
        assertEquals("rising", AiForecast.of(withLoads(recent = 100f, before = 60f)).loadTrend)
        assertEquals("steady", AiForecast.of(withLoads(recent = 62f, before = 60f)).loadTrend)
        assertEquals("falling", AiForecast.of(withLoads(recent = 40f, before = 60f)).loadTrend)
    }

    @Test fun `with too few days there is no trend and no average`() {
        val f = AiForecast.of(aiInput(days = 2))
        assertNull(f.loadTrend)
        assertNull(f.avgSleepMinutes7)
        assertNull(f.recoveryAvg7)
    }

    @Test fun `the sleep shortfall is the need less the week's average, never negative`() {
        val sleepy = aiInput(days = 14).let { it.copy(days = it.days.map { d -> d.copy(sleepMinutes = 400) }) }
        assertEquals(400, AiForecast.of(sleepy).avgSleepMinutes7)
        assertEquals(80, AiForecast.of(sleepy).sleepShortfallMinutes7)
        val rested = sleepy.copy(days = sleepy.days.map { it.copy(sleepMinutes = 540) })
        assertEquals(0, AiForecast.of(rested).sleepShortfallMinutes7)
    }

    @Test fun `sleep debt and poor form pull the expected Recovery down`() {
        val base = aiInput(days = 14, fitness = null, debt = null)
        val plain = AiForecast.of(base).expectedRecoveryTomorrow!!
        val indebted = AiForecast.of(base.copy(sleepDebtMinutes = 360)).expectedRecoveryTomorrow!!
        assertTrue("$indebted < $plain", indebted < plain)
        val tired = AiForecast.of(base.copy(fitness = AiFitness(40f, 60f, -20f, acwr = 1.8f))).let { it.expectedRecoveryTomorrow!! to it.adjustments }
        assertTrue(tired.first < plain)
        assertTrue(tired.second.containsKey("high_load"))
    }

    @Test fun `the expected Recovery stays between 5 and 95`() {
        val low = aiInput(days = 14, debt = 2_000).let { it.copy(days = it.days.map { d -> d.copy(recovery = 8) }) }
        assertEquals(5, AiForecast.of(low).expectedRecoveryTomorrow)
        val high = aiInput(days = 14, fitness = AiFitness(40f, 20f, 20f), debt = null).let { it.copy(days = it.days.map { d -> d.copy(recovery = 99) }) }
        assertEquals(95, AiForecast.of(high).expectedRecoveryTomorrow)
    }

    @Test fun `without any Recovery there is no expectation`() {
        val none = aiInput(days = 14).let { it.copy(days = it.days.map { d -> d.copy(recovery = null) }, today = it.today.copy(recovery = null)) }
        assertNull(AiForecast.of(none).expectedRecoveryTomorrow)
    }

    @Test fun `a mean needs at least three values`() {
        assertNull(AiForecast.mean(listOf(1f, 2f)))
        assertEquals(2f, AiForecast.mean(listOf(1f, 2f, 3f))!!, 0.001f)
    }
}
