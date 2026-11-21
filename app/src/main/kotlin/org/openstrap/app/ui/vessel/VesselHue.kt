package org.openstrap.app.ui.vessel

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.openstrap.app.ui.theme.AppColors

/**
 * One vessel's colours (the vessel design): [c] the liquid's light end and the hue's text colour on the page, [k] its
 * deep end, [d] the empty body of the glass on a dark theme, [ink] what is written over the liquid.
 */
@Immutable
class VesselHue(val c: Color, val k: Color, val d: Color, val ink: Color) {
    /** The empty body: the dark tint on a dark theme, a pale wash of the hue on the light one. Read while drawing. */
    val body: Color get() = if (AppColors.light) c.copy(alpha = 0.16f) else d

    /** [c] as text or a thin line on the page: itself on a dark theme, darkened on cream until it reads. */
    val text: Color get() = AppColors.readable(c)
}

/** The hues, one per meaning: Energy coral, Recovery lime, Sleep violet, Activity cyan, plain cream, good green. */
object VesselHues {
    val Energy = VesselHue(Color(0xFFFF6B5B), Color(0xFFE0453A), Color(0xFF2A1416), Color.White)
    val Recovery = VesselHue(Color(0xFFC6F432), Color(0xFF93C21A), Color(0xFF1A2410), Color(0xFF152006))
    val Sleep = VesselHue(Color(0xFF8B7BFF), Color(0xFF5F4FE6), Color(0xFF1B1A44), Color.White)
    val Activity = VesselHue(Color(0xFF3FD3FF), Color(0xFF13A3D0), Color(0xFF0B2536), Color(0xFF05222E))
    val Neutral = VesselHue(Color(0xFFEDE5D6), Color(0xFFC3B9A6), Color(0xFF1C2236), Color(0xFF14213D))
    val Good = VesselHue(Color(0xFF3DDC84), Color(0xFF22A862), Color(0xFF0E2A1E), Color(0xFF06251A))

    /** Warm amber (sleep awake, warnings, calories) and heart red, for the vessels that are neither of the above. */
    val Amber = VesselHue(Color(0xFFFFB547), Color(0xFFD98A12), Color(0xFF2B2111), Color(0xFF2A1A04))
    val Heart = VesselHue(Color(0xFFFF5A5F), Color(0xFFD63A40), Color(0xFF2A1416), Color.White)

    /** Heart-rate zones 1 to 5, steel to coral, as the workout vessels fill them. */
    val Zones: List<VesselHue> = listOf(
        zone(0xFF7F93B5, 0xFF566A8C, 0xFF171E2E, Color.White),
        zone(0xFF3FD3FF, 0xFF13A3D0, 0xFF0B2536, Color(0xFF05222E)),
        zone(0xFFC6F432, 0xFF93C21A, 0xFF1A2410, Color(0xFF152006)),
        zone(0xFFFFB547, 0xFFD98A12, 0xFF2B2111, Color(0xFF2A1A04)),
        zone(0xFFFF6B5B, 0xFFE0453A, 0xFF2A1416, Color.White),
    )

    private fun zone(c: Long, k: Long, d: Long, ink: Color) = VesselHue(Color(c), Color(k), Color(d), ink)
}
