package com.mk.daftar.feature.backup.restore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarConfirmationContent
import com.mk.daftar.core.designsystem.component.DaftarFieldKind
import com.mk.daftar.core.designsystem.component.DaftarKeypad
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarPinField
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
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.shell.pageScroll
import com.mk.daftar.feature.backup.resources.Res
import com.mk.daftar.feature.backup.resources.restore_account_count
import com.mk.daftar.feature.backup.resources.restore_bank_count
import com.mk.daftar.feature.backup.resources.restore_cancel
import com.mk.daftar.feature.backup.resources.restore_cash_count
import com.mk.daftar.feature.backup.resources.restore_choose_action
import com.mk.daftar.feature.backup.resources.restore_choose_heading
import com.mk.daftar.feature.backup.resources.restore_choose_note
import com.mk.daftar.feature.backup.resources.restore_close
import com.mk.daftar.feature.backup.resources.restore_committing_heading
import com.mk.daftar.feature.backup.resources.restore_committing_message
import com.mk.daftar.feature.backup.resources.restore_complete_action
import com.mk.daftar.feature.backup.resources.restore_complete_biometrics
import com.mk.daftar.feature.backup.resources.restore_complete_heading
import com.mk.daftar.feature.backup.resources.restore_complete_message
import com.mk.daftar.feature.backup.resources.restore_confirm_action
import com.mk.daftar.feature.backup.resources.restore_confirm_body
import com.mk.daftar.feature.backup.resources.restore_confirm_heading
import com.mk.daftar.feature.backup.resources.restore_decrypting_message
import com.mk.daftar.feature.backup.resources.restore_erase_digit
import com.mk.daftar.feature.backup.resources.restore_failed_choose_other
import com.mk.daftar.feature.backup.resources.restore_failed_file
import com.mk.daftar.feature.backup.resources.restore_failed_heading
import com.mk.daftar.feature.backup.resources.restore_failed_newer
import com.mk.daftar.feature.backup.resources.restore_failed_not_replaced
import com.mk.daftar.feature.backup.resources.restore_failed_recovery
import com.mk.daftar.feature.backup.resources.restore_failed_unreadable
import com.mk.daftar.feature.backup.resources.restore_forgot_note
import com.mk.daftar.feature.backup.resources.restore_gold_count
import com.mk.daftar.feature.backup.resources.restore_note_count
import com.mk.daftar.feature.backup.resources.restore_password_action
import com.mk.daftar.feature.backup.resources.restore_password_heading
import com.mk.daftar.feature.backup.resources.restore_password_label
import com.mk.daftar.feature.backup.resources.restore_pin_confirm
import com.mk.daftar.feature.backup.resources.restore_pin_confirm_label
import com.mk.daftar.feature.backup.resources.restore_pin_continue
import com.mk.daftar.feature.backup.resources.restore_pin_heading
import com.mk.daftar.feature.backup.resources.restore_pin_mismatch
import com.mk.daftar.feature.backup.resources.restore_pin_new_label
import com.mk.daftar.feature.backup.resources.restore_pin_note
import com.mk.daftar.feature.backup.resources.restore_preparing_heading
import com.mk.daftar.feature.backup.resources.restore_preparing_message
import com.mk.daftar.feature.backup.resources.restore_recovery_label
import com.mk.daftar.feature.backup.resources.restore_recovery_note
import com.mk.daftar.feature.backup.resources.restore_review_continue
import com.mk.daftar.feature.backup.resources.restore_review_date
import com.mk.daftar.feature.backup.resources.restore_review_heading
import com.mk.daftar.feature.backup.resources.restore_review_no_merge
import com.mk.daftar.feature.backup.resources.restore_review_validated
import com.mk.daftar.feature.backup.resources.restore_review_warning
import com.mk.daftar.feature.backup.resources.restore_title
import com.mk.daftar.feature.backup.resources.restore_use_password
import com.mk.daftar.feature.backup.resources.restore_use_recovery
import com.mk.daftar.feature.backup.resources.restore_zakat_count
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The restore wizard, hosted by the locked surface. It owns no navigation of its own: [onClose] is called when the wizard
 * is finished or closed, and the gate then returns to the locked screen with the vault still locked.
 */
@Composable
fun RestoreScreen(onClose: () -> Unit, modifier: Modifier = Modifier, autoChooseFile: Boolean = false) {
    val viewModel: RestoreViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A verified cloud download is already waiting: the picker hands it over without showing the system sheet.
    LaunchedEffect(autoChooseFile) {
        if (autoChooseFile) viewModel.onIntent(RestoreIntent.ChooseFile)
    }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            RestoreEffect.Close -> onClose()
        }
    }
    RestoreContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/**
 * The restore body. It renders [state] only, so the screenshot host can show every step with fake state. A cancelled
 * or finished wizard leaves through [RestoreIntent.Close] or [RestoreIntent.Acknowledge].
 */
