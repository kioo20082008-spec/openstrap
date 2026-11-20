package org.openstrap.app.data.selfcheck

import org.openstrap.app.data.ChargeInputs
import org.openstrap.app.data.ChargeTuning
import java.time.LocalDate

/**
 * The last days' Energy, ready to be run again under any tuning: the inputs [SelfCheckRunner] replays and the false steps
 * left out of each day. Pure: [traces] runs the in-memory Energy model, a few milliseconds a day, so a proposal can be tried
 * on the days that were actually lived before it is believed (docs/algorithms/self-check.md, AI body learning).
 */
class Replays(
    private val inputs: Map<LocalDate, ChargeInputs>,
    private val falseSteps: Map<LocalDate, Int> = emptyMap(),
    private val today: LocalDate,
) {
    /** How many days can be replayed. */
    val days: Int get() = inputs.size

    /** Each replayed day as the checks read it under [tuning], oldest first. */
    fun traces(tuning: ChargeTuning): List<DayTrace> =
        inputs.entries.sortedBy { it.key }.map { (date, charge) -> SelfFacts.trace(charge, tuning, date, falseSteps[date] ?: 0, partial = date == today) }
}

/**
 * What the self-check's own checks find on the replayed days when the self-check part of Energy's tuning is [learned]:
 * `"<day>|<check id>"` to the severity it fired with. A tuning is **less plausible** than another when it has a finding the
 * other does not, or one at a higher severity ([worse]).
 */
fun interface Plausibility {
    fun findings(learned: ChargeTuning): Map<String, Severity>

    companion object {
        /** Whether [after] has a finding [before] has not, or a more severe one. */
        fun worse(before: Map<String, Severity>, after: Map<String, Severity>): Boolean =
            after.any { (key, severity) -> before[key]?.let { severity > it } ?: true }
    }
}

/**
 * [Plausibility] over [replays]: the days run again under [base] (the morning check-ins' tuning) with the self-check part set
 * to what is asked, and every one of [checks] asked of each day (the checks whose answer depends on the knobs, as the planner
 * uses them). A check that throws is skipped, as everywhere in the self-check.
 */
class ReplayPlausibility(
    private val replays: Replays,
    private val base: ChargeTuning,
    private val checks: List<DayCheck> = TunableEnergyChecks,
) : Plausibility {
    override fun findings(learned: ChargeTuning): Map<String, Severity> {
        val out = LinkedHashMap<String, Severity>()
        for (trace in replays.traces(base.withLearned(learned))) {
            for (check in checks) {
                val finding = try { check.inspectDay(trace) } catch (e: RuntimeException) { null }
                if (finding != null) out["${trace.date}|${check.id}"] = finding.severity
            }
        }
        return out
    }
}
