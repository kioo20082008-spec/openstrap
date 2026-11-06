package org.openstrap.app.widget

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openstrap.app.data.Recovery
import org.openstrap.app.testing.LocaleRule

/** Widgets spec 3.6: what the Energy widget shows in each state, from Recovery's own Charge and copy, and how it fits. */
class EnergyWidgetModelTest {
    // TalkBack's words follow the phone's language, and these tests read them in English.
    @get:Rule val phone = LocaleRule(Locale.UK)

    private val today = LocalDate.of(2026, 10, 24)

    private fun charge(
        latest: Float? = 62.7f, atWake: Float? = 84.4f, anchored: Boolean = true, strapOff: Boolean = false,
    ) = Recovery.ChargeDay(
        points = emptyList(), latest = latest, atWake = atWake, recharged = 38.6f,
        drainedExercise = 30f, drainedStress = 18f, drainedLiving = 12.5f, highest = 92f, lowest = 54f,
        anchored = anchored, strapOffOvernight = strapOff,
    )

    /** The three sizes' content at their smallest cells, inside the 20 dp padding. */
    private val small = 110f to 110f
    private val wide = 240f to 120f
    private val large = 240f to 220f

    @Test fun `Energy now, at waking, and what the day recharged and drained`() {
        val m = EnergyWidgetModel.from(today, charge())
        assertEquals("Energy", m.title)
        assertEquals(today, m.date)
        // Cut, not rounded, as every screen shows Charge.
        assertEquals(62, m.now)
        assertEquals("62", m.number)
        assertEquals("left", m.word)
        assertEquals("Energy 62 left", m.description)
        assertEquals("At waking 84", m.waking)
        assertEquals(listOf(RecoveryFigure("Recharged", "+38"), RecoveryFigure("Drained", "60")), m.figures)
        assertEquals(0.62f, m.level, 0.0001f)
        assertNull(m.waiting)
    }

    @Test fun `after a night with the strap off, the day's start is an estimate`() {
        assertEquals("At start 84", EnergyWidgetModel.from(today, charge(strapOff = true)).waking)
    }

    @Test fun `no wake value leaves the line out`() {
        assertNull(EnergyWidgetModel.from(today, charge(atWake = null)).waking)
    }

    @Test fun `before the first night, the title and why, never the simulation's guess`() {
        val m = EnergyWidgetModel.from(today, charge(anchored = false))
        assertEquals("Energy", m.title)
        assertNull(m.now)
        assertNull(m.number)
        assertNull(m.description)
        assertNull(m.waking)
        assertTrue(m.figures.isEmpty())
        assertEquals("Energy starts after your first night. Sleep is what fills it.", m.waiting)
        assertEquals(0f, m.level, 0f)
    }

    @Test fun `without a value yet, or with Recovery not worked out, it waits for heart rate`() {
        val waiting = "Energy shows once the strap has a few hours of heart rate."
        assertEquals(waiting, EnergyWidgetModel.from(today, charge(latest = null)).waiting)
        assertEquals(waiting, EnergyWidgetModel.from(today, null).waiting)
        assertNull(EnergyWidgetModel.from(today, null).now)
    }

    @Test fun `the bar and the light stop at full and empty`() {
        assertEquals(1f, EnergyWidgetModel.from(today, charge(latest = 120f)).level, 0f)
        assertEquals(0f, EnergyWidgetModel.from(today, charge(latest = -3f)).level, 0f)
    }

    @Test fun `the sample is Moment's midday`() {
        val m = WidgetSamples.energy(today)
        assertEquals("62", m.number)
        assertEquals("At waking 84", m.waking)
        assertEquals(listOf("+38", "60"), m.figures.map { it.value })
    }

    @Test fun `every size fits its smallest cell, the number and left kept at the system font`() {
        val m = WidgetSamples.energy(today)
        val s = EnergyLayout.small(m, small.first, small.second, 1f)
        assertTrue(s.spread.fits(small.second, EnergyLayout.MIN_GAP_DP))
        assertTrue(s.word)
        val w = EnergyLayout.wide(m, wide.first, wide.second, 1f)
        assertTrue(w.spread.fits(wide.second, EnergyLayout.MIN_GAP_DP))
        assertTrue(w.word)
        assertFalse(w.figures)
        val l = EnergyLayout.large(m, large.first, large.second, 1f)
        assertTrue(l.spread.fits(large.second, EnergyLayout.MIN_GAP_DP))
        assertTrue(l.figures)
        // The number, Energy at waking and "left" share the Wide's row.
        val room = wide.first - EnergyLayout.SIDE_GAP_DP - WidgetText.fitWidth(m.waking, WidgetText.SECONDARY_SP) -
            EnergyLayout.WORD_GAP_DP - WidgetText.fitWidth(m.word, WidgetText.BODY_SP)
        assertTrue(WidgetDots.width(m.number!!, w.dot) <= room + 0.01f)
    }

    @Test fun `a large font gives up the word and the figures before anything clips`() {
        val m = WidgetSamples.energy(today)
        val s = EnergyLayout.small(m, small.first, small.second, 2f)
        assertFalse(s.word)
        assertTrue(s.spread.fits(small.second, EnergyLayout.MIN_GAP_DP))
        val l = EnergyLayout.large(m, large.first, 120f, 1.3f)
        assertFalse(l.figures)
        assertTrue(l.spread.fits(120f, EnergyLayout.MIN_GAP_DP))
    }

    @Test fun `on a tall cell the number grows as every widget's does`() {
        val m = WidgetSamples.energy(today)
        assertTrue(EnergyLayout.small(m, 140f, 200f, 1f).dot > EnergyLayout.small(m, small.first, small.second, 1f).dot)
    }

    @Test fun `with the strap status the title row counts the button`() {
        val m = WidgetSamples.energy(today)
        val line = StatusLine(StrapStatus.StaleSync(0), listOf("Synced Wednesday", "Synced Wed", "Wednesday", "Wed"))
        val w = EnergyLayout.wide(m, wide.first, wide.second, 1f, line)
        assertEquals(StatusButton.SIZE_DP, w.top.height, 0f)
        assertTrue(w.spread.fits(wide.second, EnergyLayout.MIN_GAP_DP))
        // The Small: the button alone.
        assertNull(EnergyLayout.small(m, small.first, small.second, 1f, line).top.words)
    }
}
