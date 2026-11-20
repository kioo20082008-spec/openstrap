package org.openstrap.app.data.ailearn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.ai.AiFailure
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.Plausibility
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.data.ailearn.AiLearning.Gate

/** The AI body learning's rules: when a day's run is due, what a run leaves behind, what is told and what the person's hand takes back. */
class AiLearningTest {
    private val today = LEARN_TODAY
    private val noon = 1_800_000_000L
    private val fine = Plausibility { emptyMap() }

    private fun answer(vararg proposals: RawProposal, lines: List<String> = listOf("تعلّمت أن نومك يقصر بعد السهر")) = LearnAnswer(
        learned = lines,
        profile = BodyProfile.fromJson(
            org.json.JSONObject().put("sleep", org.json.JSONArray().put(org.json.JSONObject().put("t", "ينام سبع ساعات ونصف تقريباً").put("c", 80))),
        ),
        proposals = proposals.toList(),
    )

    private fun ask(key: String, to: Double, evidence: String = "طاقته منخفضة عند الاستيقاظ خمسة أيام", confidence: Int = 80) = RawProposal(key, to, evidence, confidence)

    private fun finish(
        a: LearnAnswer, state: AiLearnState = AiLearnState.Empty, self: SelfLearning.State = SelfLearning.State.Empty, old: Profile? = null,
        day: java.time.LocalDate = today, now: Long = noon, manual: Boolean = false, knobs: Boolean = true,
    ) = AiLearning.finish(a, old, day.minusDays(100), day, now, state, self, fine, knobs, manual, 17_000, 140)

    // ---- when a run is due

    @Test fun `a run is due once a day, after the strap's first sync`() {
        assertEquals(Gate.WaitSync, AiLearning.gate(AiLearnState.Empty, noon, today, syncedToday = false))
        assertEquals(Gate.Go, AiLearning.gate(AiLearnState.Empty, noon, today, syncedToday = true))
        val done = finish(answer()).state
        assertEquals(Gate.Done, AiLearning.gate(done, noon + 3_600, today, syncedToday = true))
        assertEquals(Gate.Done, AiLearning.gate(done, noon + 3_600, today, syncedToday = false))
        assertEquals(Gate.Go, AiLearning.gate(done, noon + 86_400, today.plusDays(1), syncedToday = true))
    }

    @Test fun `a failure is waited out before the next automatic try`() {
        val failed = AiLearning.failed(AiLearnState.Empty, AiFailure.Offline, null, noon, today, manual = false)
        assertEquals(Gate.Wait, AiLearning.gate(failed, noon + 10 * 60, today, true))
        assertEquals(Gate.Go, AiLearning.gate(failed, noon + 31 * 60, today, true))
        val rate = AiLearning.failed(AiLearnState.Empty, AiFailure.RateLimit, 429, noon, today, false)
        assertEquals(Gate.Wait, AiLearning.gate(rate, noon + 90 * 60, today, true))
        assertEquals(Gate.Go, AiLearning.gate(rate, noon + 2 * 3_600 + 1, today, true))
        val key = AiLearning.failed(AiLearnState.Empty, AiFailure.BadKey, 401, noon, today, false)
        assertEquals(Gate.Wait, AiLearning.gate(key, noon + 5 * 3_600, today, true))
        assertEquals(Gate.Go, AiLearning.gate(key, noon + 6 * 3_600, today, true))
        assertEquals(401, key.failureCode)
        assertEquals(AiFailure.BadKey, key.failure)
    }

    @Test fun `three automatic tries a day at most, and a new day starts clean`() {
        var s = AiLearnState.Empty
        repeat(AiLearning.MAX_TRIES) { s = AiLearning.failed(s, AiFailure.Timeout, null, noon + it * 7_200L, today, manual = false) }
        assertEquals(Gate.Spent, AiLearning.gate(s, noon + 5 * 3_600L, today, true))
        assertEquals(Gate.Go, AiLearning.gate(s, noon + 86_400L, today.plusDays(1), true))
        assertEquals(1, AiLearning.failed(s, AiFailure.Timeout, null, noon + 86_400L, today.plusDays(1), false).tries)
    }

    @Test fun `a person's tap is not one of the day's automatic tries`() {
        val s = AiLearning.failed(AiLearnState.Empty, AiFailure.Timeout, null, noon, today, manual = true)
        assertEquals(0, s.tries)
        assertEquals(1, finish(answer(), manual = false).state.tries)
        assertEquals(0, finish(answer(), manual = true).state.tries)
    }

