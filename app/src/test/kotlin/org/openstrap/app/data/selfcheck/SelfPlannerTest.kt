package org.openstrap.app.data.selfcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.selfcheck.SelfCheckFixtures.day
import org.openstrap.app.data.selfcheck.SelfCheckFixtures.run

class SelfPlannerTest {
    private val fx = SelfCheckFixtures
    private val owner = fx.build(fx.ownersDay(), alert = 1)

    private fun tuningOf(values: Map<Knob, Float>) = values.entries.fold(ChargeTuning.Default) { t, (k, v) -> k.write(t, v) }

    private fun replaying(b: SelfCheckFixtures.Built, count: IntArray? = null) = SelfPlanner.Evaluate { v ->
        count?.let { it[0]++ }
        fx.trace(b, tuningOf(v))
    }

    private fun searchFindings(b: SelfCheckFixtures.Built) =
        SelfChecks.inspectAll(Facts(b.date, listOf(fx.trace(b)))).filter { it.plan is Plan.Search }

    @Test fun `the owner's day finds a small change that makes it plausible again`() {
        val findings = searchFindings(owner)
        assertTrue(findings.any { it.check == "energy.stuck_empty" })
        val decisions = SelfPlanner.decide(findings, replaying(owner))
        val changes = decisions.first { it.finding.check == "energy.stuck_empty" }.changes
        assertTrue(changes.isNotEmpty())
        // Every finding of the day is solved together, with the same change.
        assertEquals(1, decisions.map { it.changes }.distinct().size)
        // With the change the day is quiet.
        val after = fx.trace(owner, tuningOf(changes))
        assertEquals(emptyList<String>(), SelfChecks.inspectAll(Facts(owner.date, listOf(after))).map { it.check })
    }

    @Test fun `every value chosen is inside its knob's bounds and on its grid`() {
        val changes = SelfPlanner.decide(searchFindings(owner), replaying(owner)).flatMap { it.changes.entries }
        assertTrue(changes.isNotEmpty())
        for ((k, v) in changes) {
            assertTrue("${k.key}=$v", v in k.min..k.max)
            assertEquals(k.snap(v), v, 0f)
            assertNotEquals(k.default, v)
        }
    }

    @Test fun `nothing smaller than the answer solves the day`() {
        val findings = searchFindings(owner)
        val answer = SelfPlanner.decide(findings, replaying(owner)).first().changes
        fun distance(m: Map<Knob, Float>) = m.entries.sumOf { (k, v) -> k.distance(v).toDouble() }
        val knobs = findings.flatMap { (it.plan as Plan.Search).knobs }.distinct()
        // Brute force over the same grid.
        fun combos(i: Int, so: Map<Knob, Float>): List<Map<Knob, Float>> =
            if (i == knobs.size) listOf(so) else knobs[i].candidates(Direction.Soften).flatMap { combos(i + 1, so + (knobs[i] to it)) }
        val smaller = combos(0, emptyMap()).filter { distance(it) < distance(answer) - 1e-9 && it.any { (k, v) -> v != k.default } }
        for (c in smaller) {
            val ids = SelfChecks.inspectAll(Facts(owner.date, listOf(fx.trace(owner, tuningOf(c))))).map { it.check }
            assertTrue("$c still ${ids}", "energy.stuck_empty" in ids || "energy.drain_fast" in ids || "heart.sitting_as_effort" in ids)
        }
    }

    @Test fun `a healthy day gets no findings and so no change`() {
        val ordinary = fx.build(SelfCheckFixtures.Day(awake = fx.ordinary().awake))
        assertTrue(searchFindings(ordinary).isEmpty())
        assertTrue(SelfPlanner.decide(emptyList(), replaying(ordinary)).isEmpty())
    }

    @Test fun `a finding that cannot be replayed is recorded and changes nothing`() {
        val d = SelfPlanner.decide(searchFindings(owner), null)
        assertTrue(d.isNotEmpty())
        assertTrue(d.all { it.changes.isEmpty() })
    }

    @Test fun `an inform plan changes nothing and a set plan is snapped and bounded`() {
        val inform = Finding("x.y", Domain.Sleep, Severity.High, fx.date, emptyList(), Plan.Inform)
        val set = Finding("x.z", Domain.Energy, Severity.Medium, fx.date, emptyList(), Plan.Set(mapOf(Knob.Alert to 0.52f, Knob.Rest to 1f, Knob.Cap to 500f)))
        val d = SelfPlanner.decide(listOf(inform, set), null)
        assertTrue(d[0].changes.isEmpty())
        assertEquals(mapOf(Knob.Alert to 0.5f, Knob.Cap to 90f).filterKeys { it == Knob.Alert }, d[1].changes)
    }

    @Test fun `the search stops within its budget when nothing works`() {
        val count = intArrayOf(0)
        val stuck = day(run(900, 300, 0f))
        val evaluate = SelfPlanner.Evaluate { count[0]++; stuck }
        val f = EnergyStuckEmpty().inspectDay(stuck)!!
        val d = SelfPlanner.decide(listOf(f), evaluate)
        assertTrue(d.single().changes.isEmpty())
        assertTrue("replays ${count[0]}", count[0] <= SelfPlanner.BUDGET + 1)
        assertTrue(count[0] > 1)
    }

    @Test fun `a change that cures one finding by causing another is not taken`() {
        val bad = day(run(900, 300, 0f))
        val worse = day(run(900, 300, 50f) + run(480, 400, 100f, steps = 15))
        // Any change at all "cures" the empty day but leaves it full for hours of walking: no change may be taken.
        val evaluate = SelfPlanner.Evaluate { v -> if (v.isEmpty()) bad else worse }
        val d = SelfPlanner.decide(listOf(EnergyStuckEmpty().inspectDay(bad)!!), evaluate)
        assertTrue(d.single().changes.isEmpty())
    }

    @Test fun `the smallest of two working changes wins`() {
        val bad = day(run(900, 300, 0f))
        val fine = day(run(900, 300, 50f))
        // Only moving the cap to 60 or lower, or the resting heart rate by 8, or the active scale to 0.6, works.
        val evaluate = SelfPlanner.Evaluate { v ->
            val cap = v[Knob.Cap] ?: 90f
            val rhr = v[Knob.Resting] ?: 0f
            val active = v[Knob.Active] ?: 1f
            if (cap <= 60f || rhr >= 8f || active <= 0.6f) fine else bad
        }
        val d = SelfPlanner.decide(listOf(EnergyStuckEmpty().inspectDay(bad)!!), evaluate).single()
        assertEquals(mapOf(Knob.Cap to 60f), d.changes)
    }

    @Test fun `strengthening the Alert changes nothing, it is already the published strength`() {
        val bad = day(run(480, 400, 100f, steps = 15))
        val f = Finding("energy.stuck_full", Domain.Energy, Severity.Medium, fx.date, emptyList(), Plan.Search(listOf(Knob.Alert), Direction.Strengthen))
        val d = SelfPlanner.decide(listOf(f), SelfPlanner.Evaluate { bad })
        assertTrue(d.single().changes.isEmpty())
    }
}
