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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openstrap.app.data.selfcheck.Decision
import org.openstrap.app.data.selfcheck.Domain
import org.openstrap.app.data.selfcheck.Finding
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.Plan
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.data.selfcheck.Severity
import java.time.LocalDate

/** The self-check's switch (on by default) and memory, kept in settings, and what forgetting the strap leaves of them. */
class SelfCheckSettingsTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("settings.preferences_pb").also { it.delete() } }
    }
    private val settings by lazy { SettingsStore.of(store) }

    @After fun stop() = scope.cancel()

    private val today = LocalDate.of(2026, 11, 18)
    private fun decision(check: String) = Decision(Finding(check, Domain.Energy, Severity.Medium, today, listOf(300f), Plan.Inform), mapOf(Knob.Cap to 60f))

    @Test fun `it is on and empty until something happens`() = runTest {
        assertEquals(true, settings.selfCheckOn.first())
        assertEquals(SelfLearning.State.Empty, settings.selfCheck.first())
    }

    @Test fun `the switch and the state are kept`() = runTest {
        settings.setSelfCheckOn(false)
        settings.updateSelfCheck { SelfLearning.settle(it, listOf(decision("a.a")), 1_800_000_000L, today) }
        assertEquals(false, settings.selfCheckOn.first())
        val kept = settings.selfCheck.first()
        assertEquals(1, kept.incidents.size)
        assertEquals(60, SelfLearning.learned(kept, today).sustainedCap)
    }

    @Test fun `updates apply one after the other, each to what the last left`() = runTest {
        settings.updateSelfCheck { SelfLearning.settle(it, listOf(decision("a.a")), 1L, today) }
        settings.updateSelfCheck { SelfLearning.settle(it, listOf(decision("b.b")), 2L, today) }
        val id = settings.selfCheck.first().incidents.first { it.check == "a.a" }.id
        settings.updateSelfCheck { SelfLearning.undo(it, id) }
        val state = settings.selfCheck.first()
        assertEquals(2, state.incidents.size)
        assertEquals(SelfLearning.Status.Undone, state.incidents.first { it.id == id }.status)
        assertEquals(SelfLearning.Status.Applied, state.incidents.first { it.check == "b.b" }.status)
    }

    @Test fun `forgetting the strap keeps the switch and what was learned`() = runTest {
        settings.setSelfCheckOn(false)
        settings.updateSelfCheck { SelfLearning.settle(it, listOf(decision("a.a")), 1L, today) }
        settings.forgetDevice()
        assertEquals(false, settings.selfCheckOn.first())
        assertEquals(1, settings.selfCheck.first().incidents.size)
        assertTrue(SettingsKeys.SELF_CHECK_ON !in SettingsStore.STRAP_KEYS && SettingsKeys.SELF_CHECK_STATE !in SettingsStore.STRAP_KEYS)
    }
}
