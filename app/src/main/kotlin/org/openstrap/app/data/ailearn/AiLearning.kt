package org.openstrap.app.data.ailearn

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openstrap.app.data.ai.AiFailure
import org.openstrap.app.data.selfcheck.Decision
import org.openstrap.app.data.selfcheck.Domain
import org.openstrap.app.data.selfcheck.Finding
import org.openstrap.app.data.selfcheck.Plan
import org.openstrap.app.data.selfcheck.Plausibility
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.data.selfcheck.Severity
import java.time.LocalDate

/**
 * What the AI body learning remembers about its own runs: when it last learned, how it last failed and how many times today,
 * what it learned, what it has told the person, and the sleep need it offered. One small JSON text in settings
 * (`ai_learn_state`); the profile is kept apart (`ai_learn_profile`) and the proposals it made are incidents in the
 * self-check's state, so Undo and Remove work as they do for the app's own findings.
 */
data class AiLearnState(
    /** The day, and the moment (epoch seconds), of the last run that learned something. */
    val lastOkDay: LocalDate? = null,
    val lastOkAt: Long = 0,
    /** The last attempt, its day, how many automatic attempts that day made, and why it failed (null when it didn't). */
    val tryDay: LocalDate? = null,
    val tries: Int = 0,
    val lastTryAt: Long = 0,
    val failure: AiFailure? = null,
    val failureCode: Int? = null,
    /** What the last good run said it learned ("ما تعلّمته اليوم"), on [learnedDay]. */
    val learned: List<String> = emptyList(),
    val learnedDay: LocalDate? = null,
    /** How many knob changes the last good run applied and how many proposals it refused. */
    val changed: Int = 0,
    val refused: Int = 0,
    /** The day the notification went out (or was skipped because the person asked for the run). */
    val toldDay: LocalDate? = null,
    /** The sleep need the AI offered, minutes, why, and the day. The person's own setting is never changed by it. */
    val sleepNeedMin: Int? = null,
    val sleepNeedWhy: String = "",
    val sleepNeedDay: LocalDate? = null,
    /** What the last request was made of: its size in bytes and the rows of history in it. */
    val sentBytes: Int = 0,
    val sentRows: Int = 0,
) {
    companion object {
        val Empty = AiLearnState()
        private const val TEXT_MAX = 200
        private const val LINES_MAX = 3

        fun encode(s: AiLearnState): String = JSONObject().apply {
            put("v", 1)
            s.lastOkDay?.let { put("ok_day", it.toString()) }
            put("ok_at", s.lastOkAt)
            s.tryDay?.let { put("try_day", it.toString()) }
            put("tries", s.tries)
            put("try_at", s.lastTryAt)
            s.failure?.let { put("fail", it.name) }
            s.failureCode?.let { put("code", it) }
            put("learned", JSONArray(s.learned.take(LINES_MAX)))
            s.learnedDay?.let { put("learned_day", it.toString()) }
            put("changed", s.changed)
            put("refused", s.refused)
            s.toldDay?.let { put("told_day", it.toString()) }
            s.sleepNeedMin?.let { put("need", it) }
            if (s.sleepNeedWhy.isNotEmpty()) put("need_why", s.sleepNeedWhy)
            s.sleepNeedDay?.let { put("need_day", it.toString()) }
            put("bytes", s.sentBytes)
            put("rows", s.sentRows)
        }.toString()

        /** The state [encode] wrote; empty for nothing or text that makes no sense, and a field that does not read is left at its default. */
        fun decode(raw: String?): AiLearnState {
            if (raw.isNullOrBlank()) return Empty
            val o = try { JSONObject(raw) } catch (e: JSONException) { return Empty }
            fun day(key: String): LocalDate? = (o.opt(key) as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            return AiLearnState(
                lastOkDay = day("ok_day"),
                lastOkAt = o.optLong("ok_at", 0L),
                tryDay = day("try_day"),
                tries = o.optInt("tries", 0).coerceIn(0, AiLearning.MAX_TRIES * 4),
                lastTryAt = o.optLong("try_at", 0L),
                failure = (o.opt("fail") as? String)?.let { name -> AiFailure.entries.firstOrNull { it.name == name } },
                failureCode = (o.opt("code") as? Number)?.toInt(),
                learned = o.optJSONArray("learned")?.let { a -> (0 until a.length()).mapNotNull { (a.opt(it) as? String)?.take(TEXT_MAX) } }.orEmpty().take(LINES_MAX),
                learnedDay = day("learned_day"),
                changed = o.optInt("changed", 0).coerceAtLeast(0),
                refused = o.optInt("refused", 0).coerceAtLeast(0),
                toldDay = day("told_day"),
                sleepNeedMin = (o.opt("need") as? Number)?.toInt()?.takeIf { it in BodyProfile.NEED_MIN..BodyProfile.NEED_MAX },
                sleepNeedWhy = (o.opt("need_why") as? String).orEmpty().take(TEXT_MAX),
                sleepNeedDay = day("need_day"),
                sentBytes = o.optInt("bytes", 0).coerceAtLeast(0),
                sentRows = o.optInt("rows", 0).coerceAtLeast(0),
            )
        }
    }
}

/**
 * The AI body learning's rules (docs/algorithms/self-check.md): when a day's run is due, what a run leaves behind, and what is
 * told. Pure; [AiLearningKeeper] does the reading, asking and writing around it.
 */
object AiLearning {
    /** At most this many automatic attempts a day; a person's tap is not counted. */
    const val MAX_TRIES = 3

    /** How long to wait after a failure of each kind before the next automatic attempt, seconds. */
    private const val MINUTE = 60L
    private const val HOUR = 3600L

    enum class Gate {
        /** Ask now. */
        Go,

        /** Already learned today: once a day. */
        Done,

        /** The strap has not synced today, so there is nothing new to learn from. */
        WaitSync,

        /** A failure is still being waited out. */
        Wait,

        /** [MAX_TRIES] attempts have been made today. */
        Spent,
    }

    /** How long after a failed attempt the next automatic one may be made. */
    fun retryAfter(failure: AiFailure?): Long = when (failure) {
        null -> 0L
        AiFailure.Offline -> 30 * MINUTE
        AiFailure.RateLimit -> 2 * HOUR
        // A key that is refused or an empty balance does not fix itself within the hour.
        AiFailure.BadKey, AiFailure.NoBalance, AiFailure.NoKey, AiFailure.Disabled -> 6 * HOUR
        else -> HOUR
    }

    /**
     * Whether the automatic run is due at [now] on [today]: once a day (a day that already learned is [Gate.Done]), only after
     * the strap has synced that day ([syncedToday]), at most [MAX_TRIES] attempts a day and a wait after each failure
     * ([retryAfter]). A new day starts clean.
     */
    fun gate(state: AiLearnState, now: Long, today: LocalDate, syncedToday: Boolean): Gate {
        if (state.lastOkDay == today) return Gate.Done
        if (!syncedToday) return Gate.WaitSync
        if (state.tryDay != today) return Gate.Go
        if (state.tries >= MAX_TRIES) return Gate.Spent
        val waited = now - state.lastTryAt
        if (state.failure != null && waited >= 0 && waited < retryAfter(state.failure)) return Gate.Wait
        return Gate.Go
    }

    /** The automatic tries made on [today] once an attempt is made: one more, or the same for a person's tap ([manual]); a new day starts at none. */
    private fun triesAfter(state: AiLearnState, today: LocalDate, manual: Boolean): Int {
        val before = if (state.tryDay == today) state.tries else 0
        return if (manual) before else before + 1
    }

    /** [state] after an attempt that failed with [failure] ([code] is the HTTP status when there was one); a person's tap ([manual]) is not one of the day's automatic tries. */
    fun failed(state: AiLearnState, failure: AiFailure, code: Int?, now: Long, today: LocalDate, manual: Boolean): AiLearnState = state.copy(
        tryDay = today,
        tries = triesAfter(state, today, manual),
        lastTryAt = now,
        failure = failure,
        failureCode = code,
    )

    /** What a run that read an answer leaves behind. */
    class Finished(
        val profile: Profile,
        val state: AiLearnState,
        val selfCheck: SelfLearning.State,
        val judged: ProposalGate.Judged,
    )

    /**
     * Takes in [answer]: the [old] profile merged with the one it wrote (from [dataFrom], the first day of data sent), its
     * proposals judged ([knobs] says whether the self-check is on) and logged as incidents in [self], and the run noted in
     * [state]. [manual] runs are not told later: the person is looking at the page. Nothing is written here.
     */
    fun finish(
        answer: LearnAnswer,
        old: Profile?,
        dataFrom: LocalDate?,
        today: LocalDate,
        now: Long,
        state: AiLearnState,
        self: SelfLearning.State,
        plausibility: Plausibility?,
        knobs: Boolean,
        manual: Boolean,
        sentBytes: Int,
        sentRows: Int,
    ): Finished {
        val profile = BodyProfile.merge(old, answer.profile, today, dataFrom)
        val judged = ProposalGate.judge(answer.proposals, SelfLearning.learned(self, today), undone(self, today), plausibility, knobs)
        val next = SelfLearning.absorb(self, decisions(judged, today))
        val need = judged.sleepNeed
        val after = state.copy(
            lastOkDay = today,
            lastOkAt = now,
            tryDay = today,
            tries = triesAfter(state, today, manual),
            lastTryAt = now,
            failure = null,
            failureCode = null,
            learned = answer.learned,
            learnedDay = today,
            changed = judged.accepted.size,
            refused = judged.refused.size,
            toldDay = if (manual) today else state.toldDay,
            sleepNeedMin = need?.minutes ?: state.sleepNeedMin,
            sleepNeedWhy = need?.evidence ?: state.sleepNeedWhy,
            sleepNeedDay = if (need != null) today else state.sleepNeedDay,
            sentBytes = sentBytes,
            sentRows = sentRows,
        )
        return Finished(profile, after, next, judged)
    }

    /** The AI check ids that are still undone in [self]'s running episode: a proposal for one is refused, as the self-check does not apply an undone finding again. */
    fun undone(self: SelfLearning.State, today: LocalDate): Set<String> =
        self.incidents
            .filter { SelfLearning.isAi(it.check) && it.status == SelfLearning.Status.Undone && today <= it.last.plusDays(SelfLearning.EPISODE_GAP_DAYS.toLong()) }
            .mapTo(HashSet()) { it.check }

    /**
     * The incidents a judgement leaves: `ai.<knob>` (Medium, with the change) for each accepted one, `ai.no.<key>` (Low, nothing
     * changed) for each refused, and `ai.sleep_need` for an offered sleep need. [Decision]s for [SelfLearning.absorb]; the
     * evidence rides with them as the incident's note. The numbers, in order: accepted: from, to, limited, clamped; refused:
     * the reason's number, what was asked, where it stood (-1 when unknown); sleep need: the minutes.
     */
    fun decisions(judged: ProposalGate.Judged, today: LocalDate): List<Decision> = judged.verdicts.map { v ->
        when (v) {
            is ProposalGate.Verdict.Accepted -> Decision(
                Finding(
                    "ai.${v.knob.key}", Domain.Energy, Severity.Medium, today,
                    listOf(v.from, v.to, if (v.limited) 1f else 0f, if (v.clamped) 1f else 0f), Plan.Inform, v.evidence,
                ),
                mapOf(v.knob to v.to),
            )
            is ProposalGate.Verdict.Refused -> Decision(
                Finding(
                    "ai.no.${v.key}", Domain.Energy, Severity.Low, today,
                    listOf(v.reason.ordinal.toFloat(), v.proposed ?: -1f, v.current ?: -1f), Plan.Inform, v.evidence,
                ),
            )
            is ProposalGate.Verdict.SleepNeed -> Decision(
                Finding("ai.${ProposalGate.SLEEP_NEED_KEY}", Domain.Sleep, Severity.Low, today, listOf(v.minutes.toFloat()), Plan.Inform, v.evidence),
            )
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Telling
    // ------------------------------------------------------------------------------------------------

    /** What the notification says: what was learned and the changes it made (the AI incidents applied today, which Undo takes back). */
    data class Notice(val learned: List<String>, val applied: List<SelfLearning.Incident>)

    /**
     * The one notification of the day, or null: only for the run that learned today, only if it learned something or changed
     * something, never in [quiet] hours (it waits for the next look, and goes out the same day or not at all), and never twice
     * in a day.
     */
    fun notice(state: AiLearnState, self: SelfLearning.State, today: LocalDate, quiet: Boolean): Notice? {
        if (quiet || state.lastOkDay != today || state.learnedDay != today || state.toldDay == today) return null
        val applied = self.incidents.filter { SelfLearning.isAi(it.check) && it.status == SelfLearning.Status.Applied && it.last == today }
        if (state.learned.isEmpty() && applied.isEmpty()) return null
        return Notice(state.learned, applied)
    }

    /** [state] once the day's notification went out. */
    fun told(state: AiLearnState, today: LocalDate): AiLearnState = state.copy(toldDay = today)

    // ------------------------------------------------------------------------------------------------
    // The person's hand
    // ------------------------------------------------------------------------------------------------

    /** What "clear what it learned" does to the self-check's state: everything the AI proposed goes, applied or not. */
    fun clear(self: SelfLearning.State): SelfLearning.State = SelfLearning.clearAi(self)

    /**
     * The state with nothing learned. Today's automatic tries are spent and nothing is told, so wiping what it learned is not
     * undone by the next look an hour later; the next day it learns again from the whole history.
     */
    fun cleared(today: LocalDate): AiLearnState = AiLearnState.Empty.copy(tryDay = today, tries = MAX_TRIES, toldDay = today)
}
