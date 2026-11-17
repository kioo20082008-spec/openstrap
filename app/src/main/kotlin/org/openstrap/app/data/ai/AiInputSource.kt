package org.openstrap.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.openstrap.app.data.DailyMetric
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.Days
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.SleepHistory
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.WorkoutRepository
import org.openstrap.app.data.catching
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.data.strain
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reads what the assistant sends from the app's own models, once, when the user asks: the days' sleep, Recovery, HRV,
 * resting heart rate, load, steps (false steps already taken off) and Energy, the nights and workouts in detail, the
 * baselines' inputs, the targets and today's plan. The numbers are the ones every screen shows: Recovery is worked out
 * through the one cached [HealthRepository.recovery], a week at a time, and nothing is computed afresh by a rule of its
 * own. Heavy, so off the main thread, and never run by itself: only [AiAssistant.analyse] calls it.
 */
class AiInputSource(
    private val health: HealthRepository,
    private val workouts: WorkoutRepository,
    private val profile: Flow<UserProfile>,
    private val goals: Flow<Goals>,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / MILLIS },
) {
    // The IO and CPU work is this function's; callers just wait.
    @Suppress("InjectDispatcher", "LongMethod")
    suspend fun gather(): AiInput = withContext(Dispatchers.Default) {
        val z = zone()
        val today = Days.date(nowSeconds(), z)
        val p = profile.first()
        val g = goals.first()
        val first = today.minusDays(AiPayload.DAYS - 1L)
        val window = DayWindow(Days.start(first, z), Days.end(today, z) - 1)

        // Recovery for today and each week back: each carries 7 days of Readiness, 67 of sleep scores and 30 of loads.
        val anchors = (0 until ANCHORS).map { today.minusDays(it * WEEK) }
        val results: Map<LocalDate, RecoveryResult> = anchors.mapNotNull { d -> catching { health.recovery(d, p) }.getOrNull()?.let { d to it } }.toMap()
        val now = checkNotNull(results[today]) { "Recovery for today could not be worked out" }
        // Oldest first, so where two overlap the newest, with the freshest baselines, wins.
        val ordered = results.entries.sortedBy { it.key }.map { it.value }
        val readiness = ordered.flatMap { r -> r.readinessTrend.mapNotNull { (d, s) -> s?.let { d to it } } }.toMap()
        val sleepScores = ordered.flatMap { it.sleepScores.entries }.associate { it.key to it.value }
        val loads = ordered.flatMap { it.dailyLoads }.toMap()
        val energyAtWake = results.mapNotNull { (d, r) -> r.charge.atWake?.let { d to it.roundToInt() } }.toMap()

        val sessions = health.sleepSessions(window).first()
        val mains = SleepHistory.mainSleeps(sessions, z)
        val hrv = health.hrv(window).first()
        val nightHrv = mains.mapNotNull { (d, s) ->
            hrv.filter { it.timestamp in s.start..s.end }.takeIf { it.isNotEmpty() }?.let { d to it.map { h -> h.milliseconds }.average().toFloat() }
        }.toMap()
        val resting = health.daily(DailyMetric.RestingHr, first, today).first().associate { it.date to it.value }
        val steps = health.daily(DailyMetric.Steps, first, today).first().associate { it.date to it.value.toInt() }

        val days = (0 until AiPayload.DAYS).map { first.plusDays(it.toLong()) }.map { d ->
            val main = mains[d]
            // Today is still going: its load and steps are in `now`, so the day lists only the night that ended today.
            val dayLoad = loads[d]?.takeIf { d != today }
            AiDay(
                date = d,
                sleepMinutes = main?.let(::asleepMinutes),
                sleepScore = sleepScores[d],
                recovery = readiness[d],
                hrv = nightHrv[d],
                restingHr = resting[d]?.roundToInt(),
                load = dayLoad,
                strain = dayLoad?.let { Recovery.strain(it) },
                steps = steps[d]?.takeIf { d != today },
                energyAtWake = energyAtWake[d],
            )
        }
        val nights = (0 until AiPayload.NIGHTS).map { today.minusDays(AiPayload.NIGHTS - 1L - it) }.mapNotNull { d ->
            mains[d]?.let { s -> night(d, s, nightHrv[d], sleepScores[d], z) }
        }
        val usualWake = nights.takeLast(WEEK.toInt()).mapNotNull { n -> mains[n.date]?.let { minutesOfDay(it.end, z) } }
            .takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.let(::clock)

        AiInput(
            date = today,
            ageYears = p.age(today),
            sex = p.sex?.let { if (it == UserProfile.Sex.Male) "M" else "F" },
            sleepNeedMinutes = now.sleepNeed,
            goals = AiGoals(g.steps, g.sleepMinutes, g.activeHours, g.calories, g.workoutsPerWeek),
            usualBedtime = now.usualBedtime?.format(CLOCK),
            usualWake = usualWake,
            today = AiToday(
                recovery = now.readiness.score,
                energyNow = now.charge.latest?.roundToInt(),
                energyAtWake = energyAtWake[today],
                steps = steps[today] ?: 0,
                strain = now.strain,
                load = now.load,
                plan = AiPlan.zone(now.readiness.score),
            ),
            fitness = now.loadTrend?.let { AiFitness(it.ctl, it.atl, it.form, now.acwr, results[today.minusDays(WEEK)]?.loadTrend?.ctl) },
            sleepDebtMinutes = now.readiness.contributors.firstNotNullOfOrNull { (it.detail as? Recovery.Detail.SleepDebt)?.minutes },
            days = days,
            nights = nights,
            workouts = workoutRows(window.from, z),
        )
    }

    private fun night(day: LocalDate, s: SleepSession, hrv: Float?, score: Int?, z: ZoneId) = AiNight(
        date = day,
        bedtime = Instant.ofEpochSecond(s.start).atZone(z).toLocalTime().format(CLOCK),
        wake = Instant.ofEpochSecond(s.end).atZone(z).toLocalTime().format(CLOCK),
        asleepMinutes = asleepMinutes(s),
        deepMinutes = s.deepMinutes,
        remMinutes = s.remMinutes,
        lightMinutes = s.lightMinutes,
        awakeMinutes = s.awakeMinutes,
        wakeUps = s.wakeCount,
        hrv = hrv,
        score = score,
    )

    /** The latest [AiPayload.WORKOUTS] workouts since [from], each opened whole for its heart rate drop after it. */
    private suspend fun workoutRows(from: Long, z: ZoneId): List<AiWorkout> {
        val listed = workouts.workouts().first().filter { it.workout.start >= from }.take(AiPayload.WORKOUTS)
        return listed.mapNotNull { row ->
            val a = catching { workouts.open(row.workout.start).first() }.getOrNull() ?: row
            val minutes = (a.activeSeconds / SECONDS_A_MINUTE).toInt()
            if (minutes < 1) null else workout(a, minutes, z)
        }.sortedBy { it.date }
    }

    private fun workout(a: WorkoutAnalysis, minutes: Int, z: ZoneId) = AiWorkout(
        date = Days.date(a.workout.start, z),
        sport = a.sportName,
        minutes = minutes,
        avgHr = a.workout.averageHeartRate,
        maxHr = a.workout.maxHeartRate,
        load = a.workout.trainingLoad ?: a.effect?.epoc?.roundToInt(),
        hrDrop = a.recovery60?.takeIf { a.recoveryIsMeaningful && it > 0 },
    )

    private fun asleepMinutes(s: SleepSession): Int = (SleepHistory.asleepSeconds(s) / SECONDS_A_MINUTE).toInt()

    private fun minutesOfDay(epoch: Long, z: ZoneId): Int = Instant.ofEpochSecond(epoch).atZone(z).toLocalTime().let { it.hour * MINUTES_AN_HOUR + it.minute }

    private fun clock(minutes: Int): String = java.time.LocalTime.of(minutes / MINUTES_AN_HOUR % HOURS_A_DAY, minutes % MINUTES_AN_HOUR).format(CLOCK)

    private companion object {
        const val MILLIS = 1000L
        const val ANCHORS = 5
        const val WEEK = 7L
        const val SECONDS_A_MINUTE = 60L
        const val MINUTES_AN_HOUR = 60
        const val HOURS_A_DAY = 24

        /** Local clock times as "23:40", with Western digits whatever the language. */
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    }
}