    @Test fun `a clock set back does not hold the run up`() {
        val failed = AiLearning.failed(AiLearnState.Empty, AiFailure.Offline, null, noon, today, false)
        assertEquals(Gate.Go, AiLearning.gate(failed, noon - 1_000, today, true))
    }

    // ---- what a run leaves

    @Test fun `a run keeps the profile, the lines it learned and what it did`() {
        val f = finish(answer(ask("rest", 0.9), ask("alert", 5.0, confidence = 10)), old = sampleProfile())
        assertEquals(today, f.state.lastOkDay)
        assertEquals(today, f.state.learnedDay)
        assertEquals(listOf("تعلّمت أن نومك يقصر بعد السهر"), f.state.learned)
        assertEquals(1, f.state.changed)
        assertEquals(1, f.state.refused)
        assertEquals(17_000, f.state.sentBytes)
        assertNull(f.state.failure)
        // The profile: the section the answer named is replaced, the ones it left out stay, the span reaches back as far as the data.
        assertEquals("ينام سبع ساعات ونصف تقريباً", f.profile.sections.getValue(ProfileSection.Sleep).single().text)
        assertTrue(ProfileSection.Hrv in f.profile.sections)
        assertEquals(today, f.profile.to)
        assertTrue(BodyProfile.bytes(f.profile) <= BodyProfile.MAX_BYTES)
    }

    @Test fun `what the AI asked for is logged as incidents with its evidence, the accepted one applied`() {
        val f = finish(answer(ask("rest", 0.9, evidence = "الأيام الخمسة الأخيرة"), ask("cap", 40.0, confidence = 20), ask(ProposalGate.SLEEP_NEED_KEY, 483.0)))
        val byCheck = f.selfCheck.incidents.associateBy { it.check }
        val applied = byCheck.getValue("ai.rest")
        assertEquals(SelfLearning.Status.Applied, applied.status)
        assertEquals(listOf(SelfLearning.Change(Knob.Rest, 0.9f)), applied.changes)
        assertEquals("الأيام الخمسة الأخيرة", applied.note)
        val refused = byCheck.getValue("ai.no.cap")
        assertEquals(SelfLearning.Status.Informed, refused.status)
        assertTrue(refused.changes.isEmpty())
        assertEquals(ProposalGate.Reason.LowConfidence.ordinal.toFloat(), refused.nums.first(), 0f)
        val need = byCheck.getValue("ai.${ProposalGate.SLEEP_NEED_KEY}")
        assertEquals(SelfLearning.Status.Informed, need.status)
        assertEquals(480f, need.nums.single(), 0f)
        // The knob is in force through the self-check's own state, and nowhere else.
        assertEquals(0.9f, Knob.Rest.read(SelfLearning.learned(f.selfCheck, today)), 0.0001f)
        assertEquals(480, f.state.sleepNeedMin)
        assertEquals(today, f.state.sleepNeedDay)
    }

    @Test fun `the run does not count as the self-check's own run`() {
        val before = SelfLearning.State(lastRun = 123L, lastRunDay = today.minusDays(1))
        val f = finish(answer(ask("rest", 0.9)), self = before)
        assertEquals(123L, f.selfCheck.lastRun)
        assertEquals(today.minusDays(1), f.selfCheck.lastRunDay)
    }

    @Test fun `a later answer with no sleep need keeps the one offered before`() {
        val first = finish(answer(ask(ProposalGate.SLEEP_NEED_KEY, 480.0))).state
        val second = finish(answer(), state = first, day = today.plusDays(1)).state
        assertEquals(480, second.sleepNeedMin)
        assertEquals(today, second.sleepNeedDay)
    }

    @Test fun `with the self-check off the AI still learns the profile but moves nothing`() {
        val f = finish(answer(ask("rest", 0.9)), knobs = false)
        assertTrue(f.selfCheck.incidents.isEmpty())
        assertEquals(ChargeTuning.Default, SelfLearning.learned(f.selfCheck, today))
        assertNotNull(f.profile.sections[ProfileSection.Sleep])
    }

