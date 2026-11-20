package org.openstrap.app.data.ailearn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.Plausibility
import org.openstrap.app.data.selfcheck.Severity
import org.openstrap.app.data.ailearn.ProposalGate.Reason
import org.openstrap.app.data.ailearn.ProposalGate.Verdict

/** What the app makes of the AI's proposals: whitelisted knobs only, clamped, stepped, replayed, and logged either way. */
class ProposalGateTest {
    private val fine = Plausibility { emptyMap() }
    private val now = ChargeTuning.Default

    private fun ask(key: String, to: Double?, evidence: String = "طاقتك منخفضة عند الاستيقاظ خمسة أيام", confidence: Int? = 80) = RawProposal(key, to, evidence, confidence)

    private fun judge(vararg p: RawProposal, learned: ChargeTuning = now, undone: Set<String> = emptySet(), plausibility: Plausibility? = fine, knobs: Boolean = true) =
        ProposalGate.judge(p.toList(), learned, undone, plausibility, knobs)

    @Test fun `a sound proposal is accepted and moves the knob`() {
        val j = judge(ask("rest", 0.9))
        val a = j.accepted.single()
        assertEquals(Knob.Rest, a.knob)
        assertEquals(1f, a.from)
        assertEquals(0.9f, a.to, 0.0001f)
        assertFalse(a.clamped || a.limited)
        assertEquals(0.9f, Knob.Rest.read(j.tuning), 0.0001f)
        assertEquals(80, a.confidence)
    }

    @Test fun `keys that are not a knob are dropped, never reaching a setting`() {
        val j = judge(ask("sleep_score_weight", 0.5), ask("readiness_hrv", 2.0), ask("password", 1.0), ask("", 1.0), ask("rest", 0.9))
        assertEquals(4, j.dropped)
        assertEquals(listOf(Knob.Rest), j.accepted.map { it.knob })
        assertEquals(1, j.verdicts.size)
        val only = judge(ask("sleep_score_weight", 0.5))
        assertEquals(now, only.tuning)
        assertTrue(only.verdicts.isEmpty())
    }

    @Test fun `a value outside the bounds is put inside them`() {
        // The alert's effect is already at its top, so asking for more than that asks for what already is.
        assertEquals(1, judge(ask("alert", 5.0)).unchanged)
        val j = judge(ask("rest", 9.0), learned = Knob.Rest.write(now, 1.4f))
        val a = j.accepted.single()
        assertTrue(a.clamped)
        assertEquals(Knob.Rest.max, a.to, 0.0001f)
        val low = judge(ask("cap", -50.0), learned = Knob.Cap.write(now, Knob.Cap.min + 10f)).accepted.single()
        assertEquals(Knob.Cap.min, low.to, 0.0001f)
        assertTrue(low.clamped)
    }

    @Test fun `every value lands on the knob's grid`() {
        val a = judge(ask("rest", 0.8123)).accepted.single()
        assertEquals(Knob.Rest.snap(0.8123f), a.to, 0.0001f)
        val c = judge(ask("cap", 47.0)).accepted.single()
        assertEquals(0f, c.to % Knob.Cap.quantum.toFloat(), 0.0001f)
    }

    @Test fun `a knob moves at most a quarter of its range in a day`() {
        val most = (Knob.Rest.max - Knob.Rest.min) * ProposalGate.DAILY_STEP
        val a = judge(ask("rest", 0.5)).accepted.single()
        assertTrue(a.limited)
        assertEquals(Knob.Rest.snap(1f - most), a.to, 0.0001f)
        assertEquals(1f - most, a.to, 0.05f)
        // From where it stands today: the step is counted from the faded value, not from the published one.
        val again = judge(ask("rest", 0.5), learned = Knob.Rest.write(now, a.to)).accepted.single()
        assertEquals(Knob.Rest.snap(a.to - most), again.to, 0.0001f)
    }

    @Test fun `a proposal for what already is changes nothing and is counted`() {
        val j = judge(ask("rest", 1.0), ask("alert", 1.0))
        assertEquals(0, j.accepted.size)
        assertEquals(2, j.unchanged)
        assertEquals(now, j.tuning)
    }

    @Test fun `no evidence, little confidence or no number is refused with its reason`() {
        val j = judge(ask("rest", 0.9, evidence = "نعم"), ask("active", 0.9, confidence = 40), ask("cap", null), ask("rhr", 2.0, confidence = null))
        val reasons = j.refused.associate { it.key to it.reason }
        assertEquals(Reason.NoEvidence, reasons["rest"])
        assertEquals(Reason.LowConfidence, reasons["active"])
        assertEquals(Reason.NotANumber, reasons["cap"])
        assertEquals(Reason.LowConfidence, reasons["rhr"])
        assertEquals(now, j.tuning)
    }

    @Test fun `an infinite or not-a-number value is no number`() {
        val j = judge(ask("rest", Double.NaN), ask("active", Double.POSITIVE_INFINITY))
        assertEquals(listOf(Reason.NotANumber, Reason.NotANumber), j.refused.map { it.reason })
    }

    @Test fun `a change the person took back is not asked for again in the same episode`() {
        val j = judge(ask("rest", 0.9), undone = setOf("ai.rest"))
        assertEquals(Reason.Undone, j.refused.single().reason)
        assertEquals(now, j.tuning)
    }

