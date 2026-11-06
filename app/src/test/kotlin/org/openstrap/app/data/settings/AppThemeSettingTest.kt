package org.openstrap.app.data.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.export.SettingsBackup
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The colour theme as stored: by its key, Navy until picked or when the key is unknown, kept by forgetting the strap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppThemeSettingTest {
    private val settings = SettingsStore(ApplicationProvider.getApplicationContext())

    @Test fun `each theme's key reads back as that theme`() {
        for (theme in AppTheme.entries) assertEquals(theme, AppTheme.fromKey(theme.key))
        assertEquals(listOf("navy", "oled", "cream", "dusk"), AppTheme.entries.map { it.key })
    }

    @Test fun `nothing stored, or a key this version doesn't know, is Navy`() {
        assertEquals(AppTheme.Navy, AppTheme.fromKey(null))
        assertEquals(AppTheme.Navy, AppTheme.fromKey("sepia"))
        // Stored by key, never by the entry's name.
        assertEquals(AppTheme.Navy, AppTheme.fromKey("Cream"))
    }

    @Test fun `a picked theme is kept, through forgetting the strap too`() = runTest {
        settings.setAppTheme(AppTheme.Navy)
        assertEquals(AppTheme.Navy, settings.appTheme.first())
        for (theme in AppTheme.entries) {
            settings.setAppTheme(theme)
            assertEquals(theme, settings.appTheme.first())
        }
        settings.setAppTheme(AppTheme.Cream)
        settings.forgetDevice()
        assertEquals(AppTheme.Cream, settings.appTheme.first())
    }

    @Test fun `a backup carries the theme`() {
        assertTrue(SettingsBackup.carries(SettingsKeys.APP_THEME))
    }
}
