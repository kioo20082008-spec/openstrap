package org.openstrap.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.settings.AppTheme

/** The themes' tokens: only Cream light is light, and a metric's hue is taken dark enough to read there, never on a dark one. */
class AppColorsTest {
    @After fun reset() {
        AppColors.theme = AppTheme.Default
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    @Test fun `only Cream light is light, and the switch follows the theme`() {
        assertEquals(listOf(AppTheme.Cream), AppTheme.entries.filter { AppColors.of(it).light })
        AppColors.theme = AppTheme.Cream
        assertTrue(AppColors.light)
        assertEquals(AppPalette.Navy, AppColors.ink)
        AppColors.theme = AppTheme.Navy
        assertFalse(AppColors.light)
        assertEquals(Color(0xFFF4EDE0), AppColors.ink)
    }

    @Test fun `ink reads on its own page in every theme`() {
        for (t in AppTheme.entries) {
            val c = AppColors.of(t)
            assertTrue("$t", contrast(c.ink, c.page) >= 7f)
        }
    }

    @Test fun `a metric hue is kept on a dark theme and darkened to read on cream`() {
        AppColors.theme = AppTheme.Navy
        assertEquals(MetricColors.steps, AppColors.readable(MetricColors.steps))
        AppColors.theme = AppTheme.Cream
        for (hue in listOf(MetricColors.steps, MetricColors.calories, MetricColors.warn, MetricColors.hrv)) {
            assertTrue("$hue", contrast(AppColors.readable(hue), AppColors.page) >= 3f)
        }
        // Ink already reads, so it stays as it is.
        assertEquals(AppColors.ink, AppColors.readable(AppColors.ink))
    }
}
