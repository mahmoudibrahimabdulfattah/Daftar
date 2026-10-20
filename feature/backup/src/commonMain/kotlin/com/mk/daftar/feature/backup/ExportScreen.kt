package com.mk.daftar.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupExportState
import com.mk.daftar.core.backup.BackupPasswordProblem
import com.mk.daftar.core.designsystem.component.DaftarFieldKind
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTextField
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.pageScroll
import com.mk.daftar.feature.backup.resources.Res
import com.mk.daftar.feature.backup.resources.backup_busy_heading
import com.mk.daftar.feature.backup.resources.backup_busy_message
import com.mk.daftar.feature.backup.resources.backup_cancel
import com.mk.daftar.feature.backup.resources.backup_choose_other
import com.mk.daftar.feature.backup.resources.backup_confirm_label
import com.mk.daftar.feature.backup.resources.backup_confirm_mismatch
import com.mk.daftar.feature.backup.resources.backup_failed_heading
import com.mk.daftar.feature.backup.resources.backup_failed_other
import com.mk.daftar.feature.backup.resources.backup_failed_safe
import com.mk.daftar.feature.backup.resources.backup_failed_write
import com.mk.daftar.feature.backup.resources.backup_intro_continue
import com.mk.daftar.feature.backup.resources.backup_intro_heading
import com.mk.daftar.feature.backup.resources.backup_intro_message
import com.mk.daftar.feature.backup.resources.backup_memory_body
import com.mk.daftar.feature.backup.resources.backup_memory_cancel
import com.mk.daftar.feature.backup.resources.backup_memory_export
import com.mk.daftar.feature.backup.resources.backup_memory_title
import com.mk.daftar.feature.backup.resources.backup_memory_try_again
import com.mk.daftar.feature.backup.resources.backup_password_heading
import com.mk.daftar.feature.backup.resources.backup_password_hint
import com.mk.daftar.feature.backup.resources.backup_password_invalid
import com.mk.daftar.feature.backup.resources.backup_password_label
import com.mk.daftar.feature.backup.resources.backup_password_too_long
import com.mk.daftar.feature.backup.resources.backup_password_too_short
import com.mk.daftar.feature.backup.resources.backup_password_warning
import com.mk.daftar.feature.backup.resources.backup_submit
import com.mk.daftar.feature.backup.resources.backup_success_done
import com.mk.daftar.feature.backup.resources.backup_success_heading
import com.mk.daftar.feature.backup.resources.backup_success_message
import com.mk.daftar.feature.backup.resources.backup_title
import com.mk.daftar.feature.backup.resources.backup_writing_heading
import com.mk.daftar.feature.backup.resources.backup_writing_message
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The export screen: the view model from Koin, system back and the route's close effect. */
@Composable
fun ExportScreen(modifier: Modifier = Modifier) {
    val viewModel: ExportViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ExportEffect.Close -> navigator.goBack()
        }
    }
    ExportContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/**
 * The export screen body. It renders [state] only, so the screenshot host can show every step with fake state. One
 * scrolling column owns the IME inset, so a focused password field stays visible.
 */
