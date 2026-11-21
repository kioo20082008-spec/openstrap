package org.openstrap.app.widget

import kotlin.math.roundToInt

/*
 * The «fill» look's sums, apart from drawing so they test on the JVM: how high a widget's liquid stands for its value,
 * the level steps its bitmaps are cached by, and whether the number sits on the liquid (and so takes dark ink on lime).
 */
object FillLevel {
    /** The least liquid a value shows, as a share of the fill area: a sliver, so 0 isn't an empty card (the mockup's 6%). */
    const val MIN = 0.06f

    /** Levels are drawn and cached in steps of 1%: a refresh with the same number reuses its bitmap. */
    const val STEPS = 100

    /**
     * How full the fill area is for [value] of [max], 0 to 1: clamped to the area, at least [MIN] for any value; 0 (no
     * liquid at all) for none yet. A [max] of 0 or less counts as reached.
     */
    fun of(value: Float?, max: Float): Float {
        if (value == null || value.isNaN()) return 0f
        if (max <= 0f) return 1f
        return (value / max).coerceIn(0f, 1f).coerceAtLeast(MIN)
    }

    /** A score or charge out of 100: Recovery, Sleep, Energy. */
    fun percent(value: Int?): Float = of(value?.toFloat(), 100f)

    /** Steps as their share of the [goal], full from the goal on. */
    fun steps(steps: Int, goal: Int): Float = of(steps.toFloat(), goal.toFloat())

    /** [level] in whole steps of 1/[STEPS], what a bitmap is drawn and cached at. */
    fun bucket(level: Float): Int = (level.coerceIn(0f, 1f) * STEPS).roundToInt()

    /** The level a [bucket] stands for. */
    fun ofBucket(bucket: Int): Float = bucket.coerceIn(0, STEPS) / STEPS.toFloat()

    /** Where the liquid's surface stands between the fill area's [top] and [bottom] (any unit, y down) at [level]. */
    fun surface(top: Float, bottom: Float, level: Float): Float = top + (bottom - top) * (1f - level.coerceIn(0f, 1f))

    /**
     * Whether a number whose middle stands [middleFromBottom] above the card's bottom sits on the liquid of a fill area
     * [areaHeight] high at [level]: the surface is above its middle (the mockup's 30% for a Small).
     */
    fun onFill(level: Float, middleFromBottom: Float, areaHeight: Float): Boolean =
        areaHeight > 0f && level > 0f && level * areaHeight >= middleFromBottom

    /**
     * Whether the number takes the dark ink: only on a light liquid (Recovery's lime, where white doesn't read) and only
     * once it sits on it ([onFill]). Every other liquid and the dark card above take white.
     */
    fun darkInk(lightFill: Boolean, level: Float, middleFromBottom: Float, areaHeight: Float): Boolean =
        lightFill && onFill(level, middleFromBottom, areaHeight)
}
