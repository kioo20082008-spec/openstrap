package org.openstrap.app.data.ai

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Builds the compact JSON the smart assistant sends, from an [AiInput]: the last 30 days of summaries, the last 14
 * nights and workouts in detail, the baselines, the targets, and the simple forecast ([AiForecast]). Pure.
 *
 * What it never holds: a name, an email, a location, a device id or address, or a raw heart rate stream. [AiInput] has
 * no field for them, and the one free text in it (a sport's name) is cut down to letters, digits and spaces. The size is
 * held to [MAX_BYTES]: when the full payload is bigger, the oldest days and the least recent nights and workouts go
 * first, the baselines, targets and forecast stay.
 */
object AiPayload {
    /** The most bytes the JSON may take. */
    const val MAX_BYTES = 12_000

    const val DAYS = 30
    const val NIGHTS = 14
    const val WORKOUTS = 14

    /** Longest sport name sent. */
    private const val SPORT_CHARS = 24

    /** Every key that may appear anywhere in the JSON, so a test (and a reader) can see nothing else is sent. */
    val KEYS: Set<String> = setOf(
        "v", "today", "weekday", "user", "targets", "now", "load_model", "baselines", "forecast", "days", "nights", "workouts",
        "age", "sex", "sleep_need_min", "usual_bed", "usual_wake",
        "steps", "sleep_min", "active_hours", "kcal", "workouts_week",
        "recovery", "energy", "energy_wake", "strain", "load", "plan",
        "fitness", "fatigue", "form", "acwr", "fitness_week_ago",
        "hrv", "rhr", "sleep", "n",
        "basis", "load_trend", "load_ratio", "avg_sleep_7d", "sleep_short_7d", "recovery_avg_7d", "recovery_change_7d",
        "sleep_debt_min", "expected_recovery_tomorrow", "adjustments", "form_points", "high_load", "sleep_debt",
        "d", "w", "sscore", "rec",
        "bed", "wake", "asleep", "deep", "rem", "light", "awake", "wakeups", "score",
        "sport", "min", "avg_hr", "max_hr", "hr_drop_1m",
    )

    /** The payload: its JSON, its size and how much of the history it kept. */
    data class Built(val json: String, val bytes: Int, val days: Int, val nights: Int, val workouts: Int, val trimmed: Boolean)

    /** How much history each step keeps when the payload is over [MAX_BYTES]: days, nights, workouts. */
    private val STEPS = listOf(
        Triple(DAYS, NIGHTS, WORKOUTS), Triple(DAYS, 10, 10), Triple(24, 7, 7), Triple(21, 5, 5), Triple(14, 3, 3), Triple(10, 2, 2),
        Triple(7, 0, 0), Triple(3, 0, 0),
    )

    private val shortDate = DateTimeFormatter.ofPattern("MM-dd")

    fun build(input: AiInput): Built {
        val days = input.days.sortedBy { it.date }.takeLast(DAYS)
        val nights = input.nights.sortedBy { it.date }.takeLast(NIGHTS)
        val workouts = input.workouts.sortedBy { it.date }.takeLast(WORKOUTS)
        var last: Built? = null
        for ((d, n, w) in STEPS) {
            val json = write(input, days.takeLast(d), nights.takeLast(n), workouts.takeLast(w))
            val built = Built(json, json.toByteArray(Charsets.UTF_8).size, minOf(d, days.size), minOf(n, nights.size), minOf(w, workouts.size), false)
            last = built
            if (built.bytes <= MAX_BYTES) {
                return built.copy(trimmed = built.days < days.size || built.nights < nights.size || built.workouts < workouts.size)
            }
        }
        // Even three days over the cap (nothing but absurd input does that): send the targets and the forecast alone.
        val json = write(input, emptyList(), emptyList(), emptyList())
        return checkNotNull(last).copy(json = json, bytes = json.toByteArray(Charsets.UTF_8).size, days = 0, nights = 0, workouts = 0, trimmed = true)
    }

    private fun write(input: AiInput, days: List<AiDay>, nights: List<AiNight>, workouts: List<AiWorkout>): String {
        val root = JSONObject()
        root.put("v", 1)
        root.put("today", input.date.toString())
        root.put("weekday", input.date.dayOfWeek.value)
        root.put("user", JSONObject().apply {
            putNotNull("age", input.ageYears)
            putNotNull("sex", input.sex?.takeIf { it == "F" || it == "M" })
            put("sleep_need_min", input.sleepNeedMinutes)
            putNotNull("usual_bed", input.usualBedtime)
            putNotNull("usual_wake", input.usualWake)
        })
        root.put("targets", JSONObject().apply {
            put("steps", input.goals.steps)
            put("sleep_min", input.goals.sleepMinutes)
            put("active_hours", input.goals.activeHours)
            put("kcal", input.goals.calories)
            put("workouts_week", input.goals.workoutsPerWeek)
        })
        root.put("now", JSONObject().apply {
            putNotNull("recovery", input.today.recovery)
            putNotNull("energy", input.today.energyNow)
            putNotNull("energy_wake", input.today.energyAtWake)
            put("steps", input.today.steps)
            putNotNull("strain", input.today.strain)
            putNotNull("load", input.today.load)
            putNotNull("plan", input.today.plan)
        })
        input.fitness?.let { f ->
            root.put("load_model", JSONObject().apply {
                putNotNull("fitness", f.fitness)
                putNotNull("fatigue", f.fatigue)
                putNotNull("form", f.form)
                putNotNull("acwr", f.acwr)
                putNotNull("fitness_week_ago", f.fitnessWeekAgo)
            })
        }
        root.put("baselines", baselines(input.days.sortedBy { it.date }.takeLast(DAYS)))
        root.put("forecast", forecast(AiForecast.of(input)))
        root.put("days", JSONArray().apply {
            days.forEach { d ->
                put(JSONObject().apply {
                    put("d", d.date.format(shortDate))
                    put("w", d.date.dayOfWeek.value)
                    putNotNull("sleep", d.sleepMinutes)
                    putNotNull("sscore", d.sleepScore)
                    putNotNull("rec", d.recovery)
                    putNotNull("hrv", d.hrv)
                    putNotNull("rhr", d.restingHr)
                    putNotNull("load", d.load)
                    putNotNull("strain", d.strain)
                    putNotNull("steps", d.steps)
                    putNotNull("energy_wake", d.energyAtWake)
                })
            }
        })
        root.put("nights", JSONArray().apply {
            nights.forEach { n ->
                put(JSONObject().apply {
                    put("d", n.date.format(shortDate))
                    putNotNull("bed", n.bedtime)
                    putNotNull("wake", n.wake)
                    put("asleep", n.asleepMinutes)
                    putNotNull("deep", n.deepMinutes)
                    putNotNull("rem", n.remMinutes)
                    putNotNull("light", n.lightMinutes)
                    putNotNull("awake", n.awakeMinutes)
                    putNotNull("wakeups", n.wakeUps)
                    putNotNull("hrv", n.hrv)
                    putNotNull("score", n.score)
                })
            }
        })
        root.put("workouts", JSONArray().apply {
            workouts.forEach { w ->
                put(JSONObject().apply {
                    put("d", w.date.format(shortDate))
                    put("sport", cleanText(w.sport))
                    put("min", w.minutes)
                    putNotNull("avg_hr", w.avgHr)
                    putNotNull("max_hr", w.maxHr)
                    putNotNull("load", w.load)
                    putNotNull("hr_drop_1m", w.hrDrop)
                })
            }
        })
        return root.toString()
    }

    /** The 30 days' means, each from at least [AiForecast.mean]'s minimum of days. */
    private fun baselines(days: List<AiDay>): JSONObject = JSONObject().apply {
        put("n", days.size)
        putNotNull("hrv", AiForecast.mean(days.mapNotNull { it.hrv }))
        putNotNull("rhr", AiForecast.mean(days.mapNotNull { it.restingHr?.toFloat() }))
        putNotNull("sleep", AiForecast.mean(days.mapNotNull { it.sleepMinutes?.toFloat() }))
        putNotNull("recovery", AiForecast.mean(days.mapNotNull { it.recovery?.toFloat() }))
        putNotNull("steps", AiForecast.mean(days.mapNotNull { it.steps?.toFloat() }))
        putNotNull("load", AiForecast.mean(days.mapNotNull { it.load }))
    }

    private fun forecast(f: AiForecast.Result): JSONObject = JSONObject().apply {
        put("basis", "rule_of_thumb")
        putNotNull("load_trend", f.loadTrend)
        putNotNull("load_ratio", f.loadRatio)
        putNotNull("avg_sleep_7d", f.avgSleepMinutes7)
        putNotNull("sleep_short_7d", f.sleepShortfallMinutes7)
        putNotNull("recovery_avg_7d", f.recoveryAvg7)
        putNotNull("recovery_change_7d", f.recoveryChange7)
        putNotNull("sleep_debt_min", f.sleepDebtMinutes)
        putNotNull("expected_recovery_tomorrow", f.expectedRecoveryTomorrow)
        if (f.adjustments.isNotEmpty()) put("adjustments", JSONObject().apply { f.adjustments.forEach { (k, v) -> put(adjustmentKey(k), v) } })
    }

    private fun adjustmentKey(name: String) = if (name == "form") "form_points" else name

    /** A sport's name as sent: letters, digits and single spaces only (no "@", no links, nothing a person typed in), cut short. */
    fun cleanText(text: String): String =
        text.map { if (it.isLetterOrDigit() || it == ' ') it else ' ' }.joinToString("").trim().replace(Regex(" {2,}"), " ").take(SPORT_CHARS)
            .ifEmpty { "workout" }

    /** Puts [value] under [key] unless it is null; floats go in rounded to one decimal, so no 7.1999998. */
    private fun JSONObject.putNotNull(key: String, value: Any?) {
        when (value) {
            null -> Unit
            is Float -> if (value.isFinite()) put(key, round1(value))
            is Double -> if (value.isFinite()) put(key, round1(value.toFloat()))
            else -> put(key, value)
        }
    }

    private fun round1(v: Float): Double = (v * 10.0).roundToInt() / 10.0

    /** [date] as the payload writes a day: "11-16". */
    fun shortDay(date: LocalDate): String = date.format(shortDate)
}
