package org.openstrap.app.data.ai

import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * What the smart assistant is allowed to send, as plain values: one type for each thing in the payload, so what leaves
 * the phone is exactly what these hold. There is no field for a name, an email, a location, a device id or address, or
 * a heart rate stream: the daily summaries, the nights, the workouts and the targets only. [AiPayload] turns it to JSON.
 */

/** One finished day's summary. Every figure is null when the day had none (the strap off, no sleep). */
data class AiDay(
    val date: LocalDate,
    /** Minutes asleep in the night that ended that day. */
    val sleepMinutes: Int? = null,
    /** OpenStrap's own sleep score, 0 to 100. */
    val sleepScore: Int? = null,
    val recovery: Int? = null,
    /** The night's HRV (RMSSD, ms) and resting heart rate. */
    val hrv: Float? = null,
    val restingHr: Int? = null,
    /** The day's training load (TRIMP) and its strain, 0 to 21. */
    val load: Float? = null,
    val strain: Float? = null,
    /** Steps with the false steps (arm movement at a desk) taken off. */
    val steps: Int? = null,
    /** Energy (the 0 to 100 body battery) when the main sleep ended. */
    val energyAtWake: Int? = null,
    /**
     * For the AI body learning only ([org.openstrap.app.data.ailearn.LearnPayload]); the analysis's payload never writes them.
     * When the night's sleep began, in minutes after 18:00, and when it ended, in minutes after midnight.
     */
    val bedMinutes: Int? = null,
    val wakeMinutes: Int? = null,
    /** The day's mean skin temperature and breathing rate, as the strap measured them. */
    val skinTemp: Float? = null,
    val breathRate: Float? = null,
)

/** One night in detail. Times are the phone's clock, as "HH:mm". */
data class AiNight(
    val date: LocalDate,
    val bedtime: String? = null,
    val wake: String? = null,
    val asleepMinutes: Int,
    val deepMinutes: Int? = null,
    val remMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val wakeUps: Int? = null,
    val hrv: Float? = null,
    val score: Int? = null,
)

/** One workout in detail: its sport's name, never a place or a route. */
data class AiWorkout(
    val date: LocalDate,
    val sport: String,
    val minutes: Int,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val load: Int? = null,
    /** How far the heart rate fell in the first minute after it, when it ended from a real effort. */
    val hrDrop: Int? = null,
)

/** Today so far, which the plan and the targets are about. */
data class AiToday(
    val recovery: Int? = null,
    val energyNow: Int? = null,
    val energyAtWake: Int? = null,
    val steps: Int = 0,
    val strain: Float? = null,
    val load: Float? = null,
    /** "green", "yellow" or "red": the plan Today shows from Recovery ([AiPlan.zone]). */
    val plan: String? = null,
)

/** Fitness (42-day load), fatigue (7-day load) and form from the app's own load model, and the acute to chronic ratio. */
data class AiFitness(val fitness: Float, val fatigue: Float, val form: Float, val acwr: Float? = null, val fitnessWeekAgo: Float? = null)

/** The targets the user set. */
data class AiGoals(val steps: Int, val sleepMinutes: Int, val activeHours: Int, val calories: Int, val workoutsPerWeek: Int)

/** Everything one analysis is made from. [days] and [nights] are oldest first. */
data class AiInput(
    /** Today, the day after the last day in [days]. */
    val date: LocalDate,
    val ageYears: Int? = null,
    /** "F" or "M" when the user gave it. */
    val sex: String? = null,
    val sleepNeedMinutes: Int,
    val goals: AiGoals,
    val usualBedtime: String? = null,
    val usualWake: String? = null,
    val today: AiToday = AiToday(),
    val fitness: AiFitness? = null,
    val sleepDebtMinutes: Int? = null,
    val days: List<AiDay> = emptyList(),
    val nights: List<AiNight> = emptyList(),
    val workouts: List<AiWorkout> = emptyList(),
)

/** The plan Today shows, from Recovery: the same cuts as the Today hero (67 and up green, 34 and up yellow). */
object AiPlan {
    const val GREEN_FROM = 67
    const val YELLOW_FROM = 34

    fun zone(recovery: Int?): String? = when {
        recovery == null -> null
        recovery >= GREEN_FROM -> "green"
        recovery >= YELLOW_FROM -> "yellow"
        else -> "red"
    }
}

