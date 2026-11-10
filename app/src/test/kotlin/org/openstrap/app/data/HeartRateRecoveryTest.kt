package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.HeartRateRecovery.Trend

/** Heart rate recovery after a workout ([HeartRateRecovery]): peak of the last minute against 60 and 120 s after the stop. */
class HeartRateRecoveryTest {
    private val end = 10_000L
    private val maxHr = 190

    /** Effort at [effort] bpm for five minutes up to [end], then a fall of [first] bpm over the first minute and [second] over the next, a second a reading. */
    private fun series(effort: Int = 170, first: Int = 30, second: Int = 15, tailSeconds: Int = 180): List<Pair<Long, Int>> =
        (end - 300..end).map { it to effort } + (1..tailSeconds).map { k ->
            val fall = if (k <= 60) first * k / 60.0 else first + second * (k - 60) / 60.0
            (end + k) to Math.round(effort - fall).toInt()
        }

    @Test fun `a clean stop gives the fall at one and two minutes from the last minute's peak`() {
        val r = HeartRateRecovery.compute(series(), end, maxHr)!!
        assertEquals(170, r.peak)
        assertEquals(30, r.hrr1)
        assertEquals(45, r.hrr2)
    }

    @Test fun `the peak is the last minute's, not an earlier one`() {
        // A harder minute five minutes in doesn't count; only the last 60 s before the stop do.
        val early = (end - 300..end - 200).map { it to 188 }
        val rest = series(effort = 165).filter { it.first > end - 200 }
        val r = HeartRateRecovery.compute(early + rest, end, maxHr)!!
        assertEquals(165, r.peak)
        assertEquals(30, r.hrr1)
    }

    @Test fun `without the minute after the stop there is nothing to say`() {
        assertNull(HeartRateRecovery.compute(series(tailSeconds = 5), end, maxHr))
        assertNull(HeartRateRecovery.compute(series().filter { it.first <= end }, end, maxHr))
    }

    @Test fun `no second minute when the recording stops at one`() {
        val r = HeartRateRecovery.compute(series(tailSeconds = 62), end, maxHr)!!
        assertEquals(30, r.hrr1)
        assertNull(r.hrr2)
    }

    @Test fun `a second minute that ends higher than the first is left out`() {
        val r = HeartRateRecovery.compute(series(second = -10), end, maxHr)!!
        assertEquals(30, r.hrr1)
        assertNull(r.hrr2)
    }

    @Test fun `an easy finish under 65 percent of max is not rated`() {
        assertNull(HeartRateRecovery.compute(series(effort = 115, first = 10, second = 5), end, maxHr))
    }

    @Test fun `a heart rate that doesn't fall, or keeps climbing, is not a recovery`() {
        assertNull(HeartRateRecovery.compute(series(first = 0, second = 0), end, maxHr))
        assertNull(HeartRateRecovery.compute(series(first = -20, second = -10), end, maxHr))
    }

    @Test fun `a fall no heart makes is a bad reading`() {
        assertNull(HeartRateRecovery.compute(series(effort = 190, first = 85, second = 10), end, 200))
    }

    @Test fun `a spike over 25 bpm a second is dropped and doesn't set the peak`() {
        val withSpike = series().map { (t, bpm) -> if (t == end - 20) t to 215 else t to bpm }
        val r = HeartRateRecovery.compute(withSpike, end, maxHr)!!
        assertEquals(170, r.peak)
        assertEquals(30, r.hrr1)
        // And a dip in the minute after doesn't make the fall look bigger.
        val dip = series().map { (t, bpm) -> if (t == end + 60) t to 60 else t to bpm }
        assertTrue(HeartRateRecovery.compute(dip, end, maxHr)!!.hrr1 in 29..30)
    }

    @Test fun `clean drops readings outside the valid range and jumps, and keeps a lasting step`() {
        val s = listOf(1L to 100, 2L to 29, 3L to 101, 4L to 160, 5L to 102, 6L to 103)
        assertEquals(listOf(1L to 100, 3L to 101, 5L to 102, 6L to 103), HeartRateRecovery.clean(s))
        // A real step up that stays is let in after RESET_AFTER_REJECTS readings.
        val step = listOf(1L to 40) + (2L..8L).map { it to 170 }
        assertEquals(listOf(1L, 5L, 6L, 7L, 8L), HeartRateRecovery.clean(step).map { it.first })
    }

    @Test fun `minute readings are too sparse to rate`() {
        val minutes = (end - 600..end + 180 step 60).map { it to if (it <= end) 170 else 140 }
        assertNull(HeartRateRecovery.compute(minutes, end, maxHr))
    }

    @Test fun `your usual is the median of the month's recoveries, from three`() {
        assertNull(HeartRateRecovery.usual(listOf(20, 30)))
        assertEquals(25, HeartRateRecovery.usual(listOf(20, 25, 40)))
        assertEquals(23, HeartRateRecovery.usual(listOf(20, 25, 21, 40)))
    }

    @Test fun `the trend is faster, usual or slower by four bpm either way, and none without a usual`() {
        assertNull(HeartRateRecovery.trend(30, null))
        assertEquals(Trend.Faster, HeartRateRecovery.trend(34, 30))
        assertEquals(Trend.Usual, HeartRateRecovery.trend(33, 30))
        assertEquals(Trend.Usual, HeartRateRecovery.trend(27, 30))
        assertEquals(Trend.Slower, HeartRateRecovery.trend(26, 30))
    }

    @Test fun `twelve or under is low, thirteen is not`() {
        assertTrue(HeartRateRecovery.isLow(12))
        assertFalse(HeartRateRecovery.isLow(13))
    }
}
