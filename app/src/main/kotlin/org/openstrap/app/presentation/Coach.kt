package org.openstrap.app.presentation

import org.openstrap.app.R
import org.openstrap.app.data.BedtimePlan
import org.openstrap.app.data.BodySignals
import org.openstrap.app.data.LateNight
import org.openstrap.app.data.NapPlan
import org.openstrap.app.data.Recovery
import org.openstrap.app.i18n.Strings
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Your coach: reads the day as it comes in (last night's signals, live heart rate, stress minute by minute, steps
 * against your usual pace, load against your fitness, the strap's battery) and says only what's true right now, with
 * the numbers behind it. Nothing is canned: a tip exists only while its reading does, and its text carries the reading.
 */
object Coach {
    enum class Kind { Illness, HeartHigh, Stress, Hrv, RestingHr, LoadHigh, LoadRoom, Steps, Bedtime, Charge, Good, Fitter, LateNight, Nap }
    enum class Tone { Warn, Calm, Good }
    enum class Action { Breathe, Workout, None }

    /** One thing worth saying now: higher [priority] first, [key] stays the same while the reading behind it does. */
    data class Tip(val kind: Kind, val priority: Int, val tone: Tone, val title: String, val body: String, val action: Action = Action.None) {
        val key: String get() = kind.name
    }

    data class Input(
        val now: Long,
        val zone: ZoneId,
        val isToday: Boolean,
        val readiness: Recovery.Readiness?,
        val loadTrend: Recovery.LoadTrend?,
        val sleepNeed: Int?,
        val steps: Int,
        /** Your usual steps by the end of each hour, cumulative. */
        val usualSteps: List<Float>?,
        val stepGoal: Int,
        val stress: List<ChartPoint>,
        val restingHr: Int?,
        val liveHr: Int?,
        val recording: Boolean,
        /** Your usual time of falling asleep and of waking. */
        val usualBedtime: LocalTime?,
        val wake: LocalTime?,
        /** Resting heart rate over the last weeks, oldest first. */
        val restingTrend: List<Float>,
        val battery: Int?,
        val charging: Boolean,
        /** When last night's sleep began, for the late-night read. */
        val bedStart: LocalTime? = null,
        /** Charge now, a whole number, for the nap. */
        val charge: Int? = null,
        /** A nap already taken today. */
        val napToday: Boolean = false,
    )

    private const val STRESS_HIGH = 60f
    private const val STRESS_WINDOW_S = 45 * 60L
    private const val HEART_ABOVE_REST = 30
    private const val STEPS_A_MINUTE = 100
    private const val BED_TIP_MINUTES = 240

    fun tips(i: Input, strings: Strings = Strings.app): List<Tip> {
        if (!i.isToday) return emptyList()
        val out = mutableListOf<Tip>()
        val time = Instant.ofEpochSecond(i.now).atZone(i.zone).toLocalTime()
        val r = i.readiness
        val details = r?.contributors.orEmpty().associate { it.factor to it.detail }

        // Several body signals off together, the strap's own illness watch.
        r?.bodySignals?.takeIf { it.state != BodySignals.State.Normal && it.signals.isNotEmpty() }?.let { b ->
            out += Tip(
                Kind.Illness, 100, Tone.Warn, strings.get(R.string.coach_illness_title),
                strings.format(R.string.coach_illness_body, b.signals.take(3).joinToString(strings.get(R.string.coach_list_join))),
            )
        }
        // Heart rate high while still: live, the moment it happens.
        val live = i.liveHr
        val rest = i.restingHr
        if (live != null && rest != null && !i.recording && live - rest >= HEART_ABOVE_REST) {
            out += Tip(
                Kind.HeartHigh, 90, Tone.Warn, strings.format(R.string.coach_heart_title, live),
                strings.format(R.string.coach_heart_body, live - rest, rest), Action.Breathe,
            )
        }
        // Stress held high over the last three quarters of an hour, minute by minute from the strap.
        val recent = i.stress.filter { it.x >= i.now - STRESS_WINDOW_S }
        if (recent.size >= 3 && !i.recording) {
            val avg = recent.map { it.y }.average().toFloat()
            if (avg >= STRESS_HIGH) out += Tip(
                Kind.Stress, 80, Tone.Warn, strings.format(R.string.coach_stress_title, avg.roundToInt()),
                strings.format(R.string.coach_stress_body, (i.now - recent.first().x).div(60).toInt()), Action.Breathe,
            )
        }
        // Last night: HRV and resting heart rate against your own normal, and why.
        val asleep = (details[Recovery.Factor.Sleep] as? Recovery.Detail.Asleep)?.minutes
        val need = i.sleepNeed
        val short = if (asleep != null && need != null) need - asleep else null
        // A heavy night: the late bed, short sleep and the body's own signals together, which replace their separate tips.
        val night = LateNight.read(
            i.bedStart, i.usualBedtime, asleep, need,
            (details[Recovery.Factor.HrvBalance] as? Recovery.Detail.Hrv)?.percentVsUsual,
            (details[Recovery.Factor.RestingHr] as? Recovery.Detail.RestingHr)?.vsUsual,
            (details[Recovery.Factor.Temperature] as? Recovery.Detail.Temperature)?.vsUsual,
        )
        val heavy = night.level == LateNight.Level.Heavy
        if (night.level != LateNight.Level.None) {
            val causes = night.causes.joinToString(strings.get(R.string.coach_list_join)) { cause(it, strings) }
            out += Tip(
                Kind.LateNight, 75, Tone.Calm,
                strings.get(if (heavy) R.string.coach_late_title_heavy else R.string.coach_late_title_rough),
                strings.format(if (heavy) R.string.coach_late_body_heavy else R.string.coach_late_body_rough, causes),
            )
        }
        (details[Recovery.Factor.HrvBalance] as? Recovery.Detail.Hrv)?.let { h ->
            when {
                h.percentVsUsual <= -10 && night.level == LateNight.Level.None -> out += Tip(
                    Kind.Hrv, 70, Tone.Calm, strings.format(R.string.coach_hrv_low_title, h.ms, -h.percentVsUsual),
                    if (short != null && short >= 45) strings.format(R.string.coach_hrv_low_sleep, Units.duration(asleep!! * 60L), Units.duration(short * 60L))
                    else strings.get(R.string.coach_hrv_low_body),
                )
                h.percentVsUsual >= 8 && (r?.score ?: 0) >= 67 -> out += Tip(
                    Kind.Good, 40, Tone.Good, strings.format(R.string.coach_hrv_high_title, h.percentVsUsual), strings.get(R.string.coach_hrv_high_body), Action.Workout,
                )
                else -> Unit
            }
        }
        (details[Recovery.Factor.RestingHr] as? Recovery.Detail.RestingHr)?.takeIf { it.vsUsual >= 4f && night.level == LateNight.Level.None }?.let { h ->
            out += Tip(Kind.RestingHr, 65, Tone.Calm, strings.format(R.string.coach_rhr_title, h.bpm, h.vsUsual.roundToInt()), strings.get(R.string.coach_rhr_body))
        }
        // Load: the last week against your fitness (fatigue against the 42-day base).
        i.loadTrend?.takeIf { it.ctl > 5f }?.let { t ->
            val ratio = t.atl / t.ctl
            when {
                ratio >= 1.3f -> out += Tip(Kind.LoadHigh, 60, Tone.Calm, strings.format(R.string.coach_load_high_title, ((ratio - 1) * 100).roundToInt()), strings.get(R.string.coach_load_high_body))
                ratio <= 0.8f && (r?.score ?: 0) >= 67 -> out += Tip(Kind.LoadRoom, 45, Tone.Good, strings.get(R.string.coach_load_room_title), strings.get(R.string.coach_load_room_body), Action.Workout)
                else -> Unit
            }
        }
        // Steps against your usual pace by this hour.
        val usual = i.usualSteps?.let { u ->
            val before = if (time.hour == 0) 0f else u.getOrNull(time.hour - 1) ?: return@let null
            val after = u.getOrNull(time.hour) ?: return@let null
            (before + (after - before) * time.minute / 60f).roundToInt()
        }
        if (usual != null && time.hour in 10..20 && usual > 1000 && i.steps < usual * 0.7f && i.steps < i.stepGoal) {
            val behind = usual - i.steps
            out += Tip(
                Kind.Steps, 50, Tone.Calm, strings.format(R.string.coach_steps_title, Units.count(i.steps), Units.count(usual)),
                strings.format(R.string.coach_steps_body, (behind / STEPS_A_MINUTE).coerceAtLeast(5)),
            )
        }
        // Tonight's bedtime from your need, last night's debt and when you wake, from three hours before it.
        val debt = (details[Recovery.Factor.SleepBalance] as? Recovery.Detail.SleepDebt)?.minutes ?: 0
        val wake = i.wake
        if (need != null && wake != null) {
            val bed = BedtimePlan.bedtime(wake, need, debt, heavy)
            val until = minutesUntil(time, bed)
            if (until in 0..BED_TIP_MINUTES) out += Tip(
                Kind.Bedtime, 55, Tone.Calm, strings.format(R.string.coach_bed_title, clock(bed)),
                if (heavy) strings.format(R.string.coach_bed_heavy, clock(wake))
                else if (debt >= 20) strings.format(R.string.coach_bed_debt, clock(wake), Units.duration(debt.coerceAtMost(60) * 60L))
                else strings.format(R.string.coach_bed_body, clock(wake)),
            )
        }
        // A short nap, only when Charge is low in the early afternoon and it won't cost tonight's sleep.
        val napBed = if (need != null && wake != null) BedtimePlan.bedtime(wake, need, debt, heavy) else i.usualBedtime
        NapPlan.suggest(time, i.charge, debt, short ?: 0, heavy, i.napToday, i.recording, napBed)?.let { plan ->
            out += Tip(
                Kind.Nap, 52, Tone.Calm, strings.format(R.string.coach_nap_title, plan.minutes),
                strings.format(R.string.coach_nap_body, i.charge ?: 0, clock(plan.latestStart)),
            )
        }
        // The strap's battery: the time to charge is now, while you're up and before the night it must record.
        val battery = i.battery
        if (battery != null && !i.charging && battery <= 35) {
            val bedtime = i.usualBedtime ?: wake?.minusMinutes((need ?: 480).toLong())
            val beforeBed = bedtime?.let { minutesUntil(time, it) }
            val recordingNight = time.hour < 6
            when {
                battery <= 15 -> out += Tip(Kind.Charge, 85, Tone.Warn, strings.format(R.string.coach_charge_title, battery), strings.get(R.string.coach_charge_now))
                !recordingNight && beforeBed != null && beforeBed >= 90 -> out += Tip(
                    Kind.Charge, 58, Tone.Calm, strings.format(R.string.coach_charge_title, battery),
                    strings.format(R.string.coach_charge_good, clock(bedtime)),
                )
                else -> Unit
            }
        }
        // Fitter over the weeks: resting heart rate coming down.
        if (i.restingTrend.size >= 14) {
            val first = i.restingTrend.take(7).average()
            val last = i.restingTrend.takeLast(7).average()
            val drop = (first - last).roundToInt()
            if (drop >= 2) out += Tip(Kind.Fitter, 30, Tone.Good, strings.format(R.string.coach_fitter_title, drop), strings.get(R.string.coach_fitter_body))
        }
        return out.sortedByDescending { it.priority }
    }

    private fun cause(c: LateNight.Cause, strings: Strings): String = when (c.reason) {
        LateNight.Reason.LateBed -> strings.format(R.string.coach_late_cause_bed, Units.duration(c.value * 60L))
        LateNight.Reason.ShortSleep -> strings.format(R.string.coach_late_cause_short, Units.duration(c.value * 60L))
        LateNight.Reason.HrvDown -> strings.format(R.string.coach_late_cause_hrv, c.value)
        LateNight.Reason.HeartUp -> strings.format(R.string.coach_late_cause_heart, c.value)
        LateNight.Reason.SkinWarm -> strings.format(R.string.coach_late_cause_skin, "${c.value / 10}.${c.value % 10}")
    }

    private fun minutesUntil(now: LocalTime, at: LocalTime): Int {
        val d = at.toSecondOfDay() - now.toSecondOfDay()
        return (if (d < -6 * 3600) d + 86_400 else d) / 60
    }

    private fun clock(t: LocalTime): String = t.format(DateTimeFormatter.ofPattern("H:mm"))
}
