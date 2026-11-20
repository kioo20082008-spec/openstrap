package org.openstrap.app.data.selfcheck

import org.openstrap.app.data.ChargeTuning
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * What the self-check remembers and what it makes of it: the incidents it has seen, what each changed, the tuning that
 * follows from them today, and what is still to be told. Pure and immutable: every step takes a [State] and returns the next,
 * so the stored text is the whole truth and each rule can be tested.
 *
 * The learned tuning is not stored. It is worked out from the incidents that were applied ([learned]), so undoing one,
 * resetting, or the days passing is enough to give back the published model.
 */
object SelfLearning {
    /** A correction fades to nothing over this many days after the last day that showed the problem, unless it shows again. */
    const val DECAY_DAYS = 14

    /** Findings of the same check within this many days of an incident are the same incident. */
    const val EPISODE_GAP_DAYS = 3

    /** At most this many notifications in a day. */
    const val MAX_PER_DAY = 2

    /** Incidents kept, the most recent. */
    const val KEEP = 40

    /** The heavy replay runs about this often, and never within [MIN_GAP_SECONDS] of itself. */
    const val RUN_EVERY_SECONDS = 3 * 3600L
    const val MIN_GAP_SECONDS = 30 * 60L

    /** An incident is told only while its last evidence is no older than this many days. */
    const val NOTIFY_FRESH_DAYS = 1L

    /**
     * The check ids of what the AI body learning proposed ([org.openstrap.app.data.ailearn.AiLearning]) start with this:
     * `ai.<knob>` for a proposal that was applied, `ai.no.<knob>` for one that was refused. They are told once a day by
     * the AI learning's own notification, not by [nextNotice].
     */
    const val AI_PREFIX = "ai."

    fun isAi(check: String): Boolean = check.startsWith(AI_PREFIX)

    enum class Status {
        /** Something was changed because of it. */
        Applied,

        /** Only recorded: nothing could be changed, or the finding was too mild to. */
        Informed,

        /** The user took the change back (or reset all); it is not applied again within this episode. */
        Undone,
    }

    /** A knob set to [to] (its published value is [Knob.default]). */
    data class Change(val knob: Knob, val to: Float)

    data class Incident(
        val id: Int,
        val check: String,
        val severity: Severity,
        val first: LocalDate,
        val last: LocalDate,
        /** The numbers behind the latest finding, in the order the check documents. */
        val nums: List<Float>,
        val status: Status,
        val changes: List<Change>,
        val notified: Boolean,
        /** A line of words with the incident: the evidence the AI gave for a proposal ([isAi]); empty for the app's own checks. */
        val note: String = "",
    ) {
        /** Still changing something on [today]: applied, and not yet faded away. */
        fun active(today: LocalDate): Boolean = status == Status.Applied && factor(last, today) > 0f
    }

    data class State(
        val incidents: List<Incident> = emptyList(),
        /** When the heavy run last finished (epoch seconds) and on which day; zero and null before the first. */
        val lastRun: Long = 0,
        val lastRunDay: LocalDate? = null,
        /** The days on which a notification went out, the last few. */
        val notifiedDays: List<LocalDate> = emptyList(),
    ) {
        companion object {
            val Empty = State()
        }
    }

    // ------------------------------------------------------------------------------------------------
    // What follows from the incidents
    // ------------------------------------------------------------------------------------------------

    /** How much of an incident's correction is left [today], last evidence on [last]: 1 the day of it, 0 from [DECAY_DAYS] on. */
    fun factor(last: LocalDate, today: LocalDate): Float {
        val age = ChronoUnit.DAYS.between(last, today).toInt().coerceAtLeast(0)
        return (1f - age.toFloat() / DECAY_DAYS).coerceIn(0f, 1f)
    }

    /** [knob]'s value [today]: the latest applied incident's, faded towards the published value; the default without one. */
    fun value(state: State, knob: Knob, today: LocalDate): Float {
        val winner = state.incidents
            .filter { it.status == Status.Applied && it.changes.any { c -> c.knob == knob } }
            .maxWithOrNull(compareBy<Incident>({ it.last }, { it.id })) ?: return knob.default
        val to = winner.changes.first { it.knob == knob }.to
        return knob.snap(knob.default + (to - knob.default) * factor(winner.last, today))
    }

    /** The self-check part of Energy's tuning [today], every value inside its bounds. */
    fun learned(state: State, today: LocalDate): ChargeTuning =
        Knob.entries.fold(ChargeTuning.Default) { t, k -> k.write(t, value(state, k, today)) }

    // ------------------------------------------------------------------------------------------------
    // Taking in a run
    // ------------------------------------------------------------------------------------------------

