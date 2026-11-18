package org.openstrap.app.presentation

import org.openstrap.app.R
import org.openstrap.app.data.ai.AiClient
import org.openstrap.app.data.ai.AiFailure
import org.openstrap.app.data.ai.AiKeyState
import org.openstrap.app.i18n.Strings
import java.time.ZoneId
import java.util.Locale

/** The words of the smart assistant's settings page and Analysis screen, in one place so each is tested without a screen. */
object AiCopy {
    /** The start of a DeepSeek key, said on the page when what was typed isn't one. */
    const val KEY_PREFIX = "sk-"

    /** What went wrong, in a sentence. [code] is the HTTP status when DeepSeek refused with one the sentence names. */
    fun failure(failure: AiFailure, code: Int? = null, strings: Strings = Strings.app): String = when (failure) {
        AiFailure.Disabled -> strings.get(R.string.ai_state_off_body)
        AiFailure.NoKey -> strings.get(R.string.ai_state_no_key_body)
        AiFailure.Offline -> strings.get(R.string.ai_fail_offline)
        AiFailure.BadKey -> strings.get(R.string.ai_fail_bad_key)
        AiFailure.NoBalance -> strings.get(R.string.ai_fail_no_balance)
        AiFailure.RateLimit -> strings.get(R.string.ai_fail_rate_limit)
        AiFailure.Timeout -> strings.get(R.string.ai_fail_timeout)
        AiFailure.Malformed -> strings.get(R.string.ai_fail_malformed)
        AiFailure.NoData -> strings.get(R.string.ai_fail_no_data)
        AiFailure.Server -> strings.get(R.string.ai_fail_server)
        AiFailure.Rejected -> if (code != null) strings.format(R.string.ai_fail_rejected, code) else strings.get(R.string.ai_fail_unknown)
        AiFailure.Unknown -> strings.get(R.string.ai_fail_unknown)
    }

    /** "From 30 days, 14 nights and 9 workouts": how much history the analysis was made from. */
    fun basis(days: Int, nights: Int, workouts: Int, strings: Strings = Strings.app): String = strings.format(
        R.string.ai_basis,
        strings.plural(R.plurals.ai_basis_days, days, days),
        strings.plural(R.plurals.ai_basis_nights, nights, nights),
        strings.plural(R.plurals.ai_basis_workouts, workouts, workouts),
    )

    /** "Updated 10:42", "Updated yesterday 10:42": when the analysis came ([at] and [now] in epoch seconds). */
    fun updated(at: Long, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String =
        strings.format(R.string.ai_updated, DayWords.at(at, now, zone, locale = locale, strings = strings))

    /** The note over an analysis from an earlier day. */
    fun updatedEarlier(at: Long, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String =
        strings.format(R.string.ai_updated_earlier, DayWords.at(at, now, zone, locale = locale, strings = strings))

    /** The You page row's value: on, off, or what is missing. */
    fun rowSummary(state: AiKeyState, strings: Strings = Strings.app): String = strings.get(
        when {
            !state.hasKey -> R.string.ai_row_no_key
            state.enabled -> R.string.you_value_on
            else -> R.string.you_value_off
        },
    )

    /** The note under the key field when what was typed isn't a key. */
    fun keyInvalid(strings: Strings = Strings.app): String = strings.format(R.string.ai_key_invalid, KEY_PREFIX)

    /** Where the data goes, for the consent text. */
    fun consentWhere(strings: Strings = Strings.app): String = strings.format(R.string.ai_consent_where, AiClient.HOST)

    /** A number (with its sign, decimals, colon, slash, range or percent) as a left-to-right run, so Arabic text around it can't turn it round. */
    fun isolateNumbers(text: String): String = NUMBER.replace(text) { "\u2066${it.value}\u2069" }

    private val NUMBER = Regex("[+\\-\u2212]?\\d+(?:[.,:/\\-\u2212]\\d+)*%?")

    /** The saved key's line: where it is kept and how it is shown. */
    fun keySaved(masked: String, strings: Strings = Strings.app): String = strings.format(R.string.ai_key_saved, masked)
}
