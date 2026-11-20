package org.openstrap.app.ui.device

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import org.openstrap.app.R
import org.openstrap.app.data.ai.AiCached
import org.openstrap.app.data.ai.AiDeepSection
import org.openstrap.app.presentation.AiCopy
import org.openstrap.app.presentation.AiLearnCopy
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageField
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PageProgress
import org.openstrap.app.ui.glow.RingColors
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors

/**
 * The smart assistant's settings page, from You > App: the DeepSeek key (paste, save, test, delete, always shown
 * masked), the on/off switch, a way into the Analysis screen and, plainly, what is sent and where. The typed key
 * lives only in this composition (never saved with the screen's state) and is wiped once it is stored.
 */
@Composable
fun AiSettingsPage(
    state: AiViewModel.Ui,
    contentPadding: PaddingValues,
    onSaveKey: (String) -> Unit,
    onClearSaveError: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onDeeper: (Boolean) -> Unit,
    onOpenAnalysis: () -> Unit,
    onBack: () -> Unit,
    onOpenLearning: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var typed by remember { mutableStateOf("") }
    // The key is stored (or replaced, or deleted): the field is cleared, the plain text gone from the screen.
    LaunchedEffect(state.key.masked, state.key.hasKey) { typed = "" }
    val key = state.key
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { RingsHeader(stringResource(R.string.ai_title), onBack) }
        item {
            RingsPanel {
                Text(stringResource(R.string.ai_settings_intro), color = AppColors.ink.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.body)
            }
        }
        item { RingsSection(stringResource(R.string.ai_section_key)) }
        item {
            RingsPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Key, null, Modifier.size(20.dp), tint = AppColors.readable(RingColors.Sleep))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (key.hasKey) AiCopy.keySaved(key.masked) else stringResource(R.string.ai_key_none),
                        Modifier.weight(1f), color = AppColors.ink, style = GlowType.body,
                    )
                }
                Spacer(Modifier.height(12.dp))
                PageField(
                    typed, { typed = it; if (state.saveError != null) onClearSaveError() }, label = null,
                    placeholder = stringResource(R.string.ai_key_hint), error = state.saveError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
                state.saveError?.let { e ->
                    Spacer(Modifier.height(8.dp))
                    PageNote(
                        if (e == AiViewModel.SaveError.Invalid) AiCopy.keyInvalid() else stringResource(R.string.ai_key_save_failed),
                        color = MetricColors.warn,
                    )
                }
                Spacer(Modifier.height(12.dp))
                PageButton(
                    stringResource(R.string.ai_key_save), { onSaveKey(typed) }, Modifier.fillMaxWidth(),
                    enabled = typed.isNotBlank(), primary = true, icon = AppIcons.ShieldCheck,
                )
                if (key.hasKey) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PageButton(
                            stringResource(R.string.ai_key_test), onTest, Modifier.weight(1f),
                            enabled = state.test != AiViewModel.Test.Running, onCard = true,
                        )
                        PageButton(
                            stringResource(R.string.ai_key_delete), onDelete, Modifier.weight(1f),
                            color = MetricColors.warn, icon = AppIcons.Trash, onCard = true,
                        )
                    }
                }
                TestResult(state.test)
            }
        }
        item {
            RingsRows {
                RingsSwitchRow(
                    stringResource(R.string.ai_switch_title),
                    stringResource(if (key.hasKey) R.string.ai_switch_note else R.string.ai_switch_needs_key),
                    key.enabled && key.hasKey, onEnabled, enabled = key.hasKey, icon = AppIcons.Brain,
                )
                RingsSwitchRow(
                    stringResource(R.string.ai_deeper_title),
                    stringResource(if (key.hasKey) R.string.ai_deeper_note else R.string.ai_switch_needs_key),
                    key.deeper && key.hasKey, onDeeper, divider = false, enabled = key.hasKey, icon = AppIcons.Hourglass,
                )
                RingsRow(
                    stringResource(R.string.ai_open_analysis), stringResource(R.string.ai_open_analysis_note), onClick = onOpenAnalysis,
                    below = true, icon = AppIcons.Brain02,
                )
                RingsRow(
                    stringResource(R.string.ailearn_title), stringResource(R.string.ailearn_row_note), onClick = onOpenLearning,
                    below = true, icon = AppIcons.Brain,
                )
            }
        }
        item { RingsSection(stringResource(R.string.ai_consent_title)) }
        item {
            RingsPanel {
                val lines = listOf(
                    AiCopy.consentWhere(),
                    stringResource(R.string.ai_consent_days),
                    stringResource(R.string.ai_consent_nights),
                    stringResource(R.string.ai_consent_workouts),
                    stringResource(R.string.ai_consent_extra),
                    stringResource(R.string.ai_consent_insights),
                    stringResource(R.string.ai_consent_never),
                    stringResource(R.string.ai_consent_third),
                    stringResource(R.string.ai_consent_key),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    lines.forEachIndexed { i, line ->
                        // The first line is the headline of the rest; the others are what is sent and not.
                        if (i == 0) {
                            Text(line, color = AppColors.ink, style = GlowType.body.copy(fontWeight = FontWeight.Medium))
                        } else {
                            Bullet(line)
                        }
                    }
                }
            }
        }
        // The daily request of AI learning: what is sent, how much, where and what it may change.
        item { RingsSection(stringResource(R.string.ailearn_consent_title)) }
        item {
            RingsPanel {
                val lines = remember { AiLearnCopy.consentLines() }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    lines.forEachIndexed { i, line ->
                        if (i == 0) {
                            Text(line, color = AppColors.ink, style = GlowType.body.copy(fontWeight = FontWeight.Medium))
                        } else {
                            Bullet(line)
                        }
                    }
                }
            }
        }
    }
}