    @Test fun `a proposal that makes the replayed days less plausible is refused`() {
        val worse = Plausibility { t -> if (Knob.Rest.read(t) < 1f) mapOf("2026-11-16|energy.stuck_empty" to Severity.Medium) else emptyMap() }
        val j = judge(ask("rest", 0.8), ask("active", 1.2), plausibility = worse)
        assertEquals(Reason.Worse, j.refused.single().reason)
        assertEquals("rest", j.refused.single().key)
        assertEquals(listOf(Knob.Active), j.accepted.map { it.knob })
        assertEquals(1f, Knob.Rest.read(j.tuning), 0.0001f)
    }

    @Test fun `a finding that gets more severe, or a new one, counts as worse, a finding that goes away does not`() {
        val before = mapOf("a|x" to Severity.Medium)
        assertTrue(Plausibility.worse(before, mapOf("a|x" to Severity.High)))
        assertTrue(Plausibility.worse(before, mapOf("a|x" to Severity.Medium, "b|y" to Severity.Low)))
        assertFalse(Plausibility.worse(before, mapOf("a|x" to Severity.Low)))
        assertFalse(Plausibility.worse(before, emptyMap()))
        assertFalse(Plausibility.worse(before, before))
    }

    @Test fun `a proposal that makes the days more plausible passes`() {
        val flawed = Plausibility { t -> if (Knob.Rest.read(t) >= 1f) mapOf("2026-11-16|energy.stuck_empty" to Severity.Medium) else emptyMap() }
        assertEquals(Knob.Rest, judge(ask("rest", 0.8), plausibility = flawed).accepted.single().knob)
    }

    @Test fun `without a replay no knob is moved`() {
        val j = judge(ask("rest", 0.8), plausibility = null)
        assertEquals(Reason.NoReplay, j.refused.single().reason)
        assertEquals(now, j.tuning)
    }

    @Test fun `each accepted proposal is tried on top of the ones before it`() {
        val seen = ArrayList<ChargeTuning>()
        val spy = Plausibility { t -> seen += t; emptyMap() }
        val j = judge(ask("rest", 0.9), ask("active", 1.1), plausibility = spy)
        assertEquals(2, j.accepted.size)
        assertTrue(seen.any { Knob.Rest.read(it) == 0.9f && Knob.Active.read(it) == 1.1f })
        assertEquals(0.9f, Knob.Rest.read(j.tuning), 0.0001f)
        assertEquals(1.1f, Knob.Active.read(j.tuning), 0.0001f)
    }

    @Test fun `the last proposal for a key wins`() {
        val j = judge(ask("rest", 0.9), ask("rest", 0.8))
        assertEquals(0.8f, j.accepted.single().to, 0.0001f)
        assertEquals(1, j.verdicts.size)
    }

    @Test fun `no more than five proposals are judged`() {
        val keys = Knob.entries.map { it.key } + ProposalGate.SLEEP_NEED_KEY
        val j = judge(*keys.map { ask(it, if (it == "cap") 40.0 else 0.8) }.toTypedArray(), learned = now)
        assertTrue(j.verdicts.size <= ProposalGate.MAX_PROPOSALS)
    }

    @Test fun `the sleep need is only ever offered, on a grid and inside its range`() {
        val j = judge(ask(ProposalGate.SLEEP_NEED_KEY, 453.0))
        val need = j.sleepNeed!!
        assertEquals(450, need.minutes)
        assertEquals(now, j.tuning)
        assertTrue(j.accepted.isEmpty())
        assertEquals(Recovery.SLEEP_NEED_MAX, judge(ask(ProposalGate.SLEEP_NEED_KEY, 2_000.0)).sleepNeed!!.minutes)
        assertEquals(Recovery.SLEEP_NEED_MIN, judge(ask(ProposalGate.SLEEP_NEED_KEY, 10.0)).sleepNeed!!.minutes)
    }

    @Test fun `a sleep need with no evidence or confidence is refused`() {
        assertEquals(Reason.NoEvidence, judge(ask(ProposalGate.SLEEP_NEED_KEY, 450.0, evidence = "")).refused.single().reason)
        assertEquals(Reason.LowConfidence, judge(ask(ProposalGate.SLEEP_NEED_KEY, 450.0, confidence = 10)).refused.single().reason)
        assertNull(judge(ask(ProposalGate.SLEEP_NEED_KEY, 450.0, confidence = 10)).sleepNeed)
    }

    @Test fun `with the self-check off no knob is asked for but the sleep need still is offered`() {
        val j = judge(ask("rest", 0.8), ask(ProposalGate.SLEEP_NEED_KEY, 480.0), knobs = false)
        assertEquals(1, j.dropped)
        assertTrue(j.accepted.isEmpty())
        assertNotNull(j.sleepNeed)
    }

    @Test fun `a refusal records what was asked and where the knob stood`() {
        val r = judge(ask("rest", 0.3, confidence = 10), learned = Knob.Rest.write(now, 0.9f)).refused.single()
        assertEquals(Knob.Rest, r.knob)
        assertEquals(0.3f, r.proposed!!, 0.0001f)
        assertEquals(0.9f, r.current!!, 0.0001f)
    }
}
