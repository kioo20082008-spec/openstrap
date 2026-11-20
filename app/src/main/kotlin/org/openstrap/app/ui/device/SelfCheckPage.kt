package org.openstrap.app.ui.device

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.presentation.DayWords
import org.openstrap.app.presentation.SelfCheckCopy
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors

/**
 * The self-learning page, from You > App: a line on what it does, the master switch, what is in effect now, every
 * incident with what was seen and what was adjusted (Undo while it is at work, Remove after), and one button that takes
 * everything back; then the AI body learning ([aiLearnItems]). Numbers sit in the sentences, which come from
 * [SelfCheckCopy] with the digits Western.
 */
@Composable
fun SelfCheckPage(
    ui: SelfCheckViewModel.Ui,
    contentPadding: PaddingValues,
    onOn: (Boolean) -> Unit,
    onUndo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onResetAll: () -> Unit,
    onBack: () -> Unit,
    onAiOn: (Boolean) -> Unit = {},
    onLearnNow: () -> Unit = {},
    onApplyNeed: (Int) -> Unit = {},
    onClearLearned: () -> Unit = {},
    onOpenAssistant: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val lines = remember(ui) { if (ui.on) SelfCheckCopy.inEffect(SelfLearning.learned(ui.state, ui.today)) else emptyList() }
    val atWork = ui.state.incidents.any { it.active(ui.today) }
    // What the AI proposed is listed with the AI learning, below; this list is the app's own checks.
    val found = remember(ui) { ui.state.incidents.filterNot { SelfLearning.isAi(it.check) } }
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { RingsHeader(stringResource(R.string.selfcheck_title), onBack) }
        item {
            RingsPanel {
                Text(stringResource(R.string.selfcheck_explain), color = AppColors.ink.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.body)
            }
        }
        item {
            RingsRows {
                RingsSwitchRow(
                    stringResource(R.string.selfcheck_switch),
                    stringResource(if (ui.on) R.string.selfcheck_switch_note else R.string.selfcheck_off_note),
                    ui.on, onOn, divider = false, icon = AppIcons.ShieldCheck,
                )
            }
        }
        item { RingsSection(stringResource(R.string.selfcheck_section_now)) }
        item {
            RingsPanel {
                if (lines.isEmpty()) {
                    Text(stringResource(R.string.selfcheck_none_now), color = AppColors.muted, style = GlowType.body)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { lines.forEach { Point(it) } }
                }
                if (atWork) {
                    Spacer(Modifier.height(12.dp))
                    PageButton(stringResource(R.string.selfcheck_reset_all), onResetAll, Modifier.fillMaxWidth(), onCard = true, icon = AppIcons.Refresh04)
                }
            }
        }
        item { RingsSection(stringResource(R.string.selfcheck_section_found)) }
        if (found.isEmpty()) {
            item {
                RingsPanel { Text(stringResource(R.string.selfcheck_found_empty), color = AppColors.muted, style = GlowType.body) }
            }
        } else {
            items(found, key = { it.id }) { i -> Incident(i, ui.today, onUndo, onRemove) }
        }
        aiLearnItems(ui, onAiOn, onLearnNow, onApplyNeed, onClearLearned, onUndo, onRemove, onOpenAssistant)
    }
}

/** One incident: its day and state, what was seen, what was adjusted, and the button that takes it back or away. */
@Composable
private fun Incident(i: SelfLearning.Incident, today: java.time.LocalDate, onUndo: (Int) -> Unit, onRemove: (Int) -> Unit) {
    val atWork = i.active(today)
    RingsCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(DayWords.dayMonth(i.last), Modifier.weight(1f), color = AppColors.muted, style = GlowType.secondary, maxLines = 1)
            RingsPill(SelfCheckCopy.labelText(SelfCheckCopy.label(i, today)))
        }
        Text(SelfCheckCopy.seen(i), color = AppColors.ink, style = GlowType.body)
        val adjusted = SelfCheckCopy.adjustments(i)
        if (adjusted.isEmpty()) {
            Text(stringResource(R.string.selfcheck_noted_only), color = AppColors.muted, style = GlowType.secondary)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { adjusted.forEach { Point(it) } }
        }
        if (atWork) {
            PageButton(stringResource(R.string.selfcheck_undo), { onUndo(i.id) }, Modifier.fillMaxWidth(), onCard = true, icon = AppIcons.Refresh04)
        } else {
            PageButton(stringResource(R.string.selfcheck_remove), { onRemove(i.id) }, Modifier.fillMaxWidth(), onCard = true, color = MetricColors.warn, icon = AppIcons.Trash)
        }
    }
}

/** One point: a small dot at the start edge, then the text. */
@Composable
private fun Point(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(AppColors.muted))
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), color = AppColors.ink, style = GlowType.body)
    }
}
