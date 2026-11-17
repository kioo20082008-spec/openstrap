package org.openstrap.app.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openstrap.app.data.ai.AiAssistant
import org.openstrap.app.data.ai.AiCached
import org.openstrap.app.data.ai.AiFailure
import org.openstrap.app.data.ai.AiKey
import org.openstrap.app.data.ai.AiKeyState
import org.openstrap.app.data.ai.AiResult
import org.openstrap.app.data.catching

/**
 * The smart assistant's settings page and Analysis screen. It never asks by itself: [analyse] runs when the Analysis
 * screen's button is tapped, [test] when Test key is. An analysis lives as long as the activity, so leaving the screen
 * doesn't lose it, and [cancel] stops it.
 */
class AiViewModel(private val ai: AiAssistant) : ViewModel() {
    /** The key test's progress and result. */
    sealed interface Test {
        data object Idle : Test
        data object Running : Test
        data object Works : Test
        data class Failed(val failure: AiFailure, val code: Int?) : Test
    }

    /** Why a typed key wasn't saved. */
    enum class SaveError { Invalid, Failed }

    /** A failed analysis: why, and the HTTP status when there was one. */
    data class Failure(val failure: AiFailure, val code: Int?)

    private data class Local(
        val cached: AiCached? = null,
        val running: Boolean = false,
        val failure: Failure? = null,
        val test: Test = Test.Idle,
        val saveError: SaveError? = null,
    )

    /** Everything the two screens draw. */
    data class Ui(
        val key: AiKeyState = AiKeyState(enabled = false, hasKey = false, masked = ""),
        val cached: AiCached? = null,
        val running: Boolean = false,
        val failure: Failure? = null,
        val test: Test = Test.Idle,
        val saveError: SaveError? = null,
    )

    private val local = MutableStateFlow(Local())
    private var run: Job? = null
    private var testRun: Job? = null

    val state: StateFlow<Ui> = combine(ai.keys.state, local) { key, l ->
        Ui(key, l.cached, l.running, l.failure, l.test, l.saveError)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), Ui())

    init {
        // The last analysis kept, read once off the main thread.
        viewModelScope.launch { catching { ai.results.latest() }.getOrNull()?.let { c -> local.update { it.copy(cached = c) } } }
    }

    /** Saves [raw] as the key: sealed in the key store, or a [SaveError] saying why not. */
    fun saveKey(raw: String) {
        if (AiKey.clean(raw) == null) {
            local.update { it.copy(saveError = SaveError.Invalid) }
            return
        }
        viewModelScope.launch {
            val saved = catching { ai.keys.save(raw) }.getOrDefault(false)
            local.update { it.copy(saveError = if (saved) null else SaveError.Failed, test = Test.Idle, failure = null) }
        }
    }

    fun clearSaveError() = local.update { it.copy(saveError = null) }

    /** Deletes the key and switches the assistant off. The last analysis stays. */
    fun deleteKey() {
        testRun?.cancel()
        viewModelScope.launch {
            catching { ai.keys.delete() }
            local.update { it.copy(test = Test.Idle, failure = null, saveError = null) }
        }
    }

    fun setEnabled(on: Boolean) {
        viewModelScope.launch { catching { ai.keys.setEnabled(on) } }
        if (!on) cancel()
    }

    /** Asks DeepSeek whether the saved key works, without spending anything. */
    fun test() {
        if (local.value.test == Test.Running) return
        testRun = viewModelScope.launch {
            local.update { it.copy(test = Test.Running) }
            val key = ai.keys.load()
            val result = if (key == null) AiResult.Failed(AiFailure.NoKey) else ai.check(key)
            local.update {
                it.copy(
                    test = when (result) {
                        is AiResult.Ok -> Test.Works
                        is AiResult.Failed -> Test.Failed(result.failure, result.code)
                    },
                )
            }
        }
    }

    /** Analyses now, once: the button's work. A second tap while one runs does nothing. */
    fun analyse() {
        if (local.value.running) return
        run = viewModelScope.launch {
            local.update { it.copy(running = true, failure = null) }
            try {
                when (val result = ai.analyse()) {
                    is AiResult.Ok -> local.update { it.copy(cached = result.value, failure = null) }
                    is AiResult.Failed -> local.update { it.copy(failure = Failure(result.failure, result.code)) }
                }
            } finally {
                local.update { it.copy(running = false) }
            }
        }
    }

    /** Stops the analysis that is running; nothing is kept from it. */
    fun cancel() {
        run?.cancel()
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
