package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.db.ActivityMinute

/**
 * Arm movement at a desk counted as steps ([Recovery.falseStepMinutes]): only a long, low, sitting stretch away from
 * any walking comes off, and never one asleep or in a workout.
 */
class FalseStepsTest {
    private val rest = 60f
    private val max = 190f
    private val from = 10 * 3600L

    /** A day's minutes as the engine hands them over: steps and heart rate by minute start. */
    private class Minutes {
        val steps = HashMap<Long, Int>()
        val hr = HashMap<Long, Int>()

        /** [n] minutes from [start], the i-th at [steps] steps and [bpm] (none when null). */
        fun add(start: Long, n: Int, bpm: (Int) -> Int?, steps: (Int) -> Int) = apply {
            for (i in 0 until n) {
                val t = start + i * 60L
                this.steps[t] = steps(i)
                bpm(i)?.let { hr[t] = it }
            }
        }
    }

    /** Sitting at a desk: 12 to 18 steps a minute with every fifth minute still, at about 5 % of reserve. */
    private fun desk(start: Long = from, n: Int = 30, bpm: (Int) -> Int? = { 66 + it % 3 }) =
        Minutes().add(start, n, bpm) { i -> if (i % 5 == 4) 0 else 12 + i % 7 }

    private fun flagged(m: Minutes, asleep: (Long) -> Boolean = { false }, inWorkout: (Long) -> Boolean = { false }) =
        Recovery.falseStepMinutes(m.steps, m.hr, rest, max, asleep, inWorkout)

    @Test fun `half an hour at a desk comes off`() {
        val m = desk()
        val off = flagged(m)
        assertEquals(m.steps.filterValues { it > 0 }, off)
        assertEquals(m.steps.values.sum(), off.values.sum())
        assertTrue(off.values.sum() in 300..500)
    }

    @Test fun `a slow walk at a quarter of reserve stays`() {
        // 30 steps a minute at 92 bpm: (92 - 60) / 130 is about 25 % of reserve.
        val walk = Minutes().add(from, 30, { 92 + it % 2 }) { 28 + it % 5 }
        assertEquals(emptyMap<Long, Int>(), flagged(walk))
        // One minute up at a quarter of reserve keeps a desk stretch too.
        assertEquals(emptyMap<Long, Int>(), flagged(desk(bpm = { if (it == 12) 93 else 66 })))
    }

    @Test fun `a stretch next to a minute of walking stays`() {
        // The desk's last stepping minute is its 29th; 80 steps two minutes after it.
        val near = desk().apply { steps[from + 30 * 60L] = 80 }
        assertEquals(emptyMap<Long, Int>(), flagged(near))
        // Walking well clear of it doesn't save it.
        val clear = desk().apply { steps[from + 40 * 60L] = 80 }
        assertEquals(desk().steps.filterValues { it > 0 }, flagged(clear))
    }

    @Test fun `a stretch asleep or in a workout is left alone`() {
        val m = desk()
        val span = from..from + 30 * 60L
        assertEquals(emptyMap<Long, Int>(), flagged(m, asleep = { it in span }))
        assertEquals(emptyMap<Long, Int>(), flagged(m, inWorkout = { it in span }))
        // A workout in the middle splits it into two stretches too short to count.
        assertEquals(emptyMap<Long, Int>(), flagged(m, inWorkout = { it in from + 14 * 60L..from + 15 * 60L }))
    }

    @Test fun `a short stretch stays`() {
        assertEquals(emptyMap<Long, Int>(), flagged(desk(n = 15)))
    }

    @Test fun `a stretch mostly without heart rate stays`() {
        assertEquals(emptyMap<Long, Int>(), flagged(desk(bpm = { if (it < 10) 66 else null })))
    }

    @Test fun `a stretch that rarely steps stays`() {
        // A step every third minute, low and long: under 60 % of its minutes stepped.
        val sparse = Minutes().add(from, 60, { 66 }) { if (it % 3 == 0) 20 else 0 }
        assertEquals(emptyMap<Long, Int>(), flagged(sparse))
    }

    @Test fun `the steps shown lose them, never below nothing`() {
        val off = mapOf(from to 15, from + 60 to 20)
        assertEquals(965, Activity.withoutFalseSteps(1000, off))
        assertEquals(0, Activity.withoutFalseSteps(20, off))
        val minutes = listOf(ActivityMinute(from, 15, 0, 0, 66), ActivityMinute(from + 120, 30, 0, 0, 66))
        assertEquals(listOf(0, 30), Activity.withoutFalseSteps(minutes, off).map { it.steps })
    }
}