/**
 * The simple forecast the payload carries, from the same numbers the app already works out: the load trend, the sleep
 * debt and an expected Recovery for tomorrow. It is a rule of thumb, and the payload says so: the model is told to
 * treat it as a rough guide next to the numbers themselves.
 */
object AiForecast {
    /** This week's mean load against the week before: at or above this is rising, at or under [FALLING] falling. */
    const val RISING = 1.15f
    const val FALLING = 0.85f

    /** Days in each half of the load and Recovery comparisons. */
    const val HALF = 7
    private const val MIN_DAYS = 3

    /** The adjustments to the week's mean Recovery: form's share of fitness, the sleep debt and a high acute load. */
    private const val FORM_POINTS = 20f
    private const val FORM_MIN = -10f
    private const val FORM_MAX = 6f
    private const val DEBT_POINTS_PER_HOUR = 3f
    private const val DEBT_MIN = -9f
    private const val ACWR_HIGH = 1.5f
    private const val ACWR_POINTS = -5f
    private const val RECOVERY_MIN = 5
    private const val RECOVERY_MAX = 95

    /** The forecast's figures; each null where there was too little to say. */
    data class Result(
        /** "rising", "steady" or "falling", and this week's mean load over the week before's. */
        val loadTrend: String?,
        val loadRatio: Float?,
        val avgSleepMinutes7: Int?,
        val sleepShortfallMinutes7: Int?,
        val recoveryAvg7: Int?,
        val recoveryChange7: Int?,
        val sleepDebtMinutes: Int?,
        val expectedRecoveryTomorrow: Int?,
        /** The adjustments behind [expectedRecoveryTomorrow], in points, by name. */
        val adjustments: Map<String, Int>,
    )

    fun of(input: AiInput): Result {
        val days = input.days.sortedBy { it.date }
        val last = days.takeLast(HALF)
        val before = days.dropLast(HALF).takeLast(HALF)
        val loadNow = mean(last.mapNotNull { it.load })
        val loadBefore = mean(before.mapNotNull { it.load })
        val ratio = if (loadNow != null && loadBefore != null && loadBefore > 0f) loadNow / loadBefore else null
        val trend = ratio?.let { if (it >= RISING) "rising" else if (it <= FALLING) "falling" else "steady" }
        val sleep = mean(last.mapNotNull { it.sleepMinutes?.toFloat() })
        val recNow = mean(last.mapNotNull { it.recovery?.toFloat() })
        val recBefore = mean(before.mapNotNull { it.recovery?.toFloat() })
        val base = recNow ?: input.today.recovery?.toFloat()
        val adjustments = LinkedHashMap<String, Int>()
        var expected: Float? = null
        if (base != null) {
            var e: Float = base
            val fitness = input.fitness
            if (fitness != null && fitness.fitness > 0f) {
                val form = (fitness.form / fitness.fitness * FORM_POINTS).coerceIn(FORM_MIN, FORM_MAX)
                adjustments["form"] = form.roundToInt()
                e += form
                val acwr = fitness.acwr
                if (acwr != null && acwr >= ACWR_HIGH) {
                    adjustments["high_load"] = ACWR_POINTS.roundToInt()
                    e += ACWR_POINTS
                }
            }
            val debt = input.sleepDebtMinutes
            if (debt != null && debt > 0) {
                val sleepDebt = (-debt / 60f * DEBT_POINTS_PER_HOUR).coerceAtLeast(DEBT_MIN)
                adjustments["sleep_debt"] = sleepDebt.roundToInt()
                e += sleepDebt
            }
            expected = e
        }
        return Result(
            loadTrend = trend,
            loadRatio = ratio,
            avgSleepMinutes7 = sleep?.roundToInt(),
            sleepShortfallMinutes7 = sleep?.let { (input.sleepNeedMinutes - it).roundToInt().coerceAtLeast(0) },
            recoveryAvg7 = recNow?.roundToInt(),
            recoveryChange7 = if (recNow != null && recBefore != null) (recNow - recBefore).roundToInt() else null,
            sleepDebtMinutes = input.sleepDebtMinutes,
            expectedRecoveryTomorrow = expected?.roundToInt()?.coerceIn(RECOVERY_MIN, RECOVERY_MAX),
            adjustments = adjustments,
        )
    }

    /** The mean of [values], or null under [MIN_DAYS] of them. */
    fun mean(values: List<Float>): Float? = if (values.size < MIN_DAYS) null else values.average().toFloat()
}
