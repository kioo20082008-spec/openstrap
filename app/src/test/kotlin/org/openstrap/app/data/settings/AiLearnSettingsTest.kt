package org.openstrap.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openstrap.app.data.ai.AiFailure
import org.openstrap.app.data.ailearn.AiLearnState
import org.openstrap.app.data.ailearn.AiLearning
import org.openstrap.app.data.ailearn.BodyProfile
import org.openstrap.app.data.ailearn.sampleProfile
import org.openstrap.app.export.SettingsBackup
import java.time.LocalDate

/** The AI body learning's switch, profile and memory: kept in settings, kept when the strap is forgotten, left out of a backup. */
class AiLearnSettingsTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("settings.preferences_pb").also { it.delete() } }
    }
    private val settings by lazy { SettingsStore.of(store) }

    @After fun stop() = scope.cancel()

    private val today = LocalDate.of(2026, 11, 20)

    @Test fun `nothing is chosen, learned or remembered until something happens`() = runTest {
        assertNull(settings.aiLearnOn.first())
        assertNull(settings.aiLearnProfile.first())
        assertEquals(AiLearnState.Empty, settings.aiLearnState.first())
    }

    @Test fun `the switch is kept as chosen`() = runTest {
        settings.setAiLearnOn(false)
        assertEquals(false, settings.aiLearnOn.first())
        settings.setAiLearnOn(true)
        assertEquals(true, settings.aiLearnOn.first())
    }

    @Test fun `the profile and the state are kept together`() = runTest {
        val profile = BodyProfile.encode(sampleProfile())
        val state = AiLearnState(lastOkDay = today, lastOkAt = 1_800_000_000L, learned = listOf("تعلّمت أن نومك ثابت"), learnedDay = today, sentBytes = 17_000)
        settings.saveAiLearn(profile, state)
        assertEquals(profile, settings.aiLearnProfile.first())
        assertEquals(state, settings.aiLearnState.first())
        settings.saveAiLearn(null, AiLearning.cleared(today))
        assertNull(settings.aiLearnProfile.first())
        assertEquals(AiLearning.cleared(today), settings.aiLearnState.first())
    }

    @Test fun `a change to the state alone leaves the profile`() = runTest {
        val profile = BodyProfile.encode(sampleProfile())
        settings.saveAiLearn(profile, AiLearnState(lastOkDay = today))
        settings.updateAiLearnState { AiLearning.failed(it, AiFailure.Offline, null, 1_800_000_100L, today, manual = false) }
        assertEquals(profile, settings.aiLearnProfile.first())
        assertEquals(AiFailure.Offline, settings.aiLearnState.first().failure)
        assertEquals(today, settings.aiLearnState.first().lastOkDay)
    }

    @Test fun `forgetting the strap keeps what was learned about the person`() = runTest {
        val profile = BodyProfile.encode(sampleProfile())
        settings.saveAiLearn(profile, AiLearnState(lastOkDay = today))
        settings.setAiLearnOn(false)
        settings.forgetDevice()
        assertEquals(profile, settings.aiLearnProfile.first())
        assertEquals(today, settings.aiLearnState.first().lastOkDay)
        assertEquals(false, settings.aiLearnOn.first())
        for (key in listOf(SettingsKeys.AI_LEARN_ON, SettingsKeys.AI_LEARN_PROFILE, SettingsKeys.AI_LEARN_STATE)) assertFalse(key, key in SettingsStore.STRAP_KEYS)
    }

    @Test fun `a backup never carries the switch, the profile or the state`() {
        for (key in listOf(SettingsKeys.AI_LEARN_ON, SettingsKeys.AI_LEARN_PROFILE, SettingsKeys.AI_LEARN_STATE)) assertFalse(key, SettingsBackup.carries(key))
        assertTrue(SettingsKeys.AI_LEARN_PROFILE == "ai_learn_profile")
    }
}
