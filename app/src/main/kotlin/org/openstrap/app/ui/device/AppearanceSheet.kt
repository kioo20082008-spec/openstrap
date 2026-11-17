package org.openstrap.app.ui.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.settings.AppTheme
import org.openstrap.app.ui.glow.PageRows
import org.openstrap.app.ui.glow.PageSheet
import org.openstrap.app.ui.glow.RowName
import org.openstrap.app.ui.glow.topHairline
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.AppPalette

/** A theme's name, as the You page's row and the picker say it. */
@Composable
internal fun themeName(theme: AppTheme): String = stringResource(
    when (theme) {
        AppTheme.Navy -> R.string.you_theme_navy
        AppTheme.Oled -> R.string.you_theme_oled
        AppTheme.Cream -> R.string.you_theme_cream
        AppTheme.Dusk -> R.string.you_theme_dusk
    },
)

/**
 * Appearance, from the You page: the four themes, each with a small swatch of its sky, a card and its ink. A pick
 * applies at once, the sheet included, so the themes can be compared before closing it.
 */
@Composable
internal fun AppearanceSheet(selected: AppTheme, onPick: (AppTheme) -> Unit, onDismiss: () -> Unit) {
    PageSheet(onDismiss, stringResource(R.string.you_row_appearance), note = stringResource(R.string.you_appearance_note), side = 12.dp) {
        PageRows {
            AppTheme.entries.forEachIndexed { i, theme ->
                val on = theme == selected
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(on, role = Role.RadioButton) { if (!on) onPick(theme) }
                        .topHairline(i > 0)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ThemeSwatch(theme)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) { RowName(themeName(theme)) }
                    if (on) Icon(AppIcons.Tick02, null, Modifier.size(20.dp), tint = AppColors.ink)
                }
            }
        }
    }
}

/**
 * A theme in miniature, drawn in its own colours whatever the theme showing: its sky with the glow at the top, a
 * glass card with two lines of its ink, and the coral accent. Edged in the showing theme's hairline, so a light swatch
 * stands out on a light sheet and a black one on a black sheet.
 */
@Composable
private fun ThemeSwatch(theme: AppTheme) {
    val t = AppColors.of(theme)
    val edge = AppColors.ink.copy(alpha = 0.16f)
    Canvas(Modifier.size(width = 40.dp, height = 56.dp)) {
        val r = CornerRadius(10.dp.toPx())
        drawRoundRect(Brush.verticalGradient(0f to t.skyTop, 0.45f to t.skyMid, 1f to t.skyLow), cornerRadius = r)
        drawRoundRect(
            Brush.radialGradient(listOf(GlowHue.copy(alpha = 0.5f * t.glow), Color.Transparent), Offset(size.width / 2, 0f), size.width * 0.9f),
            cornerRadius = r,
        )
        val pad = 5.dp.toPx()
        val cardTop = size.height * 0.46f
        val card = Size(size.width - pad * 2, size.height - cardTop - pad)
        val cardR = CornerRadius(4.dp.toPx())
        drawRoundRect(t.glass, Offset(pad, cardTop), card, cardR)
        drawRoundRect(t.hairline, Offset(pad, cardTop), card, cardR, style = Stroke(1.dp.toPx()))
        val line = 2.dp.toPx()
        drawRoundRect(t.ink, Offset(pad * 2, cardTop + pad), Size(card.width * 0.55f, line), CornerRadius(line / 2))
        drawRoundRect(t.ink.copy(alpha = t.mutedAlpha), Offset(pad * 2, cardTop + pad + line * 2.5f), Size(card.width * 0.4f, line), CornerRadius(line / 2))
        drawCircle(AppPalette.Coral, 3.dp.toPx(), Offset(size.width / 2, size.height * 0.27f))
        drawRoundRect(edge, cornerRadius = r, style = Stroke(1.dp.toPx()))
    }
}

/** Today's glow, the one in each swatch's sky. */
private val GlowHue = Color(0xFFFF9662)
