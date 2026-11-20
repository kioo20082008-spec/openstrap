package org.openstrap.app.data.selfcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.selfcheck.SelfLearning.Status
import java.time.LocalDate

/** What the self-check keeps for the AI body learning: incidents with a note, told and capped apart from its own. */
class SelfLearningAiTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val now = 1_800_000_000L

    private fun decision(check: String, note: String = "", severity: Severity = Severity.Medium, day: LocalDate = today, vararg changes: Pair<Knob, Float>) =
        Decision(Finding(check, Domain.Energy, severity, day, listOf(1f, 2f), Plan.Inform, note), changes.toMap())

    @Test fun `the ids the AI learning uses are told apart by their prefix`() {
        assertTrue(SelfLearning.isAi("ai.rest"))
        assertTrue(SelfLearning.isAi("ai.no.cap"))
        assertFalse(SelfLearning.isAi("energy.stuck_empty"))
    }

    @Test fun `an incident's note survives being stored, one line and no separator`() {
        val s = SelfLearning.absorb(SelfLearning.State.Empty, listOf(decision("ai.rest", "طاقته | منخفضة\nثلاثة أيام\r", changes = arrayOf(Knob.Rest to 0.9f))))
        val back = SelfLearning.decode(SelfLearning.encode(s))
        assertEquals("طاقته   منخفضة ثلاثة أيام".replace(Regex(" +"), " "), back.incidents.single().note.replace(Regex(" +"), " "))
        assertFalse(back.incidents.single().note.any { it == '|' || it == '\n' || it == '\r' })
        assertEquals(s.incidents.single().changes, back.incidents.single().changes)
    }

    @Test fun `a state written before notes existed still reads`() {
        val old = "v1\nrun|1800000000|2026-11-18\ntold|\ninc|3|energy.stuck_empty|Medium|2026-11-17|2026-11-18|Applied|0|300.0,100.0|cap=60.0\n"
        val i = SelfLearning.decode(old).incidents.single()
        assertEquals("energy.stuck_empty", i.check)
        assertEquals("", i.note)
        assertEquals(Status.Applied, i.status)
    }

    @Test fun `absorbing a run leaves the run line alone and settling sets it`() {
        val base = SelfLearning.State(lastRun = 5L, lastRunDay = today.minusDays(1))
        val absorbed = SelfLearning.absorb(base, listOf(decision("ai.rest", changes = arrayOf(Knob.Rest to 0.9f))))
        assertEquals(5L, absorbed.lastRun)
        assertEquals(today.minusDays(1), absorbed.lastRunDay)
        val settled = SelfLearning.settle(base, listOf(decision("ai.rest", changes = arrayOf(Knob.Rest to 0.9f))), now, today)
        assertEquals(now, settled.lastRun)
        assertEquals(today, settled.lastRunDay)
        assertEquals(absorbed.incidents, SelfLearning.absorb(base, listOf(decision("ai.rest", changes = arrayOf(Knob.Rest to 0.9f)))).incidents)
    }

    @Test fun `the newest note is the one kept when the same incident comes again`() {
        var s = SelfLearning.absorb(SelfLearning.State.Empty, listOf(decision("ai.rest", "الأولى", day = today.minusDays(1), changes = arrayOf(Knob.Rest to 0.9f))))
        s = SelfLearning.absorb(s, listOf(decision("ai.rest", "الثانية", changes = arrayOf(Knob.Rest to 0.85f))))
        assertEquals("الثانية", s.incidents.single().note)
        s = SelfLearning.absorb(s, listOf(decision("ai.rest", "", day = today.minusDays(2), changes = arrayOf(Knob.Rest to 0.8f))))
        assertEquals("الثانية", s.incidents.single().note)
    }

    @Test fun `what the AI only noted is capped so it never crowds out the self-check's own incidents`() {
        var s = SelfLearning.absorb(SelfLearning.State.Empty, listOf(decision("energy.stuck_empty", changes = arrayOf(Knob.Cap to 60f), day = today.minusDays(10))))
        for (k in 1..30) s = SelfLearning.absorb(s, listOf(decision("ai.no.k$k", severity = Severity.Low, day = today.minusDays((k % 5).toLong()))))
        assertEquals(SelfLearning.KEEP_AI_NOTES, s.incidents.count { SelfLearning.isAi(it.check) })
        assertEquals(Status.Applied, s.incidents.single { it.check == "energy.stuck_empty" }.status)
    }

    @Test fun `an applied AI change is never capped away`() {
        var s = SelfLearning.absorb(SelfLearning.State.Empty, listOf(decision("ai.rest", changes = arrayOf(Knob.Rest to 0.9f), day = today.minusDays(9))))
        for (k in 1..30) s = SelfLearning.absorb(s, listOf(decision("ai.no.k$k", severity = Severity.Low)))
        assertEquals(Status.Applied, s.incidents.single { it.check == "ai.rest" }.status)
    }

    @Test fun `undoing and clearing the AI's incidents leaves the others`() {
        val s = SelfLearning.absorb(
            SelfLearning.State.Empty,
            listOf(
                decision("ai.rest", changes = arrayOf(Knob.Rest to 0.9f)),
                decision("ai.no.cap", severity = Severity.Low),
                decision("energy.stuck_empty", changes = arrayOf(Knob.Cap to 60f)),
            ),
        )
        val cleared = SelfLearning.clearAi(s)
        assertEquals(listOf("energy.stuck_empty"), cleared.incidents.map { it.check })
        val undone = SelfLearning.undoAiOn(s, today)
        assertEquals(Status.Undone, undone.incidents.single { it.check == "ai.rest" }.status)
        assertEquals(Status.Informed, undone.incidents.single { it.check == "ai.no.cap" }.status)
        assertEquals(Status.Applied, undone.incidents.single { it.check == "energy.stuck_empty" }.status)
        assertNull(SelfLearning.nextNotice(s.copy(incidents = s.incidents.filter { SelfLearning.isAi(it.check) }), today, quiet = false))
    }
}
