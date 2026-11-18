package org.openstrap.app.data.ai

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The cross-checks the model is not trusted to do itself, worked out here from the same [AiInput] the payload is made
 * of: this week against the week before and against the 30-day baseline, how sleep, bedtime, load and steps go with
 * the next Recovery and HRV, the best and worst weekday, the best and worst days with what came before them, streaks,
 * the sleep debt's path and each workout's effect on the next morning. Pure, so it is tested without a phone.
 *
 * Days line up like this: a day's sleep, sleep score, Recovery, HRV and resting heart rate belong to the night that
 * ended that morning; its load and steps are that day's own. So "load to next Recovery" pairs the load of day d with the
 * Recovery of day d+1, and "sleep to Recovery" pairs the night with the Recovery of the same morning.
 *
 * Every finding that rests on a count carries it ([Link.n]) and a confidence ([confidence]): under [MIN_N] it is
 * "insufficient" and has no figure, under [WEAK_N] "weak". An association is never causation; the prompt says so.
 */
object AiInsights {
    /** Days in a week of the comparisons. */
    const val WEEK = 7

    /** Under this many observations a finding is "insufficient" and carries no figure. */
    const val MIN_N = 8

    /** Under this many it is "weak"; under [FAIR_N] "fair"; at or over it "good". */
    const val WEAK_N = 14
    const val FAIR_N = 25

    /** Fewest pairs a correlation can be worked out from. */
    private const val MIN_PAIRS = 5

    /** A night is short when it is this many minutes under the sleep need. */
    const val SHORT_NIGHT_MARGIN = 60

    /** A day is high load at this multiple of the 30-day mean load. */
    const val HIGH_LOAD_FACTOR = 1.25f

    /** The week's average nightly shortfall must move this many minutes to be called growing or shrinking. */
    const val DEBT_BAND = 15

    /** A best-to-worst Recovery spread under this is not worth naming best and worst days over. */
    const val MIN_SPREAD = 10

    private const val BEST_DAYS = 3
    private const val WORKOUT_ITEMS = 10
    private const val DEBT_WINDOW = 14
    private const val DEBT_MIN_NIGHTS = 5
    private const val NOON = 720
    private const val DAY_MINUTES = 1440
    private const val MIN_WEEKDAYS = 4
    private const val MIN_PER_WEEKDAY = 2
    private const val HOUR = 60
    private const val PERCENT = 100

    /** Every key [toJson] writes, so [AiPayload.KEYS] holds them and a test can see nothing else is sent. */
    val KEYS: Set<String> = setOf(
        "insights", "periods", "now", "prev", "base", "d_prev", "d_base", "dir", "dir_base", "n", "n_prev",
        "sleep", "sscore", "rec", "hrv", "rhr", "load", "steps",
        "links", "k", "r", "assoc", "conf",
        "weekday", "hi", "lo", "w", "avg", "spread",
        "best_days", "worst_days", "d", "bed", "sleep_vs", "prev_load", "prev_load_pct", "prev_steps", "prev_workout",
        "streaks", "short_nights", "high_load", "low_rec", "max", "count", "limit",
        "debt", "app_debt_min", "run_now", "run_7d_ago", "avg_short_7d", "avg_short_prev_7d", "trend", "nights_7d",
        "workout_next_day", "items", "sport", "min", "rec_next", "rec_delta", "hrv_delta", "avg_delta", "rest_avg_delta", "rest_n",
    )

    // ---------------------------------------------------------------------------------------------------------------
    // The results.

    /** One metric this week ([now]) against the week before ([prev]) and the 30-day mean ([base]). Each is null with under 3 days. */
    data class Compare(
        val key: String,
        val now: Float?,
        val prev: Float?,
        val base: Float?,
        val deltaPrev: Float?,
        val deltaBase: Float?,
        /** "up", "down" or "flat" against the week before and against the baseline. */
        val dirPrev: String?,
        val dirBase: String?,
        val nNow: Int,
        val nPrev: Int,
    )

    /** How two series go together: Pearson's [r] over [n] pairs, null (and "insufficient") under [MIN_N]. */
    data class Link(val key: String, val r: Double?, val n: Int) {
        val assoc: String get() = association(r)
        val conf: String get() = confidence(n)
    }

