@file:OptIn(ExperimentalMaterial3Api::class)

package com.mk.daftar.feature.banks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.component.DaftarConfirmationContent
import com.mk.daftar.core.designsystem.component.DaftarIconButton
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_toggle_values
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.navigation.BankDetailRoute
import com.mk.daftar.core.navigation.BanksRoute
import com.mk.daftar.core.navigation.Navigator
import com.mk.daftar.feature.banks.BankAction
import com.mk.daftar.feature.banks.BankEffect
import com.mk.daftar.feature.banks.BankFailure
import com.mk.daftar.feature.banks.BankOption
import com.mk.daftar.feature.banks.resources.Res
import com.mk.daftar.feature.banks.resources.account_bank_none
import com.mk.daftar.feature.banks.resources.account_bank_picker_title
import com.mk.daftar.feature.banks.resources.banks_cancel
import com.mk.daftar.feature.banks.resources.banks_reveal_action
import com.mk.daftar.feature.banks.resources.banks_reveal_message
import com.mk.daftar.feature.banks.resources.failed_back_to_editing
import com.mk.daftar.feature.banks.resources.failed_delete_message
import com.mk.daftar.feature.banks.resources.failed_input_kept
import com.mk.daftar.feature.banks.resources.failed_retry
import com.mk.daftar.feature.banks.resources.failed_save_message
import com.mk.daftar.feature.banks.resources.failed_save_title
import com.mk.daftar.feature.banks.resources.failed_storage_message
import com.mk.daftar.feature.banks.resources.failed_uncertain_message
import org.jetbrains.compose.resources.stringResource

/** Opens the visibility control in a top bar. Its label names the action; the state reads from the icon. */
@Composable
internal fun VisibilityToggle(hidden: Boolean, onToggle: () -> Unit) {
    DaftarIconButton(
        onClick = onToggle,
        icon = if (hidden) DaftarIcons.EyeOff else DaftarIcons.Eye,
        contentDescription = stringResource(DesignRes.string.btn_toggle_values),
    )
}

/**
 * Sensitive editors and detail views open only after an explicit reveal. The panel names what it would show, and nothing
 * about the record is displayed while it waits.
 */
@Composable
internal fun RevealPrompt(onReveal: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    DaftarNotePanel(
        modifier = modifier,
        tone = DaftarNoteTone.Context,
        icon = DaftarIcons.Eye,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
                DaftarTextButton(onClick = onReveal) { Text(stringResource(Res.string.banks_reveal_action)) }
                DaftarTextButton(onClick = onCancel) { Text(stringResource(Res.string.banks_cancel)) }
            }
        },
    ) {
        Text(stringResource(Res.string.banks_reveal_message))
    }
}

/**
 * A failed write. The draft stays on screen beneath it. A save offers retry and a return to editing; an uncertain
 * outcome offers no retry, because the record may already be written.
 */
@Composable
internal fun WriteFailureNote(
    failure: BankFailure,
    onRetry: () -> Unit,
    onBackToEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaftarNotePanel(
        modifier = modifier.padding(top = 4.dp),
        tone = DaftarNoteTone.Error,
        icon = DaftarIcons.Alert,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
                if (!failure.uncertain) {
                    DaftarTextButton(onClick = onRetry) { Text(stringResource(Res.string.failed_retry)) }
                }
                DaftarTextButton(onClick = onBackToEditing) { Text(stringResource(Res.string.failed_back_to_editing)) }
            }
        },
    ) {
        when (failure.action) {
            BankAction.Save -> {
                Text(stringResource(Res.string.failed_save_title))
                Text(stringResource(saveFailureMessage(failure.error)))
                if (!failure.uncertain) Text(stringResource(Res.string.failed_input_kept))
            }

            BankAction.Delete -> Text(stringResource(Res.string.failed_delete_message))
        }
    }
}

private fun saveFailureMessage(error: StorageError): org.jetbrains.compose.resources.StringResource = when (error) {
    StorageError.DiskFull -> Res.string.failed_storage_message
    StorageError.SessionEnded -> Res.string.failed_uncertain_message
    else -> Res.string.failed_save_message
}

/** A confirmation sheet for delete or discard. Its callbacks are the caller's to guard while a write runs. */
@Composable
internal fun BankConfirmDialog(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    destructiveLabel: String? = null,
    onDestructive: (() -> Unit)? = null,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = DaftarTheme.shapes.surface, color = DaftarTheme.colors.surface) {
            DaftarConfirmationContent(
                title = title,
                message = message,
                primaryLabel = primaryLabel,
                onPrimaryClick = onPrimary,
                modifier = Modifier.padding(DaftarSpacing.md),
                destructiveLabel = destructiveLabel,
                onDestructiveClick = onDestructive,
            )
        }
    }
}

/** The choice of a bank for an account. It lists only banks that exist, so an account is never filed under a ghost. */
@Composable
internal fun BankPickerDialog(
    options: List<BankOption>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = DaftarTheme.shapes.surface, color = DaftarTheme.colors.surface) {
            Column(
                modifier = Modifier.padding(DaftarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
            ) {
                Text(
                    text = stringResource(Res.string.account_bank_picker_title),
                    style = DaftarTheme.typography.sectionHeading,
                    color = DaftarTheme.colors.ink,
                )
                if (options.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.account_bank_none),
                        style = DaftarTheme.typography.supportingBody,
                        color = DaftarTheme.colors.muted,
                    )
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xxs),
                    ) {
                        options.forEach { bank ->
                            DaftarLedgerRow(
                                title = bank.name,
                                icon = DaftarIcons.Bank,
                                selected = bank.id == selectedId,
                                onClick = { onPick(bank.id) },
                            )
                        }
                    }
                }
                DaftarTextButton(onClick = onDismiss, fullWidth = true) {
                    Text(stringResource(Res.string.banks_cancel))
                }
            }
        }
    }
}

/**
 * Applies one bank effect to the shell's navigator. A closed editor leaves the stack; a deleted bank clears every bank
 * screen beneath it; an account's destination bank is shown in place of the one the editor came from.
 */
internal fun handleBankEffect(navigator: Navigator, effect: BankEffect) {
    when (effect) {
        is BankEffect.Navigate -> if (effect.route is BankDetailRoute) {
            navigator.showDetail(effect.route)
        } else {
            navigator.goTo(effect.route)
        }

        BankEffect.Close -> navigator.goBack()

        BankEffect.CloseToList -> navigator.returnTo { it == BanksRoute }

        is BankEffect.CloseToBank -> {
            navigator.goBack()
            navigator.showDetail(BankDetailRoute(effect.bankId))
        }
    }
}
