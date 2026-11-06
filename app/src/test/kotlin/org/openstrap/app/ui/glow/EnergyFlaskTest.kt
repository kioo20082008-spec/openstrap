package org.openstrap.app.ui.glow

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openstrap.app.data.Recovery
import org.openstrap.app.testing.LocaleRule

/** What Today's Energy flask shows in each state: its level and word, the line under the number, Energy at waking. */
class EnergyFlaskTest {
    @get:Rule val phone = LocaleRule(Locale.UK)

    private fun charge(
        latest: Float? = 62.7f, atWake: Float? = 84.4f, anchored: Boolean = true, strapOff: Boolean = false,
    ) = Recovery.ChargeDay(
        points = emptyList(), latest = latest, atWake = atWake, recharged = 38.6f,
        drainedExercise = 30f, drainedStress = 18f, drainedLiving = 12.5f, highest = 92f, lowest = 54f,
        anchored = anchored, strapOffOvernight = strapOff,
    )

    @Test fun `Energy now, cut as every screen shows it, with its word and what was used since waking`() {
        val m = flaskModel(charge())
        assertEquals(62, m.level)
        assertEquals(0.62f, m.fill, 0.0001f)
        assertEquals("Good", m.word)
        // Charge has no forecast of when it runs low, so the line is the Energy card's own, against waking.
        assertEquals("Used 22 since waking", m.line)
        assertEquals(84, m.atWake)
        assertFalse(m.atStart)
    }

    @Test fun `the line follows Energy against waking, and the word its quarter`() {
        assertEquals("Up 6 since waking", flaskModel(charge(latest = 90.2f)).line)
        assertEquals("Same as at waking", flaskModel(charge(latest = 84.9f)).line)
        assertEquals("Now", flaskModel(charge(atWake = null)).line)
        assertEquals("Running low", flaskModel(charge(latest = 24f)).word)
        assertEquals("Full", flaskModel(charge(latest = 100f)).word)
    }

    @Test fun `after a night with the strap off, waking is the estimated start`() {
        val m = flaskModel(charge(strapOff = true))
        assertTrue(m.atStart)
        assertEquals(84, m.atWake)
    }

    @Test fun `before the first night the flask is empty and says why, never the simulation's guess`() {
        val m = flaskModel(charge(anchored = false))
        assertNull(m.level)
        assertNull(m.word)
        assertNull(m.atWake)
        assertEquals(0f, m.fill, 0f)
        assertEquals("Energy starts after your first night. Sleep is what fills it.", m.line)
    }

    @Test fun `without a value yet, or with Recovery not worked out, it waits for heart rate`() {
        val waiting = "Energy shows once the strap has a few hours of heart rate."
        assertEquals(waiting, flaskModel(charge(latest = null)).line)
        assertNull(flaskModel(charge(latest = null)).level)
        val failed = flaskModel(null)
        assertEquals(waiting, failed.line)
        assertNull(failed.level)
        assertNull(failed.atWake)
    }

    @Test fun `the flask fills no further than full or empty`() {
        assertEquals(1f, flaskModel(charge(latest = 120f)).fill, 0f)
        assertEquals(0f, flaskModel(charge(latest = -3f)).fill, 0f)
    }
}
