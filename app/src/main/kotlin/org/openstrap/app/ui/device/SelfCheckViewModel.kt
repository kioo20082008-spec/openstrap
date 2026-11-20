package org.openstrap.app.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.data.settings.SettingsStore
import java.time.LocalDate

/**
 * The self-learning page (docs/algorithms/self-check.md): the switch, the incidents and what is in effect now. Every
 * change goes through [SettingsStore.updateSelfCheck], so a tap and a background pass never overwrite each other.
 */
class SelfCheckViewModel(private val settings: SettingsStore) : ViewModel() {
    data class Ui(
        val on: Boolean = true,
        val state: SelfLearning.State = SelfLearning.State.Empty,
        val today: LocalDate = LocalDate.now(),
    )

    val state: StateFlow<Ui> = combine(settings.selfCheckOn, settings.selfCheck) { on, st -> Ui(on, st, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), Ui())

    fun setOn(on: Boolean) {
        viewModelScope.launch { settings.setSelfCheckOn(on) }
    }

    /** Takes incident [id]'s change back. */
    fun undo(id: Int) {
        viewModelScope.launch { settings.updateSelfCheck { SelfLearning.undo(it, id) } }
    }

    /** Removes incident [id] from the list. */
    fun remove(id: Int) {
        viewModelScope.launch { settings.updateSelfCheck { SelfLearning.dismiss(it, id, LocalDate.now()) } }
    }

    /** Takes every change back. */
    fun resetAll() {
        viewModelScope.launch { settings.updateSelfCheck { SelfLearning.resetAll(it) } }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
