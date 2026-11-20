package org.openstrap.app.data.ailearn

import org.json.JSONArray
import org.json.JSONObject
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.ai.AiInput
import org.openstrap.app.data.ai.AiInsights
import org.openstrap.app.data.ai.AiNight
import org.openstrap.app.data.ai.AiPayload
import org.openstrap.app.data.ai.AiWorkout
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.data.selfcheck.Severity
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Everything one day's learning request is made of: the history ([ai], up to a year or two of days), the profile so far, the incidents and the tuning in force. */
class LearnInput(
    val ai: AiInput,
    /** The profile as stored (its JSON), null before the first day. */
    val profile: String?,
    /** The self-check's incidents; those of the last days are sent. */
    val incidents: List<SelfLearning.Incident>,
    /** The self-check part of Energy's tuning in force today. */
    val tuning: ChargeTuning,
)

/**
 * The compact JSON the AI body learning sends once a day (docs/algorithms/self-check.md): the profile so far, the whole
 * history compressed by [LearnHistory], the last 14 nights and workouts in detail, the app's own cross-checks
 * ([AiInsights]), the self-check's incidents of the last days and the tuning in force. Pure.
 *
 * What it never holds: a name, an email, a location, a device id or address, or a raw heart rate stream. [AiInput] has no
 * field for them, a sport's name is cut down to letters, digits and spaces, and a test reads every key against [KEYS]. The size
 * is held to [MAX_BYTES] by dropping the oldest history first (months, then weeks, then the oldest days of the daily rows, then
 * the oldest workouts and nights); the profile, the tuning, the incidents and the insights always stay.
 */
object LearnPayload {
    /** The most bytes the JSON may take: about 24 KB. */
    const val MAX_BYTES = 24_000

    const val VERSION = 1

    const val NIGHTS = 14
    const val WORKOUTS = 14

    /** The incidents of this many days back are sent, at most [MAX_INCIDENTS]. */
    const val INCIDENT_DAYS = 3L
    const val MAX_INCIDENTS = 12

    /** The daily rows are never trimmed below this many days. */
    const val MIN_DAILY = 14

    /** Every key that may appear anywhere in the JSON apart from the profile's own ([BodyProfile.KEYS]), so a test can see nothing else is sent. */
    val KEYS: Set<String> = AiPayload.KEYS + BodyProfile.KEYS + Knob.entries.map { it.key } + setOf(
        "profile", "history", "daily", "weekly", "monthly", "cols", "rows", "incidents", "k", "d", "sev", "st", "ch", "tuning",
    )

    /** The payload: its JSON, its size and how much of the history it kept. */
    data class Built(
        val json: String,
        val bytes: Int,
        val daily: Int,
        val weekly: Int,
        val monthly: Int,
        val nights: Int,
        val workouts: Int,
        val trimmed: Boolean,
    ) {
        /** Days of history the request rests on: the daily rows, and each week and month at its length. */
        val rows: Int get() = daily + weekly + monthly
    }

    private val shortDate = DateTimeFormatter.ofPattern("MM-dd")

    private class Keep(val daily: Int, val weekly: Int, val monthly: Int, val nights: Int, val workouts: Int)

    fun build(input: LearnInput): Built {
        val ai = input.ai
        val rows = LearnHistory.compress(ai.days, ai.date)
        val nights = ai.nights.sortedBy { it.date }.takeLast(NIGHTS)
        val workouts = ai.workouts.sortedBy { it.date }.takeLast(WORKOUTS)
        val insights = AiInsights.toJson(AiInsights.of(ai.copy(days = ai.days.sortedBy { it.date }.takeLast(AiPayload.DAYS))))
        val profile = input.profile?.let { runCatching { JSONObject(it) }.getOrNull() }
        val incidents = incidentsJson(input)
        val tuning = tuningJson(input.tuning)
        val full = Keep(rows.daily.size, rows.weekly.size, rows.monthly.size, nights.size, workouts.size)
        var keep = full
        while (true) {
            val json = write(input, profile, insights, incidents, tuning, rows, keep, nights, workouts)
            val bytes = json.toByteArray(Charsets.UTF_8).size
            val next = trim(keep)
            if (bytes <= MAX_BYTES || next == null) {
                val trimmed = keep.daily < full.daily || keep.weekly < full.weekly || keep.monthly < full.monthly || keep.nights < full.nights || keep.workouts < full.workouts
                return Built(json, bytes, keep.daily, keep.weekly, keep.monthly, keep.nights, keep.workouts, trimmed)
            }
            keep = next
        }
    }

    /** One row less, the oldest detail first: months, then weeks, then days (down to [MIN_DAILY]), then workouts, then nights. Null when nothing is left to drop. */
    private fun trim(k: Keep): Keep? = when {
        k.monthly > 0 -> Keep(k.daily, k.weekly, k.monthly - 1, k.nights, k.workouts)
        k.weekly > 0 -> Keep(k.daily, k.weekly - 1, k.monthly, k.nights, k.workouts)
        k.daily > MIN_DAILY -> Keep(k.daily - 1, k.weekly, k.monthly, k.nights, k.workouts)
        k.workouts > 0 -> Keep(k.daily, k.weekly, k.monthly, k.nights, k.workouts - 1)
        k.nights > 0 -> Keep(k.daily, k.weekly, k.monthly, k.nights - 1, k.workouts)
        else -> null
    }

