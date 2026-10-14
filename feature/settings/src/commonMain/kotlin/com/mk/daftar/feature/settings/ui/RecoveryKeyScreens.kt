package com.mk.daftar.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarFieldKind
import com.mk.daftar.core.designsystem.component.DaftarMark
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTextField
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.feature.settings.RecoveryKeyIntent
import com.mk.daftar.feature.settings.RecoveryKeyStage
import com.mk.daftar.feature.settings.RecoveryKeyUiState
import com.mk.daftar.feature.settings.RecoveryKeyViewModel
import com.mk.daftar.feature.settings.SecurityActionFailure
import com.mk.daftar.feature.settings.resources.Res
import com.mk.daftar.feature.settings.resources.rk_check_body
import com.mk.daftar.feature.settings.resources.rk_check_heading
import com.mk.daftar.feature.settings.resources.rk_check_label
import com.mk.daftar.feature.settings.resources.rk_check_mismatch
import com.mk.daftar.feature.settings.resources.rk_continue
import com.mk.daftar.feature.settings.resources.rk_copied
import com.mk.daftar.feature.settings.resources.rk_copy
import com.mk.daftar.feature.settings.resources.rk_create
import com.mk.daftar.feature.settings.resources.rk_create_new
import com.mk.daftar.feature.settings.resources.rk_done
import com.mk.daftar.feature.settings.resources.rk_done_body
import com.mk.daftar.feature.settings.resources.rk_done_heading
import com.mk.daftar.feature.settings.resources.rk_intro_body
import com.mk.daftar.feature.settings.resources.rk_intro_heading
import com.mk.daftar.feature.settings.resources.rk_intro_limit
import com.mk.daftar.feature.settings.resources.rk_pin_label
import com.mk.daftar.feature.settings.resources.rk_saved
import com.mk.daftar.feature.settings.resources.rk_show_again
import com.mk.daftar.feature.settings.resources.rk_show_body
import com.mk.daftar.feature.settings.resources.rk_show_heading
import com.mk.daftar.feature.settings.resources.rk_status_absent
import com.mk.daftar.feature.settings.resources.rk_status_active
import com.mk.daftar.feature.settings.resources.rk_title
import com.mk.daftar.feature.settings.resources.rk_verify_heading
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Creating or replacing the recovery key. Leaving the screen drops the key from memory with the view model. */
@Composable
fun RecoveryKeyScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: RecoveryKeyViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecoveryKeyContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

@Composable
fun RecoveryKeyContent(
    state: RecoveryKeyUiState,
    onIntent: (RecoveryKeyIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(
        title = stringResource(Res.string.rk_title),
        // Hardware keys type the PIN only on its own step; the check field types its own characters.
        modifier = if (state.stage == RecoveryKeyStage.Verify) {
            modifier.pinHardwareKeys(
                onDigit = { codePoint -> onIntent(RecoveryKeyIntent.Digit(codePoint)) },
                onErase = { onIntent(RecoveryKeyIntent.Erase) },
                onSubmit = { onIntent(RecoveryKeyIntent.Submit) },
            )
        } else {
            modifier
        },
        onBack = if (state.busy) null else onBack,
    ) {
        when (val stage = state.stage) {
            RecoveryKeyStage.Intro -> IntroStep(state, onIntent)
            RecoveryKeyStage.Verify -> VerifyStep(state, onIntent)
            is RecoveryKeyStage.Show -> ShowStep(stage.key, onIntent)
            is RecoveryKeyStage.Check -> CheckStep(state, onIntent)
            RecoveryKeyStage.Done -> DoneStep(onBack)
        }
    }
}

@Composable
private fun Step(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        content = content,
    )
}

