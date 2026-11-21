package org.openstrap.app.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
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
import org.openstrap.app.data.ailearn.Profile
import org.openstrap.app.data.ailearn.ProfileSection
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.presentation.AiLearnCopy
import org.openstrap.app.presentation.SelfCheckCopy
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PageProgress
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import java.time.LocalDate
import java.time.ZoneId

/**
 * The AI body learning's part of the self-learning page (docs/algorithms/self-check.md): what it does, its switch (on by
 * default only once the smart assistant is on with a key), when it last learned and the button to learn now, what it learned
 * today, the readable profile "How your body works", the sleep need it suggests (the person's own setting, changed only by a
 * tap), what it proposed with Undo on each, the button that clears all of it, and what is sent and where. The words are
 * [AiLearnCopy]'s.
 */
// One page's list of items, read top to bottom.
@Suppress("LongMethod", "LongParameterList")
fun LazyListScope.aiLearnItems(
    ui: SelfCheckViewModel.Ui,
    onOn: (Boolean) -> Unit,
    onLearnNow: () -> Unit,
    onApplyNeed: (Int) -> Unit,
    onClear: () -> Unit,
    onUndo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onOpenAssistant: () -> Unit,
    open: Int,
    onToggle: (Int) -> Unit,
) {
    val ai = ui.ai
    val proposals = ui.state.incidents.filter { SelfLearning.isAi(it.check) }
    item {
        RingsRows {
            RingsSwitchRow(
                stringResource(R.string.ailearn_switch),
                stringResource(
                    when {
                        !ai.ready -> R.string.ailearn_needs_assistant
                        ai.on -> R.string.ailearn_switch_note
                        else -> R.string.ailearn_off_note
                    },
                ),
                ai.on, onOn, divider = false, enabled = ai.ready, icon = AppIcons.Brain,
            )
            if (!ai.ready) RingsRow(stringResource(R.string.ailearn_open_assistant), onClick = onOpenAssistant, icon = AppIcons.Brain02)
        }
    }
    item { Status(ai, onLearnNow) }
    item { FoldRow(R.string.selfcheck_how, open and Fold.AiExplain != 0) { onToggle(Fold.AiExplain) } }
    if (open and Fold.AiExplain != 0) {
        item {
            RingsPanel {
                Text(stringResource(R.string.ailearn_explain), color = AppColors.ink.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.body)
            }
        }
    }
    item { RingsSection(stringResource(R.string.ailearn_section_today)) }
    item { LearnedToday(ai, ui.today) }
    ai.offeredNeed(ui.today)?.let { minutes -> item { NeedOffer(minutes, ai.sleepNeedSetting, onApplyNeed) } }
    item { FoldRow(R.string.ailearn_section_profile, open and Fold.Profile != 0) { onToggle(Fold.Profile) } }
    if (open and Fold.Profile != 0) profileItems(ai.profile)
    item { RingsSection(stringResource(R.string.ailearn_section_changes)) }
    if (proposals.isEmpty()) {
        item { RingsPanel { Text(stringResource(R.string.ailearn_changes_empty), color = AppColors.muted, style = GlowType.body) } }
    } else {
        val shown = if (open and Fold.AllChanges != 0) proposals else proposals.take(FOUND_PREVIEW)
        items(shown, key = { it.id }) { i -> ProposalCard(i, ui.today, onUndo, onRemove) }
        if (proposals.size > FOUND_PREVIEW) {
            item {
                FoldRow(
                    if (open and Fold.AllChanges != 0) R.string.selfcheck_show_less else R.string.selfcheck_show_all, false, proposals.size.toString(),
                ) { onToggle(Fold.AllChanges) }
            }
        }
    }
    if (ai.profile != null || proposals.isNotEmpty() || ai.state.lastOkAt > 0) {
        item {
            RingsPanel {
                Text(stringResource(R.string.ailearn_clear_note), color = AppColors.muted, style = GlowType.secondary)
                PageButton(stringResource(R.string.ailearn_clear), onClear, Modifier.fillMaxWidth(), onCard = true, color = MetricColors.warn, icon = AppIcons.Trash)
            }
        }
    }
    item { FoldRow(R.string.ailearn_consent_title, open and Fold.Consent != 0) { onToggle(Fold.Consent) } }
    if (open and Fold.Consent != 0) {
        item {
            RingsPanel {
                val lines = remember { AiLearnCopy.consentLines() }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    lines.forEachIndexed { index, line ->
                        // The first line is the headline of the rest; the others are what is sent, how much and what may change.
                        if (index == 0) Text(line, color = AppColors.ink, style = GlowType.body) else Bullet(line)
                    }
                }
            }
        }
    }
}