@Composable
fun RestoreContent(state: RestoreUiState, onIntent: (RestoreIntent) -> Unit, modifier: Modifier = Modifier) {
    val phase = state.phase
    val back: (() -> Unit)? = when (phase) {
        RestorePhase.Committing, RestorePhase.Decrypting, RestorePhase.Preparing -> null
        RestorePhase.ChooseFile, RestorePhase.Complete -> ({ onIntent(RestoreIntent.Close) })
        else -> ({ onIntent(RestoreIntent.Back) })
    }
    // The wizard sits outside the shell, so it paints its own paper background.
    Column(modifier = modifier.fillMaxSize().background(DaftarTheme.colors.paper)) {
        DaftarTopBar(
            title = stringResource(Res.string.restore_title),
            onBack = back,
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
                when (phase) {
                    RestorePhase.ChooseFile -> ChooseBody(onIntent)

                    RestorePhase.Password -> PasswordBody(state, onIntent)

                    RestorePhase.Decrypting -> BusyBody(
                        heading = stringResource(Res.string.restore_password_action),
                        message = stringResource(Res.string.restore_decrypting_message),
                    )

                    RestorePhase.Review -> ReviewBody(state, onIntent)

                    is RestorePhase.NewPin -> PinBody(state, phase.confirming, onIntent)

                    RestorePhase.ConfirmReplacement -> Column(Modifier.fillMaxWidth().padding(top = DaftarSpacing.md)) {
                        DaftarConfirmationContent(
                            title = stringResource(Res.string.restore_confirm_heading),
                            message = stringResource(Res.string.restore_confirm_body),
                            primaryLabel = stringResource(Res.string.restore_cancel),
                            onPrimaryClick = { onIntent(RestoreIntent.CancelReplacement) },
                            destructiveLabel = stringResource(Res.string.restore_confirm_action),
                            onDestructiveClick = { onIntent(RestoreIntent.ConfirmReplacement) },
                        )
                    }

                    RestorePhase.Preparing -> Column {
                        BusyBody(
                            heading = stringResource(Res.string.restore_preparing_heading),
                            message = stringResource(Res.string.restore_preparing_message),
                        )
                        DaftarTextButton(
                            onClick = { onIntent(RestoreIntent.CancelPreparing) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.restore_cancel))
                        }
                    }

                    RestorePhase.Committing -> BusyBody(
                        heading = stringResource(Res.string.restore_committing_heading),
                        message = stringResource(Res.string.restore_committing_message),
                    )

                    RestorePhase.Complete -> DaftarResultState(
                        title = stringResource(Res.string.restore_complete_heading),
                        message = stringResource(Res.string.restore_complete_message) + "\n" +
                            stringResource(Res.string.restore_complete_biometrics),
                        primaryLabel = stringResource(Res.string.restore_complete_action),
                        onPrimary = { onIntent(RestoreIntent.Acknowledge) },
                    )

                    is RestorePhase.Failed -> FailedBody(phase.problem, onIntent)
                }
            }
        }
    }
}

@Composable
private fun ChooseBody(onIntent: (RestoreIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            text = stringResource(Res.string.restore_choose_heading),
            style = DaftarTheme.typography.sectionHeading,
            color = DaftarTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        DaftarNotePanel(tone = DaftarNoteTone.Context) {
            Text(stringResource(Res.string.restore_choose_note), style = DaftarTheme.typography.noteBody)
        }
        PrimaryButton(onClick = { onIntent(RestoreIntent.ChooseFile) }, fullWidth = true) {
            Text(stringResource(Res.string.restore_choose_action))
        }
        Text(
            text = stringResource(Res.string.restore_forgot_note),
            style = DaftarTheme.typography.fineText,
            color = DaftarTheme.colors.muted,
        )
    }
}

@Composable
private fun PasswordBody(state: RestoreUiState, onIntent: (RestoreIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            text = stringResource(Res.string.restore_password_heading),
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.ink,
        )
        DaftarTextField(
            value = state.password,
            onValueChange = { onIntent(RestoreIntent.PasswordChanged(it)) },
            label = stringResource(
                if (state.usingRecoveryKey) Res.string.restore_recovery_label else Res.string.restore_password_label,
            ),
            kind = if (state.usingRecoveryKey) DaftarFieldKind.Code else DaftarFieldKind.Password,
            placeholder = if (state.usingRecoveryKey) "XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX" else null,
        )
        if (state.usingRecoveryKey) {
            Text(
                text = stringResource(Res.string.restore_recovery_note),
                style = DaftarTheme.typography.fineText,
                color = DaftarTheme.colors.muted,
            )
        }
        PrimaryButton(
            onClick = { onIntent(RestoreIntent.SubmitPassword) },
            enabled = state.canSubmitPassword,
            fullWidth = true,
        ) {
            Text(stringResource(Res.string.restore_password_action))
        }
        DaftarTextButton(onClick = { onIntent(RestoreIntent.ToggleRecoveryKey) }) {
            Text(
                stringResource(
                    if (state.usingRecoveryKey) Res.string.restore_use_password else Res.string.restore_use_recovery,
                ),
            )
        }
    }
}

