package org.openstrap.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.openstrap.app.data.settings.AppTheme

/**
 * One theme's colours: what the pages, cards, text and hairlines are drawn in. Coral stays the accent and the
 * metric hues ([MetricColors]) stay the same in every theme; only what sits around them changes.
 */
@Immutable
class ThemeColors(
    /** Light pages with dark ink: the status and navigation bar icons turn dark. */
    val light: Boolean,
    /** The tabs' sky, top to bottom. */
    val skyTop: Color,
    val skyMid: Color,
    val skyLow: Color,
    /** How strongly a tab's glow shows at the top of its sky, 1 being Navy's. */
    val glow: Float,
    /** How strongly the grain shows over the sky, 1 being Navy's. */
    val grain: Float,
    /** A page over a tab (details, settings), flat. */
    val page: Color,
    /** The workout pages (Ready, live, summary), a step deeper than [page] on a dark theme. */
    val stage: Color,
    /** Text, icons and lines; a lesser one is this at an alpha. */
    val ink: Color,
    /** How much of [ink] a secondary line takes. */
    val mutedAlpha: Float,
    /** Text on a pill filled with [ink] (a page's main button, a chosen chip). */
    val onInk: Color,
    /** A card over the sky, and its edge. */
    val glass: Color,
    val hairline: Color,
    /** A grouped list or a quiet panel, a step quieter than [glass]. */
    val list: Color,
    /** Buttons, chips, pills and fields (spec 2.2's Control surface). */
    val control: Color,
    /** Menus, and what sits on a [control] when it's chosen. */
    val raised: Color,
    /** The floating nav bar's fill and a sheet's glass, both drawn over a blur. */
    val nav: Color,
    val sheet: Color,
    /** The darkening over the page behind a sheet or a popup. */
    val scrim: Color,
    /** The edge of a round glass on the page (Today's Energy flask, the dials). */
    val ring: Color,
)

/**
 * The theme's colours for everything drawn in the app (widgets keep their own). Held in snapshot state rather than a
 * CompositionLocal, so a colour read anywhere, in a composable, a draw block or a top-level getter, follows a switch
 * at once: whatever read it is drawn or composed again. Set from the stored [AppTheme] at the app's root.
 */
object AppColors {
    var theme: AppTheme by mutableStateOf(AppTheme.Default)

    val current: ThemeColors get() = of(theme)

    val light: Boolean get() = current.light
    val ink: Color get() = current.ink
    /** Ink for a secondary line: 55% (50% on OLED black). */
    val muted: Color get() = current.ink.copy(alpha = current.mutedAlpha)
    val onInk: Color get() = current.onInk
    val page: Color get() = current.page
    val stage: Color get() = current.stage
    val glass: Color get() = current.glass
    val hairline: Color get() = current.hairline
    val list: Color get() = current.list
    val control: Color get() = current.control
    val raised: Color get() = current.raised
    val nav: Color get() = current.nav
    val sheet: Color get() = current.sheet
    val scrim: Color get() = current.scrim
    val ring: Color get() = current.ring

    /** Shade under a mark (a scrubbed dot's halo, the dimmed part of a chart): black on a dark theme, the page on the light one. */
    val shade: Color get() = if (current.light) current.page else Color.Black

    /** A well set into a card (a progress track, a dark button on a lit card): black at [alpha] on a dark theme, a navy tint on the light one. */
    fun well(alpha: Float = 0.4f): Color = if (current.light) current.ink.copy(alpha = alpha * 0.18f) else Color.Black.copy(alpha = alpha)

    /** A panel set into a sheet (the info sheet's rows, the header chip): a step off the sheet's own glass. */
    val inset: Color get() = if (current.light) Color.White.copy(alpha = 0.7f) else androidx.compose.ui.graphics.lerp(current.page, current.control, 0.3f)

    /**
     * What the tab bar's fade rises from: black on a dark theme, as the dark designs have it, the sky's foot on the light
     * one, where black would read as a band.
     */
    val fade: Color get() = if (current.light) current.skyLow else Color.Black

    fun of(theme: AppTheme): ThemeColors = when (theme) {
        AppTheme.Navy -> Navy
        AppTheme.Oled -> Oled
        AppTheme.Cream -> Cream
        AppTheme.Dusk -> Dusk
    }

    /**
     * [color] (a metric's hue, a warning amber) as text or a thin line on the page: itself on a dark theme; on a light
     * one taken darker, hue kept, until it reads on cream (3:1, enough for the large and bold type these are set in).
     */
    fun readable(color: Color): Color {
        if (!light || color == Color.Unspecified) return color
        val page = current.page
        if (contrast(color.copy(alpha = 1f), page) >= READABLE) return color
        val (h, s, l0) = hsl(color)
        var l = l0
        var c = color
        // Lightness down in small steps; a dozen reach any hue's dark end.
        repeat(12) {
            l = (l - 0.05f).coerceAtLeast(0.12f)
            c = Color.hsl(h, s, l)
            if (contrast(c, page) >= READABLE) return c.copy(alpha = color.alpha)
        }
        return c.copy(alpha = color.alpha)
    }