    @Test fun `a proposal for the same knob the next day is one incident whose evidence moves on`() {
        val one = finish(answer(ask("rest", 0.9, evidence = "الأيام الخمسة الأخيرة")))
        val two = finish(answer(ask("rest", 0.8, evidence = "الأيام الستة الأخيرة")), self = one.selfCheck, day = today.plusDays(1))
        val rest = two.selfCheck.incidents.filter { it.check == "ai.rest" }
        assertEquals(1, rest.size)
        assertEquals(today.plusDays(1), rest.single().last)
        assertEquals("الأيام الستة الأخيرة", rest.single().note)
        assertEquals(0.8f, Knob.Rest.read(SelfLearning.learned(two.selfCheck, today.plusDays(1))), 0.0001f)
    }

    @Test fun `a change fades back to the published value over 14 days unless it is asked for again`() {
        val f = finish(answer(ask("rest", 0.9)))
        assertEquals(0.9f, Knob.Rest.read(SelfLearning.learned(f.selfCheck, today)), 0.0001f)
        val half = Knob.Rest.read(SelfLearning.learned(f.selfCheck, today.plusDays(7)))
        assertTrue("$half", half > 0.9f && half < 1f)
        assertEquals(1f, Knob.Rest.read(SelfLearning.learned(f.selfCheck, today.plusDays(SelfLearning.DECAY_DAYS.toLong()))), 0f)
    }

    // ---- the person's hand

    @Test fun `undoing a change takes it back, and the same one is not asked for again in the episode`() {
        val f = finish(answer(ask("rest", 0.9)))
        val id = f.selfCheck.incidents.single { it.check == "ai.rest" }.id
        val undone = SelfLearning.undo(f.selfCheck, id)
        assertEquals(1f, Knob.Rest.read(SelfLearning.learned(undone, today)), 0f)
        assertEquals(setOf("ai.rest"), AiLearning.undone(undone, today.plusDays(1)))
        val again = finish(answer(ask("rest", 0.9)), self = undone, day = today.plusDays(1))
        assertEquals(ProposalGate.Reason.Undone, again.judged.refused.single().reason)
        assertEquals(1f, Knob.Rest.read(SelfLearning.learned(again.selfCheck, today.plusDays(1))), 0f)
        // Once the episode is over, it may be proposed afresh.
        val later = today.plusDays(SelfLearning.EPISODE_GAP_DAYS.toLong() + 2)
        assertEquals(emptySet<String>(), AiLearning.undone(undone, later))
        assertEquals(1, finish(answer(ask("rest", 0.9)), self = undone, day = later).judged.accepted.size)
    }

    @Test fun `the notification's Undo takes back only what the AI applied that day`() {
        val own = SelfLearning.State(listOf(incident(7, "energy.stuck_empty", changes = listOf(SelfLearning.Change(Knob.Cap, 60f)))))
        val f = finish(answer(ask("rest", 0.9), ask("active", 1.1)), self = own)
        val undone = SelfLearning.undoAiOn(f.selfCheck, today)
        assertEquals(SelfLearning.Status.Applied, undone.incidents.single { it.check == "energy.stuck_empty" }.status)
        assertTrue(undone.incidents.filter { SelfLearning.isAi(it.check) }.all { it.status != SelfLearning.Status.Applied })
        assertEquals(60f, Knob.Cap.read(SelfLearning.learned(undone, today)), 0f)
        assertEquals(1f, Knob.Rest.read(SelfLearning.learned(undone, today)), 0f)
        // Another day's change is left alone.
        assertEquals(SelfLearning.Status.Applied, SelfLearning.undoAiOn(f.selfCheck, today.minusDays(1)).incidents.single { it.check == "ai.rest" }.status)
    }

    @Test fun `clearing what it learned removes everything it proposed and nothing of the self-check's`() {
        val own = SelfLearning.State(listOf(incident(7, "energy.stuck_empty")))
        val f = finish(answer(ask("rest", 0.9), ask("cap", 40.0, confidence = 5)), self = own)
        assertEquals(3, f.selfCheck.incidents.size)
        val cleared = AiLearning.clear(f.selfCheck)
        assertEquals(listOf("energy.stuck_empty"), cleared.incidents.map { it.check })
        assertEquals(ChargeTuning.Default, SelfLearning.learned(cleared, today))
    }

