package org.openstrap.app.presentation

import org.openstrap.app.R
import org.openstrap.app.data.ChargeTuning
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.i18n.Strings
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The words of the self-check (docs/algorithms/self-check.md): what a check saw, what was adjusted and the notification
 * that says so. Built from an incident's numbers, in the order each check documents them, so every rule is tested without
 * a screen. The text is in res/values/strings_selfcheck.xml. The Arabic is Modern Standard Arabic, like the coach's.
 */
object SelfCheckCopy {
    /** How an incident is shown on the page. */
    enum class Label { Applied, Faded, Noted, Undone }

    fun label(i: SelfLearning.Incident, today: LocalDate): Label = when (i.status) {
        SelfLearning.Status.Applied -> if (i.active(today)) Label.Applied else Label.Faded
        SelfLearning.Status.Informed -> Label.Noted
        SelfLearning.Status.Undone -> Label.Undone
    }

    fun labelText(l: Label, strings: Strings = Strings.app): String = strings.get(
        when (l) {
            Label.Applied -> R.string.selfcheck_status_applied
            Label.Faded -> R.string.selfcheck_status_faded
            Label.Noted -> R.string.selfcheck_status_noted
            Label.Undone -> R.string.selfcheck_status_undone
        },
    )

    /** The You page row's value: on or off. */
    fun rowSummary(on: Boolean, strings: Strings = Strings.app): String =
        strings.get(if (on) R.string.you_value_on else R.string.you_value_off)

    /** What the check saw, in a sentence, from [nums] in the order the check [check] documents. */
    fun seen(check: String, nums: List<Float>, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String {
        fun n(i: Int) = nums.getOrNull(i) ?: 0f
        fun whole(i: Int) = String.format(locale, "%d", n(i).roundToInt())
        fun count(i: Int) = Units.count(n(i).roundToInt(), locale)
        fun time(i: Int) = Units.duration(n(i).roundToInt() * 60L, locale, strings)
        fun f(id: Int, vararg args: Any) = strings.format(id, *args)
        return when (check) {
            "energy.stuck_empty" -> f(R.string.selfcheck_seen_stuck_empty, time(0), count(1))
            "energy.stuck_full" -> f(R.string.selfcheck_seen_stuck_full, time(0), count(1))
            "energy.low_wake" -> f(R.string.selfcheck_seen_low_wake, whole(0), time(1))
            "energy.drain_fast" -> f(R.string.selfcheck_seen_drain_fast, whole(0))
            "energy.alert_streak" -> f(R.string.selfcheck_seen_alert_streak, strings.plural(R.plurals.core_detail_nights, n(0).roundToInt(), DetailCopy.numberWord(n(0).roundToInt(), strings)))
            "heart.sitting_as_effort" -> f(R.string.selfcheck_seen_sitting, time(0))
            "heart.peak_over_max" -> f(R.string.selfcheck_seen_peak, time(0), whole(1))
            "steps.no_response" -> f(R.string.selfcheck_seen_steps_no_response, count(0))
            "steps.mostly_false" -> f(R.string.selfcheck_seen_steps_false, count(0))
            "sleep.implausible" -> f(R.string.selfcheck_seen_sleep_odd, time(0))
            "sleep.score_gap" -> f(R.string.selfcheck_seen_sleep_gap, String.format(locale, "%d", abs(n(1)).roundToInt()))
            "sleep.midpoint_jump" -> f(R.string.selfcheck_seen_sleep_midpoint, time(0))
            "readiness.jump" -> f(R.string.selfcheck_seen_ready_jump, String.format(locale, "%d", abs(n(0)).roundToInt()))
            "readiness.stuck" -> f(R.string.selfcheck_seen_ready_stuck, whole(1))
            "calories.pal" -> f(R.string.selfcheck_seen_calories, count(0))
            "strap.gaps" -> f(R.string.selfcheck_seen_strap_gaps, time(0), strings.plural(R.plurals.core_detail_days, n(1).roundToInt(), DetailCopy.numberWord(n(1).roundToInt(), strings)))
            "strap.battery" -> f(R.string.selfcheck_seen_battery, String.format(locale, "%d%%", n(0).roundToInt()))
            else -> strings.get(R.string.selfcheck_seen_generic)
        }
    }

    fun seen(i: SelfLearning.Incident, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String =
        seen(i.check, i.nums, locale, strings)

    /** One knob at [value], in words: "The drain of Energy while resting is scaled to 75%". */
    fun adjustment(knob: Knob, value: Float, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String {
        fun percent() = String.format(locale, "%d%%", (value * 100f).roundToInt())
        return when (knob) {
            Knob.Alert -> strings.format(R.string.selfcheck_adj_alert, percent())
            Knob.Active -> strings.format(R.string.selfcheck_adj_active, percent())
            Knob.Rest -> strings.format(R.string.selfcheck_adj_rest, percent())
            Knob.Cap -> strings.format(R.string.selfcheck_adj_cap, Units.duration(value.roundToInt() * 60L, locale, strings))
            Knob.Resting -> strings.format(
                R.string.selfcheck_adj_rhr,
                if (value % 1f == 0f) String.format(locale, "%d", value.roundToInt()) else String.format(locale, "%.1f", value),
            )
        }
    }

    /** What an incident changed, one line a knob; empty when it only noted. */
    fun adjustments(i: SelfLearning.Incident, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): List<String> =
        i.changes.map { adjustment(it.knob, it.to, locale, strings) }

    /** What is in effect now: one line for each knob that is off its published value in [learned]. */
    fun inEffect(learned: ChargeTuning, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): List<String> =
        Knob.entries.filter { it.read(learned) != it.default }.map { adjustment(it, it.read(learned), locale, strings) }

    fun channelName(strings: Strings = Strings.app): String = strings.get(R.string.selfcheck_channel)

    /** The notification's title: that something was adjusted, or only noticed. */
    fun noticeTitle(i: SelfLearning.Incident, strings: Strings = Strings.app): String =
        strings.get(if (i.status == SelfLearning.Status.Applied) R.string.selfcheck_notice_applied else R.string.selfcheck_notice_noted)

    /** The notification's line: what was seen, then that the calculation was adjusted, or that nothing was. */
    fun noticeText(i: SelfLearning.Incident, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String {
        val note = strings.get(if (i.status == SelfLearning.Status.Applied) R.string.selfcheck_note_adjusted else R.string.selfcheck_note_only)
        return seen(i, locale, strings) + " " + note
    }
}
