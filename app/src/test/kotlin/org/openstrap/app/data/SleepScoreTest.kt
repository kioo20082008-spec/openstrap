package org.openstrap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openstrap.app.data.Recovery.Night
import org.openstrap.app.data.db.SleepStage

class SleepScoreTest {
    private val day = 86_400L

    /** Main sleep [i] with [asleep] minutes and its midpoint at [midpoint] minutes after local midnight. */
    private fun night(i: Int, asleep: Int = 480, midpoint: Int? = 3 * 60 + 30, waso: Int? = null, awakenings: Int? = null) = Night(
        day = i * day, start = i * day - 5 * 3600, end = i * day + 3 * 3600, asleepMinutes = asleep,
        wasoMinutes = waso, awakenings = awakenings, midpointMinutes = midpoint,
    )

    @Test fun `duration is full from 7 to 9 h at an 8 h need, and falls off either side`() {
        listOf(7 * 60, 8 * 60, 9 * 60).forEach { assertEquals("$it", 100f, SleepScore.duration(it, 480), 1e-4f) }
        assertEquals(60f, SleepScore.duration(6 * 60, 480), 1e-4f)
        assertEquals(80f, SleepScore.duration(6 * 60 + 30, 480), 1e-4f)
        assertEquals(30f, SleepScore.duration(5 * 60, 480), 1e-4f)
        assertEquals(0f, SleepScore.duration(4 * 60, 480), 1e-4f)
        assertEquals(0f, SleepScore.duration(2 * 60, 480), 1e-4f)
        assertEquals(85f, SleepScore.duration(10 * 60, 480), 1e-4f)
        assertEquals(55f, SleepScore.duration(12 * 60, 480), 1e-4f)
        // On at the same rate past 12 h, never under the floor.
        assertEquals(47.5f, SleepScore.duration(12 * 60 + 30, 480), 1e-4f)
        assertEquals(SleepScore.LONG_FLOOR, SleepScore.duration(13 * 60, 480), 1e-4f)
        assertEquals(SleepScore.LONG_FLOOR, SleepScore.duration(16 * 60, 480), 1e-4f)
    }

    @Test fun `the band moves with the need`() {
        // From 65 the need is 7 h 30: 6 h 30 to 8 h 30 is full, and 9 h is past it.
        val need = Recovery.sleepNeed(70)
        assertEquals(100f, SleepScore.duration(6 * 60 + 30, need), 1e-4f)
        assertEquals(100f, SleepScore.duration(8 * 60 + 30, need), 1e-4f)
        assertEquals(92.5f, SleepScore.duration(9 * 60, need), 1e-4f)
        assertEquals(60f, SleepScore.duration(5 * 60 + 30, need), 1e-4f)
    }

    @Test fun `continuity is the mean of wake after sleep onset and awakenings`() {
        assertEquals(100f, SleepScore.continuity(11, 1), 1e-4f)
        assertEquals(100f, SleepScore.continuity(20, 0), 1e-4f)
        // 35 min awake is halfway from 20 to 50, and two awakenings score 67.
        assertEquals(50f, SleepScore.wasoPart(35), 1e-4f)
        assertEquals(66.67f, SleepScore.awakeningsPart(2), 0.01f)
        assertEquals(58.33f, SleepScore.continuity(35, 2), 0.01f)
        assertEquals(33.33f, SleepScore.awakeningsPart(3), 0.01f)
        assertEquals(0f, SleepScore.awakeningsPart(4), 1e-4f)
        assertEquals(0f, SleepScore.wasoPart(50), 1e-4f)
        assertEquals(0f, SleepScore.continuity(90, 6), 1e-4f)
        // Without an awakenings count, WASO alone.
        assertEquals(50f, SleepScore.continuity(35, null), 1e-4f)
    }

    @Test fun `awakenings are awake stretches of 5 min or more inside the sleep, touching rows as one`() {
        val m = 60L
        val stages = listOf(
            Triple(0L, 10 * m, SleepStage.AWAKE), // before sleep onset
            Triple(10 * m, 100 * m, SleepStage.LIGHT),
            Triple(100 * m, 103 * m, SleepStage.AWAKE), // 3 min, then 3 more in the next row: one 6 min stretch
            Triple(103 * m, 106 * m, SleepStage.AWAKE),
            Triple(106 * m, 200 * m, SleepStage.DEEP),
            Triple(200 * m, 204 * m, SleepStage.AWAKE), // 4 min: too short
            Triple(204 * m, 300 * m, SleepStage.REM),
            Triple(300 * m, 310 * m, SleepStage.AWAKE), // 10 min
            Triple(310 * m, 400 * m, SleepStage.LIGHT),
            Triple(400 * m, 430 * m, SleepStage.AWAKE), // after the final wake
        )
        assertEquals(2, SleepScore.awakenings(stages))
        assertEquals(0, SleepScore.awakenings(listOf(Triple(0L, 480 * m, SleepStage.LIGHT))))
        assertNull(SleepScore.awakenings(listOf(Triple(0L, 600L, SleepStage.AWAKE))))
    }