    /** The weekday with the highest mean ([hiDay], 1 Monday to 7 Sunday) and the lowest, and how far apart they are. */
    data class Weekday(
        val key: String,
        val hiDay: Int,
        val hiAvg: Float,
        val hiN: Int,
        val loDay: Int,
        val loAvg: Float,
        val loN: Int,
        val spread: Float,
        /** How the spread compares with the metric's own day-to-day scatter: with few days most of it is chance. */
        val assoc: String,
        val n: Int,
    ) {
        val conf: String get() = confidence(n)
    }

    /** A best or worst day by Recovery with what came before it. */
    data class DayNote(
        val date: LocalDate,
        val weekday: Int,
        val recovery: Int,
        val sleepMin: Int?,
        /** Minutes of sleep over (plus) or under (minus) the 30-day mean. */
        val sleepVs: Int?,
        val hrv: Float?,
        val bedtime: String?,
        val prevLoad: Float?,
        /** The previous day's load as a percent over (plus) or under (minus) the 30-day mean. */
        val prevLoadPct: Int?,
        val prevSteps: Int?,
        val prevWorkout: String?,
    )

    /** Consecutive days meeting a condition: the run ending at the last day, the longest in the window, and how many days in all. */
    data class Streak(val now: Int, val max: Int, val count: Int, val limit: Int)

    /** The sleep debt's path: the app's own figure, a running balance over 14 nights, and this week's shortfall against the week before's. */
    data class Debt(
        val appDebtMin: Int?,
        val runningNow: Int?,
        val running7dAgo: Int?,
        /** Mean nightly shortfall against the need, in minutes: plus is short, minus is over. */
        val avgShort7: Int?,
        val avgShortPrev7: Int?,
        /** "growing", "shrinking" or "steady". */
        val trend: String?,
        val nights7: Int,
    )

    /** One workout day and what the next morning looked like. */
    data class WorkoutEffect(
        val date: LocalDate,
        val sport: String,
        val minutes: Int,
        val load: Int?,
        val rec: Int?,
        val recNext: Int?,
        val recDelta: Int?,
        val hrvDelta: Float?,
    )

    /** The workouts' effects, with the same change after days without one for comparison. */
    data class WorkoutEffects(val items: List<WorkoutEffect>, val avgDelta: Float?, val n: Int, val restAvgDelta: Float?, val restN: Int)

    data class Result(
        val compare: List<Compare>,
        val links: List<Link>,
        val weekdays: List<Weekday>,
        val bestDays: List<DayNote>,
        val worstDays: List<DayNote>,
        val shortNights: Streak?,
        val highLoad: Streak?,
        val lowRecovery: Streak?,
        val debt: Debt?,
        val workouts: WorkoutEffects?,
    )

    // ---------------------------------------------------------------------------------------------------------------
    // The words for a count and for a correlation.

    /** "insufficient", "weak", "fair" or "good" by how many observations stand behind a finding. */
    fun confidence(n: Int): String = when {
        n < MIN_N -> "insufficient"
        n < WEAK_N -> "weak"
        n < FAIR_N -> "fair"
        else -> "good"
    }

    /** "negligible", "mild", "moderate" or "strong" by the size of [r], "unknown" without one. */
    fun association(r: Double?): String = when {
        r == null -> "unknown"
        abs(r) < R_MILD -> "negligible"
        abs(r) < R_MODERATE -> "mild"
        abs(r) < R_STRONG -> "moderate"
        else -> "strong"
    }

    private const val R_MILD = 0.2
    private const val R_MODERATE = 0.4
    private const val R_STRONG = 0.6

    /** Pearson's correlation of the [pairs], null under [MIN_PAIRS] of them or when either side doesn't vary. */
    fun pearson(pairs: List<Pair<Double, Double>>): Double? {
        if (pairs.size < MIN_PAIRS) return null
        val mx = pairs.sumOf { it.first } / pairs.size
        val my = pairs.sumOf { it.second } / pairs.size
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for ((x, y) in pairs) {
            sxy += (x - mx) * (y - my)
            sxx += (x - mx) * (x - mx)
            syy += (y - my) * (y - my)
        }
        if (sxx < EPS || syy < EPS) return null
        return (sxy / sqrt(sxx * syy)).coerceIn(-1.0, 1.0)
    }