/** The key test's line under the buttons: working, fine, or why not. */
@Composable
private fun TestResult(test: AiViewModel.Test) {
    when (test) {
        AiViewModel.Test.Idle -> Unit
        AiViewModel.Test.Running -> {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.ai_key_testing), color = AppColors.muted, style = GlowType.secondary)
            Spacer(Modifier.height(8.dp))
            PageProgress()
        }
        AiViewModel.Test.Works -> {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.CheckCircle, null, Modifier.size(18.dp), tint = AppColors.readable(RingColors.Recovery))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_key_works), color = AppColors.readable(RingColors.Recovery), style = GlowType.secondary)
            }
        }
        is AiViewModel.Test.Failed -> {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(AppIcons.WarningCircle, null, Modifier.size(18.dp), tint = AppColors.readable(MetricColors.warn))
                Spacer(Modifier.width(8.dp))
                PageNote(AiCopy.failure(test.failure, test.code), color = MetricColors.warn)
            }
        }
    }
}

/**
 * The Analysis screen: the last analysis in cards (summary, patterns, what to do, what to adjust, outlook), kept per
 * day with its time, and one button that asks for a new one. It never asks by itself.
 */
@Composable
fun AiAnalysisPage(
    state: AiViewModel.Ui,
    contentPadding: PaddingValues,
    onAnalyse: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    // Read again whenever the analysis changes, so "today" and "updated" follow it.
    val zone = remember { ZoneId.systemDefault() }
    val now = remember(state.cached, state.running) { System.currentTimeMillis() / 1000 }
    val cached = state.cached
    val fresh = cached != null && cached.day == LocalDate.now(zone)
    val ready = state.key.ready
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item {
            RingsHeader(
                stringResource(R.string.ai_analysis_title), onBack,
                subtitle = if (cached != null && fresh) AiCopy.updated(cached.at, now, zone) else null,
            )
        }
        when {
            !state.key.hasKey -> item {
                SetupCard(R.string.ai_state_no_key_title, R.string.ai_state_no_key_body, onOpenSettings)
            }
            !state.key.enabled -> item {
                SetupCard(R.string.ai_state_off_title, R.string.ai_state_off_body, onOpenSettings)
            }
        }
        if (ready) item {
            RingsCard {
                PageButton(
                    stringResource(if (state.running) R.string.glow_cancel else R.string.ai_analyse_now),
                    if (state.running) onCancel else onAnalyse, Modifier.fillMaxWidth(),
                    primary = !state.running, icon = AppIcons.Brain,
                )
                if (state.running) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.ai_analysing), color = AppColors.ink, style = GlowType.body)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(if (state.key.deeper) R.string.ai_analysing_deep else R.string.ai_analysing_note),
                        color = AppColors.muted, style = GlowType.secondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    PageProgress()
                }
            }
        }
        state.failure?.let { f ->
            item {
                RingsCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(AppIcons.WarningCircle, null, Modifier.size(20.dp), tint = AppColors.readable(MetricColors.warn))
                        Spacer(Modifier.width(10.dp))
                        Text(AiCopy.failure(f.failure, f.code), Modifier.weight(1f), color = AppColors.ink, style = GlowType.body)
                    }
                }
            }
        }
        if (cached == null) {
            if (ready) item {
                RingsPanel(title = stringResource(R.string.ai_empty_title)) {
                    Text(stringResource(R.string.ai_empty_body), color = AppColors.muted, style = GlowType.body)
                }
            }
        } else {
            analysisCards(cached, fresh, now, zone)
        }
        item { PageNote(stringResource(R.string.ai_not_medical)) }
    }
}

