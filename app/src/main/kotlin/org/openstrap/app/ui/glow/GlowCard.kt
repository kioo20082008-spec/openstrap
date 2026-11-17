package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import org.openstrap.app.ui.components.LocalCardNumbers
import org.openstrap.app.ui.components.cardOpening
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.glow.draw.GlowStyle
import org.openstrap.app.ui.theme.AppColors

/**
 * Whether cards should hold still right now; a scrolling screen provides its scroll state. Cards themselves no longer
 * move (the Rings design is flat), but the moving border and the energy flask still follow it.
 */
val LocalGlowPaused = staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * Whether the page holding these cards is off screen or the tab pager is moving; provided by the pager, apart from
 * [LocalGlowPaused] so a screen's own scroll state doesn't override it.
 */
val LocalGlowPagePaused = staticCompositionLocalOf<() -> Boolean> { { false } }

/** The rings design: cards are flat solid panels, no edge, no glow. */
internal const val FlatCards = true
internal val FlatSurface: Color get() = AppColors.glass
internal val FlatHairline: Color get() = Color.Transparent

/**
 * A card: a flat solid panel in [AppColors.glass], clipped to the card shape, with no glow, light, grain or edge.
 * [style] is kept so every caller still compiles; only its [GlowStyle] type is shared with widgets.
 * With [onClick] the card presses in and grows into what it opens (spec 3.5, [cardOpening]).
 */
@Composable
fun GlowCard(
    @Suppress("UNUSED_PARAMETER") style: GlowStyle,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = GlowLayout.CardPadding,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = GlowLayout.CardShape
    // Tapped, the card grows into what it opens (spec 3.5).
    val opening = cardOpening(onClick, shape)
    Box(
        modifier
            .then(opening?.modifier ?: Modifier)
            .let { if (onClick != null) it.pressClickable(opening?.onClick ?: onClick, shape) else it }
            .clip(shape)
            .drawBehind { drawRect(FlatSurface) },
        // Content gets the card's full size, e.g. to centre itself in a fixed-height card.
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalCardNumbers provides opening?.numbers, LocalCardOpens provides (onClick != null)) {
            Box(Modifier.padding(contentPadding), content = content)
        }
    }
}
