package org.openstrap.app.data.ailearn

import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.Plausibility
import org.openstrap.app.data.selfcheck.Severity
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The app's safety over what the AI proposes (docs/algorithms/self-check.md, AI body learning): the AI never changes anything
 * itself, it only asks, and every request passes these rules or is refused and logged.
 *
 * Only the knobs the self-check owns ([Knob]) may be asked for, plus the sleep need, which is only ever *offered* to the
 * person ([Verdict.SleepNeed]) because it feeds the sleep score and Readiness, which the AI never edits. A key that is on
 * neither list is dropped. For each knob the proposal must carry evidence and enough confidence, the user must not have undone
 * it in this episode, the value is put inside the knob's bounds and onto its grid, it may move the knob only a quarter of its
 * range a day ([DAILY_STEP]) from where it stands today, and it is tried on the last days' replayed Energy: if the self-check's
 * own checks find the days **less plausible** with it ([Plausibility.worse]), it is refused. Accepted ones are tried one after
 * another, each on top of the ones before it. Pure.
 */
object ProposalGate {
    /** The most a knob may move in a day, as a share of its whole range. */
    const val DAILY_STEP = 0.25f

    /** Fewest points of confidence, and fewest characters of evidence, a proposal needs. */
    const val MIN_CONFIDENCE = 60
    const val MIN_EVIDENCE = 8

    /** Most proposals looked at in one answer. */
    const val MAX_PROPOSALS = 5

    /** The key of the one baseline that is a setting of the person's own, only ever offered. */
    const val SLEEP_NEED_KEY = "sleep_need_min"

    /** The grid the offered sleep need is rounded to, minutes. */
    const val SLEEP_NEED_GRID = 15

    /** Why a proposal was refused. The order is stored (as a number in the incident), so only add at the end. */
    enum class Reason { NoEvidence, LowConfidence, NotANumber, Undone, Worse, NoReplay }

    sealed interface Verdict {
        /** A knob moved from [from] to [to] (what it stood at today and what it is set to); [clamped] when the value asked for was outside the bounds, [limited] when the daily step shortened it. */
        data class Accepted(
            val knob: Knob,
            val from: Float,
            val to: Float,
            val clamped: Boolean,
            val limited: Boolean,
            val evidence: String,
            val confidence: Int,
        ) : Verdict

        /** Refused for [reason]. [knob] is null for the sleep need; [proposed] is what was asked for and [current] where it stood, null when unknown. */
        data class Refused(val key: String, val knob: Knob?, val proposed: Float?, val current: Float?, val reason: Reason, val evidence: String) : Verdict

        /** The sleep need the AI offers, in minutes, for the person to take or leave. */
        data class SleepNeed(val minutes: Int, val evidence: String, val confidence: Int) : Verdict
    }

    /**
     * What came of an answer's proposals: [verdicts] in order, the self-check part of the [tuning] they leave, the keys
     * [dropped] as unknown (or surplus) and the proposals that asked for what already was ([unchanged]).
     */
    data class Judged(val verdicts: List<Verdict>, val tuning: ChargeTuning, val dropped: Int = 0, val unchanged: Int = 0) {
        val accepted: List<Verdict.Accepted> get() = verdicts.filterIsInstance<Verdict.Accepted>()
        val refused: List<Verdict.Refused> get() = verdicts.filterIsInstance<Verdict.Refused>()
        val sleepNeed: Verdict.SleepNeed? get() = verdicts.filterIsInstance<Verdict.SleepNeed>().lastOrNull()
    }

