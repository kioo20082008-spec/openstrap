package org.openstrap.app.data.ai

/**
 * The user's own DeepSeek API key as typed or pasted: cleaned up, checked for shape and masked for display. Pure.
 * The key is never logged and never shown whole after it is saved.
 */
object AiKey {
    /** DeepSeek keys are "sk-" and then a run of letters and digits; this is only a shape check, the server decides. */
    private const val PREFIX = "sk-"
    private const val MIN_LENGTH = 16
    private const val MAX_LENGTH = 200

    /** Characters kept at each end of the masked key. */
    private const val SHOWN_START = 3
    private const val SHOWN_END = 4

    /** The dots between them. */
    private const val MASK_DOTS = 8
    private const val DOT = '•'

    private val keyChars = Regex("[A-Za-z0-9_\\-]+")

    /**
     * [raw] as a key: surrounding spaces, line breaks, quotes and a pasted "Bearer " removed. Null when nothing that
     * can be a key is left (empty, spaces inside, other characters, too short or too long).
     */
    fun clean(raw: String?): String? {
        var s = raw?.trim().orEmpty().trim('"', '\'', '`', '“', '”').trim()
        if (s.startsWith("bearer", ignoreCase = true)) s = s.substring("bearer".length).trim()
        if (s.length !in MIN_LENGTH..MAX_LENGTH) return null
        if (!keyChars.matches(s)) return null
        return s
    }

    /** Whether [key] has the shape of a DeepSeek key. */
    fun looksValid(key: String?): Boolean = key != null && key.startsWith(PREFIX) && clean(key) == key

    /**
     * [key] for the screen: its first three characters, dots, and its last four ("sk-••••••••ab12"). A key too short to
     * hide anything shows only dots, so the mask never gives away a short key.
     */
    fun mask(key: String?): String {
        if (key.isNullOrEmpty()) return ""
        val dots = DOT.toString().repeat(MASK_DOTS)
        if (key.length < SHOWN_START + SHOWN_END + MASK_DOTS) return dots
        return key.take(SHOWN_START) + dots + key.takeLast(SHOWN_END)
    }
}
