package org.openstrap.app.presentation

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.openstrap.app.presentation.TodayStatus.Kind
import org.openstrap.app.testing.LocaleRule
import org.openstrap.app.testing.assertCleanCopy

/** The sentence under Today's date, and the level words beside the rings: which is chosen, and what each says. */
class TodayStatusTest {
    @get:Rule val phone = LocaleRule(Locale.US)

    private fun choose(
        recovery: Int? = 80, strain: Boolean = false, energy: Int? = 70, endOfDay: Boolean = false, isToday: Boolean = true,
    ) = TodayStatus.choose(recovery, strain, energy, endOfDay, isToday)

    @Test fun `the recovery's zone picks the sentence, at the plan's edges`() {
        assertEquals(Kind.Ready, choose(recovery = 67))
        assertEquals(Kind.Steady, choose(recovery = 66))
        assertEquals(Kind.Steady, choose(recovery = 34))
        assertEquals(Kind.Rest, choose(recovery = 33))
        assertEquals(Kind.Rest, choose(recovery = 0))
    }

    @Test fun `no sentence for a past day or without a score, where other heroes say why`() {
        assertNull(choose(isToday = false))
        assertNull(choose(recovery = null))
        assertNull(choose(recovery = null, strain = true, endOfDay = true))
    }

    @Test fun `a strain alert beats a green score, and the evening beats everything but the alert`() {
        assertEquals(Kind.Strained, choose(recovery = 90, strain = true))
        assertEquals(Kind.Strained, choose(recovery = 90, strain = true, endOfDay = true))
        assertEquals(Kind.WindDown, choose(recovery = 90, endOfDay = true, energy = 5))
        assertEquals(Kind.WindDown, choose(recovery = 20, endOfDay = true))
    }

    @Test fun `energy nearly spent in the day says to take a break, whatever the score`() {
        assertEquals(Kind.LowEnergy, choose(recovery = 90, energy = 20))
        assertEquals(Kind.Ready, choose(recovery = 90, energy = 21))
        assertEquals(Kind.Ready, choose(recovery = 90, energy = null))
    }

    @Test fun `every sentence is written and clean`() {
        val all = Kind.entries.map { TodayStatus.text(it) }
        all.forEach { assertNotNull(it) }
        assertEquals(Kind.entries.size, all.toSet().size)
        assertEquals("Today is a rest day", TodayStatus.text(Kind.Rest))
        assertCleanCopy(all)
    }

    @Test fun `a level is one word from the same zones`() {
        assertEquals("Great", LevelWord.of(67))
        assertEquals("Good", LevelWord.of(66))
        assertEquals("Good", LevelWord.of(34))
        assertEquals("Low", LevelWord.of(33))
        assertNull(LevelWord.of(null))
    }
}