    /**
     * [state] after a run that reached [decisions] (see [SelfPlanner.decide]) on [today] at [now]. A finding joins the
     * incident of its check that is within [EPISODE_GAP_DAYS] of its day, or starts one. Only a Medium or High finding with
     * a change applies it; a Low one, or one with nothing to change, is recorded. An undone incident is not applied again.
     */
    fun settle(state: State, decisions: List<Decision>, now: Long, today: LocalDate): State =
        absorb(state, decisions).copy(lastRun = now, lastRunDay = today)

    /**
     * [state] with [decisions] taken in as [settle] does, but without touching the run line: the AI body learning logs its
     * proposals this way, so its pass never counts as the self-check's own.
     */
    fun absorb(state: State, decisions: List<Decision>): State {
        var incidents = state.incidents
        var nextId = (incidents.maxOfOrNull { it.id } ?: 0) + 1
        for (d in decisions.sortedBy { it.finding.day }) {
            val f = d.finding
            val changes = if (f.severity >= Severity.Medium) {
                d.changes.filter { (k, v) -> k.snap(v) != k.default }.map { (k, v) -> Change(k, k.snap(v)) }.sortedBy { it.knob.ordinal }
            } else emptyList()
            val at = incidents.indexOfLast { it.check == f.check && f.day >= it.first.minusDays(EPISODE_GAP_DAYS.toLong()) && f.day <= it.last.plusDays(EPISODE_GAP_DAYS.toLong()) }
            if (at < 0) {
                incidents = incidents + Incident(
                    nextId++, f.check, f.severity, f.day, f.day, f.nums, if (changes.isEmpty()) Status.Informed else Status.Applied, changes, notified = false,
                    note = f.note,
                )
                continue
            }
            val old = incidents[at]
            val newest = f.day >= old.last
            val merged = old.copy(
                first = minOf(old.first, f.day),
                last = maxOf(old.last, f.day),
                severity = maxOf(old.severity, f.severity),
                nums = if (newest) f.nums else old.nums,
                note = if (newest && f.note.isNotEmpty()) f.note else old.note,
                status = when {
                    old.status == Status.Undone -> Status.Undone
                    changes.isNotEmpty() -> Status.Applied
                    else -> old.status
                },
                changes = if (old.status != Status.Undone && changes.isNotEmpty() && newest) changes else old.changes,
            )
            incidents = incidents.toMutableList().also { it[at] = merged }
        }
        return state.copy(incidents = capAi(incidents).sortedByDescending { it.last }.take(KEEP))
    }

    /** At most this many of what the AI body learning only noted (refused, offered, undone) are kept, the newest, so they never crowd out the self-check's own incidents. */
    const val KEEP_AI_NOTES = 10

    private fun capAi(list: List<Incident>): List<Incident> {
        val notes = list.filter { isAi(it.check) && it.status != Status.Applied }
            .sortedWith(compareByDescending<Incident> { it.last }.thenByDescending { it.id })
        if (notes.size <= KEEP_AI_NOTES) return list
        val drop = notes.drop(KEEP_AI_NOTES).mapTo(HashSet()) { it.id }
        return list.filter { it.id !in drop }
    }

    /** Whether the heavy run is due at [now]: the first, the first of a day, or [RUN_EVERY_SECONDS] after the last; never twice within [MIN_GAP_SECONDS]. */
    fun due(state: State, now: Long, today: LocalDate): Boolean {
        if (state.lastRun == 0L) return true
        val since = now - state.lastRun
        if (since < 0) return true
        if (since < MIN_GAP_SECONDS) return false
        return state.lastRunDay != today || since >= RUN_EVERY_SECONDS
    }

    // ------------------------------------------------------------------------------------------------
    // Telling
    // ------------------------------------------------------------------------------------------------

    /**
     * The one incident to tell about now, or null: not in [quiet] hours, fewer than [MAX_PER_DAY] told [today], not already
     * told, recent, and either Medium or High and applied, or High and only recorded (nothing could be changed, but the user
     * should know). The most severe, then the most recent, comes first.
     */
    fun nextNotice(state: State, today: LocalDate, quiet: Boolean): Incident? {
        if (quiet || state.notifiedDays.count { it == today } >= MAX_PER_DAY) return null
        return state.incidents
            .filter { !it.notified && !isAi(it.check) && it.last >= today.minusDays(NOTIFY_FRESH_DAYS) && worthTelling(it) }
            .maxWithOrNull(compareBy<Incident>({ it.severity }, { it.last }, { it.id }))
    }

    private fun worthTelling(i: Incident): Boolean = when (i.status) {
        Status.Applied -> i.severity >= Severity.Medium
        Status.Informed -> i.severity == Severity.High
        Status.Undone -> false
    }

    /** [state] once incident [id] was told on [today]. */
    fun told(state: State, id: Int, today: LocalDate): State = state.copy(
        incidents = state.incidents.map { if (it.id == id) it.copy(notified = true) else it },
        notifiedDays = (state.notifiedDays + today).takeLast(MAX_PER_DAY * 2),
    )

