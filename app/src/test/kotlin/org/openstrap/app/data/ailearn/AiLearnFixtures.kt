package org.openstrap.app.data.ailearn

import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.ai.AiDay
import org.openstrap.app.data.ai.AiInput
import org.openstrap.app.data.ai.AiNight
import org.openstrap.app.data.ai.AiWorkout
import org.openstrap.app.data.ai.AI_TODAY
import org.openstrap.app.data.ai.aiInput
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.data.selfcheck.Severity
import java.time.LocalDate

/** The day the AI learning's tests are about: the same one the smart assistant's fixtures use. */
val LEARN_TODAY: LocalDate = AI_TODAY

/** [count] days ending with [LEARN_TODAY], oldest first, as the app gathers them, each with every figure the learning sends. */
fun learnDays(count: Int): List<AiDay> = ((count - 1) downTo 0).map { i ->
    AiDay(
        date = LEARN_TODAY.minusDays(i.toLong()), sleepMinutes = 400 + i % 7 * 10, sleepScore = 70 + i % 9, recovery = 50 + i % 30,
        hrv = 55.2345f + i % 5, restingHr = 55 + i % 4, load = 40f + i % 6 * 10, strain = 8.25f + i % 5, steps = 6_000 + i * 10,
        energyAtWake = 70 + i % 20, bedMinutes = 270 + i % 40, wakeMinutes = 400 + i % 30, skinTemp = 33.1234f + i % 3 * 0.1f, breathRate = 14.5f + i % 4 * 0.1f,
    )
}

/** An input with [days] days of history (up to two years), the last 14 nights and 14 workouts in detail. */
fun learnAi(days: Int = 730, sport: String = "Running"): AiInput =
    aiInput(days = 1, nights = 14, workouts = 14, sport = sport).copy(days = learnDays(days))

fun learnInput(
    days: Int = 730,
    profile: String? = null,
    incidents: List<SelfLearning.Incident> = emptyList(),
    tuning: ChargeTuning = ChargeTuning.Default,
    sport: String = "Running",
) = LearnInput(learnAi(days, sport), profile, incidents, tuning)

/** An incident of the self-check or of the AI learning, for the payload and notice tests. */
fun incident(
    id: Int, check: String, last: LocalDate = LEARN_TODAY, status: SelfLearning.Status = SelfLearning.Status.Applied,
    changes: List<SelfLearning.Change> = emptyList(), note: String = "", nums: List<Float> = listOf(1f, 2f),
) = SelfLearning.Incident(id, check, Severity.Medium, last, last, nums, status, changes, notified = false, note = note)

/** A profile with a few statements, to be merged and sent. */
fun sampleProfile(updated: LocalDate = LEARN_TODAY.minusDays(1)): Profile = Profile(
    updated = updated, from = LEARN_TODAY.minusDays(200), to = updated,
    sections = mapOf(
        ProfileSection.Sleep to listOf(Statement("ينام عادة نحو 7 ساعات ونصف", 80, 60), Statement("يتقطع نومه بعد السهر", 55, 12)),
        ProfileSection.Hrv to listOf(Statement("ينخفض HRV بعد الأيام الشاقة", 70, 30)),
    ),
    facts = ProfileFacts(needMin = 450, needConf = 65, bed = "23:30", wake = "06:45", recoveryDays = 1.5f, hrvMs = 55, rhrBpm = 58),
)

/** Every knob's key, for tests that go through all of them. */
val KNOB_KEYS: List<String> = Knob.entries.map { it.key }

/** A night and a workout as the payload sends them, used to build bigger inputs. */
fun night(day: LocalDate) = AiNight(date = day, bedtime = "23:10", wake = "06:50", asleepMinutes = 420, deepMinutes = 70, remMinutes = 90, lightMinutes = 250, awakeMinutes = 25, wakeUps = 2, hrv = 52.5f, score = 75)

fun workout(day: LocalDate, sport: String = "Running") = AiWorkout(date = day, sport = sport, minutes = 40, avgHr = 140, maxHr = 172, load = 55, hrDrop = 28)