    /** [c]'s hue (degrees), saturation and lightness, as [Color.hsl] takes them. */
    private fun hsl(c: Color): Triple<Float, Float, Float> {
        val max = maxOf(c.red, c.green, c.blue)
        val min = minOf(c.red, c.green, c.blue)
        val l = (max + min) / 2f
        val d = max - min
        if (d == 0f) return Triple(0f, 0f, l)
        val s = d / (1f - kotlin.math.abs(2f * l - 1f))
        val h = when (max) {
            c.red -> 60f * (((c.green - c.blue) / d) % 6f)
            c.green -> 60f * ((c.blue - c.red) / d + 2f)
            else -> 60f * ((c.red - c.green) / d + 4f)
        }
        return Triple((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    private const val READABLE = 3f

    private val NavyInk = Color(0xFFF4EDE0)
    private val NavyPage = Color(0xFF14213D)

    /** The original: a deep navy sky with cream ink. */
    private val Navy = ThemeColors(
        light = false,
        skyTop = Color(0xFF18264A), skyMid = NavyPage, skyLow = Color(0xFF0E1830),
        glow = 1f, grain = 1f,
        page = NavyPage, stage = Color.Black,
        ink = NavyInk, mutedAlpha = 0.55f, onInk = Color.Black,
        glass = Color.White.copy(alpha = 0.055f), hairline = Color.White.copy(alpha = 0.10f), list = Color.White.copy(alpha = 0.045f),
        control = Color(0xFF1C2C50), raised = Color(0xFF243760),
        nav = NavyPage.copy(alpha = 0.85f), sheet = Color(0xFF0E1830).copy(alpha = 0.72f),
        scrim = Color.Black.copy(alpha = 0.45f), ring = NavyInk.copy(alpha = 0.3f),
    )

    /** True black for OLED screens: the glow held low, less grain, near-white ink. */
    private val Oled = ThemeColors(
        light = false,
        skyTop = Color(0xFF0A0A0C), skyMid = Color.Black, skyLow = Color.Black,
        glow = 0.44f, grain = 0.4f,
        page = Color.Black, stage = Color.Black,
        ink = Color(0xFFF2F2F2), mutedAlpha = 0.5f, onInk = Color.Black,
        glass = Color.White.copy(alpha = 0.06f), hairline = Color.White.copy(alpha = 0.09f), list = Color.White.copy(alpha = 0.05f),
        control = Color(0xFF1C1C1F), raised = Color(0xFF2A2A2E),
        nav = Color(0xFF141416).copy(alpha = 0.9f), sheet = Color(0xFF111113).copy(alpha = 0.8f),
        scrim = Color.Black.copy(alpha = 0.55f), ring = Color.White.copy(alpha = 0.25f),
    )

    /** Light: a cream sky with navy ink, glass cards in white. */
    private val Cream = ThemeColors(
        light = true,
        skyTop = Color(0xFFFBF6EC), skyMid = Color(0xFFF4EDE0), skyLow = Color(0xFFEDE3D2),
        glow = 0.7f, grain = 0.6f,
        page = Color(0xFFF4EDE0), stage = Color(0xFFF4EDE0),
        ink = NavyPage, mutedAlpha = 0.55f, onInk = Color(0xFFFBF6EC),
        glass = Color.White.copy(alpha = 0.6f), hairline = NavyPage.copy(alpha = 0.08f), list = Color.White.copy(alpha = 0.55f),
        control = Color(0xFFE8E0D0), raised = Color.White,
        nav = Color.White.copy(alpha = 0.8f), sheet = Color(0xFFFBF6EC).copy(alpha = 0.92f),
        scrim = NavyPage.copy(alpha = 0.25f), ring = NavyPage.copy(alpha = 0.22f),
    )

    /** A warm plum dusk with sand ink. */
    private val Dusk = ThemeColors(
        light = false,
        skyTop = Color(0xFF2B1D2E), skyMid = Color(0xFF1E1524), skyLow = Color(0xFF140E19),
        glow = 0.9f, grain = 0.9f,
        page = Color(0xFF1E1524), stage = Color(0xFF0E0A11),
        ink = Color(0xFFF6EBDD), mutedAlpha = 0.55f, onInk = Color.Black,
        glass = Color.White.copy(alpha = 0.06f), hairline = Color.White.copy(alpha = 0.10f), list = Color.White.copy(alpha = 0.05f),
        control = Color(0xFF33243A), raised = Color(0xFF3E2D46),
        nav = Color(0xFF281C2C).copy(alpha = 0.9f), sheet = Color(0xFF140E19).copy(alpha = 0.78f),
        scrim = Color.Black.copy(alpha = 0.45f), ring = Color(0xFFF6EBDD).copy(alpha = 0.28f),
    )
}
