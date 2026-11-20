package org.openstrap.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.R
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.SelfChecks
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.data.selfcheck.Severity
import org.openstrap.app.i18n.Strings
import java.time.LocalDate
import java.util.Locale

class SelfCheckCopyTest {
    private val locale = Locale.US
    private val day = LocalDate.of(2026, 11, 18)

    private fun incident(
        check: String, nums: List<Float>, status: SelfLearning.Status = SelfLearning.Status.Applied,
        changes: List<SelfLearning.Change> = emptyList(), last: LocalDate = day,
    ) = SelfLearning.Incident(1, check, Severity.Medium, last, last, nums, status, changes, notified = false)

    @Test fun `the owner's day reads in plain words`() {
        val i = incident("energy.stuck_empty", listOf(317f, 975f, 600f), changes = listOf(SelfLearning.Change(Knob.Cap, 60f)))
        assertEquals("Energy stayed at zero for 5 h 17 while you were awake, with only 975 steps.", SelfCheckCopy.seen(i, locale))
        assertEquals(listOf("Raised heart rate without steps counts as exercise for at most 1 h."), SelfCheckCopy.adjustments(i, locale))
        assertEquals("Self-learning adjusted a reading", SelfCheckCopy.noticeTitle(i))
        assertEquals(
            "Energy stayed at zero for 5 h 17 while you were awake, with only 975 steps. We adjusted the calculation a little.",
            SelfCheckCopy.noticeText(i, locale),
        )
    }

    @Test fun `something only noticed says nothing was changed`() {
        val i = incident("strap.battery", listOf(4f), status = SelfLearning.Status.Informed)
        assertEquals("Self-learning noticed something", SelfCheckCopy.noticeTitle(i))
        assertEquals("The strap battery is at 4%. Nothing was changed.", SelfCheckCopy.noticeText(i, locale))
        assertEquals(emptyList<String>(), SelfCheckCopy.adjustments(i, locale))
    }

    @Test fun `every check has words of its own, with its numbers in them`() {
        val generic = Strings.app.get(R.string.selfcheck_seen_generic)
        for (c in SelfChecks.all) {
            val text = SelfCheckCopy.seen(c.id, listOf(300f, 150f, 600f, 50f), locale)
            assertNotEquals(c.id, generic, text)
            assertFalse("${c.id}: $text", text.contains("%") && !text.contains("%."))
            assertFalse(c.id, text.contains("null") || text.contains("\$s"))
        }
        assertEquals(generic, SelfCheckCopy.seen("no.such", listOf(1f), locale))
        // Missing numbers are zeros, not a crash.
        for (c in SelfChecks.all) SelfCheckCopy.seen(c.id, emptyList(), locale)
    }

    @Test fun `counts of nights and days are said in words`() {
        assertEquals("The body-signal alert stayed on for four nights in a row.", SelfCheckCopy.seen("energy.alert_streak", listOf(4f), locale))
        assertEquals("The body-signal alert stayed on for 12 nights in a row.", SelfCheckCopy.seen("energy.alert_streak", listOf(12f), locale))
        assertEquals(
            "The strap had no heart rate for about 3 h 20 on two days, with no reason found.",
            SelfCheckCopy.seen("strap.gaps", listOf(200f, 2f), locale),
        )
    }

    @Test fun `each knob reads as the value it was set to`() {
        assertEquals("The body-signal alert counts for 50% of its usual effect on Energy.", SelfCheckCopy.adjustment(Knob.Alert, 0.5f, locale))
        assertEquals("The drain of Energy while moving is scaled to 80%.", SelfCheckCopy.adjustment(Knob.Active, 0.8f, locale))
        assertEquals("The drain of Energy while resting is scaled to 75%.", SelfCheckCopy.adjustment(Knob.Rest, 0.75f, locale))
        assertEquals("Raised heart rate without steps counts as exercise for at most 30 min.", SelfCheckCopy.adjustment(Knob.Cap, 30f, locale))
        assertEquals("Your resting heart rate for Energy is taken as 4 bpm higher.", SelfCheckCopy.adjustment(Knob.Resting, 4f, locale))
        assertEquals("Your resting heart rate for Energy is taken as 2.5 bpm higher.", SelfCheckCopy.adjustment(Knob.Resting, 2.5f, locale))
    }

    @Test fun `what is in effect lists only what is off its published value`() {
        assertEquals(emptyList<String>(), SelfCheckCopy.inEffect(ChargeTuning.Default, locale))
        val lines = SelfCheckCopy.inEffect(ChargeTuning(alert = 0.5f, sustainedCap = 60), locale)
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("50%"))
        assertTrue(lines[1].contains("1 h"))
        // The check-in multipliers are not the self-check's to name.
        assertEquals(emptyList<String>(), SelfCheckCopy.inEffect(ChargeTuning(sleep = 1.2f), locale))
    }

    @Test fun `an incident is labelled by what it did and whether it is still at work`() {
        val applied = incident("energy.stuck_empty", listOf(300f), changes = listOf(SelfLearning.Change(Knob.Cap, 60f)))
        assertEquals(SelfCheckCopy.Label.Applied, SelfCheckCopy.label(applied, day.plusDays(3)))
        assertEquals(SelfCheckCopy.Label.Faded, SelfCheckCopy.label(applied, day.plusDays(14)))
        assertEquals(SelfCheckCopy.Label.Noted, SelfCheckCopy.label(applied.copy(status = SelfLearning.Status.Informed), day))
        assertEquals(SelfCheckCopy.Label.Undone, SelfCheckCopy.label(applied.copy(status = SelfLearning.Status.Undone), day))
        assertEquals(
            listOf("Adjusted", "Faded", "Noted only", "Undone"),
            SelfCheckCopy.Label.entries.map { SelfCheckCopy.labelText(it) },
        )
    }

    @Test fun `the row says on or off`() {
        assertEquals("On", SelfCheckCopy.rowSummary(true))
        assertEquals("Off", SelfCheckCopy.rowSummary(false))
    }
}
