package org.openstrap.app.widget

import org.openstrap.app.R
import org.openstrap.app.data.Recovery
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.TabCopy
import org.openstrap.app.presentation.chargeShown
import java.time.LocalDate

/**
 * What the Energy widget shows (widgets spec 3.6), the same model at every size: the Small shows the title, Energy
 * now with [word] and the battery bar; the Wide adds Energy at [waking]; the Large adds what the day [figures]
 * recharged and drained. Without a number there's only the title and [waiting], as Today's Energy card says it.
 */
data class EnergyWidgetModel(
    /** Today, which a tap opens the Energy detail on. */
    val date: LocalDate,
    val title: String,
    /** Energy now, whole, as the Energy detail's headline ([chargeShown]); null before the first night or any value. */
    val now: Int?,
    /** "left", beside the number, as Moment's. */
    val word: String,
    /** "At waking 84", or "At start 82" after a night with the strap off; null without one. */
    val waking: String?,
    /** Recharged "+38" and Drained "60", the amounts as the Energy detail writes them; empty without a number. */
    val figures: List<RecoveryFigure>,
    /** In place of the number, in Secondary: Today's Energy line without one ([TabCopy.chargeLine]). */
    val waiting: String?,
) {
    /** The number in dots. */
    val number: String? get() = now?.toString()

    /** How full the battery bar and the light are, 0 to 1. */
    val level: Float get() = (now ?: 0).coerceIn(0, FULL) / FULL.toFloat()

    /** What TalkBack reads for the dotted number: "Energy 62 left". */
    val description: String? get() = now?.let { Strings.app.format(R.string.system_widget_moment_energy, it) }

    companion object {
        /** Energy's top: a full battery. */
        const val FULL = 100

        /**
         * The widget for [today] from its [charge] as Recovery works it out ([org.openstrap.app.data.RecoveryEngine]),
         * null when that failed. The number is the latest charge, shown only once the simulation has seen a night
         * ([Recovery.ChargeDay.anchored]): before, it would only echo the starting guess, so the widget says why instead.
         */
        fun from(today: LocalDate, charge: Recovery.ChargeDay?, strings: Strings = Strings.app): EnergyWidgetModel {
            val title = strings.get(R.string.widget_energy_name)
            val word = strings.get(R.string.system_widget_moment_left)
            val latest = charge?.takeIf { it.anchored }?.latest
            if (charge == null || latest == null) {
                // A failed read waits as a day with no heart rate yet: the first night's line is only for before it.
                val line = TabCopy.chargeLine(null, null, anchored = charge?.anchored ?: true, strings = strings)
                return EnergyWidgetModel(today, title, null, word, null, emptyList(), line)
            }
            // After a night with the strap off, the day restarted from an estimate: "At start", as the Energy detail says.
            val wakingId = if (charge.strapOffOvernight) R.string.system_widget_energy_at_start else R.string.system_widget_energy_at_waking
            val waking = charge.atWake?.toInt()?.let { strings.format(wakingId, it) }
            return EnergyWidgetModel(
                date = today,
                title = title,
                now = chargeShown(latest),
                word = word,
                waking = waking,
                figures = listOf(
                    RecoveryFigure(strings.get(R.string.system_widget_energy_recharged), TabCopy.chargeAmount(charge.recharged, +1)),
                    RecoveryFigure(
                        strings.get(R.string.system_widget_energy_drained),
                        TabCopy.drainedAmount(charge.drainedExercise + charge.drainedStress + charge.drainedLiving),
                    ),
                ),
                waiting = null,
            )
        }
    }
}

/**
 * The sums that fit the Energy widget into its smallest cell at any system font size (spec 2.1), as Steps' and
 * Recovery's: what each size keeps when it can't hold everything and how small the dots go. Widths and heights in dp,
 * at the content size ([WidgetFrame.contentWidth]), text with [WidgetText]'s sums.
 */
object EnergyLayout {
    /** The battery bar: its body's height and corner, and the cap at its full end, dp. */
    const val BAR_DP = 8f
    const val BAR_CORNER_DP = 3f
    const val CAP_WIDTH_DP = 2f
    const val CAP_GAP_DP = 2f

    /** Small: between the number and "left". Wide and Large: between them, and before Energy at waking. */
    const val SMALL_WORD_GAP_DP = 4f
    const val WORD_GAP_DP = 8f
    const val SIDE_GAP_DP = 12f