/** When it last learned and how the last run went, the reason it could not when it could not, and the button. */
@Composable
private fun Status(ai: SelfCheckViewModel.AiUi, onLearnNow: () -> Unit) {
    val now = remember(ai.state) { System.currentTimeMillis() / MILLIS }
    val last = remember(ai.state, now) { AiLearnCopy.lastLearned(ai.state, now, ZoneId.systemDefault()) }
    val summary = remember(ai.state) { AiLearnCopy.runSummary(ai.state) }
    val failure = remember(ai.state) { AiLearnCopy.failure(ai.state) }
    RingsPanel {
        Text(last ?: stringResource(R.string.ailearn_last_none), color = AppColors.ink, style = GlowType.body)
        if (summary != null) PageNote(summary)
        if (failure != null) PageNote(failure, MetricColors.warn)
        if (ai.running) {
            Spacer(Modifier.height(4.dp))
            PageProgress()
        }
        PageButton(
            stringResource(if (ai.running) R.string.ailearn_now_running else R.string.ailearn_now), onLearnNow, Modifier.fillMaxWidth(),
            enabled = ai.on && !ai.running, onCard = true, icon = AppIcons.Refresh04,
        )
    }
}

/** The lines it said it learned today, or that there is nothing new. */
@Composable
private fun LearnedToday(ai: SelfCheckViewModel.AiUi, today: LocalDate) {
    val lines = if (ai.state.learnedDay == today) ai.state.learned else emptyList()
    RingsPanel {
        if (lines.isEmpty()) {
            Text(stringResource(R.string.ailearn_none_today), color = AppColors.muted, style = GlowType.body)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { lines.forEach { Bullet(AiLearnCopy.said(it), AppColors.ink) } }
        }
    }
}

/** The sleep need the AI suggests, and the button that makes it the person's setting: only a tap changes it. */
@Composable
private fun NeedOffer(minutes: Int, setting: Int?, onApply: (Int) -> Unit) {
    val duration = Units.duration(minutes * SECONDS_A_MINUTE)
    val settingNote = if (setting == null) {
        stringResource(R.string.ailearn_need_auto)
    } else {
        stringResource(R.string.ailearn_need_set, Units.duration(setting * SECONDS_A_MINUTE))
    }
    RingsPanel(title = stringResource(R.string.ailearn_need_title)) {
        Text(stringResource(R.string.ailearn_need_body, duration, settingNote), color = AppColors.ink, style = GlowType.body)
        PageButton(stringResource(R.string.ailearn_need_apply, duration), { onApply(minutes) }, Modifier.fillMaxWidth(), onCard = true, icon = AppIcons.CheckCircle)
    }
}

/** The profile as the person reads it: one card a section that has something in it, then his numbers, then the span it rests on. */
private fun LazyListScope.profileItems(profile: Profile?) {
    if (profile == null || profile.isEmpty) {
        item { RingsPanel { Text(stringResource(R.string.ailearn_profile_empty), color = AppColors.muted, style = GlowType.body) } }
        return
    }
    for (section in ProfileSection.entries) {
        val statements = profile.sections[section].orEmpty()
        if (statements.isEmpty()) continue
        item(key = "profile.${section.key}") {
            RingsPanel(title = AiLearnCopy.sectionTitle(section)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    statements.forEach { s ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(AiLearnCopy.said(s.text), color = AppColors.ink, style = GlowType.body)
                            Text(AiLearnCopy.statementMeta(s), color = AppColors.muted, style = GlowType.secondary)
                        }
                    }
                }
            }
        }
    }
    val facts = AiLearnCopy.facts(profile.facts)
    if (facts.isNotEmpty()) {
        item(key = "profile.facts") {
            RingsPanel(title = stringResource(R.string.ailearn_sec_facts)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { facts.forEach { Bullet(it, AppColors.ink) } }
            }
        }
    }
    AiLearnCopy.span(profile)?.let { span -> item(key = "profile.span") { PageNote(span) } }
}

/** One thing the AI proposed: its day and what became of it, the words, and Undo while it is at work or Remove after. */
@Composable
private fun ProposalCard(i: SelfLearning.Incident, today: LocalDate, onUndo: (Int) -> Unit, onRemove: (Int) -> Unit) {
    val atWork = i.active(today)
    RingsCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(org.openstrap.app.presentation.DayWords.dayMonth(i.last), Modifier.weight(1f), color = AppColors.muted, style = GlowType.secondary, maxLines = 1)
            RingsPill(SelfCheckCopy.labelText(SelfCheckCopy.label(i, today)))
        }
        Text(SelfCheckCopy.seen(i), color = AppColors.ink, style = GlowType.body)
        val adjusted = SelfCheckCopy.adjustments(i)
        if (adjusted.isEmpty()) {
            Text(stringResource(R.string.selfcheck_noted_only), color = AppColors.muted, style = GlowType.secondary)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { adjusted.forEach { Bullet(it, AppColors.ink) } }
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
private fun Bullet(text: String, hue: androidx.compose.ui.graphics.Color = AppColors.muted) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(AppColors.readable(hue)))
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), color = AppColors.ink, style = GlowType.body)
    }
}

private const val MILLIS = 1000L
private const val SECONDS_A_MINUTE = 60L
