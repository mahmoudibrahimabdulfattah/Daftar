package com.mk.daftar.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarSettingRow
import com.mk.daftar.core.designsystem.component.DaftarSettingTrailing
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.cloud.CloudAvailability
import com.mk.daftar.core.domain.cloud.CloudBackupRepository
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.navigation.AboutRoute
import com.mk.daftar.core.navigation.BackupExportRoute
import com.mk.daftar.core.navigation.ChangePinRoute
import com.mk.daftar.core.navigation.CloudRoute
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.navigation.PreferencesRoute
import com.mk.daftar.feature.settings.AppBuildInfo
import com.mk.daftar.feature.settings.BiometricAvailability
import com.mk.daftar.feature.settings.BiometricPinSheet
import com.mk.daftar.feature.settings.BiometricSettings
import com.mk.daftar.feature.settings.SecurityActionFailure
import com.mk.daftar.feature.settings.SettingsIntent
import com.mk.daftar.feature.settings.SettingsUiState
import com.mk.daftar.feature.settings.SettingsViewModel
import com.mk.daftar.feature.settings.resources.Res
import com.mk.daftar.feature.settings.resources.bio_enable_action
import com.mk.daftar.feature.settings.resources.bio_sheet_body
import com.mk.daftar.feature.settings.resources.bio_sheet_heading
import com.mk.daftar.feature.settings.resources.settings_about_body
import com.mk.daftar.feature.settings.resources.settings_about_open
import com.mk.daftar.feature.settings.resources.settings_about_title
import com.mk.daftar.feature.settings.resources.settings_backup_action
import com.mk.daftar.feature.settings.resources.settings_backup_heading
import com.mk.daftar.feature.settings.resources.settings_backup_subtitle
import com.mk.daftar.feature.settings.resources.settings_backup_title
import com.mk.daftar.feature.settings.resources.settings_biometrics_body
import com.mk.daftar.feature.settings.resources.settings_biometrics_invalidated
import com.mk.daftar.feature.settings.resources.settings_biometrics_not_enrolled
import com.mk.daftar.feature.settings.resources.settings_biometrics_title
import com.mk.daftar.feature.settings.resources.settings_biometrics_unavailable
import com.mk.daftar.feature.settings.resources.settings_cancel
import com.mk.daftar.feature.settings.resources.settings_cloud_action
import com.mk.daftar.feature.settings.resources.settings_cloud_subtitle
import com.mk.daftar.feature.settings.resources.settings_cloud_title
import com.mk.daftar.feature.settings.resources.settings_footer
import com.mk.daftar.feature.settings.resources.settings_language_body
import com.mk.daftar.feature.settings.resources.settings_language_title
import com.mk.daftar.feature.settings.resources.settings_lock_body
import com.mk.daftar.feature.settings.resources.settings_lock_title
import com.mk.daftar.feature.settings.resources.settings_lock_value
import com.mk.daftar.feature.settings.resources.settings_pin_body
import com.mk.daftar.feature.settings.resources.settings_pin_change
import com.mk.daftar.feature.settings.resources.settings_pin_title
import com.mk.daftar.feature.settings.resources.settings_section_about
import com.mk.daftar.feature.settings.resources.settings_section_appearance
import com.mk.daftar.feature.settings.resources.settings_section_security
import com.mk.daftar.feature.settings.resources.settings_theme_body
import com.mk.daftar.feature.settings.resources.settings_theme_title
import com.mk.daftar.feature.settings.resources.settings_timeout_body
import com.mk.daftar.feature.settings.resources.settings_timeout_title
import com.mk.daftar.feature.settings.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Settings tab. It reads the security state and the preferences, and turns biometrics on or off. Every change waits
 * for the security layer or the preference store to commit it. Rows that open another screen go through the navigator.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appInfo: AppBuildInfo = koinInject()
    val cloud: CloudBackupRepository = koinInject()
    // Device settings may have changed biometric enrollment while the app was away: read the platform again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onIntent(SettingsIntent.Refresh) }
    SettingsContent(
        state = state,
        appInfo = appInfo,
        onIntent = viewModel::onIntent,
        onChangePin = { navigator.goTo(ChangePinRoute) },
        onOpenPreferences = { navigator.goTo(PreferencesRoute) },
        onOpenAbout = { navigator.goTo(AboutRoute) },
        onOpenExport = { navigator.goTo(BackupExportRoute) },
        cloudOffered = cloud.availability == CloudAvailability.AVAILABLE,
        onOpenCloud = { navigator.goTo(CloudRoute) },
        modifier = modifier,
    )
}

