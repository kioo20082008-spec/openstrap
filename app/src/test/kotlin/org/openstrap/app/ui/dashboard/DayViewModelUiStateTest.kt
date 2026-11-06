package org.openstrap.app.ui.dashboard

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.data.DayData
import org.openstrap.app.data.DayRepository
import org.openstrap.app.data.DayValue
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.Healthspan
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.SleepDay
import org.openstrap.app.data.SleepEdits
import org.openstrap.app.data.db.ActivityMinute
import org.openstrap.app.data.db.DailyCaloriesRow
import org.openstrap.app.data.db.DailyPai
import org.openstrap.app.data.db.HeartRateSample
import org.openstrap.app.data.db.HrvSample
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.testing.FixedClock
import org.openstrap.app.testing.LocaleRule
import org.openstrap.app.testing.ZoneRule
import org.openstrap.app.presentation.ChartPoint
import org.openstrap.app.presentation.StageSpan
import org.openstrap.app.presentation.TrendPoint
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** The day view model only maps a [DayData] to what the screens draw (audit A3), and hands the Activity screens state (A11). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DayViewModelUiStateTest {
    @get:Rule val zone = ZoneRule()
    @get:Rule val locale = LocaleRule()

    private val date = LocalDate.of(2026, 1, 15)
    private fun at(h: Int, m: Int = 0) = date.atTime(h, m).toEpochSecond(ZoneOffset.UTC)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `an empty day maps to an empty loaded state`() {
        val s = DayViewModel.toUiState(DayData(date))
        assertTrue(s.loaded)
        assertEquals(date, s.date)
        assertEquals(DayWindow.of(date).from, s.dayStart)
        assertEquals(DayWindow.of(date).to, s.dayEnd)
        assertNull(s.restingHeartRate)
        assertFalse(s.restingEstimated)
        assertNull(s.sleep)
        assertNull(s.sleepBreakdown)
        assertEquals(List(24) { 0f }, s.hourlySteps)
    }

    @Test fun `steps come by the hour, and the shown total stays the repository's`() {
        val minutes = (0 until 5).map { ActivityMinute(at(9) + it * 60L, 100, 40, 1, null) } + ActivityMinute(at(13), 50, 40, 1, null)
        val s = DayViewModel.toUiState(DayData(date, minutes = minutes, steps = 900))
        assertEquals(900, s.steps)
        assertEquals(550, s.minuteSteps)
        assertEquals(500f, s.hourlySteps[9])
        assertEquals(50f, s.hourlySteps[13])
    }

    @Test fun `false steps come off the total, the minutes' sum and their hour`() {
        val desk = (0 until 30).map { ActivityMinute(at(10) + it * 60L, 15, 10, 0, 66) }
        val walk = (0 until 3).map { ActivityMinute(at(14) + it * 60L, 100, 40, 1, 110) }
        // The strap's live count is ahead of the minutes; another day's minute is left alone.
        val s = DayViewModel.toUiState(DayData(date, minutes = desk + walk, steps = 1000))
        val off = desk.associate { it.timestamp to it.steps } + (at(10) - 86_400L to 15)
        val shown = DayViewModel.withoutFalseSteps(s, off)
        assertEquals(550, shown.steps)
        assertEquals(300, shown.minuteSteps)
        assertEquals(0f, shown.hourlySteps[10])
        assertEquals(300f, shown.hourlySteps[14])
        assertEquals(1, shown.activeHours)
        // Nothing to take off leaves the day as it was, and the total never goes below nothing.
        assertEquals(s, DayViewModel.withoutFalseSteps(s, emptyMap()))
        assertEquals(0, DayViewModel.withoutFalseSteps(s.copy(steps = 100), off).steps)
    }

    @Test fun `every sleep score is OpenStrap's own from the day's Recovery, and none until it's in`() {
        val main = SleepSession(at(0), at(7), score = 91)
        val before = SleepSession(at(0) - 86_400L, at(7) - 86_400L, score = 88)
        val nights = listOf(
            org.openstrap.app.data.SleepNight(date.minusDays(2), null),
            org.openstrap.app.data.SleepNight(date.minusDays(1), before),
            org.openstrap.app.data.SleepNight(date, main),
        )
        val s = DayViewModel.toUiState(DayData(date, sleep = SleepDay(main = main, previousScore = 88), nights = nights))
        val parts = org.openstrap.app.data.SleepScore.of(420, 480, 11, 1, 300)
        fun recovery(day: LocalDate) = RecoveryResult(
            day, org.openstrap.app.data.Recovery.Readiness(null, emptyList(), 0, false, emptyList(), false),
            org.openstrap.app.data.Recovery.ChargeDay(emptyList(), null, null, 0f, 0f, 0f, 0f, null, null, anchored = false),
            emptyList(), 0f, null, 52f, 190f, true, org.openstrap.app.data.Recovery.StressThreshold(60, null, 0),
            sleepScore = parts, sleepScores = mapOf(date to parts.total, date.minusDays(1) to 64),
        )
        val shown = DayViewModel.withSleepScores(s, recovery(date))
        assertEquals(parts.total, shown.sleep?.score)
        assertEquals(parts, shown.sleepScore)
        assertEquals(64, shown.previousSleepScore)
        assertEquals(listOf(null, 64, parts.total), shown.nights.map { it.session?.score })
        // The times and totals stay the strap's session's.
        assertEquals(main.copy(score = parts.total), shown.sleep)
        // Before the day's Recovery, or with another day's, no score at all rather than the strap's.
        listOf(null, recovery(date.minusDays(1))).forEach { r ->
            val waiting = DayViewModel.withSleepScores(s, r)
            assertNull(waiting.sleep?.score)
            assertNull(waiting.sleepScore)
            assertNull(waiting.previousSleepScore)
            assertEquals(listOf<Int?>(null, null, null), waiting.nights.map { it.session?.score })
        }
    }

    @Test fun `the trends carry their weekday labels, and today's resting value and flag come from the last day`() {
        val days = (6L downTo 0L).map { date.minusDays(it) }
        val data = DayData(
            date,
            restingTrend = days.map { DayValue(it, if (it == date) 52f else null) },
            restingEstimated = days.map { it == date },
            hrvTrend = days.map { DayValue(it, 40f) },
        )
        val s = DayViewModel.toUiState(data)
        val label = { d: LocalDate -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2) }
        assertEquals(days.map { TrendPoint(label(it), if (it == date) 52f else null) }, s.restingTrend)
        assertEquals("Th", s.restingTrend.last().label)
        assertEquals(52, s.restingHeartRate)
        assertTrue(s.restingEstimated)
        assertEquals(days.map { TrendPoint(label(it), 40f) }, s.hrvTrend)
    }

    @Test fun `the sleep's stages become spans, and only naps with stages get a breakdown`() {
        val main = SleepSession(at(0), at(7), score = 80)
        val stages = listOf(SleepStage(at(0), at(3), main.start, SleepStage.DEEP), SleepStage(at(3), at(7), main.start, SleepStage.AWAKE))
        val nap = SleepSession(at(14), at(14, 30), nap = true)
        val bare = SleepSession(at(16), at(16, 20), nap = true)
        val napStages = listOf(SleepStage(at(14), at(14, 30), nap.start, SleepStage.LIGHT))
        val deleted = SleepEdits.Resolved(main, null)
        val data = DayData(
            date,
            sleep = SleepDay(
                main = main, mainStages = stages, naps = listOf(nap, bare), napStages = mapOf(nap.start to napStages, bare.start to emptyList()),
                previousScore = 70, mainAsDetected = main, strapStages = stages, deletedMain = deleted, neighbours = listOf(main, nap),
            ),
        )
        val s = DayViewModel.toUiState(data)
        assertEquals(main, s.sleep)
        assertEquals(70, s.previousSleepScore)
        assertEquals(stages.map { StageSpan(it.start, it.end, it.stage) }, s.sleepStages)
        assertEquals(s.sleepStages, s.strapSleepStages)
        assertEquals(3 * 3600L, s.sleepBreakdown?.asleepSeconds)
        assertEquals(setOf(nap.start, bare.start), s.napStages.keys)
        assertEquals(setOf(nap.start), s.napBreakdowns.keys)
        assertEquals(deleted, s.deletedSleep)
        assertEquals(listOf(main, nap), s.sleepNeighbours)
    }

    @Test fun `heart rate and the night's readings become charts`() {
        val hr = (0 until 10).map { HeartRateSample(at(10) + it * 60L, 60 + it) }
        val data = DayData(date, heartRate = hr, nightHeartRate = hr, sleepHrv = listOf(HrvSample(at(2), 55)), hrv = listOf(HrvSample(at(12), 45)))
        val s = DayViewModel.toUiState(data)
        assertEquals(69, s.latestHeartRate)
        assertTrue(s.heartRate.isNotEmpty())
        assertEquals(s.heartRate, s.sleepHeartRate)
        assertEquals(listOf(ChartPoint(at(2), 55f)), s.sleepHrv)
        assertEquals(listOf(ChartPoint(at(12), 45f)), s.hrv)
    }

    @Test fun `the Activity screens get the day's Heart points and energy as state`() = runTest {
        val repo = FakeDays()
        val clock = FixedClock(Instant.parse("2026-01-15T12:00:00Z"))
        val vm = DayViewModel(repo, clock = { clock.clock }, work = UnconfinedTestDispatcher(testScheduler))
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.activityHistory.collect {} }
        val h = vm.activityHistory.first { it.date == date }
        assertEquals(date.minusDays(ActivityHistory.HEART_POINTS_DAYS - 1L) to date, repo.paiWindow)
        assertEquals(date.minusDays(ActivityHistory.CALORIES_DAYS - 1L) to date, repo.energyWindow)
        assertEquals(HeartPointsDay(12f, 80f), h.heartPointsOn(date)[date])
        assertEquals(emptyMap<LocalDate, HeartPointsDay>(), h.heartPointsOn(date.minusDays(1)))
        assertEquals(CaloriesDay(400f, 2400f, 150f, true), h.caloriesOn(date, date.minusDays(6))?.get(date))
        // A range only gets its own days, and another day's energy is still loading.
        assertEquals(setOf(date), h.caloriesOn(date, date)?.keys)
        assertNull(h.caloriesOn(date.minusDays(1), date.minusDays(7)))
        collector.cancel()
        vm.viewModelScope.coroutineContext[Job]?.cancel()
    }

    /** A day repository with one day of Heart points and energy, noting the windows asked for. */
    private inner class FakeDays : DayRepository {
        var paiWindow: Pair<LocalDate, LocalDate>? = null
        var energyWindow: Pair<LocalDate, LocalDate>? = null

        override fun day(date: LocalDate, isToday: Boolean): Flow<DayData> = flowOf(DayData(date, isToday))
        override fun daysWithData(): Flow<Set<LocalDate>> = flowOf(emptySet())
        override suspend fun hourlySteps(from: LocalDate, to: LocalDate): Map<LocalDate, List<Float>> = emptyMap()
        override suspend fun recovery(date: LocalDate, profile: UserProfile): RecoveryResult = error("not read here")
        override suspend fun healthspan(date: LocalDate, profile: UserProfile): Healthspan.Outcome = error("not read here")
        override fun changes(): Flow<Unit> = emptyFlow()
        override fun deletedSleep(): Flow<List<SleepEdits.Resolved>> = flowOf(emptyList())
        override suspend fun setSleepTimes(strap: SleepSession, start: Long, end: Long) = Unit
        override suspend fun deleteSleep(strap: SleepSession) = Unit
        override suspend fun restoreSleep(strap: SleepSession) = Unit

        override fun dailyPai(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DailyPai>> {
            paiWindow = from to to
            return flowOf(mapOf(date to DailyPai(date.toString(), 12f, 80f, at(9))))
        }

        override fun dailyCalories(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DailyCaloriesRow>> {
            energyWindow = from to to
            val row = DailyCaloriesRow(date, at(12), 2400f, 400f, 1700f, 600, true, workoutKcal = 150f, walkingKcal = 100f, hourly = "")
            return flowOf(mapOf(date to row, date.minusDays(10) to row))
        }
    }
}
