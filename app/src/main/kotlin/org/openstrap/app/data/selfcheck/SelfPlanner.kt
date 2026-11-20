package org.openstrap.app.data.selfcheck

/** A finding and what is to be changed because of it: nothing ([changes] empty) when it is only recorded. */
data class Decision(val finding: Finding, val changes: Map<Knob, Float> = emptyMap())

/**
 * Turns a day's findings into changes. Findings with a [Plan.Search] are solved together: of every setting of the knobs they
 * name (each tried at its default, halfway and the end the direction points at), the one that makes all of them go quiet
 * with the smallest total change, and without a new finding appearing. That is the least-change idea of the calibration's
 * ridge penalty, without a fit: the published model is the prior and moves only as far as the day demands. The search is
 * bounded by [BUDGET] replays. Pure: [evaluate] replays the day.
 */
object SelfPlanner {
    /** The most replays one search may spend: 3 values for each of 5 knobs is 243, the cheapest [BUDGET] of them are tried. */
    const val BUDGET = 160

    /** Replays the finding's day with the given knob values (the others at their defaults); null when it cannot be replayed. */
    fun interface Evaluate {
        fun on(values: Map<Knob, Float>): DayTrace?
    }

    /** [Decision]s for the findings of one day, in the order given. [evaluate] is null when the day cannot be replayed. */
    fun decide(findings: List<Finding>, evaluate: Evaluate?, checks: List<DayCheck> = TunableEnergyChecks): List<Decision> {
        val solved = HashMap<Direction, Map<Knob, Float>>()
        findings.filter { it.plan is Plan.Search }.groupBy { (it.plan as Plan.Search).direction }.forEach { (direction, group) ->
            solved[direction] = if (evaluate == null) emptyMap() else search(group, direction, evaluate, checks)
        }
        return findings.map { f ->
            when (val plan = f.plan) {
                Plan.Inform -> Decision(f)
                is Plan.Set -> Decision(f, plan.to.mapValues { (k, v) -> k.snap(v) }.filter { (k, v) -> v != k.default })
                is Plan.Search -> Decision(f, solved[plan.direction].orEmpty().filterKeys { it in plan.knobs })
            }
        }
    }

    private fun search(group: List<Finding>, direction: Direction, evaluate: Evaluate, checks: List<DayCheck>): Map<Knob, Float> {
        val knobs = group.flatMap { (it.plan as Plan.Search).knobs }.distinct()
        val before = evaluate.on(emptyMap())?.let { firing(it, checks) } ?: return emptyMap()
        val targets = group.map { it.check }.filter { it in before }.toSet()
        if (targets.isEmpty()) return emptyMap()
        val options = knobs.map { k -> k.candidates(direction) }
        val combos = ArrayList<Map<Knob, Float>>()
        fun grow(i: Int, so: Map<Knob, Float>) {
            if (i == knobs.size) {
                if (so.any { (k, v) -> v != k.default }) combos += so
                return
            }
            for (v in options[i]) grow(i + 1, so + (knobs[i] to v))
        }
        grow(0, emptyMap())
        // The smallest change first (the share of each knob's range it moves), then the fewest knobs moved.
        val ordered = combos.sortedWith(compareBy({ c -> c.entries.sumOf { (k, v) -> k.distance(v).toDouble() } }, { c -> c.count { (k, v) -> v != k.default } }))
        for (c in ordered.take(BUDGET)) {
            val now = evaluate.on(c)?.let { firing(it, checks) } ?: continue
            if (now.none { it in targets } && before.containsAll(now)) return c.filter { (k, v) -> v != k.default }
        }
        return emptyMap()
    }

    private fun firing(trace: DayTrace, checks: List<DayCheck>): Set<String> =
        checks.filter { it.inspectDay(trace) != null }.mapTo(HashSet()) { it.id }
}
