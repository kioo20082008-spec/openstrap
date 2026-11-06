package org.openstrap.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.data.db.ActivityMinute
import org.openstrap.app.data.db.RestingHeartRate
import org.openstrap.app.testing.ZoneRule
import org.openstrap.app.testing.inMemoryDb
import org.openstrap.app.testing.profile
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Recovery's activity and calories leave out arm movement at a desk ([Recovery.falseStepMinutes]) on the day's own
 * resting and max heart rate, so the steps, the distance and the kcal of a day agree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FalseStepsEngineTest {
    private val zone = ZoneOffset.UTC

    @get:Rule val zoneRule = ZoneRule(zone)

    private val date = LocalDate.of(2026, 1, 15)

    private fun at(h: Int, m: Int = 0) = date.atTime(LocalTime.of(h, m)).atZone(zone).toEpochSecond()

    /**
     * The day worked out with half an hour at a desk from 10:00 at [deskSteps] a minute (every fifth minute still) and
     * [deskBpm], and ten minutes' walk at 100 steps a minute from 14:00. Resting HR 60 (the strap's), max 190 (set).
     */
    private suspend fun day(deskSteps: Int, deskBpm: Int = 66): RecoveryResult {
        val db = inMemoryDb()
        try {
            val dao = db.healthDao()
            dao.insertRestingHr(listOf(RestingHeartRate(date, 60)))
            val desk = (0 until 30).map { i -> ActivityMinute(at(10, i), if (i % 5 == 4) 0 else deskSteps, 10, 0, deskBpm) }
            val walk = (0 until 10).map { i -> ActivityMinute(at(14, i), 100, 40, 0, 120) }
            dao.insertActivity(desk + walk)
            return RecoveryEngine(dao).compute(date, profile(maxHeartRate = 190), zone, at(20))
        } finally {
            db.close()
        }
    }

    @Test fun `a desk stretch comes off the day's steps and kcal`() = runTest {
        val r = day(deskSteps = 15)
        assertEquals(60f, r.restingHr, 1e-4f)
        assertEquals(190f, r.maxHr, 1e-4f)
        val a = requireNotNull(r.activity)
        assertEquals(1000, a.steps)
        assertEquals(24, a.falseSteps.size)
        assertEquals(360, a.falseSteps.values.sum())
        // The day's kcal are those of the same day sitting still at the desk.
        val still = day(deskSteps = 0)
        assertEquals(a.distanceMeters, still.activity?.distanceMeters)
        assertEquals(requireNotNull(still.calories).totalKcal, requireNotNull(r.calories).totalKcal, 0.01f)
    }

    @Test fun `the same stretch at a quarter of reserve keeps its steps`() = runTest {
        // (93 - 60) / 130 is 25 % of reserve: a slow walk, not a desk.
        val a = requireNotNull(day(deskSteps = 15, deskBpm = 93).activity)
        assertEquals(1360, a.steps)
        assertEquals(emptyMap<Long, Int>(), a.falseSteps)
    }
}
