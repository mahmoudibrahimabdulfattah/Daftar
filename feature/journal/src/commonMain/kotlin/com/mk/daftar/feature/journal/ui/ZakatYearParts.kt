@file:OptIn(ExperimentalMaterial3Api::class)

package com.mk.daftar.feature.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarStatusTag
import com.mk.daftar.core.designsystem.component.DaftarStatusTone
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.actionCursor
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.zakat.ZAKAT_TIME_ZONE
import com.mk.daftar.core.domain.zakat.ZakatYearStatus
import com.mk.daftar.feature.journal.resources.Res
import com.mk.daftar.feature.journal.resources.journal_cancel
import com.mk.daftar.feature.journal.resources.zakat_for_year
import com.mk.daftar.feature.journal.resources.zakat_status_due
import com.mk.daftar.feature.journal.resources.zakat_status_paid
import com.mk.daftar.feature.journal.resources.zakat_status_upcoming
import com.mk.daftar.feature.journal.resources.zakat_unassigned
import com.mk.daftar.feature.journal.resources.zakat_year_label
import com.mk.daftar.feature.journal.zakat.ZakatYearSummary
import com.mk.daftar.feature.journal.zakat.unrecordedSummary
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** The Hijri year and its Gregorian year, isolated as one label: `1447 هـ · 2026`. */
@Composable
internal fun zakatYearLabel(hijriYear: Int, gregorianYear: Int): String =
    stringResource(Res.string.zakat_year_label, hijriYear, gregorianYear)

/** The status words, in the copy contract's wording. */
@Composable
internal fun zakatStatusLabel(status: ZakatYearStatus): String = stringResource(
    when (status) {
        ZakatYearStatus.DUE -> Res.string.zakat_status_due
        ZakatYearStatus.PAID -> Res.string.zakat_status_paid
        ZakatYearStatus.UPCOMING -> Res.string.zakat_status_upcoming
    },
)

/** A status tag: a warning while the year awaits confirmation, otherwise the neutral positive tone. */
@Composable
internal fun ZakatStatusTag(status: ZakatYearStatus) {
    DaftarStatusTag(
        label = zakatStatusLabel(status),
        tone = if (status == ZakatYearStatus.DUE) DaftarStatusTone.Warning else DaftarStatusTone.Positive,
        icon = if (status == ZakatYearStatus.PAID) DaftarIcons.Check else null,
    )
}

/**
 * The declaration checkbox: a full row with its label and helper, one touch target of at least 48dp. The label names the
 * full year for screen readers, and the state is announced as a checkbox.
 */
@Composable
internal fun ZakatDeclarationCheckbox(
    label: String,
    helper: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .actionCursor(enabled).toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .semantics { contentDescription = description }
            .padding(vertical = DaftarSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xxs)) {
            Text(text = label, style = DaftarTheme.typography.settingTitle, color = colors.ink)
            Text(text = helper, style = DaftarTheme.typography.fineText, color = colors.muted)
        }
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.primary,
                uncheckedColor = colors.muted,
                checkmarkColor = colors.onPrimary,
            ),
        )
    }
}

/**
 * A year picker for a payment. Unassigned is first; the declared years follow, newest first. The chosen one is marked
 * selected, and choosing one closes the picker at once.
 */
@Composable
internal fun ZakatYearPickerDialog(
    choices: List<ZakatYearSummary>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DaftarTheme.colors
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = DaftarTheme.shapes.surface, color = colors.surface) {
            Column(
                modifier = Modifier.padding(DaftarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
            ) {
                Text(
                    text = stringResource(Res.string.zakat_for_year),
                    style = DaftarTheme.typography.sectionHeading,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                DaftarLedgerRow(
                    title = stringResource(Res.string.zakat_unassigned),
                    selected = selected == null,
                    onClick = { onSelect(null) },
                )
                choices.forEach { year ->
                    DaftarLedgerRow(
                        title = zakatYearLabel(year.hijriYear, year.gregorianYear),
                        selected = selected == year.hijriYear,
                        onClick = { onSelect(year.hijriYear) },
                    )
                }
                DaftarTextButton(onClick = onDismiss) { Text(stringResource(Res.string.journal_cancel)) }
            }
        }
    }
}

/** The summary of a year a payment or screen refers to, using the declarations when it is one of them. */
internal fun yearSummaryOf(hijriYear: Int, declared: List<ZakatYearSummary>): ZakatYearSummary =
    declared.firstOrNull { it.hijriYear == hijriYear } ?: unrecordedSummary(hijriYear)

/** A quote or Ramadan time in Cairo, as `day month year, HH:mm` with the month name in the language. */
internal fun cairoStampParts(instant: Instant): CairoStamp {
    val local = instant.toLocalDateTime(ZAKAT_TIME_ZONE)
    val minute = local.minute.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    return CairoStamp(local.date, "$hour:$minute")
}

internal data class CairoStamp(val date: LocalDate, val time: String)
