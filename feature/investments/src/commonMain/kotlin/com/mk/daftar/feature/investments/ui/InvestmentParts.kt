@file:OptIn(ExperimentalMaterial3Api::class)

package com.mk.daftar.feature.investments.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.component.DaftarBankTile
import com.mk.daftar.core.designsystem.component.DaftarConfirmationContent
import com.mk.daftar.core.designsystem.component.DaftarIcon
import com.mk.daftar.core.designsystem.component.DaftarIconButton
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTileIcon
import com.mk.daftar.core.designsystem.component.DaftarTileTone
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_toggle_values
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.feature.investments.InvestmentFailure
import com.mk.daftar.feature.investments.ValueDateHint
import com.mk.daftar.feature.investments.brokerMarkFor
import com.mk.daftar.feature.investments.cairoInstantOf
import com.mk.daftar.feature.investments.cairoLocalOf
import com.mk.daftar.feature.investments.resources.Res
import com.mk.daftar.feature.investments.resources.investments_back_to_editing
import com.mk.daftar.feature.investments.resources.investments_cancel
import com.mk.daftar.feature.investments.resources.investments_check_date
import com.mk.daftar.feature.investments.resources.investments_date_pick
import com.mk.daftar.feature.investments.resources.investments_datetime
import com.mk.daftar.feature.investments.resources.investments_month_1
import com.mk.daftar.feature.investments.resources.investments_month_10
import com.mk.daftar.feature.investments.resources.investments_month_11
import com.mk.daftar.feature.investments.resources.investments_month_12
import com.mk.daftar.feature.investments.resources.investments_month_2
import com.mk.daftar.feature.investments.resources.investments_month_3
import com.mk.daftar.feature.investments.resources.investments_month_4
import com.mk.daftar.feature.investments.resources.investments_month_5
import com.mk.daftar.feature.investments.resources.investments_month_6
import com.mk.daftar.feature.investments.resources.investments_month_7
import com.mk.daftar.feature.investments.resources.investments_month_8
import com.mk.daftar.feature.investments.resources.investments_month_9
import com.mk.daftar.feature.investments.resources.investments_retry
import com.mk.daftar.feature.investments.resources.investments_reveal_action
import com.mk.daftar.feature.investments.resources.investments_reveal_message
import com.mk.daftar.feature.investments.resources.investments_save_failed
import com.mk.daftar.feature.investments.resources.investments_time_pick
import com.mk.daftar.feature.investments.resources.investments_update_hint
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
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

/** Private editors and lists open only after an explicit reveal. The panel names what it would show, and nothing else. */
@Composable
internal fun RevealPrompt(onReveal: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    DaftarNotePanel(
        modifier = modifier,
        tone = DaftarNoteTone.Context,
        icon = DaftarIcons.Eye,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
                DaftarTextButton(onClick = onReveal) { Text(stringResource(Res.string.investments_reveal_action)) }
                DaftarTextButton(onClick = onCancel) { Text(stringResource(Res.string.investments_cancel)) }
            }
        },
    ) {
        Text(stringResource(Res.string.investments_reveal_message))
    }
}

/**
 * A failed write. The draft stays on screen beneath it. Save offers retry and a return to editing; an uncertain outcome
 * offers no retry, because the record may already be written.
 */
@Composable
internal fun WriteFailureNote(
    failure: InvestmentFailure,
    onRetry: () -> Unit,
    onBackToEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaftarNotePanel(
        modifier = modifier,
        tone = DaftarNoteTone.Error,
        icon = DaftarIcons.Alert,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
                if (!failure.uncertain) {
                    DaftarTextButton(onClick = onRetry) { Text(stringResource(Res.string.investments_retry)) }
                }
                DaftarTextButton(onClick = onBackToEditing) {
                    Text(stringResource(Res.string.investments_back_to_editing))
                }
            }
        },
    ) {
        // A failed delete and a failed save share the one approved copy: the saved data is unchanged.
        Text(stringResource(Res.string.investments_save_failed))
    }
}

