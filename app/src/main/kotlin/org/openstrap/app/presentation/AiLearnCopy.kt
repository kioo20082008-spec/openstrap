package org.openstrap.app.presentation

import org.openstrap.app.R
import org.openstrap.app.data.ai.AiClient
import org.openstrap.app.data.ai.AiFailure
import org.openstrap.app.data.ailearn.AiLearnState
import org.openstrap.app.data.ailearn.AiLearning
import org.openstrap.app.data.ailearn.BodyProfile
import org.openstrap.app.data.ailearn.LearnPayload
import org.openstrap.app.data.ailearn.Profile
import org.openstrap.app.data.ailearn.ProfileFacts
import org.openstrap.app.data.ailearn.ProfileSection
import org.openstrap.app.data.ailearn.ProposalGate
import org.openstrap.app.data.ailearn.Statement
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.i18n.Strings
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The words of the AI body learning (docs/algorithms/self-check.md): the profile "How your body works" as sentences, what the
 * AI proposed and what the app made of it, the state of the last run, the notification and the consent. Built from the data
 * with no screen, so every rule is tested. The text is in res/values/strings_ailearn.xml, in Modern Standard Arabic like the
 * coach's. What the AI wrote (a statement, a line it learned, its evidence) is shown as it is: [BodyProfile.cleanText] has
 * already cut it down to plain text.
 */
object AiLearnCopy {
    fun title(strings: Strings = Strings.app): String = strings.get(R.string.ailearn_title)

    /** Something the AI wrote, for the screen: its numbers set left to right so Arabic text around them cannot turn them round. */
    fun said(text: String): String = AiCopy.isolateNumbers(text)

    fun channelName(strings: Strings = Strings.app): String = strings.get(R.string.ailearn_channel)

    // ---------------------------------------------------------------------------------------------------------------
    // The profile

    fun sectionTitle(section: ProfileSection, strings: Strings = Strings.app): String = strings.get(
        when (section) {
            ProfileSection.Sleep -> R.string.ailearn_sec_sleep
            ProfileSection.Rhythm -> R.string.ailearn_sec_rhythm
            ProfileSection.SleepHurts -> R.string.ailearn_sec_sleep_hurts
            ProfileSection.Hrv -> R.string.ailearn_sec_hrv
            ProfileSection.RestingHr -> R.string.ailearn_sec_rhr
            ProfileSection.SkinTemp -> R.string.ailearn_sec_temp
            ProfileSection.Breathing -> R.string.ailearn_sec_resp
            ProfileSection.Illness -> R.string.ailearn_sec_illness
            ProfileSection.Recovery -> R.string.ailearn_sec_recovery
            ProfileSection.Implausible -> R.string.ailearn_sec_implausible
        },
    )

    /** The profile's numbers as sentences: the sleep he seems to need, his usual times, how long a hard day takes, his usual HRV and resting rate. */
    fun facts(f: ProfileFacts, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): List<String> = buildList {
        f.needMin?.let { add(strings.format(R.string.ailearn_fact_need, Units.duration(it * SECONDS_A_MINUTE, locale, strings))) }
        when {
            f.bed != null && f.wake != null -> add(strings.format(R.string.ailearn_fact_usual, f.bed, f.wake))
            f.bed != null -> add(strings.format(R.string.ailearn_fact_bed, f.bed))
            f.wake != null -> add(strings.format(R.string.ailearn_fact_wake, f.wake))
        }
        f.recoveryDays?.let { d ->
            val n = d.roundToInt().coerceAtLeast(1)
            add(strings.format(R.string.ailearn_fact_recovery, strings.plural(R.plurals.core_detail_days, n, DetailCopy.numberWord(n, strings))))
        }
        f.hrvMs?.let { add(strings.format(R.string.ailearn_fact_hrv, String.format(locale, "%d", it))) }
        f.rhrBpm?.let { add(strings.format(R.string.ailearn_fact_rhr, String.format(locale, "%d", it))) }
    }