    @Test fun `regularity is the midpoint against the usual, the short way round the clock`() {
        assertEquals(100f, SleepScore.regularity(SleepScore.midpointShift(3 * 60 + 45, 3 * 60 + 30)), 1e-4f)
        // Usual 03:30, tonight 09:33: over 6 h off.
        assertEquals(363, SleepScore.midpointShift(9 * 60 + 33, 3 * 60 + 30))
        assertEquals(0f, SleepScore.regularity(363), 1e-4f)
        assertEquals(50f, SleepScore.regularity(105), 1e-4f)
        // Across midnight: 23:40 against 00:20 is 40 min, not 23 h 20.
        assertEquals(40, SleepScore.midpointShift(23 * 60 + 40, 20))
        assertEquals(40, SleepScore.midpointShift(20, 23 * 60 + 40))
        // A usual midpoint either side of midnight averages round the clock.
        assertEquals(0, SleepScore.usualMidpoint(listOf(23 * 60 + 50, 10, 23 * 60 + 40, 20, 0)))
    }

    @Test fun `regularity needs five earlier main sleeps and looks at the last 14`() {
        val four = (1..4).map { night(it) }
        assertNull(SleepScore.of(night(5), four).regularity)
        val five = (1..5).map { night(it) }
        assertEquals(100f, SleepScore.of(night(6, midpoint = 3 * 60 + 45), five).regularity!!, 1e-4f)
        // Nights on or after tonight don't count.
        assertNull(SleepScore.of(night(3), five).regularity)
        // Twenty nights at 06:00, then fourteen at 03:30: only the fourteen count.
        val history = (1..20).map { night(it, midpoint = 6 * 60) } + (21..34).map { night(it) }
        assertEquals(3 * 60 + 30, SleepScore.usualMidpoint(history.mapNotNull { it.midpointMinutes }))
        assertEquals(15, SleepScore.of(night(35, midpoint = 3 * 60 + 45), history).midpointShiftMinutes)
    }

    @Test fun `the parts a night lacks are left out and the rest renormalised`() {
        // Duration alone: 6 h is 60.
        assertEquals(60, SleepScore.of(360, 480, null, null, null).total)
        // Duration 60 and continuity 100: (0.45·60 + 0.30·100) / 0.75 = 76.
        assertEquals(76, SleepScore.of(360, 480, 10, 0, null).total)
        // Duration 60 and regularity 0: (0.45·60) / 0.70 ≈ 38.6.
        assertEquals(39, SleepScore.of(360, 480, null, null, 300).total)
        // All three.
        assertEquals(100, SleepScore.of(480, 480, 10, 1, 10).total)
        assertEquals(Math.round(0.45 * 60 + 0.30 * 58.333 + 0.25 * 50).toInt(), SleepScore.of(360, 480, 35, 2, 105).total)
    }

    @Test fun `a twelve-hour day sleep far from the usual midpoint scores about 55`() {
        // 12 h 02 asleep from 03:27 to 15:39, 11 min awake, one awakening, against a usual midpoint of 03:30.
        val usual = (1..14).map { night(it, midpoint = 3 * 60 + 30 + (it % 3 - 1) * 10) }
        val tonight = night(15, asleep = 12 * 60 + 2, midpoint = 9 * 60 + 33, waso = 11, awakenings = 1)
        val s = SleepScore.of(tonight, usual, 480)
        assertEquals(54.5f, s.duration, 1e-4f)
        assertEquals(100f, s.continuity!!, 1e-4f)
        assertEquals(0f, s.regularity!!, 1e-4f)
        assertEquals(55, s.total)
    }

    @Test fun `readiness scores sleep with OpenStrap's score, never the strap's`() {
        val history = (1..20).map { night(it, waso = 10, awakenings = 0) }
        val tonight = night(21, asleep = 12 * 60 + 2, midpoint = 9 * 60 + 33, waso = 11, awakenings = 1).copy(strapScore = 91)
        val sleep = Recovery.readiness(tonight, history).contributors.first { it.factor == Recovery.Factor.Sleep }
        assertEquals(55, sleep.score)
        assertEquals(SleepScore.of(tonight, history).total, sleep.score)
    }
}
