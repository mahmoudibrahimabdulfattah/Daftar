package com.mk.daftar.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarMark
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.feature.settings.ChangePinIntent
import com.mk.daftar.feature.settings.ChangePinStage
import com.mk.daftar.feature.settings.ChangePinUiState
import com.mk.daftar.feature.settings.ChangePinViewModel
import com.mk.daftar.feature.settings.SecurityActionFailure
import com.mk.daftar.feature.settings.resources.Res
import com.mk.daftar.feature.settings.resources.pin_change_action
import com.mk.daftar.feature.settings.resources.pin_confirm_heading
import com.mk.daftar.feature.settings.resources.pin_confirm_label
import com.mk.daftar.feature.settings.resources.pin_continue
import com.mk.daftar.feature.settings.resources.pin_current_label
import com.mk.daftar.feature.settings.resources.pin_failed_back
import com.mk.daftar.feature.settings.resources.pin_failed_previous_body
import com.mk.daftar.feature.settings.resources.pin_failed_previous_heading
import com.mk.daftar.feature.settings.resources.pin_failed_retry
import com.mk.daftar.feature.settings.resources.pin_failed_unknown_body
import com.mk.daftar.feature.settings.resources.pin_failed_unknown_heading
import com.mk.daftar.feature.settings.resources.pin_mismatch
import com.mk.daftar.feature.settings.resources.pin_new_heading
import com.mk.daftar.feature.settings.resources.pin_new_label
import com.mk.daftar.feature.settings.resources.pin_new_note
import com.mk.daftar.feature.settings.resources.pin_new_note_biometrics
import com.mk.daftar.feature.settings.resources.pin_saving
import com.mk.daftar.feature.settings.resources.pin_title
import com.mk.daftar.feature.settings.resources.pin_verify_body
import com.mk.daftar.feature.settings.resources.pin_verify_heading
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Changing the PIN, in its steps: the current PIN first, then the new PIN and its confirmation. The screen only draws
 * [ChangePinUiState] and reports intents. Leaving the screen drops every digit, because the view model is cleared with it.
 */
@Composable
fun ChangePinScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: ChangePinViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChangePinContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The stateless PIN change page. Hardware keys work on every input step, and the top bar's back action is hidden while saving. */
@Composable
fun ChangePinContent(
    state: ChangePinUiState,
    onIntent: (ChangePinIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val saving = state.stage == ChangePinStage.Saving
    SettingsPage(
        title = stringResource(Res.string.pin_title),
        modifier = modifier.pinHardwareKeys(
            onDigit = { codePoint -> onIntent(ChangePinIntent.Digit(codePoint)) },
            onErase = { onIntent(ChangePinIntent.Erase) },
            onSubmit = { onIntent(ChangePinIntent.Submit) },
        ),
        onBack = if (saving) null else onBack,
    ) {
        when (val stage = state.stage) {
            ChangePinStage.Verify -> VerifyStep(state = state, onIntent = onIntent)

            ChangePinStage.NewPin -> NewPinStep(state = state, onIntent = onIntent)

            ChangePinStage.ConfirmPin -> ConfirmStep(state = state, onIntent = onIntent)

            ChangePinStage.Saving -> SavingStep()

            is ChangePinStage.Failed -> FailedStep(
                previousPinStillWorks = stage.previousPinStillWorks,
                onRetry = { onIntent(ChangePinIntent.Restart) },
                onBack = onBack,
            )
        }
    }
}

/** One column per PIN step, so each step emits a single root, as the auth phases do. */
@Composable
private fun StepColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        content = content,
    )
}

@Composable
private fun VerifyStep(state: ChangePinUiState, onIntent: (ChangePinIntent) -> Unit) {
    StepColumn {
        PinIntro(
            heading = stringResource(Res.string.pin_verify_heading),
            body = stringResource(Res.string.pin_verify_body),
        )
        PinField(
            label = stringResource(Res.string.pin_current_label),
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
        KeypadAndContinue(state = state, onIntent = onIntent, label = stringResource(Res.string.pin_continue))
    }
}

@Composable
private fun NewPinStep(state: ChangePinUiState, onIntent: (ChangePinIntent) -> Unit) {
    StepColumn {
        PinIntro(heading = stringResource(Res.string.pin_new_heading), body = null)
        PinField(
            label = stringResource(Res.string.pin_new_label),
            enteredCount = state.enteredCount,
            enabled = state.inputEnabled,
            invalid = false,
        )
        SettingsNote(message = stringResource(Res.string.pin_new_note))
        SettingsNote(message = stringResource(Res.string.pin_new_note_biometrics))
        KeypadAndContinue(state = state, onIntent = onIntent, label = stringResource(Res.string.pin_continue))
    }
}

@Composable
private fun ConfirmStep(state: ChangePinUiState, onIntent: (ChangePinIntent) -> Unit) {
    StepColumn {
        PinIntro(heading = stringResource(Res.string.pin_confirm_heading), body = null)
        PinField(
            label = stringResource(Res.string.pin_confirm_label),
            enteredCount = state.enteredCount,
            enabled = state.inputEnabled,
            invalid = state.mismatch,
        )
        if (state.mismatch) {
            SettingsNote(
                message = stringResource(Res.string.pin_mismatch),
                tone = DaftarNoteTone.Error,
                icon = DaftarIcons.Alert,
            )
        }
        KeypadAndContinue(state = state, onIntent = onIntent, label = stringResource(Res.string.pin_change_action))
    }
}

@Composable
private fun KeypadAndContinue(state: ChangePinUiState, onIntent: (ChangePinIntent) -> Unit, label: String) {
    StepColumn {
        PinKeypad(
            enabled = state.inputEnabled,
            onDigit = { digit -> onIntent(ChangePinIntent.Digit('0'.code + digit)) },
            onErase = { onIntent(ChangePinIntent.Erase) },
        )
        PrimaryButton(
            onClick = { onIntent(ChangePinIntent.Submit) },
            enabled = state.submitEnabled,
            loading = state.busy,
            fullWidth = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label)
        }
    }
}

@Composable
private fun SavingStep() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = DaftarSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
    ) {
        CircularProgressIndicator(color = DaftarTheme.colors.primary)
        Text(
            text = stringResource(Res.string.pin_saving),
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A failed change. "Your previous PIN still works" appears only when the failure was confirmed before the envelope was
 * replaced. Any other outcome is described as unconfirmed.
 */
@Composable
private fun FailedStep(previousPinStillWorks: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = DaftarSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
    ) {
        DaftarMark(Modifier.size(PIN_MARK_SIZE), contentDescription = null)
        Text(
            text = stringResource(
                if (previousPinStillWorks) {
                    Res.string.pin_failed_previous_heading
                } else {
                    Res.string.pin_failed_unknown_heading
                },
            ),
            style = typography.screenHeading,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            text = stringResource(
                if (previousPinStillWorks) Res.string.pin_failed_previous_body else Res.string.pin_failed_unknown_body,
            ),
            style = typography.supportingBody,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(DaftarSpacing.xs))
        OutlineButton(onClick = onRetry, fullWidth = true, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.pin_failed_retry))
        }
        DaftarTextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.pin_failed_back))
        }
    }
}

@Composable
private fun PinIntro(heading: String, body: String?) {
    val colors = DaftarTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
    ) {
        DaftarMark(Modifier.size(PIN_MARK_SIZE), contentDescription = null)
        Text(
            text = heading,
            style = DaftarTheme.typography.screenHeading,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        if (body != null) {
            Text(
                text = body,
                style = DaftarTheme.typography.supportingBody,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val PIN_MARK_SIZE = 44.dp
