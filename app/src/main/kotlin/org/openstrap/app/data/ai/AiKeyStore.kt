package org.openstrap.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.openstrap.app.data.settings.SettingsStore

/** What the settings page needs to know about the key: whether there is one, how it is shown, and the switch. */
data class AiKeyState(val enabled: Boolean, val hasKey: Boolean, val masked: String) {
    /** The assistant can be asked: switched on and a key saved. */
    val ready: Boolean get() = enabled && hasKey
}

/**
 * The user's DeepSeek key and the assistant's switch, over [SettingsStore]. The key is kept sealed by [vault] (the
 * Android Keystore in the app), so DataStore never holds it as text, and it is read back only to make a request.
 */
class AiKeyStore(private val settings: SettingsStore, private val vault: AiVault) {
    /** The key as the page shows it: masked, never whole. A sealed text the vault can't open counts as no key. */
    val state: Flow<AiKeyState> = combine(settings.aiEnabled, settings.aiKeySealed) { on, sealed ->
        val key = sealed?.let(vault::open)
        AiKeyState(on, key != null, AiKey.mask(key))
    }.flowOn(Dispatchers.Default)

    /** Whether the assistant is switched on. */
    val enabled: Flow<Boolean> = settings.aiEnabled

    /** Seals and saves [raw] (as [AiKey.clean] reads it); false when it isn't a usable key. */
    suspend fun save(raw: String?): Boolean {
        val key = AiKey.clean(raw) ?: return false
        settings.setAiKeySealed(vault.seal(key))
        return true
    }

    /** The key, for a request: null without one, or when its sealed text can't be opened. */
    suspend fun load(): String? = settings.aiKeySealed.first()?.let(vault::open)

    /** Deletes the key and switches the assistant off, so nothing is left to send. */
    suspend fun delete() {
        settings.setAiKeySealed(null)
        settings.setAiEnabled(false)
    }

    suspend fun setEnabled(on: Boolean) {
        settings.setAiEnabled(on)
    }

    /** True while a key is saved: for turning the switch on only with one. */
    val hasKey: Flow<Boolean> = state.map { it.hasKey }
}
