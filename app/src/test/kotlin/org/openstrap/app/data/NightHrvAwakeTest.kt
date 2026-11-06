package org.openstrap.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.data.db.HeartRateSample
import org.openstrap.app.data.db.HrvSample
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.testing.ZoneRule
import org.openstrap.app.testing.inMemoryDb
import org.openstrap.app.testing.profile
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * A night's HRV leaves out the RMSSD samples taken in its awake stages ([Recovery.nightHrv]), every night, and keeps
 * them when the night would have too few without them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NightHrvAwakeTest {
    private val zone = ZoneOffset.UTC

    @get:Rule val zoneRule = ZoneRule(zone)

    /** A sample every 5 minutes from 0, at [ms] for the k-th. */
    private fun samples(n: Int, ms: (Int) -> Int) = (0 until n).map { k -> k * 300L to ms(k) }

    @Test fun `samples in an awake stage are left out`() {
        // Awake from 50 to 80 minutes, with RMSSD at 60 ms there against 40 asleep: not far enough out to be an outlier.
        val awake = listOf(3000L until 4800L)
        val night = samples(24) { k -> if (k * 300L in awake[0]) 60 else 40 }
        assertEquals(24, Recovery.nightHrvSamples(night.map { it.second.toFloat() }).size)
        // Without the stages they all count and pull the mean up to 45 ms; with them it is the 40 ms of the sleep.
        assertEquals(45f, Recovery.nightHrv(night, emptyList()).average().toFloat(), 1e-4f)
        assertEquals(List(18) { 40f }, Recovery.nightHrv(night, awake))
    }

    @Test fun `a night left with too few samples keeps its awake ones`() {
        // Ten samples, five of them awake: five asleep is under MIN_HRV_SAMPLES, so all ten count.
        val awake = listOf(0L until 1500L)
        val night = samples(10) { k -> if (k < 5) 45 else 40 }
        assertEquals(night.map { it.second.toFloat() }, Recovery.nightHrv(night, awake))
        // The 5-200 ms gate still applies first.
        assertEquals(List(6) { 40f }, Recovery.nightHrv(samples(8) { k -> if (k < 2) 250 else 40 }, emptyList()))
    }

    @Test fun `the engine leaves out the night's awake stages`() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.healthDao()
            val date = LocalDate.of(2026, 1, 15)
            fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(LocalTime.of(h, m)).atZone(zone).toEpochSecond()
            val sleep = SleepSession(at(date.minusDays(1), 23), at(date, 7), DayKey.of(date), nap = false, score = 80)
            dao.insertSleepSessions(listOf(sleep))
            // Awake from 02:00 to 02:30, light sleep either side.
            val awakeFrom = at(date, 2)
            val awakeTo = at(date, 2, 30)
            dao.insertSleepStages(
                listOf(
                    SleepStage(sleep.start, awakeFrom, sleep.start, SleepStage.LIGHT),
                    SleepStage(awakeFrom, awakeTo, sleep.start, SleepStage.AWAKE),
                    SleepStage(awakeTo, sleep.end, sleep.start, SleepStage.LIGHT),
                ),
            )
            dao.insertHeartRate((sleep.start until sleep.end step 60L).map { HeartRateSample(it, 52 + (it / 60 % 5).toInt()) })
            dao.insertHrv((sleep.start until sleep.end step 300L).map { HrvSample(it, if (it in awakeFrom until awakeTo) 60 else 40) })
            val tonight = requireNotNull(RecoveryEngine(dao).compute(date, profile(), zone, at(date, 12)).tonight)
            // 96 samples over the night, the 6 awake ones left out.
            assertEquals(90, tonight.hrvSamples)
            assertEquals(40f, requireNotNull(tonight.hrv), 1e-4f)
        } finally {
            db.close()
        }
    }
}
