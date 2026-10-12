@file:OptIn(ExperimentalMaterial3Api::class)

package com.mk.daftar.feature.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
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
import com.mk.daftar.feature.journal.JournalAction
import com.mk.daftar.feature.journal.JournalFailure
import com.mk.daftar.feature.journal.resources.Res
import com.mk.daftar.feature.journal.resources.journal_back_to_editing
import com.mk.daftar.feature.journal.resources.journal_cancel
import com.mk.daftar.feature.journal.resources.journal_delete_failed_message
import com.mk.daftar.feature.journal.resources.journal_input_kept
import com.mk.daftar.feature.journal.resources.journal_retry
import com.mk.daftar.feature.journal.resources.journal_reveal_action
import com.mk.daftar.feature.journal.resources.journal_reveal_message
import com.mk.daftar.feature.journal.resources.journal_save_failed_message
import com.mk.daftar.feature.journal.resources.journal_save_failed_title
import com.mk.daftar.feature.journal.resources.journal_save_storage_message
import com.mk.daftar.feature.journal.resources.journal_save_uncertain_message
import com.mk.daftar.feature.journal.resources.journal_select_date
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * A date for display: day and year are isolated as numbers, and the month name follows the language. The text is
 * built from the localized month names, so it never reads a numeric pattern in the wrong order.
 */
internal fun journalDateText(date: LocalDate, months: List<String>): String =
    "⁦${date.day}⁩ ${months[date.month.ordinal]} ⁦${date.year}⁩"

internal fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

internal fun Long.toUtcLocalDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

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
                DaftarTextButton(onClick = onReveal) { Text(stringResource(Res.string.journal_reveal_action)) }
                DaftarTextButton(onClick = onCancel) { Text(stringResource(Res.string.journal_cancel)) }
            }
        },
    ) {
        Text(stringResource(Res.string.journal_reveal_message))
    }
}

/**
 * A failed write. The draft stays on screen beneath it. A save offers retry and a return to editing; an uncertain
 * outcome offers no retry, because the record may already be written.
 */
@Composable
internal fun WriteFailureNote(
    failure: JournalFailure,
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
                    DaftarTextButton(onClick = onRetry) { Text(stringResource(Res.string.journal_retry)) }
                }
                DaftarTextButton(onClick = onBackToEditing) {
                    Text(stringResource(Res.string.journal_back_to_editing))
                }
            }
        },
    ) {
        when (failure.action) {
            JournalAction.Save -> {
                Text(stringResource(Res.string.journal_save_failed_title))
                Text(stringResource(saveFailureMessage(failure.error)))
                if (!failure.uncertain) Text(stringResource(Res.string.journal_input_kept))
            }

            JournalAction.Delete ->
                Text(stringResource(Res.string.journal_delete_failed_message))
        }
    }
}

private fun saveFailureMessage(error: StorageError): org.jetbrains.compose.resources.StringResource = when (error) {
    StorageError.DiskFull -> Res.string.journal_save_storage_message
    StorageError.SessionEnded -> Res.string.journal_save_uncertain_message
    else -> Res.string.journal_save_failed_message
}

/** A confirmation sheet for delete or discard. Its callbacks are the caller's to guard while a write runs. */
@Composable
internal fun JournalConfirmDialog(
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

/** The platform date picker, limited to days up to and including [latest]. The result is a calendar date only. */
@Composable
internal fun JournalDatePicker(
    initial: LocalDate,
    latest: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val latestMillis = latest.toUtcMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= latestMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DaftarTextButton(onClick = {
                state.selectedDateMillis?.let { onPick(it.toUtcLocalDate()) }
            }) {
                Text(stringResource(Res.string.journal_select_date))
            }
        },
        dismissButton = {
            DaftarTextButton(onClick = onDismiss) { Text(stringResource(Res.string.journal_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}
