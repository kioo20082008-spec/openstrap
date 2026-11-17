package org.openstrap.app.data.ai

import java.time.LocalDate

/** The day the fixtures are about: the smart assistant's tests all read from here. */
val AI_TODAY: LocalDate = LocalDate.of(2026, 11, 17)

/** A full input: [days] finished days, [nights] nights and [workouts] workouts ending the day before [AI_TODAY]. */
fun aiInput(
    days: Int = 40,
    nights: Int = 20,
    workouts: Int = 20,
    bedtime: String = "23:10",
    sport: String = "Running",
    fitness: AiFitness? = AiFitness(fitness = 40f, fatigue = 46f, form = -6f, acwr = 1.2f, fitnessWeekAgo = 38f),
    debt: Int? = 150,
) = AiInput(
    date = AI_TODAY,
    ageYears = 34,
    sex = "M",
    sleepNeedMinutes = 480,
    goals = AiGoals(steps = 10_000, sleepMinutes = 480, activeHours = 12, calories = 600, workoutsPerWeek = 4),
    usualBedtime = "23:00",
    usualWake = "06:45",
    today = AiToday(recovery = 62, energyNow = 55, energyAtWake = 80, steps = 3_200, strain = 4.5f, load = 30f, plan = AiPlan.zone(62)),
    fitness = fitness,
    sleepDebtMinutes = debt,
    days = (1..days).map { i ->
        AiDay(
            date = AI_TODAY.minusDays(i.toLong()), sleepMinutes = 400 + i % 7 * 10, sleepScore = 70 + i % 9, recovery = 50 + i % 30,
            hrv = 55.2345f + i % 5, restingHr = 55 + i % 4, load = 40f + i % 6 * 10, strain = 8.25f + i % 5, steps = 6_000 + i * 100,
            energyAtWake = 70 + i % 20,
        )
    },
    nights = (1..nights).map { i ->
        AiNight(
            date = AI_TODAY.minusDays(i.toLong()), bedtime = bedtime, wake = "06:50", asleepMinutes = 410 + i % 5 * 8, deepMinutes = 70,
            remMinutes = 90, lightMinutes = 250, awakeMinutes = 25, wakeUps = i % 3, hrv = 52.5f, score = 75,
        )
    },
    workouts = (1..workouts).map { i ->
        AiWorkout(
            date = AI_TODAY.minusDays(i.toLong()), sport = sport, minutes = 30 + i, avgHr = 140, maxHr = 172, load = 55, hrDrop = 28,
        )
    },
)
