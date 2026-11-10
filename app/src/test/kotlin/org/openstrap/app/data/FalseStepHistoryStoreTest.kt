package org.openstrap.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import java.time.ZoneId

/**
 * A day with half an hour at a desk is worked out once, its false steps stored beside its calories, and then the daily
 * and hourly step history, Habit Age and the rewards all read the day without them. The raw minutes stay as synced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FalseStepHistoryStoreTest {
    private val zone = ZoneId.of("Asia/Kathmandu")

    @get:Rule val zoneRule = ZoneRule(zone)

    private val db = inMemoryDb()
    private val dao = db.healthDao()
    private val date = LocalDate.of(2026, 1, 15)

    @After fun close() = db.close()

    private fun at(h: Int, m: Int = 0) = date.atTime(LocalTime.of(h, m)).atZone(zone).toEpochSecond()

    /** Half an hour at 15 steps a minute from 10:00 (every fifth minute still), ten minutes' walk at 100 from 14:00: 1360 raw. */
    private suspend fun seed() {
        dao.insertRestingHr(listOf(RestingHeartRate(date, 60)))
        val desk = (0 until 30).map { i -> ActivityMinute(at(10, i), if (i % 5 == 4) 0 else 15, 10, 0, 66) }
        val walk = (0 until 10).map { i -> ActivityMinute(at(14, i), 100, 40, 0, 120) }
        dao.insertActivity(desk + walk)
    }

    private suspend fun store(): EnergyExpenditure.Day {
        val e = requireNotNull(RecoveryEngine(dao).calories(date, profile(maxHeartRate = 190), 60f, 190f, null, zone, at(20)))
        dao.insertDailyCalories(listOf(DailyCaloriesHistory.row(date, e, at(20))))
        return e
    }

    @Test fun `the day's calories carry the steps they left out, by local hour`() = runTest {
        seed()
        val e = RecoveryEngine(dao).calories(date, profile(maxHeartRate = 190), 60f, 190f, null, zone, at(20))!!
        assertEquals(24, e.falseStepsByHour.size)
        assertEquals(360, e.falseStepsByHour[10])
        assertEquals(360, e.falseStepsByHour.sum())
    }

    @Test fun `daily history reads the corrected sums and the raw rows are untouched`() = runTest {
        seed()
        val from = at(0)
        val to = from + 86_399
        // Before the day is stored, the history is raw.
        assertEquals(1360.0, dao.dailyStepsCorrectedOnce(from, to, zone).single().value, 0.0)
        store()
        assertEquals(1360.0, dao.dailyStepsOnce(from, to).single().value, 0.0)
        assertEquals(1000.0, dao.dailyStepsCorrectedOnce(from, to, zone).single().value, 0.0)
        assertEquals(1360, dao.activityOnce(from, to).sumOf { it.steps })
        val repo = HealthRepository(dao)
        assertEquals(1000f, repo.daily(DailyMetric.Steps, date, date, zone).first().single().value, 0f)
    }

    @Test fun `your usual steps by hour read the corrected hours`() = runTest {
        seed()
        store()
        val hours = HealthRepository(dao).hourlySteps(date, date, zone).getValue(date)
        // The desk's hour is empty, the walk's is whole.
        assertEquals(0f, hours[10], 0f)
        assertEquals(1000f, hours[14], 0f)
        assertEquals(1000f, hours.sum(), 0f)
    }

    @Test fun `a row from before the column counts as not worked out`() = runTest {
        seed()
        assertTrue(dao.falseStepsDaysOnce().isEmpty())
        store()
        assertEquals(listOf(date), dao.falseStepsDaysOnce())
    }
}
