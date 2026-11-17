package org.openstrap.app.presentation

import androidx.annotation.StringRes
import org.openstrap.app.R
import org.openstrap.app.i18n.Strings

/**
 * The one short sentence under Today's date: how the day looks, said from what Today already worked out (Recovery,
 * its strain alert, Energy), never from a new reading. [choose] picks it and [text] says it.
 */
object TodayStatus {
    enum class Kind(@StringRes val text: Int) {
        /** A green Recovery in the morning or afternoon: go for it. */
        Ready(R.string.today_status_ready),

        /** A middling Recovery: train, but moderate. */
        Steady(R.string.today_status_steady),

        /** A low Recovery: rest. */
        Rest(R.string.today_status_rest),

        /** Signals off your normal ([org.openstrap.app.data.Recovery.Readiness.strainAlert]): ease off whatever the score. */
        Strained(R.string.today_status_strained),

        /** Energy is nearly spent while the day is still going. */
        LowEnergy(R.string.today_status_low_energy),

        /** The evening and night: the day is for winding down. */
        WindDown(R.string.today_status_wind_down),
    }

    /** Recovery from here is green, from [STEADY_FROM] middling: the same zones as the day's plan on Today. */
    const val READY_FROM = 67
    const val STEADY_FROM = 34

    /** Energy at or under this, during the day, says to take a break. */
    const val LOW_ENERGY_AT = 20

    /**
     * The sentence for the day, or null where none is true: a past day, or no [recovery] score (the pairing hero, the
     * first nights and a failed Recovery already say why). A [strain] alert comes first, then the evening's wind down
     * ([endOfDay]), then a nearly spent [energy], then the Recovery's zone.
     */
    fun choose(recovery: Int?, strain: Boolean, energy: Int?, endOfDay: Boolean, isToday: Boolean): Kind? = when {
        !isToday || recovery == null -> null
        strain -> Kind.Strained
        endOfDay -> Kind.WindDown
        energy != null && energy <= LOW_ENERGY_AT -> Kind.LowEnergy
        recovery >= READY_FROM -> Kind.Ready
        recovery >= STEADY_FROM -> Kind.Steady
        else -> Kind.Rest
    }

    fun text(kind: Kind, strings: Strings = Strings.app): String = strings.get(kind.text)
}

/**
 * A metric's level as a word, for where a colour or a ring alone would say it (spec: never colour alone): Great from
 * [TodayStatus.READY_FROM], Good from [TodayStatus.STEADY_FROM], else Low.
 */
object LevelWord {
    fun of(score: Int?, strings: Strings = Strings.app): String? = score?.let {
        strings.get(
            when {
                it >= TodayStatus.READY_FROM -> R.string.level_great
                it >= TodayStatus.STEADY_FROM -> R.string.level_good
                else -> R.string.level_low
            },
        )
    }
}