    /** How sure a statement is, and since when it has held: "80% sure, since 3 Mar". */
    fun statementMeta(s: Statement, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String {
        val sure = strings.format(R.string.ailearn_percent, String.format(locale, "%d", s.confidence))
        return s.from?.let { strings.format(R.string.ailearn_stmt_meta_since, sure, DayWords.dayMonth(it, locale)) }
            ?: strings.format(R.string.ailearn_stmt_meta, sure)
    }

    /** The span the profile rests on: "Based on your data from 3 Mar to 20 Nov"; null before it has one. */
    fun span(p: Profile, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String? {
        val from = p.from ?: return null
        val to = p.to ?: return null
        return strings.format(R.string.ailearn_profile_span, DayWords.dayMonthYear(from, locale), DayWords.dayMonthYear(to, locale))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The last run

    /** "Last learned today at 10:42"; null before the first run that learned. */
    fun lastLearned(
        state: AiLearnState, now: Long, zone: ZoneId, use24Hour: Boolean = ClockSetting.use24Hour,
        locale: Locale = Locale.getDefault(), strings: Strings = Strings.app,
    ): String? =
        state.lastOkAt.takeIf { it > 0 }?.let { strings.format(R.string.ailearn_last_at, DayWords.whenDone(it, now, zone, use24Hour, locale, strings)) }

    /** What the last run applied, refused and sent; null before the first run that learned. */
    fun runSummary(state: AiLearnState, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String? {
        if (state.lastOkAt <= 0) return null
        val kb = ((state.sentBytes + BYTES_ROUNDING) / BYTES_A_KB).coerceAtLeast(1)
        return strings.format(
            R.string.ailearn_run_summary, String.format(locale, "%d", state.changed), String.format(locale, "%d", state.refused), String.format(locale, "%d", kb),
        )
    }

    /** Why the last attempt failed, in a sentence; null when it did not. */
    fun failure(state: AiLearnState, strings: Strings = Strings.app): String? = state.failure?.let { failure(it, strings) }

    fun failure(failure: AiFailure, strings: Strings = Strings.app): String = strings.get(
        when (failure) {
            AiFailure.Offline -> R.string.ailearn_fail_offline
            AiFailure.BadKey, AiFailure.NoKey -> R.string.ailearn_fail_key
            AiFailure.NoBalance -> R.string.ailearn_fail_balance
            AiFailure.RateLimit -> R.string.ailearn_fail_rate
            AiFailure.Timeout -> R.string.ailearn_fail_timeout
            AiFailure.Malformed -> R.string.ailearn_fail_malformed
            AiFailure.NoData -> R.string.ailearn_fail_nodata
            AiFailure.Server -> R.string.ailearn_fail_server
            AiFailure.Disabled, AiFailure.Rejected, AiFailure.Unknown -> R.string.ailearn_fail_other
        },
    )

    // ---------------------------------------------------------------------------------------------------------------
    // What the AI proposed

    /** The name of the knob (or the sleep need) whose key is [key], as the person reads it. */
    fun knobName(key: String, strings: Strings = Strings.app): String = strings.get(
        when (key) {
            Knob.Alert.key -> R.string.ailearn_knob_alert
            Knob.Active.key -> R.string.ailearn_knob_active
            Knob.Rest.key -> R.string.ailearn_knob_rest
            Knob.Cap.key -> R.string.ailearn_knob_cap
            Knob.Resting.key -> R.string.ailearn_knob_rhr
            ProposalGate.SLEEP_NEED_KEY -> R.string.ailearn_knob_sleep_need
            else -> R.string.ailearn_knob_other
        },
    )

    fun reason(r: ProposalGate.Reason, strings: Strings = Strings.app): String = strings.get(
        when (r) {
            ProposalGate.Reason.NoEvidence -> R.string.ailearn_reason_evidence
            ProposalGate.Reason.LowConfidence -> R.string.ailearn_reason_confidence
            ProposalGate.Reason.NotANumber -> R.string.ailearn_reason_number
            ProposalGate.Reason.Undone -> R.string.ailearn_reason_undone
            ProposalGate.Reason.Worse -> R.string.ailearn_reason_worse
            ProposalGate.Reason.NoReplay -> R.string.ailearn_reason_replay
        },
    )

    /** The incident's sentence: what the AI asked for, what the app made of it, and the evidence the AI gave. */
    fun seen(i: SelfLearning.Incident, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String {
        val why = i.note.ifBlank { null }?.let(::said) ?: strings.get(R.string.ailearn_why_none)
        fun n(at: Int) = i.nums.getOrNull(at) ?: 0f
        return when {
            i.check == NEED_CHECK -> strings.format(R.string.ailearn_inc_need, Units.duration(n(0).roundToInt() * SECONDS_A_MINUTE, locale, strings), why)
            i.check.startsWith(REFUSED_PREFIX) -> {
                val reason = ProposalGate.Reason.entries.getOrNull(n(0).roundToInt()) ?: ProposalGate.Reason.NoEvidence
                strings.format(R.string.ailearn_inc_refused, knobName(i.check.removePrefix(REFUSED_PREFIX), strings), reason(reason, strings), why)
            }
            else -> buildString {
                append(strings.format(R.string.ailearn_inc_applied, why))
                if (n(2) > 0f) append(' ').append(strings.get(R.string.ailearn_inc_limited))
                if (n(3) > 0f) append(' ').append(strings.get(R.string.ailearn_inc_clamped))
            }
        }
    }

    /** The sleep need an incident offered, in minutes; null for any other. */
    fun offeredNeed(i: SelfLearning.Incident): Int? = i.takeIf { it.check == NEED_CHECK }?.nums?.firstOrNull()?.roundToInt()

    // ---------------------------------------------------------------------------------------------------------------
    // The notification

    fun noticeTitle(strings: Strings = Strings.app): String = strings.get(R.string.ailearn_section_today)

    /** The line of the notification: what was learned today, or only that Energy was adjusted. */
    fun noticeText(n: AiLearning.Notice, strings: Strings = Strings.app): String =
        if (n.learned.isNotEmpty()) n.learned.joinToString(" ") { said(it) } else strings.get(R.string.ailearn_notice_only_changed)

    /** The expanded notification: the lines it learned, then what it adjusted, one line each. */
    fun noticeBig(n: AiLearning.Notice, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String {
        val lines = ArrayList<String>()
        if (n.learned.isNotEmpty()) lines += n.learned.map { said(it) }
        val adjusted = n.applied.flatMap { SelfCheckCopy.adjustments(it, locale, strings) }
        when {
            adjusted.isNotEmpty() -> lines += strings.get(if (n.learned.isNotEmpty()) R.string.ailearn_notice_changed else R.string.ailearn_notice_only_changed)
            n.learned.isEmpty() -> lines += strings.get(R.string.ailearn_notice_only_changed)
        }
        lines += adjusted
        return lines.joinToString("\n")
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Consent

    fun consentWhere(strings: Strings = Strings.app): String = strings.format(R.string.ailearn_consent_where, AiClient.HOST)

    /**
     * What is sent, how much, where, and what it may change, for the settings page and the learning page: the headline
     * first, then the points. What is never sent is the assistant's line ([R.string.ai_consent_never]).
     */
    fun consentLines(locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): List<String> = listOf(
        consentWhere(strings),
        strings.get(R.string.ailearn_consent_history),
        strings.get(R.string.ailearn_consent_profile),
        strings.format(
            R.string.ailearn_consent_size,
            String.format(locale, "%d", LearnPayload.MAX_BYTES / BYTES_A_KB),
            String.format(locale, "%d", BodyProfile.MAX_BYTES / BYTES_A_KIBI),
        ),
        strings.get(R.string.ailearn_consent_changes),
        strings.get(R.string.ai_consent_never),
    )

    private const val NEED_CHECK = SelfLearning.AI_PREFIX + ProposalGate.SLEEP_NEED_KEY
    private const val REFUSED_PREFIX = SelfLearning.AI_PREFIX + "no."
    private const val SECONDS_A_MINUTE = 60L
    private const val BYTES_A_KB = 1000
    private const val BYTES_A_KIBI = 1024
    private const val BYTES_ROUNDING = 500
}