/** The cards of one analysis. A plain-text answer is one card; a structured one is a card per part that has content. */
private fun androidx.compose.foundation.lazy.LazyListScope.analysisCards(c: AiCached, fresh: Boolean, now: Long, zone: ZoneId) {
    val a = c.answer
    if (!fresh) item { PageNote(AiCopy.updatedEarlier(c.at, now, zone)) }
    if (c.days > 0 || c.nights > 0 || c.workouts > 0) item {
        Text(AiCopy.basis(c.days, c.nights, c.workouts), color = AppColors.muted, style = GlowType.secondary)
    }
    if (a.plain != null) {
        item {
            RingsPanel(title = stringResource(R.string.ai_section_summary)) {
                Text(a.plain, color = AppColors.ink, style = GlowType.body)
            }
        }
        item { PageNote(stringResource(R.string.ai_plain)) }
        return
    }
    if (a.summary.isNotBlank()) item {
        RingsPanel(title = stringResource(R.string.ai_section_summary)) {
            Text(a.summary, color = AppColors.ink, style = GlowType.body)
        }
    }
    if (a.patterns.isNotEmpty()) item { ListCard(R.string.ai_section_patterns, a.patterns, RingColors.Sleep) }
    if (a.doThis.isNotEmpty()) item { ListCard(R.string.ai_section_do, a.doThis, RingColors.Recovery) }
    if (a.adjust.isNotEmpty()) item { ListCard(R.string.ai_section_adjust, a.adjust, RingColors.Energy) }
    if (a.outlook.isNotBlank()) item {
        RingsPanel(title = stringResource(R.string.ai_section_outlook)) {
            Text(a.outlook, color = AppColors.ink, style = GlowType.body)
        }
    }
    if (a.deep.isNotEmpty()) item { DeepAnalysis(a.deep) }
    if (a.partial) item { PageNote(stringResource(R.string.ai_partial)) }
}

/** The detailed analysis: one header, closed to begin with; opened, a card per section. */
@Composable
private fun DeepAnalysis(sections: List<AiDeepSection>) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.ai_deep_toggle)
    RingsCard(Modifier.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = label, role = Role.Button) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_deep_title), color = AppColors.ink, style = RingsType.cardTitle)
                Text(
                    pluralStringResource(R.plurals.ai_deep_count, sections.size, sections.size),
                    color = AppColors.muted, style = GlowType.secondary,
                )
            }
            Icon(AppIcons.CaretDown, null, Modifier.size(20.dp).rotate(if (open) 180f else 0f), tint = AppColors.muted)
        }
        if (open) sections.forEachIndexed { i, section -> key(i) { DeepCard(section) } }
    }
}

/** One section of the detailed analysis: its title and the start of its text, and, opened, all of it with its evidence as chips. */
@Composable
private fun DeepCard(section: AiDeepSection) {
    var open by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val label = stringResource(R.string.ai_deep_toggle_section)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(AppColors.ink.copy(alpha = 0.06f), shape).animateContentSize()
            .clickable(onClickLabel = label, role = Role.Button) { open = !open }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                section.title.ifBlank { stringResource(R.string.ai_deep_untitled) }, Modifier.weight(1f), color = AppColors.ink,
                style = GlowType.body.copy(fontWeight = FontWeight.Bold),
            )
            Icon(AppIcons.CaretDown, null, Modifier.size(18.dp).rotate(if (open) 180f else 0f), tint = AppColors.muted)
        }
        if (section.text.isNotEmpty()) Text(
            AiCopy.isolateNumbers(section.text), color = AppColors.ink.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.body,
            maxLines = if (open) Int.MAX_VALUE else COLLAPSED_LINES, overflow = TextOverflow.Ellipsis,
        )
        if (open && section.evidence.isNotEmpty()) EvidenceChips(section.evidence)
    }
}

/** The figures a section rests on, as small chips; the numbers inside are isolated so they read left to right in Arabic. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EvidenceChips(items: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Text(
                AiCopy.isolateNumbers(item), color = AppColors.ink.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary,
                modifier = Modifier.clip(CircleShape).background(AppColors.ink.copy(alpha = 0.08f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

private const val COLLAPSED_LINES = 2

/** A card of short points, each with a dot in the card's [hue]. */
@Composable
private fun ListCard(title: Int, points: List<String>, hue: Color) {
    RingsPanel(title = stringResource(title)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            points.forEach { Bullet(it, hue) }
        }
    }
}

/** One point: a small dot at the start edge, then the text. */
@Composable
private fun Bullet(text: String, hue: Color = AppColors.muted) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(AppColors.readable(hue)))
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), color = AppColors.ink, style = GlowType.body)
    }
}

/** What stops an analysis (no key, switched off) and the way to the settings that fix it. */
@Composable
private fun SetupCard(title: Int, body: Int, onOpenSettings: () -> Unit) {
    RingsPanel(title = stringResource(title)) {
        Text(stringResource(body), color = AppColors.muted, style = GlowType.body)
        Spacer(Modifier.height(12.dp))
        PageButton(stringResource(R.string.ai_open_settings), onOpenSettings, Modifier.fillMaxWidth(), primary = true, icon = AppIcons.Key)
    }
}