    // ------------------------------------------------------------------------------------------------
    // The user's hand
    // ------------------------------------------------------------------------------------------------

    /** Takes incident [id]'s change back. It stays listed, and is not applied again within its episode. */
    fun undo(state: State, id: Int): State =
        state.copy(incidents = state.incidents.map { if (it.id == id && it.status == Status.Applied) it.copy(status = Status.Undone) else it })

    /** Takes back every change at once; the incidents stay listed as undone. */
    fun resetAll(state: State): State =
        state.copy(incidents = state.incidents.map { if (it.status == Status.Applied) it.copy(status = Status.Undone) else it })

    /** Takes back every AI-proposed change that was last applied on [day] (the notification's Undo); they stay listed as undone. */
    fun undoAiOn(state: State, day: LocalDate): State = state.copy(
        incidents = state.incidents.map { if (isAi(it.check) && it.status == Status.Applied && it.last == day) it.copy(status = Status.Undone) else it },
    )

    /** Removes everything the AI proposed, applied or not: "clear what it learned" leaves the published model and no trace of it. */
    fun clearAi(state: State): State = state.copy(incidents = state.incidents.filterNot { isAi(it.check) })

    /** Removes incident [id] from the list; one still changing something is undone instead, so it stays until the evidence moves on. */
    fun dismiss(state: State, id: Int, today: LocalDate): State {
        val i = state.incidents.firstOrNull { it.id == id } ?: return state
        return if (i.active(today)) undo(state, id) else state.copy(incidents = state.incidents.filter { it.id != id })
    }

    // ------------------------------------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------------------------------------

    private const val VERSION = "v1"
    private const val SEP = '|'

    /** The text kept in settings: a version line, the run line, the told days, then one line per incident. Bounded by [KEEP]. */
    fun encode(state: State): String = buildString {
        append(VERSION).append('\n')
        append("run").append(SEP).append(state.lastRun).append(SEP).append(state.lastRunDay ?: "-").append('\n')
        append("told").append(SEP).append(state.notifiedDays.joinToString(",")).append('\n')
        for (i in state.incidents) {
            append("inc").append(SEP).append(i.id).append(SEP).append(i.check).append(SEP).append(i.severity.name).append(SEP)
            append(i.first).append(SEP).append(i.last).append(SEP).append(i.status.name).append(SEP).append(if (i.notified) 1 else 0).append(SEP)
            append(i.nums.joinToString(",")).append(SEP)
            append(i.changes.joinToString(",") { "${it.knob.key}=${it.to}" }).append(SEP).append(oneLine(i.note)).append('\n')
        }
    }

    /** [text] on one line with no separator in it, so it fits a stored line. */
    private fun oneLine(text: String): String = text.map { if (it == SEP || it == '\n' || it == '\r') ' ' else it }.joinToString("").trim()

    /** The state [encode] wrote; empty for nothing, another version or text that makes no sense. A bad line is skipped. */
    fun decode(raw: String?): State {
        if (raw.isNullOrBlank()) return State.Empty
        val lines = raw.lines()
        if (lines.firstOrNull() != VERSION) return State.Empty
        var state = State.Empty
        val incidents = ArrayList<Incident>()
        for (line in lines.drop(1)) {
            val p = line.split(SEP)
            try {
                when (p[0]) {
                    "run" -> state = state.copy(lastRun = p[1].toLong(), lastRunDay = p.getOrNull(2)?.takeIf { it != "-" }?.let(LocalDate::parse))
                    "told" -> state = state.copy(notifiedDays = p.getOrNull(1).orEmpty().split(',').filter { it.isNotEmpty() }.map(LocalDate::parse))
                    "inc" -> incidents += Incident(
                        id = p[1].toInt(), check = p[2], severity = Severity.valueOf(p[3]), first = LocalDate.parse(p[4]), last = LocalDate.parse(p[5]),
                        status = Status.valueOf(p[6]), notified = p[7] == "1",
                        nums = p[8].split(',').mapNotNull { it.toFloatOrNull() },
                        changes = p.getOrNull(9).orEmpty().split(',').mapNotNull { c ->
                            val kv = c.split('=')
                            val k = kv.getOrNull(0)?.let { Knob.of(it) }
                            val v = kv.getOrNull(1)?.toFloatOrNull()
                            if (k != null && v != null) Change(k, k.snap(v)) else null
                        },
                        note = p.getOrNull(10).orEmpty(),
                    )
                }
            } catch (e: RuntimeException) {
                // A line that does not read is left out; the rest stands.
            }
        }
        return state.copy(incidents = incidents.sortedByDescending { it.last }.take(KEEP))
    }
}