@Composable
fun ExportContent(state: ExportUiState, onIntent: (ExportIntent) -> Unit, modifier: Modifier = Modifier) {
    val operation = state.operation
    val busy = operation == BackupExportState.Preparing ||
        operation == BackupExportState.ChoosingDestination ||
        operation == BackupExportState.Writing
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(Res.string.backup_title),
                onBack = when {
                    operation == BackupExportState.Preparing -> ({ onIntent(ExportIntent.CancelPreparing) })
                    busy -> null
                    else -> ({ onIntent(ExportIntent.Back) })
                },
                backLabel = stringResource(DesignRes.string.btn_back),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .pageScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md)
                    .padding(bottom = DaftarSpacing.xl),
            ) {
                Column(modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth()) {
                    when (operation) {
                        BackupExportState.Idle -> when (state.step) {
                            ExportStep.INTRO -> IntroBody(onIntent)
                            ExportStep.PASSWORD -> PasswordBody(state, onIntent)
                        }

                        BackupExportState.Preparing -> BusyBody(
                            heading = stringResource(Res.string.backup_busy_heading),
                            message = stringResource(Res.string.backup_busy_message),
                        )

                        BackupExportState.ChoosingDestination -> BusyBody(
                            heading = stringResource(Res.string.backup_busy_heading),
                            message = stringResource(Res.string.backup_busy_message),
                        )

                        BackupExportState.Writing -> BusyBody(
                            heading = stringResource(Res.string.backup_writing_heading),
                            message = stringResource(Res.string.backup_writing_message),
                        )

                        is BackupExportState.Complete -> DaftarResultState(
                            title = stringResource(Res.string.backup_success_heading),
                            message = stringResource(Res.string.backup_success_message) + "\n" +
                                "⁦${operation.fileName}⁩",
                            primaryLabel = stringResource(Res.string.backup_success_done),
                            onPrimary = { onIntent(ExportIntent.Acknowledge) },
                        )

                        is BackupExportState.Failed -> FailedBody(operation.error, onIntent)
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroBody(onIntent: (ExportIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            text = stringResource(Res.string.backup_intro_heading),
            style = DaftarTheme.typography.sectionHeading,
            color = DaftarTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.backup_intro_message),
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.muted,
        )
        PrimaryButton(onClick = { onIntent(ExportIntent.Continue) }, fullWidth = true) {
            Text(stringResource(Res.string.backup_intro_continue))
        }
    }
}

@Composable
private fun PasswordBody(state: ExportUiState, onIntent: (ExportIntent) -> Unit) {
    val colors = DaftarTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            text = stringResource(Res.string.backup_password_heading),
            style = DaftarTheme.typography.supportingBody,
            color = colors.ink,
        )
        DaftarTextField(
            value = state.password,
            onValueChange = { onIntent(ExportIntent.PasswordChanged(it)) },
            label = stringResource(Res.string.backup_password_label),
            kind = DaftarFieldKind.Password,
            helper = passwordHelper(state),
            invalid = state.showPasswordError,
        )
        DaftarTextField(
            value = state.confirm,
            onValueChange = { onIntent(ExportIntent.ConfirmChanged(it)) },
            label = stringResource(Res.string.backup_confirm_label),
            kind = DaftarFieldKind.Password,
            helper = if (state.confirmMismatch) stringResource(Res.string.backup_confirm_mismatch) else null,
            invalid = state.confirmMismatch,
        )
        DaftarNotePanel(tone = DaftarNoteTone.Warning) {
            Text(
                text = stringResource(Res.string.backup_password_warning),
                style = DaftarTheme.typography.supportingBody,
                color = colors.ink,
            )
        }
        Text(
            text = stringResource(pickerNoteResource()),
            style = DaftarTheme.typography.supportingBody,
            color = colors.muted,
        )
        PrimaryButton(onClick = { onIntent(ExportIntent.Submit) }, fullWidth = true) {
            Text(stringResource(Res.string.backup_submit))
        }
    }
}

@Composable
private fun passwordHelper(state: ExportUiState): String = when {
    state.showPasswordError -> when (state.passwordProblem) {
        BackupPasswordProblem.TOO_SHORT -> stringResource(Res.string.backup_password_too_short)
        BackupPasswordProblem.TOO_LONG -> stringResource(Res.string.backup_password_too_long)
        BackupPasswordProblem.INVALID_CHARACTERS -> stringResource(Res.string.backup_password_invalid)
        null -> stringResource(Res.string.backup_password_hint)
    }

    else -> stringResource(Res.string.backup_password_hint)
}

@Composable
private fun BusyBody(heading: String, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = DaftarTheme.colors.primary)
        Spacer(Modifier.height(DaftarSpacing.lg))
        Text(
            text = heading,
            style = DaftarTheme.typography.sectionHeading,
            color = DaftarTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(DaftarSpacing.xs))
        Text(
            text = message,
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FailedBody(error: BackupError, onIntent: (ExportIntent) -> Unit) {
    if (error == BackupError.InsufficientMemory) {
        // Nothing was saved and the data is unchanged: the notice offers the same export again or cancels it.
        DaftarResultState(
            title = stringResource(Res.string.backup_memory_title),
            message = stringResource(Res.string.backup_memory_body) + "\n" +
                stringResource(Res.string.backup_memory_export),
            primaryLabel = stringResource(Res.string.backup_memory_try_again),
            onPrimary = { onIntent(ExportIntent.RetryAfterMemory) },
            tone = DaftarResultTone.Error,
            secondaryLabel = stringResource(Res.string.backup_memory_cancel),
            onSecondary = { onIntent(ExportIntent.Back) },
        )
        return
    }
    val reason = if (error == BackupError.WriteFailed) {
        stringResource(Res.string.backup_failed_write)
    } else {
        stringResource(Res.string.backup_failed_other)
    }
    DaftarResultState(
        title = stringResource(Res.string.backup_failed_heading),
        message = reason + "\n" + stringResource(Res.string.backup_failed_safe),
        primaryLabel = stringResource(Res.string.backup_choose_other),
        onPrimary = { onIntent(ExportIntent.Acknowledge) },
        tone = DaftarResultTone.Error,
        secondaryLabel = stringResource(Res.string.backup_cancel),
        onSecondary = { onIntent(ExportIntent.Back) },
    )
}

private val CONTENT_MAX_WIDTH = 560.dp
