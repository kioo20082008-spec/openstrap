package org.openstrap.app.widget

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.openstrap.app.ui.glow.draw.renderToBitmap

/*
 * The rings theme's widget colours and flat card, which Start and Moment still wear. The metric widgets and the
 * summary wear the fill look ([FillContent]).
 */

/** The rings theme's widget colours. Widgets stay dark whatever the app's theme, as the mockups draw them. */
internal object RingWidget {
    /** The widget's card. */
    val Page = Color(0xFF0A0A0C)

    /** Tiles and the status button inside the card. */
    val Card = Color(0xFF17171B)

    /** Secondary text. */
    val Muted = Color(0xFFA7A7B0)

    /** Dark ink, on a hue. */
    val Ink = Color(0xFF0A0A0C)

    /** The metric hues ([org.openstrap.app.ui.glow.RingColors]). */
    val Energy = Color(0xFFFF5C39)
    val Recovery = Color(0xFFC8FF3D)
    val Sleep = Color(0xFF8B7BFF)
    val SleepText = Color(0xFFB3A8FF)
    val Steps = Color(0xFF3DD6FF)

    /** The ring's stroke as a share of its diameter (the mockup's 13 in 100) and its track's alpha. */
    const val STROKE = 0.13f
    const val TRACK_ALPHA = 0.30f

    /** Stands for the day's summary, which wears no one metric's hue. */
    val SummaryHue = Color(0xFFFFB067)

    /**
     * The card's colours for [hue]: a deep shade of the metric's own colour at the top start, deeper at the bottom end,
     * so each widget wears its metric (Energy red, Recovery green, Sleep violet, Steps blue) rather than black.
     */
    fun card(hue: Color): Pair<Color, Color> = when (hue) {
        Recovery -> Color(0xFF4B6D10) to Color(0xFF1B2A06)
        Sleep -> Color(0xFF4C3FAE) to Color(0xFF1A1542)
        Steps -> Color(0xFF106B86) to Color(0xFF082A34)
        SummaryHue -> Color(0xFF45306F) to Color(0xFF3F1C1A)
        else -> Color(0xFF99361B) to Color(0xFF3D140C)
    }

    /** The title's sizes, largest first, and the word under it. */
    val TITLE_SIZES = listOf(15f, 13f, 12f)
    const val WORD_SP = 12f
    const val LINE_LABEL_SP = 12f
    const val LINE_VALUE_SP = 13f
    const val LINE_GAP_DP = 6f
    const val RING_GAP_DP = 12f
    const val BELOW_GAP_DP = 10f
    const val MIN_RING_DP = 48f
    const val MAX_RING_DP = 140f
}

/** A figure: a [label] in grey with its [value] at the far end, or the label alone; [dot] marks it (unused by the fill look). */
internal data class RingLine(val label: String, val value: String? = null, val dot: Color? = null)

/** The rings theme's flat card [w] by [h] dp with [corner] dp corners at [density]. */
internal fun flatBackground(w: Float, h: Float, corner: Float, density: Float, hue: Color = RingWidget.Energy): Bitmap {
    val pw = w * density
    val ph = h * density
    val r = corner * density
    val (from, to) = RingWidget.card(hue)
    return renderToBitmap(pw, ph) { c ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(0f, 0f, pw, ph, from.toArgb(), to.toArgb(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, pw, ph, r, r, paint)
        // A soft light of the hue in the top corner, so the card isn't a flat fill.
        val light = Paint(Paint.ANTI_ALIAS_FLAG)
        light.shader = android.graphics.RadialGradient(
            pw * 0.8f, 0f, pw * 0.9f, hue.copy(alpha = 0.28f).toArgb(), hue.copy(alpha = 0f).toArgb(), android.graphics.Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(0f, 0f, pw, ph, r, r, light)
    }
}
