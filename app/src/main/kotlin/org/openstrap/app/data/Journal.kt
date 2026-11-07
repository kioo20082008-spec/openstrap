package org.openstrap.app.data

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The evening journal: what happened on a day, from a short fixed list, tapped in one touch. Stored as one line of text
 * in settings (`2026-11-07=caffeine,dinner;2026-11-06=nap`), the last [KEEP_DAYS] days.
 */
object Journal {
    enum class Tag(val id: String) {
        LateCaffeine("caffeine"), HeavyDinner("dinner"), LateWorkout("workout"),
        LateScreens("screens"), HardDay("stress"), LongNap("nap"),
    }

    const val KEEP_DAYS = 180

    fun encode(days: Map<LocalDate, Set<Tag>>): String =
        days.filterValues { it.isNotEmpty() }.toSortedMap().entries
            .joinToString(";") { (day, tags) -> "$day=" + tags.sortedBy { it.ordinal }.joinToString(",") { it.id } }

    /** What [encode] wrote; a damaged piece is skipped rather than failing the rest. */
    fun decode(raw: String?): Map<LocalDate, Set<Tag>> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = HashMap<LocalDate, Set<Tag>>()
        raw.split(';').forEach { piece ->
            val parts = piece.split('=', limit = 2)
            val day = runCatching { LocalDate.parse(parts[0].trim()) }.getOrNull() ?: return@forEach
            val tags = parts.getOrNull(1).orEmpty().split(',').mapNotNull { id -> Tag.entries.firstOrNull { it.id == id.trim() } }.toSet()
            if (tags.isNotEmpty()) out[day] = tags
        }
        return out
    }

    /** [day]'s tags with [tag] switched, and the days older than [KEEP_DAYS] before [today] dropped. */
    fun toggled(all: Map<LocalDate, Set<Tag>>, day: LocalDate, tag: Tag, today: LocalDate): Map<LocalDate, Set<Tag>> {
        val now = all[day].orEmpty()
        val next = all + (day to if (tag in now) now - tag else now + tag)
        val oldest = today.minusDays(KEEP_DAYS.toLong())
        return next.filter { (d, t) -> t.isNotEmpty() && d >= oldest }
    }
}

/**
 * What a habit does to the next morning, from the user's own days: the mornings after the days a habit was logged
 * against the mornings after the days it wasn't (from the first day logged on). A habit is only named once there are
 * [MIN_DAYS] of each, the difference is big enough to matter and Welch's t says it isn't the usual night-to-night
 * wobble. It is a pattern in the data, never proof of a cause.
 */
object Insights {
    enum class Metric { Hrv, RestingHr, SleepScore }

    /** [diff]: the mean after days with the habit minus the mean after days without, in the metric's unit. */
    data class Finding(
        val tag: Journal.Tag, val metric: Metric, val diff: Float, val percent: Int,
        val withDays: Int, val withoutDays: Int, val good: Boolean,
    )

    /** [tagDays]: how many days each habit was logged, for the progress line. */
    data class Result(val findings: List<Finding>, val loggedDays: Int, val tagDays: Map<Journal.Tag, Int>) {
        companion object { val EMPTY = Result(emptyList(), 0, emptyMap()) }
    }

    const val MIN_DAYS = 5
    private const val MIN_T = 1.5
    private const val MAX_FINDINGS = 3
    private const val HRV_SHARE = 0.05
    private const val HEART_BPM = 1.5
    private const val SLEEP_POINTS = 4.0

    /**
     * [tags] by the day they happened; [morning] each metric by the morning it was measured. A day's habit is read
     * against the morning after it, so [today]'s habits have no morning yet and are left out.
     */
    fun analyze(tags: Map<LocalDate, Set<Journal.Tag>>, morning: Map<Metric, Map<LocalDate, Float>>, today: LocalDate): Result {
        val first = tags.filterValues { it.isNotEmpty() }.keys.minOrNull() ?: return Result.EMPTY
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it < today }.toList()
        val perTag = Journal.Tag.entries.associateWith { t -> days.count { t in tags[it].orEmpty() } }
        // Each habit's strongest metric, then the strongest habits first, by the t each was found at.
        val found = Journal.Tag.entries.mapNotNull { tag ->
            Metric.entries.mapNotNull { metric -> finding(tag, metric, days, tags, morning[metric].orEmpty()) }.maxByOrNull { it.second }
        }
        return Result(found.sortedByDescending { it.second }.take(MAX_FINDINGS).map { it.first }, days.size, perTag)
    }

    private fun values(days: List<LocalDate>, morning: Map<LocalDate, Float>, tagged: (LocalDate) -> Boolean, want: Boolean): List<Double> =
        days.filter { tagged(it) == want }.mapNotNull { morning[it.plusDays(1)]?.toDouble() }

    /** The finding with the t it was found at, or null. */
    private fun finding(
        tag: Journal.Tag, metric: Metric, days: List<LocalDate>, tags: Map<LocalDate, Set<Journal.Tag>>, morning: Map<LocalDate, Float>,
    ): Pair<Finding, Double>? {
        val has = { d: LocalDate -> tag in tags[d].orEmpty() }
        val a = values(days, morning, has, true)
        val b = values(days, morning, has, false)
        if (a.size < MIN_DAYS || b.size < MIN_DAYS) return null
        val ma = a.average()
        val mb = b.average()
        val se = sqrt(variance(a, ma) / a.size + variance(b, mb) / b.size)
        if (se <= 0.0) return null
        val diff = ma - mb
        val big = when (metric) {
            Metric.Hrv -> abs(diff) >= HRV_SHARE * mb
            Metric.RestingHr -> abs(diff) >= HEART_BPM
            Metric.SleepScore -> abs(diff) >= SLEEP_POINTS
        }
        val t = abs(diff / se)
        if (!big || t < MIN_T) return null
        val percent = if (mb != 0.0) Math.round(abs(diff) / abs(mb) * 100).toInt() else 0
        val good = if (metric == Metric.RestingHr) diff < 0 else diff > 0
        return Finding(tag, metric, diff.toFloat(), percent, a.size, b.size, good) to t
    }

    private fun variance(xs: List<Double>, mean: Double): Double = xs.sumOf { (it - mean) * (it - mean) } / (xs.size - 1)
}