@Composable
private fun ReviewBody(state: RestoreUiState, onIntent: (RestoreIntent) -> Unit) {
    val summary = state.summary
    val colors = DaftarTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            text = stringResource(Res.string.restore_review_heading),
            style = DaftarTheme.typography.sectionHeading,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.restore_review_validated),
            style = DaftarTheme.typography.supportingBody,
            color = colors.muted,
        )
        if (summary != null) {
            Text(
                text = stringResource(Res.string.restore_review_date, summary.exportedAt.localDateText()),
                style = DaftarTheme.typography.fineText,
                color = colors.muted,
            )
            CountLine(pluralStringResource(Res.plurals.restore_gold_count, summary.goldHoldings, summary.goldHoldings))
            CountLine(pluralStringResource(Res.plurals.restore_bank_count, summary.banks, summary.banks))
            CountLine(
                pluralStringResource(Res.plurals.restore_account_count, summary.bankAccounts, summary.bankAccounts),
            )
            CountLine(pluralStringResource(Res.plurals.restore_cash_count, summary.cashBalances, summary.cashBalances))
            CountLine(
                pluralStringResource(Res.plurals.restore_zakat_count, summary.zakatPayments, summary.zakatPayments),
            )
            CountLine(
                pluralStringResource(Res.plurals.restore_note_count, summary.personalNotes, summary.personalNotes),
            )
        }
        Text(
            text = stringResource(Res.string.restore_review_no_merge),
            style = DaftarTheme.typography.fineText,
            color = colors.muted,
        )
        DaftarNotePanel(tone = DaftarNoteTone.Warning) {
            Text(stringResource(Res.string.restore_review_warning), style = DaftarTheme.typography.noteBody)
        }
        PrimaryButton(onClick = { onIntent(RestoreIntent.ContinueToPin) }, fullWidth = true) {
            Text(stringResource(Res.string.restore_review_continue))
        }
        DaftarTextButton(onClick = { onIntent(RestoreIntent.Close) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.restore_cancel))
        }
    }
}

@Composable
private fun CountLine(text: String) {
    Text(
        text = text,
        style = DaftarTheme.typography.supportingBody,
        color = DaftarTheme.colors.ink,
    )
}

@Composable
private fun PinBody(state: RestoreUiState, confirming: Boolean, onIntent: (RestoreIntent) -> Unit) {
    val colors = DaftarTheme.colors
    val label =
        stringResource(if (confirming) Res.string.restore_pin_confirm_label else Res.string.restore_pin_new_label)
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            text = stringResource(Res.string.restore_pin_heading),
            style = DaftarTheme.typography.screenHeading,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            text = label,
            style = DaftarTheme.typography.fieldLabel,
            color = colors.muted,
            modifier = Modifier.fillMaxWidth(),
        )
        DaftarPinField(
            enteredCount = state.pinCount,
            stateDescription = label,
            placeholder = "••••••",
            invalid = state.pinMismatch,
        )
        if (state.pinMismatch) {
            DaftarNotePanel(tone = DaftarNoteTone.Error) {
                Text(stringResource(Res.string.restore_pin_mismatch), style = DaftarTheme.typography.noteBody)
            }
        }
        DaftarKeypad(
            onDigit = { digit -> onIntent(RestoreIntent.Digit('0'.code + digit)) },
            onErase = { onIntent(RestoreIntent.Erase) },
            eraseLabel = stringResource(Res.string.restore_erase_digit),
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            onClick = { onIntent(RestoreIntent.SubmitPin) },
            enabled = state.canSubmitPin,
            fullWidth = true,
        ) {
            Text(
                stringResource(
                    if (confirming) Res.string.restore_pin_confirm else Res.string.restore_pin_continue,
                ),
            )
        }
        Text(
            text = stringResource(Res.string.restore_pin_note),
            style = DaftarTheme.typography.fineText,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
private fun FailedBody(problem: RestoreProblem, onIntent: (RestoreIntent) -> Unit) {
    val reason = when (problem) {
        RestoreProblem.Unreadable -> stringResource(Res.string.restore_failed_unreadable)
        RestoreProblem.NewerFormat -> stringResource(Res.string.restore_failed_newer)
        RestoreProblem.FileUnavailable -> stringResource(Res.string.restore_failed_file)
        RestoreProblem.NotReplaced -> stringResource(Res.string.restore_failed_not_replaced)
        RestoreProblem.RecoveryRequired -> stringResource(Res.string.restore_failed_recovery)
    }
    val retryable = problem != RestoreProblem.RecoveryRequired
    DaftarResultState(
        title = stringResource(Res.string.restore_failed_heading),
        message = reason,
        primaryLabel = if (retryable) {
            stringResource(Res.string.restore_failed_choose_other)
        } else {
            stringResource(Res.string.restore_close)
        },
        onPrimary = {
            if (retryable) onIntent(RestoreIntent.Acknowledge) else onIntent(RestoreIntent.Close)
        },
        tone = DaftarResultTone.Error,
        secondaryLabel = if (retryable) stringResource(Res.string.restore_close) else null,
        onSecondary = if (retryable) ({ onIntent(RestoreIntent.Close) }) else null,
    )
}

/** The export date in the device's time zone, as ISO `yyyy-MM-dd`; the stored instant is UTC. */
private fun Instant.localDateText(): String = toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

private val CONTENT_MAX_WIDTH = 560.dp
