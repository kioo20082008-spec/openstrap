package org.openstrap.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The page's faint mood: a few percent of colour, following recovery and the hour. */
class MoodTintTest {
    @Test fun `no score and an hour with no light is no tint`() {
        assertEquals(Color.Transparent, MoodTint.of(null, 13, light = false))
        assertEquals(Color.Transparent, MoodTint.of(null, 13, light = true))
    }

    @Test fun `a tint is only a few percent on every theme, so ink keeps its contrast`() {
        for (score in listOf(null, 10, 50, 90)) for (hour in 0..23) for (light in listOf(false, true)) {
            val a = MoodTint.of(score, hour, light).alpha
            assertTrue("score $score hour $hour light $light: $a", a <= 0.07f)
        }
    }

    @Test fun `the score picks the zone's colour and the hour only pulls it`() {
        assertEquals(Color(0xFFC8FF3D), MoodTint.zoneColor(67))
        assertEquals(Color(0xFFFFB43D), MoodTint.zoneColor(34))
        assertEquals(Color(0xFFFF5C39), MoodTint.zoneColor(33))
        assertEquals(Color(0xFFC8FF3D).copy(alpha = 0.05f), MoodTint.of(90, 13, light = false))
        assertTrue(MoodTint.of(90, 8, light = false) != MoodTint.of(90, 13, light = false))
    }

    @Test fun `the hours have their light, none through the middle of the day`() {
        assertEquals(null, MoodTint.hourLight(12))
        assertEquals(null, MoodTint.hourLight(16))
        assertTrue(MoodTint.hourLight(7) != null)
        assertTrue(MoodTint.hourLight(18) != null)
        assertTrue(MoodTint.hourLight(23) != null)
        assertTrue(MoodTint.hourLight(2) != null)
    }

    @Test fun `the light theme deepens the tint toward the navy ink`() {
        val dark = MoodTint.of(90, 13, light = false)
        val light = MoodTint.of(90, 13, light = true)
        assertTrue(light.green < dark.green)
    }
}
