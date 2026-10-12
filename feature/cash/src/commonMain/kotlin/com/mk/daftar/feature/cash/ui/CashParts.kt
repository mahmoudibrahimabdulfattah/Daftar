@file:OptIn(ExperimentalMaterial3Api::class)

package com.mk.daftar.feature.cash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.component.DaftarConfirmationContent
import com.mk.daftar.core.designsystem.component.DaftarIconButton
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_toggle_values
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.feature.cash.CashAction
import com.mk.daftar.feature.cash.CashFailure
import com.mk.daftar.feature.cash.resources.Res
import com.mk.daftar.feature.cash.resources.cash_back_to_editing
import com.mk.daftar.feature.cash.resources.cash_cancel
import com.mk.daftar.feature.cash.resources.cash_delete_failed_message
import com.mk.daftar.feature.cash.resources.cash_input_kept
import com.mk.daftar.feature.cash.resources.cash_retry
import com.mk.daftar.feature.cash.resources.cash_reveal_action
import com.mk.daftar.feature.cash.resources.cash_reveal_message
import com.mk.daftar.feature.cash.resources.cash_save_failed_message
import com.mk.daftar.feature.cash.resources.cash_save_failed_title
import com.mk.daftar.feature.cash.resources.cash_save_storage_message
import com.mk.daftar.feature.cash.resources.cash_save_uncertain_message
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The visibility control in a top bar. Its label names the action, and the state reads from the icon. */
@Composable
internal fun VisibilityToggle(hidden: Boolean, onToggle: () -> Unit) {
    DaftarIconButton(
        onClick = onToggle,
        icon = if (hidden) DaftarIcons.EyeOff else DaftarIcons.Eye,
        contentDescription = stringResource(DesignRes.string.btn_toggle_values),
    )
}

/**
 * Sensitive editors and lists open only after an explicit reveal. The panel names what it would show, and nothing about
 * the record is displayed while it waits.
 */
@Composable
internal fun RevealPrompt(onReveal: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    DaftarNotePanel(
        modifier = modifier,
        tone = DaftarNoteTone.Context,
        icon = DaftarIcons.Eye,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
                DaftarTextButton(onClick = onReveal) { Text(stringResource(Res.string.cash_reveal_action)) }
                DaftarTextButton(onClick = onCancel) { Text(stringResource(Res.string.cash_cancel)) }
            }
        },
    ) {
        Text(stringResource(Res.string.cash_reveal_message))
    }
}

/**
 * A failed write. The draft stays on screen beneath it. A save offers retry and a return to editing; an uncertain
 * outcome offers no retry, because the record may already be written.
 */
@Composable
internal fun WriteFailureNote(
    failure: CashFailure,
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
                    DaftarTextButton(onClick = onRetry) { Text(stringResource(Res.string.cash_retry)) }
                }
                DaftarTextButton(onClick = onBackToEditing) {
                    Text(stringResource(Res.string.cash_back_to_editing))
                }
            }
        },
    ) {
        when (failure.action) {
            CashAction.Save -> {
                Text(stringResource(Res.string.cash_save_failed_title))
                Text(stringResource(saveFailureMessage(failure.error)))
                if (!failure.uncertain) Text(stringResource(Res.string.cash_input_kept))
            }

            CashAction.Delete -> Text(stringResource(Res.string.cash_delete_failed_message))
        }
    }
}

private fun saveFailureMessage(error: StorageError): StringResource = when (error) {
    StorageError.DiskFull -> Res.string.cash_save_storage_message
    StorageError.SessionEnded -> Res.string.cash_save_uncertain_message
    else -> Res.string.cash_save_failed_message
}

/** A confirmation for delete or discard. Its callbacks are the caller's to guard while a write runs. */
@Composable
internal fun CashConfirmDialog(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    destructiveLabel: String,
    onDestructive: () -> Unit,
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