    /**
     * [raw] judged. [learned] is the self-check part of Energy's tuning in force today (what the knobs stand at, faded as they
     * are), [undone] the check ids (`ai.<knob>`) the person took back in the episode that is still running, [plausibility] how
     * the last days replay under a tuning (null when they cannot be replayed: then no knob is moved), and [knobs] whether the
     * self-check is on (when it is off, its tuning is not applied, so no knob is asked for).
     */
    fun judge(
        raw: List<RawProposal>,
        learned: ChargeTuning,
        undone: Set<String>,
        plausibility: Plausibility?,
        knobs: Boolean = true,
    ): Judged {
        // The last of each key wins; keys that are neither a knob nor the sleep need are dropped; only the first few are looked at.
        val byKey = LinkedHashMap<String, RawProposal>()
        var dropped = 0
        for (p in raw) {
            val known = p.key == SLEEP_NEED_KEY || (Knob.of(p.key) != null && knobs)
            if (known) byKey[p.key] = p else dropped++
        }
        val chosen = byKey.values.take(MAX_PROPOSALS)
        dropped += byKey.size - chosen.size
        val verdicts = ArrayList<Verdict>()
        var unchanged = 0
        var tuning = learned
        var before: Map<String, Severity>? = null
        for (p in chosen) {
            if (p.key == SLEEP_NEED_KEY) {
                verdicts += sleepNeed(p)
                continue
            }
            val knob = checkNotNull(Knob.of(p.key))
            val current = knob.read(tuning)
            val to = p.to?.toFloat()?.takeIf { it.isFinite() }
            val refuse = { reason: Reason -> Verdict.Refused(p.key, knob, to, current, reason, p.evidence) }
            when {
                to == null -> verdicts += refuse(Reason.NotANumber)
                p.evidence.length < MIN_EVIDENCE -> verdicts += refuse(Reason.NoEvidence)
                (p.confidence ?: 0) < MIN_CONFIDENCE -> verdicts += refuse(Reason.LowConfidence)
                "ai.${knob.key}" in undone -> verdicts += refuse(Reason.Undone)
                plausibility == null -> verdicts += refuse(Reason.NoReplay)
                else -> {
                    val want = checkNotNull(to)
                    val clamped = want < knob.min || want > knob.max
                    val target = step(knob, knob.snap(want), current)
                    if (target == current) {
                        unchanged++
                        continue
                    }
                    val limited = target != knob.snap(want)
                    val candidate = knob.write(tuning, target)
                    val base = before ?: plausibility.findings(tuning).also { before = it }
                    val after = plausibility.findings(candidate)
                    if (Plausibility.worse(base, after)) {
                        verdicts += refuse(Reason.Worse)
                    } else {
                        tuning = candidate
                        before = after
                        verdicts += Verdict.Accepted(knob, current, target, clamped, limited, p.evidence, p.confidence ?: 0)
                    }
                }
            }
        }
        return Judged(verdicts, tuning, dropped, unchanged)
    }

    /** [wanted] moved from [current] by at most [DAILY_STEP] of the knob's range, on its grid. */
    fun step(knob: Knob, wanted: Float, current: Float): Float {
        val most = (knob.max - knob.min) * DAILY_STEP
        if (abs(wanted - current) <= most) return knob.snap(wanted)
        return knob.snap(if (wanted > current) current + most else current - most)
    }

    private fun sleepNeed(p: RawProposal): Verdict {
        val to = p.to?.takeIf { it.isFinite() }
        val refuse = { reason: Reason -> Verdict.Refused(SLEEP_NEED_KEY, null, to?.toFloat(), null, reason, p.evidence) }
        return when {
            to == null -> refuse(Reason.NotANumber)
            p.evidence.length < MIN_EVIDENCE -> refuse(Reason.NoEvidence)
            (p.confidence ?: 0) < MIN_CONFIDENCE -> refuse(Reason.LowConfidence)
            else -> {
                val grid = (to / SLEEP_NEED_GRID).roundToInt() * SLEEP_NEED_GRID
                Verdict.SleepNeed(grid.coerceIn(Recovery.SLEEP_NEED_MIN, Recovery.SLEEP_NEED_MAX), p.evidence, p.confidence ?: 0)
            }
        }
    }
}
