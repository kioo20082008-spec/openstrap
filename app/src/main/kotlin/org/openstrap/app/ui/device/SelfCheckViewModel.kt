package org.openstrap.app.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openstrap.app.data.ai.AiKeyStore
import org.openstrap.app.data.ailearn.AiLearnState
import org.openstrap.app.data.ailearn.AiLearningKeeper
import org.openstrap.app.data.ailearn.BodyProfile
import org.openstrap.app.data.ailearn.Profile
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.data.settings.SettingsStore
import java.time.LocalDate

/**
 * The self-learning page (docs/algorithms/self-check.md): the switch, the incidents and what is in effect now, and the AI body
 * learning's part ([AiUi]). Every change to the self-check goes through [SettingsStore.updateSelfCheck], so a tap and a
 * background pass never overwrite each other. [appScope] runs what must not stop when the page is left: a request to DeepSeek
 * that was started, and clearing what was learned.
 */
class SelfCheckViewModel(
    private val settings: SettingsStore,
    private val keeper: AiLearningKeeper,
    keys: AiKeyStore,
    private val appScope: CoroutineScope,
) : ViewModel() {
    /** The AI body learning as the page shows it. */
    data class AiUi(
        /** The smart assistant is on with a key saved: the learning can run. */
        val ready: Boolean = false,
        /** The learning's own switch as chosen (on until it is turned off). */
        val switch: Boolean = true,
        val profile: Profile? = null,
        val state: AiLearnState = AiLearnState.Empty,
        val running: Boolean = false,
        /** The person's own sleep need setting, minutes; null for the automatic one. */
        val sleepNeedSetting: Int? = null,
    ) {
        /** The learning is on: the assistant is ready and the switch is not off. */
        val on: Boolean get() = ready && switch

        /** The sleep need the AI suggested within the last week, when it is not already the person's setting. */
        fun offeredNeed(today: LocalDate): Int? = state.sleepNeedMin?.takeIf { minutes ->
            state.sleepNeedDay?.let { !it.isBefore(today.minusDays(NEED_FRESH_DAYS)) } == true && minutes != sleepNeedSetting
        }
    }

    data class Ui(
        val on: Boolean = true,
        val state: SelfLearning.State = SelfLearning.State.Empty,
        val today: LocalDate = LocalDate.now(),
        val ai: AiUi = AiUi(),
    )

    private val ai = combine(keys.state, settings.aiLearnOn, settings.aiLearnProfile, settings.aiLearnState, keeper.running) { key, on, profile, state, running ->
        AiUi(key.ready, on ?: true, BodyProfile.decode(profile), state, running)
    }

    val state: StateFlow<Ui> = combine(settings.selfCheckOn, settings.selfCheck, ai, settings.profile) { on, st, aiUi, profile ->
        Ui(on, st, LocalDate.now(), aiUi.copy(sleepNeedSetting = profile.sleepNeedMinutes))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), Ui())

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

    /** The AI body learning's own switch. */
    fun setAiOn(on: Boolean) {
        viewModelScope.launch { settings.setAiLearnOn(on) }
    }

    /** "Learn now": a run now, which carries on if the page is left. */
    fun learnNow() {
        appScope.launch { keeper.learnNow() }
    }

    /** "Clear what it learned": the profile and everything the AI proposed. */
    fun clearLearned() {
        appScope.launch { keeper.clear() }
    }

    /** Makes the sleep need the AI suggested the person's own setting: the one place the learning's suggestion becomes a setting, and only on a tap. */
    fun applySleepNeed(minutes: Int) {
        viewModelScope.launch { settings.saveProfile(settings.profile.first().copy(sleepNeedMinutes = minutes)) }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}

/** A sleep need the AI suggested is offered for this many days. */
private const val NEED_FRESH_DAYS = 7L
