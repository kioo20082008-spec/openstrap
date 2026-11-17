package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.theme.AppColors

/*
 * The rings design's small parts, shared by the tabs: the status pill, the solid card and the list divider. Flat:
 * no edge, no glow, no gradient.
 */

/** A status word's pill and a chosen tab's: rounded 12 dp. */
internal val PillShape = RoundedCornerShape(12.dp)

/** The dark ink on a pill or card filled with a metric's hue. */
internal val OnHueInk = Color(0xFF0A0A0C)

/**
 * A status word as a pill: filled with [hue] and dark ink when [strong] (the one that matters most), else the hue at
 * 20% with the hue as text, or neutral when [hue] is null.
 */
@Composable
internal fun StatusPill(text: String, hue: Color?, modifier: Modifier = Modifier, strong: Boolean = false, onClick: (() -> Unit)? = null) {
    val (fill, ink) = when {
        hue == null -> AppColors.raised to AppColors.ink.copy(alpha = 0.85f)
        strong -> hue to OnHueInk
        else -> hue.copy(alpha = 0.2f) to AppColors.readable(hue)
    }
    Text(
        text,
        modifier.clip(PillShape).background(fill)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = ink, style = GlowType.caption.copy(fontWeight = FontWeight.Bold), maxLines = 1,
    )
}

/** The 1 dp divider between rows inside a card. */
@Composable
internal fun RingDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(RowHairline))
}

/** A solid card: the theme's card colour, 22 dp corners, nothing else. */
internal fun Modifier.ringCard(): Modifier = clip(GlowLayout.CardShape).background(AppColors.glass)

/** The rings design's type over [GlowType]: bold card titles, and row figures in the number face, extra bold. */
internal object RingType {
    val cardTitle get() = GlowType.cardTitle.copy(fontWeight = FontWeight.Bold)
    val rowValue get() = GlowType.rowValue.copy(
        fontFamily = org.openstrap.app.ui.theme.NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 22.sp,
    )
}