    /** The least gap between blocks. */
    const val MIN_GAP_DP = 4f

    /** The dots at their least, as Recovery's Small and short Wide. */
    const val SHORT_DOT = 3f

    /**
     * A size laid out: the [top] row, the dots at [dot], whether [word] shows beside (or under) them, whether the
     * Large's [figures] show, and how the blocks share the height ([spread]).
     */
    data class Fit(val top: TopRow, val dot: Float, val word: Boolean, val spread: Spread, val figures: Boolean = false)

    /** The figures' block: a Caption over a Body value, as Recovery's. */
    fun figuresHeight(fontScale: Float): Float = RecoveryLayout.figuresHeight(fontScale)

    /**
     * The Small in [width] by [height] dp: the top row, the number with "left" under it, the battery bar. The dots
     * take the size every widget's number takes there, smaller only when the rest doesn't fit; "left" goes first.
     */
    fun small(m: EnergyWidgetModel, width: Float, height: Float, fontScale: Float, status: StatusLine? = null): Fit {
        val top = TopRow.of(m.title, null, status, width, fontScale, withWords = false)
        val number = m.number.orEmpty()
        fun at(dot: Float, word: Boolean): Fit {
            val d = WidgetDots.fit(number, width, dot)
            val block = WidgetDots.height(d) + (if (word) SMALL_WORD_GAP_DP + WidgetText.line(WidgetText.BODY_SP, fontScale) else 0f)
            return Fit(top, d, word, Spread.of(top.height, block, BAR_DP))
        }
        val words = if (WidgetText.fits(m.word, WidgetText.BODY_SP, width, fontScale)) listOf(true, false) else listOf(false)
        for (word in words) {
            WidgetHero.ladder(WidgetSizes.Small, height).map { at(it, word) }
                .firstOrNull { it.spread.fits(height, MIN_GAP_DP) }?.let { return it }
        }
        return at(SHORT_DOT, false)
    }

    /** The Wide in [width] by [height] dp: the top row, the number with "left" and Energy at waking, the bar. */
    fun wide(m: EnergyWidgetModel, width: Float, height: Float, fontScale: Float, status: StatusLine? = null): Fit =
        fit(m, width, height, fontScale, status, large = false)

    /** The Large: the Wide with what the day recharged and drained under the bar, which goes first when short. */
    fun large(m: EnergyWidgetModel, width: Float, height: Float, fontScale: Float, status: StatusLine? = null): Fit =
        fit(m, width, height, fontScale, status, large = true)

    /** The number row's height at [dot]: the dots, "left" in Body, Energy at waking in Secondary. */
    fun rowHeight(dot: Float, fontScale: Float): Float =
        maxOf(WidgetDots.height(dot), WidgetText.line(WidgetText.BODY_SP, fontScale), WidgetText.line(WidgetText.SECONDARY_SP, fontScale))

    private fun fit(m: EnergyWidgetModel, width: Float, height: Float, fontScale: Float, status: StatusLine?, large: Boolean): Fit {
        val top = TopRow.of(m.title, null, status, width, fontScale, withWords = true)
        val number = m.number.orEmpty()
        // Energy at waking keeps its place on the right: the dots, then "left", give way to it.
        val side = m.waking?.let { SIDE_GAP_DP + WidgetText.fitWidth(it, WidgetText.SECONDARY_SP, fontScale) } ?: 0f
        val wordW = WORD_GAP_DP + WidgetText.fitWidth(m.word, WidgetText.BODY_SP, fontScale)
        fun at(dot: Float, figures: Boolean): Fit {
            val withWord = WidgetDots.fit(number, width - side - wordW, dot)
            val word = withWord >= SHORT_DOT
            val d = if (word) withWord else WidgetDots.fit(number, width - side, dot)
            val spread = Spread.of(top.height, rowHeight(d, fontScale), BAR_DP, if (figures) figuresHeight(fontScale) else null)
            return Fit(top, d, word, spread, figures)
        }
        val figures = large && m.figures.isNotEmpty()
        WidgetHero.ladder(if (large) WidgetSizes.Large else WidgetSizes.Wide, height).map { at(it, figures) }
            .firstOrNull { it.spread.fits(height, MIN_GAP_DP) }?.let { return it }
        return listOf(at(SHORT_DOT, figures), at(SHORT_DOT, false)).firstOrNull { it.spread.fits(height, MIN_GAP_DP) }
            ?: at(SHORT_DOT, false)
    }
}