/** The stateless Settings page. Screenshots render it with fake state; the route passes the navigation actions. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    appInfo: AppBuildInfo,
    onIntent: (SettingsIntent) -> Unit,
    onChangePin: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenExport: () -> Unit,
    cloudOffered: Boolean = false,
    onOpenCloud: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val prefs = state.preferences ?: UserPreferences()
    SettingsPage(title = stringResource(Res.string.settings_title), modifier = modifier) {
        SectionTitle(stringResource(Res.string.settings_section_security))
        DaftarSettingRow(
            title = stringResource(Res.string.settings_pin_title),
            subtitle = stringResource(Res.string.settings_pin_body),
            trailing = DaftarSettingTrailing.Link(stringResource(Res.string.settings_pin_change), onChangePin),
        )
        BiometricsRow(state = state, onIntent = onIntent)
        DaftarSettingRow(
            title = stringResource(Res.string.settings_lock_title),
            subtitle = stringResource(Res.string.settings_lock_body),
            trailing = DaftarSettingTrailing.Value(stringResource(Res.string.settings_lock_value)),
        )
        DaftarSettingRow(
            title = stringResource(Res.string.settings_timeout_title),
            subtitle = stringResource(Res.string.settings_timeout_body),
            trailing = DaftarSettingTrailing.Link(timeoutLabel(prefs.inactivityTimeout), onOpenPreferences),
        )

        SectionTitle(stringResource(Res.string.settings_section_appearance))
        DaftarSettingRow(
            title = stringResource(Res.string.settings_theme_title),
            subtitle = stringResource(Res.string.settings_theme_body),
            trailing = DaftarSettingTrailing.Link(themeLabel(prefs.themeMode), onOpenPreferences),
        )
        DaftarSettingRow(
            title = stringResource(Res.string.settings_language_title),
            subtitle = stringResource(Res.string.settings_language_body),
            trailing = DaftarSettingTrailing.Link(languageLabel(prefs.language), onOpenPreferences),
        )

        SectionTitle(stringResource(Res.string.settings_backup_heading))
        DaftarSettingRow(
            title = stringResource(Res.string.settings_backup_title),
            subtitle = stringResource(Res.string.settings_backup_subtitle),
            trailing = DaftarSettingTrailing.Link(stringResource(Res.string.settings_backup_action), onOpenExport),
        )
        if (cloudOffered) {
            DaftarSettingRow(
                title = stringResource(Res.string.settings_cloud_title),
                subtitle = stringResource(Res.string.settings_cloud_subtitle),
                trailing = DaftarSettingTrailing.Link(stringResource(Res.string.settings_cloud_action), onOpenCloud),
            )
        }

        SectionTitle(stringResource(Res.string.settings_section_about))
        DaftarSettingRow(
            title = stringResource(Res.string.settings_about_title),
            subtitle = stringResource(Res.string.settings_about_body, appInfo.versionName),
            trailing = DaftarSettingTrailing.Link(stringResource(Res.string.settings_about_open), onOpenAbout),
        )
        Text(
            text = stringResource(Res.string.settings_footer, appInfo.versionName),
            style = DaftarTheme.typography.fineText,
            color = DaftarTheme.colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = DaftarSpacing.sm),
        )
    }
    state.biometricSheet?.let { sheet -> BiometricPinSheetContent(sheet = sheet, onIntent = onIntent) }
}

/**
 * The biometric switch and what the platform says about it. A switch that cannot be turned on says why. Turning it off
 * needs no prompt and is always allowed.
 */
@Composable
private fun BiometricsRow(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    val settings: BiometricSettings? = state.biometric
    val switchEnabled = settings != null && !state.biometricBusy && (settings.enabled || settings.canEnable)
    val availabilityNote = settings?.availability?.let { availabilityMessage(it) }
    val failureNote = state.biometricFailure?.let { biometricFailureMessage(it) }
    val failureIsError = state.biometricFailure != null && state.biometricFailure != SecurityActionFailure.Cancelled
    DaftarSettingRow(
        title = stringResource(Res.string.settings_biometrics_title),
        subtitle = stringResource(Res.string.settings_biometrics_body),
        trailing = DaftarSettingTrailing.Switch(
            checked = settings?.enabled == true,
            onCheckedChange = { enabled -> onIntent(SettingsIntent.SetBiometricEnabled(enabled)) },
            label = stringResource(Res.string.settings_biometrics_title),
            enabled = switchEnabled,
        ),
    )
    availabilityNote?.let { SettingsNote(message = it) }
    if (availabilityNote == null && failureNote != null) {
        SettingsNote(
            message = failureNote,
            tone = if (failureIsError) DaftarNoteTone.Error else DaftarNoteTone.Context,
            icon = if (failureIsError) DaftarIcons.Alert else null,
        )
    }
}

@Composable
private fun availabilityMessage(availability: BiometricAvailability): String? = when (availability) {
    BiometricAvailability.Ready -> null
    BiometricAvailability.NotEnrolled -> stringResource(Res.string.settings_biometrics_not_enrolled)
    BiometricAvailability.Unavailable -> stringResource(Res.string.settings_biometrics_unavailable)
    BiometricAvailability.Invalidated -> stringResource(Res.string.settings_biometrics_invalidated)
}

/** The PIN prompt that precedes the platform biometric prompt. It is a sheet: the Settings list stays behind it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BiometricPinSheetContent(sheet: BiometricPinSheet, onIntent: (SettingsIntent) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onIntent(SettingsIntent.DismissBiometricSheet) }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DaftarSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.bio_sheet_heading),
                style = DaftarTheme.typography.screenHeading,
                color = DaftarTheme.colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.bio_sheet_body),
                style = DaftarTheme.typography.supportingBody,
                color = DaftarTheme.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            PinField(
                label = stringResource(Res.string.settings_pin_title),
                enteredCount = sheet.enteredCount,
                enabled = sheet.inputEnabled,
                invalid = sheet.failure == SecurityActionFailure.WrongPin,
            )
            pinFailureMessage(sheet.failure)?.let { message ->
                SettingsNote(message = message, tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert)
            }
            sheet.retrySeconds?.let { seconds ->
                SettingsNote(message = backoffMessage(seconds), icon = DaftarIcons.Clock)
            }
            PinKeypad(
                enabled = sheet.inputEnabled,
                onDigit = { digit -> onIntent(SettingsIntent.Digit('0'.code + digit)) },
                onErase = { onIntent(SettingsIntent.Erase) },
            )
            PrimaryButton(
                onClick = { onIntent(SettingsIntent.Submit) },
                enabled = sheet.submitEnabled,
                loading = sheet.busy,
                fullWidth = true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.bio_enable_action))
            }
            DaftarTextButton(
                onClick = { onIntent(SettingsIntent.DismissBiometricSheet) },
                enabled = !sheet.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.settings_cancel))
            }
            Spacer(Modifier.height(DaftarSpacing.xxl))
        }
    }
}
