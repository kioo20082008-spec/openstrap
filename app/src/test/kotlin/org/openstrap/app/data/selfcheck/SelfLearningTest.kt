package org.openstrap.app.data.selfcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.selfcheck.SelfLearning.Status
import java.time.LocalDate

class SelfLearningTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val now = 1_800_000_000L
    private fun d(back: Int): LocalDate = today.minusDays(back.toLong())

    private fun finding(check: String = "energy.stuck_empty", day: LocalDate = today, severity: Severity = Severity.Medium) =
        Finding(check, Domain.Energy, severity, day, listOf(300f, 100f, 600f), Plan.Inform)

    private fun decision(check: String = "energy.stuck_empty", day: LocalDate = today, severity: Severity = Severity.Medium, vararg changes: Pair<Knob, Float>) =
        Decision(finding(check, day, severity), changes.toMap())

    private fun settle(state: SelfLearning.State, vararg ds: Decision, on: LocalDate = today) = SelfLearning.settle(state, ds.toList(), now, on)

    // ---- Settling -------------------------------------------------------------------------------------

    @Test fun `a medium finding with a change is applied`() {
        val s = settle(SelfLearning.State.Empty, decision(changes = arrayOf(Knob.Cap to 60f)))
        val i = s.incidents.single()
        assertEquals(Status.Applied, i.status)
        assertEquals(listOf(SelfLearning.Change(Knob.Cap, 60f)), i.changes)
        assertEquals(60, SelfLearning.learned(s, today).sustainedCap)
        assertEquals(now, s.lastRun)
        assertEquals(today, s.lastRunDay)
    }

    @Test fun `a low finding or one without a change is only recorded`() {
        val low = settle(SelfLearning.State.Empty, decision(severity = Severity.Low, changes = arrayOf(Knob.Cap to 60f)))
        assertEquals(Status.Informed, low.incidents.single().status)
        assertTrue(SelfLearning.learned(low, today).isDefault)
        val none = settle(SelfLearning.State.Empty, decision())
        assertEquals(Status.Informed, none.incidents.single().status)
        val atDefault = settle(SelfLearning.State.Empty, decision(changes = arrayOf(Knob.Cap to 90f)))
        assertEquals(Status.Informed, atDefault.incidents.single().status)
    }

    @Test fun `the same finding again is the same incident, and its last day moves`() {
        var s = settle(SelfLearning.State.Empty, decision(day = d(1), changes = arrayOf(Knob.Cap to 60f)))
        s = settle(s, decision(day = today, changes = arrayOf(Knob.Cap to 60f)))
        s = settle(s, decision(day = d(1), changes = arrayOf(Knob.Cap to 60f)))
        val i = s.incidents.single()
        assertEquals(d(1), i.first)
        assertEquals(today, i.last)
    }

    @Test fun `a finding more than three days on is a new incident`() {
        var s = settle(SelfLearning.State.Empty, decision(day = d(10), changes = arrayOf(Knob.Cap to 60f)))
        s = settle(s, decision(day = today, changes = arrayOf(Knob.Cap to 60f)))
        assertEquals(2, s.incidents.size)
        assertEquals(setOf(1, 2), s.incidents.map { it.id }.toSet())
        s = settle(SelfLearning.State.Empty, decision(day = d(3), changes = arrayOf(Knob.Cap to 60f)))
        s = settle(s, decision(day = today, changes = arrayOf(Knob.Cap to 60f)))
        assertEquals(1, s.incidents.size)
    }

    @Test fun `different checks are different incidents`() {
        val s = settle(SelfLearning.State.Empty, decision("energy.stuck_empty", changes = arrayOf(Knob.Cap to 60f)), decision("energy.drain_fast", changes = arrayOf(Knob.Cap to 60f)))
        assertEquals(2, s.incidents.size)
    }

    @Test fun `an undone incident is not applied again, and a later episode is`() {
        var s = settle(SelfLearning.State.Empty, decision(day = d(1), changes = arrayOf(Knob.Cap to 60f)))
        s = SelfLearning.undo(s, s.incidents.single().id)
        assertTrue(SelfLearning.learned(s, today).isDefault)
        s = settle(s, decision(day = today, changes = arrayOf(Knob.Cap to 60f)))
        assertEquals(Status.Undone, s.incidents.single().status)
        assertTrue(SelfLearning.learned(s, today).isDefault)
        s = settle(s, decision(day = today.plusDays(10), changes = arrayOf(Knob.Cap to 60f)), on = today.plusDays(10))
        assertEquals(Status.Applied, s.incidents.maxByOrNull { it.id }!!.status)
    }

    @Test fun `a recorded incident that later gets a change is applied, and keeps its notified flag`() {
        var s = settle(SelfLearning.State.Empty, decision(severity = Severity.High))
        s = SelfLearning.told(s, s.incidents.single().id, today)
        s = settle(s, decision(severity = Severity.Medium, changes = arrayOf(Knob.Cap to 60f)))
        val i = s.incidents.single()
        assertEquals(Status.Applied, i.status)
        assertEquals(Severity.High, i.severity)
        assertTrue(i.notified)
    }

    @Test fun `only the latest forty incidents are kept`() {
        var s = SelfLearning.State.Empty
        for (n in 0 until 60) s = settle(s, decision(check = "c.$n", day = d(0)))
        assertEquals(SelfLearning.KEEP, s.incidents.size)
    }

    // ---- Learned values, bounds and decay ---------------------------------------------------------------

    private fun applied(vararg c: Pair<Knob, Float>, last: LocalDate = today) =
        settle(SelfLearning.State.Empty, decision(day = last, changes = c), on = last)

    @Test fun `a correction fades linearly over fourteen days to the published value`() {
        val s = applied(Knob.Cap to 30f)
        assertEquals(30f, SelfLearning.value(s, Knob.Cap, today), 0f)
        assertEquals(60f, SelfLearning.value(s, Knob.Cap, today.plusDays(7)), 0f)
        assertEquals(90f, SelfLearning.value(s, Knob.Cap, today.plusDays(14)), 0f)
        assertEquals(90f, SelfLearning.value(s, Knob.Cap, today.plusDays(40)), 0f)
        assertTrue(SelfLearning.learned(s, today.plusDays(14)).isDefault)
        var prev = 0f
        for (n in 0..14) {
            val v = SelfLearning.value(s, Knob.Cap, today.plusDays(n.toLong()))
            assertTrue("day $n: $v", v >= prev)
            prev = v
        }
    }

    @Test fun `evidence again holds the correction at full strength`() {
        var s = applied(Knob.Cap to 30f, last = d(10))
        assertEquals(30f + 60f * 10 / 14f, SelfLearning.value(s, Knob.Cap, today), 3f)
        s = settle(s, decision(day = today, changes = arrayOf(Knob.Cap to 30f)))
        assertEquals(30f, SelfLearning.value(s, Knob.Cap, today), 0f)
    }

    @Test fun `the learned tuning is always inside every bound`() {
        val extreme = mapOf(Knob.Alert to -5f, Knob.Active to 9f, Knob.Rest to -3f, Knob.Cap to 5f, Knob.Resting to 99f)
        val s = settle(SelfLearning.State.Empty, Decision(finding(), extreme))
        val t = SelfLearning.learned(s, today)
        assertEquals(t, t.clamped())
        assertEquals(ChargeTuning.ALERT_MIN, t.alert, 0f)
        assertEquals(ChargeTuning.ACTIVE_MAX, t.active, 0f)
        assertEquals(ChargeTuning.REST_MIN, t.rest, 0f)
        assertEquals(ChargeTuning.CAP_MIN, t.sustainedCap)
        assertEquals(ChargeTuning.OFFSET_MAX, t.restingOffset, 0f)
        // The check-in part is never touched.
        assertEquals(1f, t.sleep, 0f)
        assertEquals(1f, t.exercise, 0f)
        assertEquals(1f, t.carry, 0f)
    }

    @Test fun `two incidents on one knob follow the later evidence`() {
        var s = settle(SelfLearning.State.Empty, decision("a.a", day = d(2), changes = arrayOf(Knob.Cap to 30f)), on = d(2))
        s = settle(s, decision("b.b", day = d(1), changes = arrayOf(Knob.Cap to 60f)), on = d(1))
        val v = SelfLearning.value(s, Knob.Cap, today)
        assertTrue("cap $v", v in 55f..65f)
    }

    @Test fun `undo, reset and dismiss`() {
        var s = settle(SelfLearning.State.Empty, decision("a.a", changes = arrayOf(Knob.Cap to 60f)), decision("b.b", changes = arrayOf(Knob.Rest to 0.75f)), decision("c.c"))
        val (a, b, c) = s.incidents.sortedBy { it.check }
        s = SelfLearning.undo(s, a.id)
        assertEquals(90, SelfLearning.learned(s, today).sustainedCap)
        assertEquals(0.75f, SelfLearning.learned(s, today).rest, 0f)
        // Undo of an informed one does nothing.
        assertEquals(s, SelfLearning.undo(s, c.id))
        s = SelfLearning.resetAll(s)
        assertTrue(SelfLearning.learned(s, today).isDefault)
        assertEquals(3, s.incidents.size)
        assertEquals(Status.Undone, s.incidents.first { it.id == b.id }.status)
        assertEquals(Status.Informed, s.incidents.first { it.id == c.id }.status)
        // Dismissing removes a recorded or undone one, and only undoes one still at work.
        assertEquals(2, SelfLearning.dismiss(s, c.id, today).incidents.size)
        val live = settle(SelfLearning.State.Empty, decision(changes = arrayOf(Knob.Cap to 60f)))
        assertEquals(Status.Undone, SelfLearning.dismiss(live, live.incidents.single().id, today).incidents.single().status)
        assertEquals(s, SelfLearning.dismiss(s, 99, today))
    }

    // ---- Telling --------------------------------------------------------------------------------------

    private fun medium(check: String) = decision(check, changes = arrayOf(Knob.Cap to 60f))

    @Test fun `a medium applied change is told once`() {
        var s = settle(SelfLearning.State.Empty, medium("a.a"))
        val i = SelfLearning.nextNotice(s, today, quiet = false)
        assertNotNull(i)
        s = SelfLearning.told(s, i!!.id, today)
        assertNull(SelfLearning.nextNotice(s, today, quiet = false))
        // Again on the next run: still the same incident, still told once.
        s = settle(s, medium("a.a"))
        assertNull(SelfLearning.nextNotice(s, today, quiet = false))
        assertNull(SelfLearning.nextNotice(s, today.plusDays(1), quiet = false))
    }

    @Test fun `at most two a day, the most severe first, and quiet hours defer`() {
        var s = settle(
            SelfLearning.State.Empty, medium("a.a"), medium("b.b"), medium("c.c"),
            decision("h.h", severity = Severity.High, changes = arrayOf(Knob.Cap to 60f)),
        )
        assertNull(SelfLearning.nextNotice(s, today, quiet = true))
        val first = SelfLearning.nextNotice(s, today, quiet = false)!!
        assertEquals("h.h", first.check)
        s = SelfLearning.told(s, first.id, today)
        val second = SelfLearning.nextNotice(s, today, quiet = false)!!
        s = SelfLearning.told(s, second.id, today)
        assertNull(SelfLearning.nextNotice(s, today, quiet = false))
        // The next day the third may go out, while it is still recent.
        assertNotNull(SelfLearning.nextNotice(s, today.plusDays(1), quiet = false))
        assertNull(SelfLearning.nextNotice(s, today.plusDays(2), quiet = false))
    }

    @Test fun `a high one that nothing could change is told, a medium one is not, and a low one never`() {
        val s = settle(
            SelfLearning.State.Empty, decision("h.h", severity = Severity.High), decision("m.m", severity = Severity.Medium),
            decision("l.l", severity = Severity.Low, changes = arrayOf(Knob.Cap to 60f)),
        )
        assertEquals("h.h", SelfLearning.nextNotice(s, today, quiet = false)!!.check)
        val told = SelfLearning.told(s, SelfLearning.nextNotice(s, today, quiet = false)!!.id, today)
        assertNull(SelfLearning.nextNotice(told, today, quiet = false))
    }

    @Test fun `an undone incident is not told`() {
        var s = settle(SelfLearning.State.Empty, medium("a.a"))
        s = SelfLearning.undo(s, s.incidents.single().id)
        assertNull(SelfLearning.nextNotice(s, today, quiet = false))
    }

    // ---- When it runs ---------------------------------------------------------------------------------

    @Test fun `the heavy run is due the first time, once a day, and every three hours`() {
        val first = SelfLearning.State.Empty
        assertTrue(SelfLearning.due(first, now, today))
        val ran = first.copy(lastRun = now, lastRunDay = today)
        assertFalse(SelfLearning.due(ran, now + 10 * 60, today))
        assertFalse(SelfLearning.due(ran, now + 2 * 3600, today))
        assertTrue(SelfLearning.due(ran, now + 3 * 3600, today))
        assertTrue(SelfLearning.due(ran, now + 31 * 60, today.plusDays(1)))
        assertFalse(SelfLearning.due(ran, now + 10 * 60, today.plusDays(1)))
        assertTrue(SelfLearning.due(ran, now - 5, today))
    }

    // ---- Storage --------------------------------------------------------------------------------------

    @Test fun `the state survives a round trip`() {
        var s = settle(SelfLearning.State.Empty, decision("a.a", day = d(1), changes = arrayOf(Knob.Cap to 60f, Knob.Resting to 4f)), decision("h.h", severity = Severity.High))
        s = SelfLearning.told(s, s.incidents.first().id, today)
        assertEquals(s, SelfLearning.decode(SelfLearning.encode(s)))
        assertEquals(SelfLearning.State.Empty, SelfLearning.decode(SelfLearning.encode(SelfLearning.State.Empty)))
    }

    @Test fun `unreadable text is no state, and a bad line is skipped`() {
        assertEquals(SelfLearning.State.Empty, SelfLearning.decode(null))
        assertEquals(SelfLearning.State.Empty, SelfLearning.decode(""))
        assertEquals(SelfLearning.State.Empty, SelfLearning.decode("garbage"))
        assertEquals(SelfLearning.State.Empty, SelfLearning.decode("v9\ninc|1|a.a"))
        val s = settle(SelfLearning.State.Empty, medium("a.a"))
        val text = SelfLearning.encode(s).trimEnd() + "\ninc|x|broken\ninc|7|b.b|Nope|2026-01-01|2026-01-01|Applied|0||\n"
        assertEquals(s.incidents, SelfLearning.decode(text).incidents)
    }

    @Test fun `values read back are bounded`() {
        val text = "v1\nrun|5|2026-11-18\ntold|\ninc|1|a.a|Medium|2026-11-18|2026-11-18|Applied|0|1.0|cap=5,rhr=99,alert=-4,zzz=1,rest=oops\n"
        val t = SelfLearning.learned(SelfLearning.decode(text), today)
        assertEquals(t, t.clamped())
        assertEquals(ChargeTuning.CAP_MIN, t.sustainedCap)
        assertEquals(ChargeTuning.OFFSET_MAX, t.restingOffset, 0f)
        assertEquals(ChargeTuning.ALERT_MIN, t.alert, 0f)
        assertEquals(1f, t.rest, 0f)
    }
}
