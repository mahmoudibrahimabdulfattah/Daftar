package com.mk.daftar.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarIcon
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarSettingRow
import com.mk.daftar.core.designsystem.component.DaftarSettingTrailing
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.feature.settings.PreferenceSheet
import com.mk.daftar.feature.settings.PreferencesIntent
import com.mk.daftar.feature.settings.PreferencesUiState
import com.mk.daftar.feature.settings.PreferencesViewModel
import com.mk.daftar.feature.settings.resources.Res
import com.mk.daftar.feature.settings.resources.prefs_done
import com.mk.daftar.feature.settings.resources.prefs_lock_note
import com.mk.daftar.feature.settings.resources.prefs_numerals_body
import com.mk.daftar.feature.settings.resources.prefs_numerals_note
import com.mk.daftar.feature.settings.resources.prefs_numerals_title
import com.mk.daftar.feature.settings.resources.prefs_save_failed_body
import com.mk.daftar.feature.settings.resources.prefs_save_failed_title
import com.mk.daftar.feature.settings.resources.prefs_title
import com.mk.daftar.feature.settings.resources.prefs_unreadable
import com.mk.daftar.feature.settings.resources.settings_language_body
import com.mk.daftar.feature.settings.resources.settings_language_title
import com.mk.daftar.feature.settings.resources.settings_section_appearance
import com.mk.daftar.feature.settings.resources.settings_theme_body
import com.mk.daftar.feature.settings.resources.settings_theme_title
import com.mk.daftar.feature.settings.resources.settings_timeout_body
import com.mk.daftar.feature.settings.resources.settings_timeout_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Theme, language and the inactivity timeout. Choices apply through the committed preference: the screen shows what the
 * store has accepted, and a failed write keeps the previous value. Done only returns.
 */
@Composable
fun PreferencesScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: PreferencesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    PreferencesContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The stateless preferences page. Its sheets are part of the page, so a screenshot can show each one. */
@Composable
fun PreferencesContent(
    state: PreferencesUiState,
    onIntent: (PreferencesIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.shown
    SettingsPage(title = stringResource(Res.string.prefs_title), modifier = modifier, onBack = onBack) {
        if (state.unreadable) {
            SettingsNote(message = stringResource(Res.string.prefs_unreadable))
        }
        SectionTitle(stringResource(Res.string.settings_section_appearance))
        DaftarSettingRow(
            title = stringResource(Res.string.settings_theme_title),
            subtitle = stringResource(Res.string.settings_theme_body),
            trailing = DaftarSettingTrailing.Link(themeLabel(prefs.themeMode)) {
                onIntent(PreferencesIntent.OpenSheet(PreferenceSheet.Theme))
            },
        )
        DaftarSettingRow(
            title = stringResource(Res.string.settings_language_title),
            subtitle = stringResource(Res.string.settings_language_body),
            trailing = DaftarSettingTrailing.Link(languageLabel(prefs.language)) {
                onIntent(PreferencesIntent.OpenSheet(PreferenceSheet.Language))
            },
        )
        DaftarSettingRow(
            title = stringResource(Res.string.settings_timeout_title),
            subtitle = stringResource(Res.string.settings_timeout_body),
            trailing = DaftarSettingTrailing.Link(timeoutLabel(prefs.inactivityTimeout)) {
                onIntent(PreferencesIntent.OpenSheet(PreferenceSheet.Timeout))
            },
        )
        SettingsNote(message = stringResource(Res.string.prefs_lock_note))

        SectionTitle(stringResource(Res.string.prefs_numerals_title))
        Text(
            text = stringResource(Res.string.prefs_numerals_body),
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.ink,
        )
        Text(
            text = LTR_SAMPLE,
            style = DaftarTheme.typography.body,
            color = DaftarTheme.colors.ink,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(Res.string.prefs_numerals_note),
            style = DaftarTheme.typography.fineText,
            color = DaftarTheme.colors.muted,
        )

        if (state.saveFailed) {
            DaftarNotePanel(modifier = Modifier.fillMaxWidth(), tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
                Text(
                    text = stringResource(Res.string.prefs_save_failed_title),
                    style = DaftarTheme.typography.noteBody,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(Res.string.prefs_save_failed_body), style = DaftarTheme.typography.noteBody)
            }
        }

        Spacer(Modifier.height(DaftarSpacing.xs))
        PrimaryButton(onClick = onBack, fullWidth = true, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.prefs_done))
        }
    }

    when (state.sheet) {
        PreferenceSheet.Theme -> ThemeSheet(
            current = prefs.themeMode,
            onSelect = { mode -> onIntent(PreferencesIntent.SelectTheme(mode)) },
            onDismiss = { onIntent(PreferencesIntent.CloseSheet) },
        )

        PreferenceSheet.Language -> LanguageSheet(
            current = prefs.language,
            onSelect = { language -> onIntent(PreferencesIntent.SelectLanguage(language)) },
            onDismiss = { onIntent(PreferencesIntent.CloseSheet) },
        )

        PreferenceSheet.Timeout -> TimeoutSheet(
            current = prefs.inactivityTimeout,
            onSelect = { timeout -> onIntent(PreferencesIntent.SelectTimeout(timeout)) },
            onDismiss = { onIntent(PreferencesIntent.CloseSheet) },
        )

        null -> Unit
    }
}

@Composable
private fun ThemeSheet(current: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    ChoiceSheet(
        title = stringResource(Res.string.settings_theme_title),
        options = ThemeMode.entries.map { mode ->
            ChoiceOption(label = themeLabel(mode), selected = mode == current) { onSelect(mode) }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun LanguageSheet(current: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    ChoiceSheet(
        title = stringResource(Res.string.settings_language_title),
        options = AppLanguage.entries.map { language ->
            ChoiceOption(label = languageLabel(language), selected = language == current) { onSelect(language) }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun TimeoutSheet(current: InactivityTimeout, onSelect: (InactivityTimeout) -> Unit, onDismiss: () -> Unit) {
    ChoiceSheet(
        title = stringResource(Res.string.settings_timeout_title),
        options = InactivityTimeout.entries.map { timeout ->
            ChoiceOption(label = timeoutLabel(timeout), selected = timeout == current) { onSelect(timeout) }
        },
        onDismiss = onDismiss,
    )
}

/** One choice inside a sheet: its label, whether it is the committed value, and what choosing it does. */
private class ChoiceOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(title: String, options: List<ChoiceOption>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DaftarSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
        ) {
            Text(
                text = title,
                style = DaftarTheme.typography.sectionHeading,
                color = DaftarTheme.colors.ink,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            options.forEach { option ->
                ChoiceRow(label = option.label, selected = option.selected, onClick = option.onSelect)
            }
            Spacer(Modifier.height(DaftarSpacing.xxl))
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DaftarTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DaftarSpacing.touch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = DaftarSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
    ) {
        Text(
            text = label,
            style = DaftarTheme.typography.settingTitle,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            DaftarIcon(icon = DaftarIcons.Check, contentDescription = null, tint = colors.primary)
        }
    }
}

/** A sample figure with the numeral rules applied: ASCII digits, comma grouping, a decimal point, in an LTR isolate. */
private const val LTR_SAMPLE: String = "⁦1,076,212.74 EGP⁩"