    private const val EPS = 1e-9

    /** "HH:mm" as minutes into the evening: before noon counts as after midnight, so 00:30 is later than 23:10. Null for anything else. */
    fun bedMinutes(text: String?): Int? {
        val m = text?.let { TIME.matchEntire(it.trim()) } ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h > 23 || min > 59) return null
        val total = h * HOUR + min
        return if (total < NOON) total + DAY_MINUTES else total
    }

    private val TIME = Regex("(\\d{1,2}):(\\d{2})")

    // ---------------------------------------------------------------------------------------------------------------
    // The work.

    /** What each compared metric is, how far it must move to count as a change, and where to read it. */
    private class Spec(val key: String, val abs: Float, val rel: Float, val pick: (AiDay) -> Float?)

    private val SPECS = listOf(
        Spec("sleep", 15f, 0f) { it.sleepMinutes?.toFloat() },
        Spec("sscore", 3f, 0f) { it.sleepScore?.toFloat() },
        Spec("rec", 4f, 0f) { it.recovery?.toFloat() },
        Spec("hrv", 2f, 0.05f) { it.hrv },
        Spec("rhr", 1f, 0f) { it.restingHr?.toFloat() },
        Spec("load", 0f, 0.1f) { it.load },
        Spec("steps", 500f, 0.1f) { it.steps?.toFloat() },
    )

    private fun direction(delta: Float?, reference: Float?, spec: Spec): String? {
        if (delta == null || reference == null) return null
        val band = max(spec.abs, spec.rel * abs(reference))
        return if (abs(delta) <= band) "flat" else if (delta > 0f) "up" else "down"
    }

    fun of(input: AiInput): Result {
        val days = input.days.sortedBy { it.date }
        val byDate = days.associateBy { it.date }
        val last = days.lastOrNull()?.date
        // Today's Recovery is known too, so the last finished day's load has a next morning.
        val recovery = HashMap<LocalDate, Int>().also { m ->
            days.forEach { d -> d.recovery?.let { m[d.date] = it } }
            input.today.recovery?.let { m[input.date] = it }
        }
        val nights = input.nights.sortedBy { it.date }
        val bedOf = nights.mapNotNull { n -> bedMinutes(n.bedtime)?.let { n.date to it } }.toMap()
        val bedMean = if (bedOf.size >= MIN_PAIRS) bedOf.values.average() else null
        val ctx = Ctx(input, days, byDate, recovery, nights.associateBy { it.date }, bedOf, bedMean)
        val meanLoad = AiForecast.mean(days.mapNotNull { it.load })
        val (best, worst) = bestAndWorst(ctx, meanLoad)
        val shortLimit = input.sleepNeedMinutes - SHORT_NIGHT_MARGIN
        return Result(
            compare = if (last == null) emptyList() else compare(days, last),
            links = if (last == null) emptyList() else links(ctx),
            weekdays = weekdays(days),
            bestDays = best,
            worstDays = worst,
            shortNights = streakOf(ctx) { d -> byDate[d]?.sleepMinutes?.let { it < shortLimit } }?.copy(limit = shortLimit),
            highLoad = meanLoad?.let { m ->
                val limit = m * HIGH_LOAD_FACTOR
                streakOf(ctx) { d -> byDate[d]?.load?.let { it >= limit } }?.copy(limit = limit.roundToInt())
            },
            lowRecovery = streakOf(ctx) { d -> byDate[d]?.recovery?.let { it < AiPlan.YELLOW_FROM } }?.copy(limit = AiPlan.YELLOW_FROM),
            debt = debt(ctx),
            workouts = workoutEffects(ctx),
        )
    }

    /** What the pieces of [of] read from, worked out once. */
    private class Ctx(
        val input: AiInput,
        val days: List<AiDay>,
        val byDate: Map<LocalDate, AiDay>,
        val recovery: Map<LocalDate, Int>,
        val nightByDate: Map<LocalDate, AiNight>,
        val bedOf: Map<LocalDate, Int>,
        val bedMean: Double?,
    ) {
        fun sleep(d: LocalDate): Double? = byDate[d]?.sleepMinutes?.toDouble()
        fun hrv(d: LocalDate): Double? = byDate[d]?.hrv?.toDouble()
        fun load(d: LocalDate): Double? = byDate[d]?.load?.toDouble()
        fun steps(d: LocalDate): Double? = byDate[d]?.steps?.toDouble()
        fun rec(d: LocalDate): Double? = recovery[d]?.toDouble()
        fun bedDeviation(d: LocalDate): Double? = bedMean?.let { m -> bedOf[d]?.let { abs(it - m) } }
    }

    // This week, the week before and the baseline.

    private fun compare(days: List<AiDay>, last: LocalDate): List<Compare> {
        val weekStart = last.minusDays(WEEK - 1L)
        val prevStart = last.minusDays(2L * WEEK - 1)
        val now = days.filter { it.date >= weekStart }
        val prev = days.filter { it.date >= prevStart && it.date < weekStart }
        return SPECS.map { s ->
            val a = now.mapNotNull(s.pick)
            val b = prev.mapNotNull(s.pick)
            val nowMean = AiForecast.mean(a)
            val prevMean = AiForecast.mean(b)
            val base = AiForecast.mean(days.mapNotNull(s.pick))
            val dPrev = if (nowMean != null && prevMean != null) nowMean - prevMean else null
            val dBase = if (nowMean != null && base != null) nowMean - base else null
            Compare(
                s.key, nowMean, prevMean, base, dPrev, dBase, direction(dPrev, prevMean, s), direction(dBase, base, s), a.size, b.size,
            )
        }
    }

    // Associations.

    private fun link(key: String, dates: Collection<LocalDate>, x: (LocalDate) -> Double?, y: (LocalDate) -> Double?): Link {
        val pairs = dates.mapNotNull { d ->
            val a = x(d)
            val b = y(d)
            if (a != null && b != null) a to b else null
        }
        return Link(key, if (pairs.size >= MIN_N) pearson(pairs) else null, pairs.size)
    }

    private fun links(c: Ctx): List<Link> {
        val dayDates = c.days.map { it.date }
        val nightDates = c.nightByDate.keys.sorted()
        val next = { d: LocalDate -> d.plusDays(1) }
        return listOf(
            link("sleep_rec", dayDates, c::sleep, c::rec),
            link("sleep_hrv", dayDates, c::sleep, c::hrv),
            link("bed_dev_rec", nightDates, c::bedDeviation, c::rec),
            link("bed_dev_hrv", nightDates, c::bedDeviation, c::hrv),
            link("load_next_rec", dayDates, c::load) { d -> c.rec(next(d)) },
            link("load_next_hrv", dayDates, c::load) { d -> c.hrv(next(d)) },
            link("load_next_sleep", dayDates, c::load) { d -> c.sleep(next(d)) },
            link("steps_next_sleep", dayDates, c::steps) { d -> c.sleep(next(d)) },
            link("late_bed_wakeups", nightDates, { d -> c.bedOf[d]?.toDouble() }) { d -> c.nightByDate[d]?.wakeUps?.toDouble() },
            link("late_bed_awake", nightDates, { d -> c.bedOf[d]?.toDouble() }) { d -> c.nightByDate[d]?.awakeMinutes?.toDouble() },
        )
    }

    // Weekday patterns.

    private fun weekdays(days: List<AiDay>): List<Weekday> = SPECS.filter { it.key in WEEKDAY_KEYS }
        .mapNotNull { s -> weekday(s.key, days.mapNotNull { d -> s.pick(d)?.let { v -> d.date to v } }) }

    private val WEEKDAY_KEYS = setOf("rec", "sleep", "steps", "load")

    /** The highest and lowest weekday of [values]; null with under [MIN_N] values or under four weekdays seen at least twice. */
    fun weekday(key: String, values: List<Pair<LocalDate, Float>>): Weekday? {
        if (values.size < MIN_N) return null
        val groups = values.groupBy({ it.first.dayOfWeek.value }, { it.second }).filterValues { it.size >= MIN_PER_WEEKDAY }
        if (groups.size < MIN_WEEKDAYS) return null
        val means = groups.mapValues { (_, v) -> v.average() }
        val hi = means.entries.maxByOrNull { it.value } ?: return null
        val lo = means.entries.minByOrNull { it.value } ?: return null
        if (hi.key == lo.key) return null
        val all = values.map { it.second.toDouble() }
        val mean = all.average()
        val sd = sqrt(all.sumOf { (it - mean) * (it - mean) } / all.size)
        val spread = hi.value - lo.value
        val z = if (sd < EPS) 0.0 else spread / sd
        // Weekday means from a few days each wander by themselves: spreads under 1.5 sd are what chance gives.
        val assoc = when {
            z < Z_MILD -> "negligible"
            z < Z_MODERATE -> "mild"
            z < Z_STRONG -> "moderate"
            else -> "strong"
        }
        return Weekday(key, hi.key, hi.value.toFloat(), groups.getValue(hi.key).size, lo.key, lo.value.toFloat(), groups.getValue(lo.key).size, spread.toFloat(), assoc, values.size)
    }

    private const val Z_MILD = 1.5
    private const val Z_MODERATE = 2.2
    private const val Z_STRONG = 3.0

    // Best and worst days.

    private fun bestAndWorst(c: Ctx, meanLoad: Float?): Pair<List<DayNote>, List<DayNote>> {
        val scored = c.days.filter { it.recovery != null }
        if (scored.size < MIN_N - 1) return emptyList<DayNote>() to emptyList()
        val best = scored.sortedWith(compareByDescending<AiDay> { it.recovery }.thenByDescending { it.date }).take(BEST_DAYS)
        val worst = scored.sortedWith(compareBy<AiDay> { it.recovery }.thenByDescending { it.date }).take(BEST_DAYS)
        val spread = (best.first().recovery ?: 0) - (worst.first().recovery ?: 0)
        if (spread < MIN_SPREAD) return emptyList<DayNote>() to emptyList()
        val meanSleep = AiForecast.mean(c.days.mapNotNull { it.sleepMinutes?.toFloat() })
        val note = { d: AiDay -> dayNote(c, d, meanLoad, meanSleep) }
        return best.map(note) to worst.filter { w -> best.none { it.date == w.date } }.map(note)
    }

    private fun dayNote(c: Ctx, d: AiDay, meanLoad: Float?, meanSleep: Float?): DayNote {
        val before = c.byDate[d.date.minusDays(1)]
        val prevLoad = before?.load
        val sleep = d.sleepMinutes
        val workouts = c.input.workouts.filter { it.date == d.date.minusDays(1) }
            .joinToString(", ") { "${AiPayload.cleanText(it.sport)} ${it.minutes}min" }.ifEmpty { null }
        return DayNote(
            date = d.date,
            weekday = d.date.dayOfWeek.value,
            recovery = d.recovery ?: 0,
            sleepMin = d.sleepMinutes,
            sleepVs = if (sleep != null && meanSleep != null) (sleep - meanSleep).roundToInt() else null,
            hrv = d.hrv,
            bedtime = c.nightByDate[d.date]?.bedtime,
            prevLoad = prevLoad,
            prevLoadPct = if (prevLoad != null && meanLoad != null && meanLoad > 0f) ((prevLoad / meanLoad - 1f) * PERCENT).roundToInt() else null,
            prevSteps = before?.steps,
            prevWorkout = workouts,
        )
    }

    // Streaks.

    /** The run of days for which [flag] is true, over every calendar day of the window (a day with no value breaks it); null with no days. */
    private fun streakOf(c: Ctx, flag: (LocalDate) -> Boolean?): Streak? {
        val first = c.days.firstOrNull()?.date ?: return null
        val last = c.days.last().date
        var run = 0
        var longest = 0
        var count = 0
        var d = first
        while (!d.isAfter(last)) {
            if (flag(d) == true) {
                run++
                count++
                if (run > longest) longest = run
            } else {
                run = 0
            }
            d = d.plusDays(1)
        }
        return Streak(run, longest, count, 0)
    }

    // The sleep debt's path.

    private fun debt(c: Ctx): Debt? {
        val last = c.days.lastOrNull()?.date ?: return null
        val need = c.input.sleepNeedMinutes
        var running = 0
        var known = 0
        var at7: Int? = null
        for (back in DEBT_WINDOW - 1 downTo 0) {
            val d = last.minusDays(back.toLong())
            c.byDate[d]?.sleepMinutes?.let {
                running = max(0, running + need - it)
                known++
            }
            if (back == WEEK) at7 = running
        }
        val enough = known >= DEBT_MIN_NIGHTS
        val weekStart = last.minusDays(WEEK - 1L)
        val prevStart = last.minusDays(2L * WEEK - 1)
        val short = { from: LocalDate, to: LocalDate ->
            c.days.filter { it.date >= from && it.date <= to }.mapNotNull { d -> d.sleepMinutes?.let { (need - it).toFloat() } }
        }
        val thisWeek = short(weekStart, last)
        val before = AiForecast.mean(short(prevStart, weekStart.minusDays(1)))?.roundToInt()
        val now = AiForecast.mean(thisWeek)?.roundToInt()
        val trend = if (now != null && before != null) {
            val delta = now - before
            if (delta > DEBT_BAND) "growing" else if (delta < -DEBT_BAND) "shrinking" else "steady"
        } else {
            null
        }
        return Debt(c.input.sleepDebtMinutes, if (enough) running else null, if (enough) at7 else null, now, before, trend, thisWeek.size)
    }

    // Each workout's effect on the next morning.

    private fun workoutEffects(c: Ctx): WorkoutEffects? {
        val input = c.input
        if (input.workouts.isEmpty()) return null
        val byDay = input.workouts.filter { it.date < input.date }.groupBy { it.date }
        val items = byDay.entries.sortedBy { it.key }.map { (date, list) ->
            val main = list.maxByOrNull { it.minutes } ?: list.first()
            val rec = c.recovery[date]
            val recNext = c.recovery[date.plusDays(1)]
            val hrvNow = c.byDate[date]?.hrv
            val hrvNext = c.byDate[date.plusDays(1)]?.hrv
            WorkoutEffect(
                date = date,
                sport = AiPayload.cleanText(main.sport),
                minutes = list.sumOf { it.minutes },
                load = list.mapNotNull { it.load }.takeIf { it.isNotEmpty() }?.sum(),
                rec = rec,
                recNext = recNext,
                recDelta = if (rec != null && recNext != null) recNext - rec else null,
                hrvDelta = if (hrvNow != null && hrvNext != null) hrvNext - hrvNow else null,
            )
        }
        val deltas = items.mapNotNull { it.recDelta?.toFloat() }
        // Days without a workout, from where the workout list starts (it holds only the latest ones).
        val from = if (input.workouts.size >= AiPayload.WORKOUTS) input.workouts.minOf { it.date } else c.days.firstOrNull()?.date
        val rest = c.days.filter { d -> from != null && d.date >= from && d.date < input.date && input.workouts.none { it.date == d.date } }
            .mapNotNull { d ->
                val a = c.recovery[d.date]
                val b = c.recovery[d.date.plusDays(1)]
                if (a != null && b != null) (b - a).toFloat() else null
            }
        return WorkoutEffects(items.takeLast(WORKOUT_ITEMS), AiForecast.mean(deltas), deltas.size, AiForecast.mean(rest), rest.size)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The JSON.

    /** [of]'s result as the payload's `insights`: figures rounded, nothing null, nothing for what there was too little to say. */
    fun toJson(r: Result): JSONObject = JSONObject().apply {
        put("periods", JSONObject().apply {
            r.compare.forEach { c ->
                val dec = c.key == "hrv"
                put(c.key, JSONObject().apply {
                    putF("now", c.now, dec)
                    putF("prev", c.prev, dec)
                    putF("base", c.base, dec)
                    putF("d_prev", c.deltaPrev, dec)
                    putF("d_base", c.deltaBase, dec)
                    c.dirPrev?.let { put("dir", it) }
                    c.dirBase?.let { put("dir_base", it) }
                    put("n", c.nNow)
                    put("n_prev", c.nPrev)
                })
            }
        })
        if (r.links.isNotEmpty()) put("links", JSONArray().apply {
            r.links.forEach { l ->
                put(JSONObject().apply {
                    put("k", l.key)
                    l.r?.let { put("r", (it * PERCENT).roundToInt() / PERCENT.toDouble()) }
                    put("n", l.n)
                    put("assoc", l.assoc)
                    put("conf", l.conf)
                })
            }
        })
        if (r.weekdays.isNotEmpty()) put("weekday", JSONObject().apply {
            r.weekdays.forEach { w ->
                put(w.key, JSONObject().apply {
                    put("hi", JSONObject().apply { put("w", w.hiDay); putF("avg", w.hiAvg, false); put("n", w.hiN) })
                    put("lo", JSONObject().apply { put("w", w.loDay); putF("avg", w.loAvg, false); put("n", w.loN) })
                    putF("spread", w.spread, false)
                    put("assoc", w.assoc)
                    put("n", w.n)
                    put("conf", w.conf)
                })
            }
        })
        if (r.bestDays.isNotEmpty()) put("best_days", dayNotes(r.bestDays))
        if (r.worstDays.isNotEmpty()) put("worst_days", dayNotes(r.worstDays))
        val streaks = JSONObject().apply {
            r.shortNights?.let { put("short_nights", streak(it)) }
            r.highLoad?.let { put("high_load", streak(it)) }
            r.lowRecovery?.let { put("low_rec", streak(it)) }
        }
        if (streaks.length() > 0) put("streaks", streaks)
        r.debt?.let { d ->
            put("debt", JSONObject().apply {
                d.appDebtMin?.let { put("app_debt_min", it) }
                d.runningNow?.let { put("run_now", it) }
                d.running7dAgo?.let { put("run_7d_ago", it) }
                d.avgShort7?.let { put("avg_short_7d", it) }
                d.avgShortPrev7?.let { put("avg_short_prev_7d", it) }
                d.trend?.let { put("trend", it) }
                put("nights_7d", d.nights7)
            })
        }
        r.workouts?.let { w ->
            put("workout_next_day", JSONObject().apply {
                put("items", JSONArray().apply {
                    w.items.forEach { e ->
                        put(JSONObject().apply {
                            put("d", AiPayload.shortDay(e.date))
                            put("sport", e.sport)
                            put("min", e.minutes)
                            e.load?.let { put("load", it) }
                            e.rec?.let { put("rec", it) }
                            e.recNext?.let { put("rec_next", it) }
                            e.recDelta?.let { put("rec_delta", it) }
                            putF("hrv_delta", e.hrvDelta, true)
                        })
                    }
                })
                putF("avg_delta", w.avgDelta, true)
                put("n", w.n)
                putF("rest_avg_delta", w.restAvgDelta, true)
                put("rest_n", w.restN)
            })
        }
    }

    private fun streak(s: Streak) = JSONObject().apply {
        put("now", s.now)
        put("max", s.max)
        put("count", s.count)
        put("limit", s.limit)
    }

    private fun dayNotes(list: List<DayNote>) = JSONArray().apply {
        list.forEach { n ->
            put(JSONObject().apply {
                put("d", AiPayload.shortDay(n.date))
                put("w", n.weekday)
                put("rec", n.recovery)
                n.sleepMin?.let { put("sleep", it) }
                n.sleepVs?.let { put("sleep_vs", it) }
                putF("hrv", n.hrv, true)
                n.bedtime?.let { put("bed", it) }
                putF("prev_load", n.prevLoad, false)
                n.prevLoadPct?.let { put("prev_load_pct", it) }
                n.prevSteps?.let { put("prev_steps", it) }
                n.prevWorkout?.let { put("prev_workout", it) }
            })
        }
    }

    /** Puts [v] under [key] unless it is null: with one decimal when [decimals], else as a whole number. */
    private fun JSONObject.putF(key: String, v: Float?, decimals: Boolean) {
        if (v == null || !v.isFinite()) return
        if (decimals) put(key, (v * 10.0).roundToInt() / 10.0) else put(key, v.roundToInt())
    }
}
