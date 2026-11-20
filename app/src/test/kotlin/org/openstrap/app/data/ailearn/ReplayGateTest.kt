package org.openstrap.app.data.ailearn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.Plausibility
import org.openstrap.app.data.selfcheck.ReplayPlausibility
import org.openstrap.app.data.selfcheck.Replays
import org.openstrap.app.data.selfcheck.SelfCheckFixtures

/** The proposal gate against real replayed days: the Energy model run again under the tuning asked for, and the self-check's own checks read the result. */
class ReplayGateTest {
    private fun plausibility(day: SelfCheckFixtures.Day): Pair<Plausibility, SelfCheckFixtures.Built> {
        val built = SelfCheckFixtures.build(day)
        return ReplayPlausibility(Replays(mapOf(built.date to built.inputs), mapOf(built.date to 0), built.date), ChargeTuning.Default) to built
    }

    private fun ask(key: String, to: Double) = RawProposal(key, to, "طاقته لا تناسب أيامه الأخيرة", 85)

    @Test fun `asking for more drain on a day that already drains fast is refused as worse`() {
        val (p, _) = plausibility(SelfCheckFixtures.ownersDay())
        val j = ProposalGate.judge(listOf(ask("active", 1.4)), ChargeTuning.Default, emptySet(), p)
        assertEquals(ProposalGate.Reason.Worse, j.refused.single().reason)
        assertEquals(ChargeTuning.Default, j.tuning)
    }

    @Test fun `asking for a shorter effort cap on the owner's day is accepted, as the day then reads right`() {
        val (p, _) = plausibility(SelfCheckFixtures.ownersDay())
        val before = p.findings(ChargeTuning.Default)
        val j = ProposalGate.judge(listOf(ask("cap", 30.0)), ChargeTuning.Default, emptySet(), p)
        val a = j.accepted.single()
        assertEquals(Knob.Cap, a.knob)
        // The daily step holds it to a quarter of the range from where it stands.
        assertTrue(a.limited)
        assertFalse(Plausibility.worse(before, p.findings(j.tuning)))
    }

    @Test fun `an ordinary day takes a small change and stays plausible`() {
        val (p, _) = plausibility(SelfCheckFixtures.Day(awake = SelfCheckFixtures.ordinary().awake))
        val j = ProposalGate.judge(listOf(ask("rest", 0.9), ask("active", 1.1)), ChargeTuning.Default, emptySet(), p)
        assertEquals(2, j.accepted.size)
        assertTrue(p.findings(j.tuning).isEmpty())
    }

    @Test fun `trying a tuning leaves nothing behind`() {
        val (p, built) = plausibility(SelfCheckFixtures.ownersDay())
        val before = p.findings(ChargeTuning.Default)
        p.findings(Knob.Cap.write(ChargeTuning.Default, 30f))
        p.findings(Knob.Active.write(ChargeTuning.Default, 1.4f))
        assertEquals(before, p.findings(ChargeTuning.Default))
        assertEquals(1, Replays(mapOf(built.date to built.inputs), mapOf(built.date to 0), built.date).days)
    }
}
