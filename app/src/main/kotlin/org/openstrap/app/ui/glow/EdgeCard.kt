package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import org.openstrap.app.ui.components.LocalCardNumbers
import org.openstrap.app.ui.components.cardOpening
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.glow.draw.EdgeStatus
import org.openstrap.app.ui.glow.draw.EdgeKind
import org.openstrap.app.ui.glow.draw.Edge

/*
 * The middle tier of the cards: in the rings design a flat, solid card ([FlatSurface]) with no light, border or glow.
 * The metric's colour shows in the bar a Progress or Level card carries and in the card's own chart. The edge-light
 * parameters ([status], [kind]'s edge, [LocalEdgeOverride]'s edge) stay so every caller and the widgets' shared
 * specs keep their API, but nothing is drawn from them.
 */

/** The neutral edge light, hsl 220 10% 55%: a cool soft white for cards that speak for no one metric. */
val EdgeNeutral = Color.hsl(220f, 0.10f, 0.55f)

/**
 * A screen's say over the next edge card's look, so two cards in a row never look the same (spec 2.4): the [edge] the
 * light comes in from and the [color] it's lit in, each null to keep the card's own. [plain] draws the card unlit, a
 * dark panel with a hairline like a list's, for a card whose light would say nothing.
 */
@Immutable
data class EdgeOverride(val edge: Edge? = null, val color: Color? = null, val plain: Boolean = false)

val LocalEdgeOverride = staticCompositionLocalOf { EdgeOverride() }

private val EdgeShape = GlowLayout.CardShape

/**
 * A flat card for [color]'s metric. For a [EdgeKind.Progress] or [EdgeKind.Level] card, [progress] (0..1) fills the
 * metric-coloured bar under [content].
 */
@Composable
fun EdgeCard(
    color: Color,
    modifier: Modifier = Modifier,
    status: EdgeStatus = EdgeStatus.Usual,
    kind: EdgeKind = EdgeKind.Value,
    progress: Float? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = GlowLayout.CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Only the override's colour is used: a card told to speak for no one metric draws its bar in the neutral.
    val tint = LocalEdgeOverride.current.color ?: color
    // Tapped, the card grows into what it opens (spec 3.5).
    val opening = cardOpening(onClick, EdgeShape)
    Column(
        modifier
            .then(opening?.modifier ?: Modifier)
            .let { if (onClick != null) it.pressClickable(opening?.onClick ?: onClick, EdgeShape) else it }
            .clip(EdgeShape).background(FlatSurface, EdgeShape)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalCardNumbers provides opening?.numbers, LocalCardOpens provides (onClick != null)) { content() }
        if ((kind == EdgeKind.Progress || kind == EdgeKind.Level) && progress != null) {
            Spacer(Modifier.weight(1f, fill = false).padding(top = 12.dp))
            FillBar(progress, tint)
        }
    }
}

/** How far past its level a level card's light fades out. */
internal val LevelFade = 40.dp

/**
 * An edge glow, after the CSS `box-shadow: inset` it was designed with: [alpha] of the colour, its hard edge [shift]
 * in from the card's edge, blurred with a standard deviation of [sigma].
 */
internal class EdgeGlow(val alpha: Float, val shift: Dp, val sigma: Dp) {
    /** How far in the glow reaches before it's gone. */
    val depth: Dp get() = shift + sigma * 2.5f

    /** Colour stops along [depth] (position 0..1 to alpha), scaled by the mood's [strength]. */
    fun stops(strength: Float): List<Pair<Float, Float>> = List(STEPS + 1) { i ->
        val at = i / STEPS.toFloat()
        val x = depth.value * at
        // The Gaussian's cumulative curve, closely enough: a logistic with the matching slope. Scaled so the lit edge
        // is [alpha] of the colour exactly.
        at to (alpha * strength * cover(x) / cover(0f) * (1f - at)).coerceIn(0f, 1f)
    }

    private fun cover(x: Float) = 1f / (1f + exp(-1.702f * (shift.value - x) / sigma.value))

    private companion object {
        const val STEPS = 8
    }
}

/** L1, a usual value and every strip (spec 2.1): the ink at 70% at the edge, reaching 48 dp in. */
internal val SoftGlow = EdgeGlow(alpha = 0.70f, shift = 10.5.dp, sigma = 15.dp)

/** L2, off your normal: the ink at 90% at the edge, reaching 72 dp in. */
internal val StrongGlow = EdgeGlow(alpha = 0.90f, shift = 19.5.dp, sigma = 21.dp)
