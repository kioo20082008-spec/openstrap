package org.openstrap.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The page's mood: a very faint tint laid over it, following how recovered you are and the time of day. A colour shift
 * and nothing else (no gradient, no motion), worked out from two numbers and kept in [AppColors.mood], so the page
 * draws one more flat rectangle. It stays under 7% of the page's colour on every theme, so Cream's ink reads as before.
 */
object MoodTint {
    /** Recovery's green, amber and Energy's coral, the three zones of the day's plan. */
    private val Green = Color(0xFFC8FF3D)
    private val Amber = Color(0xFFFFB43D)
    private val Coral = Color(0xFFFF5C39)

    /** The hours' light: warm in the morning, none through the day, dusk in the evening, a cool violet at night. */
    private val Morning = Color(0xFFFFB46B)
    private val Dusk = Color(0xFFFF8A5B)
    private val Night = Color(0xFF6B7BFF)

    /** The dark navy a tint is mixed toward on the light theme, so it deepens instead of washing out. */
    private val Ink = Color(0xFF14213D)

    private const val DARK_ALPHA = 0.05f
    private const val LIGHT_ALPHA = 0.06f

    /** How far the hour's light pulls the recovery's colour. */
    private const val HOUR_SHARE = 0.4f

    /** The hour's light for [hour] (0 to 23), or null through the middle of the day. */
    internal fun hourLight(hour: Int): Color? = when (hour) {
        in 5..9 -> Morning
        in 17..19 -> Dusk
        in 20..23, in 0..4 -> Night
        else -> null
    }

    /** The recovery's colour for [score] (the plan's zones: 67 and up, 34 and up, below), or null without one. */
    internal fun zoneColor(score: Int?): Color? = when {
        score == null -> null
        score >= 67 -> Green
        score >= 34 -> Amber
        else -> Coral
    }

    /**
     * The tint for a Recovery [score] (null: none yet) at [hour], as a colour with its own alpha, over the page. On the
     * [light] theme it is mixed toward the navy ink first. Transparent when neither the score nor the hour has a light.
     */
    fun of(score: Int?, hour: Int, light: Boolean): Color {
        val zone = zoneColor(score)
        val time = hourLight(hour)
        val base = when {
            zone != null && time != null -> lerp(zone, time, HOUR_SHARE)
            zone != null -> zone
            time != null -> time
            else -> return Color.Transparent
        }
        return if (light) lerp(base, Ink, 0.35f).copy(alpha = LIGHT_ALPHA) else base.copy(alpha = DARK_ALPHA)
    }
}