@Composable
private fun Heading(text: String, body: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
    ) {
        DaftarMark(Modifier.size(MARK_SIZE), contentDescription = null)
        Text(
            text = text,
            style = DaftarTheme.typography.screenHeading,
            color = DaftarTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        if (body != null) {
            Text(
                text = body,
                style = DaftarTheme.typography.supportingBody,
                color = DaftarTheme.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun IntroStep(state: RecoveryKeyUiState, onIntent: (RecoveryKeyIntent) -> Unit) {
    Step {
        Heading(stringResource(Res.string.rk_intro_heading), stringResource(Res.string.rk_intro_body))
        SettingsNote(message = stringResource(Res.string.rk_intro_limit), tone = DaftarNoteTone.Warning)
        when (state.active) {
            true -> SettingsNote(message = stringResource(Res.string.rk_status_active))
            false -> SettingsNote(message = stringResource(Res.string.rk_status_absent))
            null -> Unit
        }
        PrimaryButton(
            onClick = { onIntent(RecoveryKeyIntent.Start) },
            enabled = state.active != null,
            fullWidth = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (state.active == true) Res.string.rk_create_new else Res.string.rk_create))
        }
    }
}

@Composable
private fun VerifyStep(state: RecoveryKeyUiState, onIntent: (RecoveryKeyIntent) -> Unit) {
    Step {
        Heading(stringResource(Res.string.rk_verify_heading), null)
        PinField(
            label = stringResource(Res.string.rk_pin_label),
            enteredCount = state.enteredCount,
            enabled = state.inputEnabled,
            invalid = state.failure == SecurityActionFailure.WrongPin,
        )
        pinFailureMessage(state.failure)?.let { message ->
            SettingsNote(message = message, tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert)
        }
        state.retrySeconds?.let { seconds ->
            SettingsNote(message = backoffMessage(seconds), icon = DaftarIcons.Clock)
        }
        PinKeypad(
            enabled = state.inputEnabled,
            onDigit = { digit -> onIntent(RecoveryKeyIntent.Digit('0'.code + digit)) },
            onErase = { onIntent(RecoveryKeyIntent.Erase) },
        )
        PrimaryButton(
            onClick = { onIntent(RecoveryKeyIntent.Submit) },
            enabled = state.submitEnabled,
            loading = state.busy,
            fullWidth = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.rk_continue))
        }
    }
}

/** The key, in two columns of four groups, always left to right, in the numeric face for unambiguous characters. */
@Composable
private fun ShowStep(key: String, onIntent: (RecoveryKeyIntent) -> Unit) {
    Step {
        Heading(stringResource(Res.string.rk_show_heading), stringResource(Res.string.rk_show_body))
        KeyGroups(key)
        val copy = rememberSensitiveCopy()
        var copied by remember(key) { mutableStateOf(false) }
        OutlineButton(
            onClick = {
                copy(key)
                copied = true
            },
            fullWidth = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.rk_copy))
        }
        if (copied) SettingsNote(message = stringResource(Res.string.rk_copied))
        PrimaryButton(
            onClick = { onIntent(RecoveryKeyIntent.Saved) },
            fullWidth = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.rk_saved))
        }
    }
}

@Composable
private fun KeyGroups(key: String) {
    val groups = key.split('-')
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = DaftarSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            groups.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.md)) {
                    pair.forEachIndexed { column, group ->
                        Text(
                            text = "${row * 2 + column + 1}. $group",
                            style = DaftarTheme.typography.ledgerValue.copy(textDirection = TextDirection.Ltr),
                            color = DaftarTheme.colors.ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckStep(state: RecoveryKeyUiState, onIntent: (RecoveryKeyIntent) -> Unit) {
    Step {
        Heading(stringResource(Res.string.rk_check_heading), stringResource(Res.string.rk_check_body))
        var text by remember { mutableStateOf("") }
        DaftarTextField(
            value = text,
            onValueChange = { value ->
                text = value
                onIntent(RecoveryKeyIntent.CheckGroup(value))
            },
            label = stringResource(Res.string.rk_check_label),
            kind = DaftarFieldKind.Code,
            helper = if (state.checkMismatch) stringResource(Res.string.rk_check_mismatch) else null,
            invalid = state.checkMismatch,
            modifier = Modifier.fillMaxWidth(),
        )
        DaftarTextButton(onClick = { onIntent(RecoveryKeyIntent.ShowAgain) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.rk_show_again))
        }
    }
}

@Composable
private fun DoneStep(onBack: () -> Unit) {
    Step {
        Heading(stringResource(Res.string.rk_done_heading), stringResource(Res.string.rk_done_body))
        PrimaryButton(onClick = onBack, fullWidth = true, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.rk_done))
        }
    }
}

private val MARK_SIZE = 44.dp