    @Test fun `after clearing, today's automatic tries are spent and tomorrow it learns again`() {
        val s = AiLearning.cleared(today)
        assertNull(s.lastOkDay)
        assertTrue(s.learned.isEmpty())
        assertEquals(Gate.Spent, AiLearning.gate(s, noon, today, true))
        assertEquals(Gate.Go, AiLearning.gate(s, noon + 86_400, today.plusDays(1), true))
        assertNull(AiLearning.notice(s, SelfLearning.State.Empty, today, quiet = false))
    }

    @Test fun `the self-check's own notice never picks up what the AI proposed`() {
        val f = finish(answer(ask("rest", 0.9)))
        assertNull(SelfLearning.nextNotice(f.selfCheck, today, quiet = false))
    }

    // ---- telling

    @Test fun `one notification a day, with what it learned and what it changed`() {
        val f = finish(answer(ask("rest", 0.9)))
        val n = AiLearning.notice(f.state, f.selfCheck, today, quiet = false)!!
        assertEquals(f.state.learned, n.learned)
        assertEquals(listOf("ai.rest"), n.applied.map { it.check })
        val told = AiLearning.told(f.state, today)
        assertNull(AiLearning.notice(told, f.selfCheck, today, quiet = false))
    }

    @Test fun `nothing is told in quiet hours, but it may be told later the same day`() {
        val f = finish(answer())
        assertNull(AiLearning.notice(f.state, f.selfCheck, today, quiet = true))
        assertNotNull(AiLearning.notice(f.state, f.selfCheck, today, quiet = false))
        assertNull(AiLearning.notice(f.state, f.selfCheck, today.plusDays(1), quiet = false))
    }

    @Test fun `nothing learned and nothing changed is nothing to tell`() {
        val f = finish(LearnAnswer(learned = emptyList(), profile = answer().profile, proposals = emptyList()))
        assertNull(AiLearning.notice(f.state, f.selfCheck, today, quiet = false))
    }

    @Test fun `a refused proposal alone is not told, a change alone is`() {
        val refused = finish(LearnAnswer(proposals = listOf(ask("rest", 0.9, confidence = 5)), profile = answer().profile))
        assertNull(AiLearning.notice(refused.state, refused.selfCheck, today, false))
        val changed = finish(LearnAnswer(proposals = listOf(ask("rest", 0.9)), profile = answer().profile))
        assertNotNull(AiLearning.notice(changed.state, changed.selfCheck, today, false))
    }

    @Test fun `a run the person asked for is not told afterwards`() {
        val f = finish(answer(ask("rest", 0.9)), manual = true)
        assertNull(AiLearning.notice(f.state, f.selfCheck, today, quiet = false))
    }

    // ---- what is kept

    @Test fun `the state survives being written and read`() {
        val f = finish(answer(ask(ProposalGate.SLEEP_NEED_KEY, 470.0)))
        val s = AiLearning.failed(f.state, AiFailure.NoBalance, 402, noon, today, false).copy(toldDay = today)
        assertEquals(s, AiLearnState.decode(AiLearnState.encode(s)))
        assertEquals(AiLearnState.Empty, AiLearnState.decode(AiLearnState.encode(AiLearnState.Empty)))
    }

    @Test fun `a state that does not read is empty, and a field that does not read is left at its default`() {
        assertEquals(AiLearnState.Empty, AiLearnState.decode(null))
        assertEquals(AiLearnState.Empty, AiLearnState.decode("garbage"))
        val s = AiLearnState.decode("{\"ok_day\":\"never\",\"fail\":\"Nope\",\"need\":9000,\"tries\":99999,\"learned\":[\"سطر واحد\",5]}")
        assertNull(s.lastOkDay)
        assertNull(s.failure)
        assertNull(s.sleepNeedMin)
        assertTrue(s.tries <= AiLearning.MAX_TRIES * 4)
        assertEquals(listOf("سطر واحد"), s.learned)
    }

    @Test fun `the state holds no key, no payload and no raw text beyond the few lines it learned`() {
        val text = AiLearnState.encode(finish(answer(ask(ProposalGate.SLEEP_NEED_KEY, 470.0))).state)
        val keys = org.json.JSONObject(text).let { o -> o.keys().asSequence().toSet() }
        assertEquals(setOf("v", "ok_day", "ok_at", "try_day", "tries", "try_at", "learned", "learned_day", "changed", "refused", "need", "need_why", "need_day", "bytes", "rows"), keys)
        assertFalse(text.contains("sk-"))
    }
}