    // The payload is one flat description of everything sent, built in one place so it can be read top to bottom.
    @Suppress("LongParameterList", "LongMethod")
    private fun write(
        input: LearnInput,
        profile: JSONObject?,
        insights: JSONObject,
        incidents: JSONArray,
        tuning: JSONObject,
        rows: LearnHistory.Rows,
        keep: Keep,
        nights: List<AiNight>,
        workouts: List<AiWorkout>,
    ): String {
        val ai = input.ai
        val root = JSONObject()
        root.put("v", VERSION)
        root.put("today", ai.date.toString())
        root.put("weekday", ai.date.dayOfWeek.value)
        root.put(
            "user",
            JSONObject().apply {
                putNotNull("age", ai.ageYears)
                putNotNull("sex", ai.sex?.takeIf { it == "F" || it == "M" })
                put("sleep_need_min", ai.sleepNeedMinutes)
                putNotNull("usual_bed", ai.usualBedtime)
                putNotNull("usual_wake", ai.usualWake)
            },
        )
        root.put(
            "targets",
            JSONObject().apply {
                put("steps", ai.goals.steps)
                put("sleep_min", ai.goals.sleepMinutes)
            },
        )
        root.put(
            "now",
            JSONObject().apply {
                putNotNull("recovery", ai.today.recovery)
                putNotNull("energy", ai.today.energyNow)
                putNotNull("energy_wake", ai.today.energyAtWake)
                put("steps", ai.today.steps)
                putNotNull("strain", ai.today.strain)
                putNotNull("load", ai.today.load)
            },
        )
        ai.fitness?.let { f ->
            root.put(
                "load_model",
                JSONObject().apply {
                    putNotNull("fitness", f.fitness)
                    putNotNull("fatigue", f.fatigue)
                    putNotNull("form", f.form)
                    putNotNull("acwr", f.acwr)
                },
            )
        }
        profile?.let { root.put("profile", it) }
        root.put(
            "history",
            JSONObject().apply {
                put("daily", LearnHistory.toJson(rows.daily.takeLast(keep.daily), "d"))
                if (keep.weekly > 0) put("weekly", LearnHistory.toJson(rows.weekly.takeLast(keep.weekly), "wk"))
                if (keep.monthly > 0) put("monthly", LearnHistory.toJson(rows.monthly.takeLast(keep.monthly), "mo"))
            },
        )
        root.put(
            "nights",
            JSONArray().apply {
                nights.takeLast(keep.nights).forEach { n ->
                    put(
                        JSONObject().apply {
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
                        },
                    )
                }
            },
        )
        root.put(
            "workouts",
            JSONArray().apply {
                workouts.takeLast(keep.workouts).forEach { w ->
                    put(
                        JSONObject().apply {
                            put("d", w.date.format(shortDate))
                            put("sport", AiPayload.cleanText(w.sport))
                            put("min", w.minutes)
                            putNotNull("avg_hr", w.avgHr)
                            putNotNull("max_hr", w.maxHr)
                            putNotNull("load", w.load)
                            putNotNull("hr_drop_1m", w.hrDrop)
                        },
                    )
                }
            },
        )
        root.put("insights", insights)
        root.put("incidents", incidents)
        root.put("tuning", tuning)
        return root.toString()
    }

    /** The self-check's incidents of the last [INCIDENT_DAYS] days, most recent first: which check, when, how severe, what became of it and what it changed. */
    private fun incidentsJson(input: LearnInput): JSONArray {
        val since = input.ai.date.minusDays(INCIDENT_DAYS)
        return JSONArray().apply {
            input.incidents.filter { it.last >= since }.sortedWith(compareByDescending<SelfLearning.Incident> { it.last }.thenBy { it.id })
                .take(MAX_INCIDENTS).forEach { i ->
                    put(
                        JSONObject().apply {
                            put("k", i.check)
                            put("d", i.last.format(shortDate))
                            put("sev", severity(i.severity))
                            put("st", i.status.name.lowercase())
                            put("n", JSONArray(i.nums.mapNotNull { n -> n.takeIf { it.isFinite() }?.let { x -> round1(x) } }))
                            if (i.changes.isNotEmpty()) put("ch", JSONObject().apply { i.changes.forEach { c -> put(c.knob.key, round2(c.to)) } })
                        },
                    )
                }
        }
    }

    private fun severity(s: Severity): String = when (s) {
        Severity.Low -> "L"
        Severity.Medium -> "M"
        Severity.High -> "H"
    }

    /** The tuning in force: every knob's value now. */
    private fun tuningJson(t: ChargeTuning): JSONObject = JSONObject().apply { Knob.entries.forEach { put(it.key, round2(it.read(t))) } }

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

    private fun round2(v: Float): Double = (v * 100.0).roundToInt() / 100.0
}