/** A confirmation for delete or discard. Its callbacks are the caller's to guard while a write runs. */
@Composable
internal fun InvestmentConfirmDialog(
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

/**
 * The identity tile of a broker row. A catalog broker shows its own mark; Other and unknown brokers show the Investments
 * glyph on the default tone. Decorative: the row names the broker once.
 */
@Composable
internal fun BrokerTile(providerId: String?, modifier: Modifier = Modifier) {
    val mark = brokerMarkFor(providerId)
    if (mark != null) {
        DaftarBankTile(mark = mark, modifier = modifier)
    } else {
        DaftarTileIcon(
            icon = DaftarIcons.Investments,
            contentDescription = null,
            modifier = modifier,
            tone = DaftarTileTone.Default,
        )
    }
}

/** The muted clock line under a value's date. It is a reminder about the user's own balance, never a warning. */
@Composable
internal fun ValueDateHintLine(hint: ValueDateHint, modifier: Modifier = Modifier) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    when (hint) {
        ValueDateHint.None -> Unit

        ValueDateHint.UpdateDue -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
        ) {
            DaftarIcon(icon = DaftarIcons.Clock, contentDescription = null, tint = colors.muted)
            Text(
                text = stringResource(Res.string.investments_update_hint),
                style = typography.fineText,
                color = colors.muted,
            )
        }

        ValueDateHint.CheckDate -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
        ) {
            DaftarIcon(icon = DaftarIcons.Clock, contentDescription = null, tint = colors.muted)
            Text(
                text = stringResource(Res.string.investments_check_date),
                style = typography.fineText,
                color = colors.muted,
            )
        }
    }
}

/** The month names in the app's language, for the Cairo date text. */
@Composable
internal fun monthNames(): List<String> = listOf(
    stringResource(Res.string.investments_month_1),
    stringResource(Res.string.investments_month_2),
    stringResource(Res.string.investments_month_3),
    stringResource(Res.string.investments_month_4),
    stringResource(Res.string.investments_month_5),
    stringResource(Res.string.investments_month_6),
    stringResource(Res.string.investments_month_7),
    stringResource(Res.string.investments_month_8),
    stringResource(Res.string.investments_month_9),
    stringResource(Res.string.investments_month_10),
    stringResource(Res.string.investments_month_11),
    stringResource(Res.string.investments_month_12),
)

/**
 * A value date in Cairo time. Each number is isolated left to right on its own, so the day, year and time keep their
 * place in the Arabic sentence instead of the whole date being reordered as one run.
 */
@Composable
internal fun cairoDateText(instant: Instant): String {
    val months = monthNames()
    val local = cairoLocalOf(instant)
    val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    return stringResource(
        Res.string.investments_datetime,
        "⁦${local.day}⁩",
        months[local.month.ordinal],
        "⁦${local.year}⁩",
        "⁦$time⁩",
    )
}

/**
 * The Cairo date and time picker: a calendar day up to today in Cairo, then a time. The result is a real Cairo instant,
 * or null when the chosen local time does not exist there or is ambiguous. [onPick] never receives a future time from
 * here; the ViewModel also rejects one.
 */
@Composable
internal fun CairoDateTimePicker(initial: Instant, now: Instant, onPick: (Instant?) -> Unit, onDismiss: () -> Unit) {
    val initialLocal = cairoLocalOf(initial)
    val today = cairoLocalOf(now).date
    val todayMillis = today.toUtcMillis()
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initialLocal.date.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
        },
    )
    var chosenDate by remember { mutableStateOf<LocalDate?>(null) }
    val pickedDate = chosenDate
    if (pickedDate == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                DaftarTextButton(onClick = {
                    dateState.selectedDateMillis?.let { chosenDate = it.toUtcLocalDate() }
                }) {
                    Text(stringResource(Res.string.investments_date_pick))
                }
            },
            dismissButton = {
                DaftarTextButton(onClick = onDismiss) { Text(stringResource(Res.string.investments_cancel)) }
            },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = initialLocal.hour,
            initialMinute = initialLocal.minute,
            is24Hour = true,
        )
        BasicAlertDialog(onDismissRequest = onDismiss) {
            Surface(shape = DaftarTheme.shapes.surface, color = DaftarTheme.colors.surface) {
                Column(modifier = Modifier.padding(DaftarSpacing.md)) {
                    TimePicker(state = timeState)
                    Row(horizontalArrangement = Arrangement.End) {
                        DaftarTextButton(onClick = onDismiss) { Text(stringResource(Res.string.investments_cancel)) }
                        DaftarTextButton(onClick = {
                            val local = LocalDateTime(pickedDate, LocalTime(timeState.hour, timeState.minute))
                            onPick(cairoInstantOf(local))
                        }) {
                            Text(stringResource(Res.string.investments_time_pick))
                        }
                    }
                }
            }
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toUtcLocalDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
