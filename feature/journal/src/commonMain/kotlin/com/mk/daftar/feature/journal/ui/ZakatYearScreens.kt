package com.mk.daftar.feature.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarAmount
import com.mk.daftar.core.designsystem.component.DaftarEmptyState
import com.mk.daftar.core.designsystem.component.DaftarFieldKind
import com.mk.daftar.core.designsystem.component.DaftarFooterAction
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarSkeletonSize
import com.mk.daftar.core.designsystem.component.DaftarStatusTag
import com.mk.daftar.core.designsystem.component.DaftarStatusTone
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTextField
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.balance_hidden
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.resources.btn_saving
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.zakat.ZakatYearStatus
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.DesktopWidths
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.desktopPage
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.resources.Res
import com.mk.daftar.feature.journal.resources.journal_load_failed_message
import com.mk.daftar.feature.journal.resources.journal_load_failed_title
import com.mk.daftar.feature.journal.resources.journal_missing_message
import com.mk.daftar.feature.journal.resources.journal_missing_title
import com.mk.daftar.feature.journal.resources.journal_months
import com.mk.daftar.feature.journal.resources.journal_retry
import com.mk.daftar.feature.journal.resources.journal_unit_short
import com.mk.daftar.feature.journal.resources.journal_zakat
import com.mk.daftar.feature.journal.resources.journal_zakat_calculator
import com.mk.daftar.feature.journal.resources.journal_zakat_calculator_help
import com.mk.daftar.feature.journal.resources.zakat_add_past_year
import com.mk.daftar.feature.journal.resources.zakat_add_year_action
import com.mk.daftar.feature.journal.resources.zakat_adjust_ramadan
import com.mk.daftar.feature.journal.resources.zakat_adjusted_date
import com.mk.daftar.feature.journal.resources.zakat_check_a11y
import com.mk.daftar.feature.journal.resources.zakat_check_helper
import com.mk.daftar.feature.journal.resources.zakat_check_label
import com.mk.daftar.feature.journal.resources.zakat_completion_help
import com.mk.daftar.feature.journal.resources.zakat_date_calculated
import com.mk.daftar.feature.journal.resources.zakat_date_earlier
import com.mk.daftar.feature.journal.resources.zakat_date_field
import com.mk.daftar.feature.journal.resources.zakat_date_heading
import com.mk.daftar.feature.journal.resources.zakat_date_help
import com.mk.daftar.feature.journal.resources.zakat_date_later
import com.mk.daftar.feature.journal.resources.zakat_date_note
import com.mk.daftar.feature.journal.resources.zakat_date_restore_help
import com.mk.daftar.feature.journal.resources.zakat_date_title
import com.mk.daftar.feature.journal.resources.zakat_empty_log_action
import com.mk.daftar.feature.journal.resources.zakat_empty_log_help
import com.mk.daftar.feature.journal.resources.zakat_empty_message
import com.mk.daftar.feature.journal.resources.zakat_empty_title
import com.mk.daftar.feature.journal.resources.zakat_error_year_duplicate
import com.mk.daftar.feature.journal.resources.zakat_error_year_range
import com.mk.daftar.feature.journal.resources.zakat_first_use
import com.mk.daftar.feature.journal.resources.zakat_hijri_year
import com.mk.daftar.feature.journal.resources.zakat_history_heading
import com.mk.daftar.feature.journal.resources.zakat_history_help
import com.mk.daftar.feature.journal.resources.zakat_history_title
import com.mk.daftar.feature.journal.resources.zakat_keep_mark
import com.mk.daftar.feature.journal.resources.zakat_payments_for_year
import com.mk.daftar.feature.journal.resources.zakat_ramadan_falls
import com.mk.daftar.feature.journal.resources.zakat_ramadan_on
import com.mk.daftar.feature.journal.resources.zakat_record_payment
import com.mk.daftar.feature.journal.resources.zakat_recorded_payments
import com.mk.daftar.feature.journal.resources.zakat_reminder
import com.mk.daftar.feature.journal.resources.zakat_remove_mark
import com.mk.daftar.feature.journal.resources.zakat_reopen_message
import com.mk.daftar.feature.journal.resources.zakat_reopen_title
import com.mk.daftar.feature.journal.resources.zakat_reopened_message
import com.mk.daftar.feature.journal.resources.zakat_restore_date
import com.mk.daftar.feature.journal.resources.zakat_row_due
import com.mk.daftar.feature.journal.resources.zakat_row_paid
import com.mk.daftar.feature.journal.resources.zakat_row_upcoming
import com.mk.daftar.feature.journal.resources.zakat_save_date
import com.mk.daftar.feature.journal.resources.zakat_save_error
import com.mk.daftar.feature.journal.resources.zakat_saved_message
import com.mk.daftar.feature.journal.resources.zakat_saving
import com.mk.daftar.feature.journal.resources.zakat_unassigned
import com.mk.daftar.feature.journal.resources.zakat_year_add_help
import com.mk.daftar.feature.journal.resources.zakat_year_heading
import com.mk.daftar.feature.journal.resources.zakat_year_range
import com.mk.daftar.feature.journal.resources.zakat_your_date
import com.mk.daftar.feature.journal.zakat.ZakatDateIntent
import com.mk.daftar.feature.journal.zakat.ZakatDateOption
import com.mk.daftar.feature.journal.zakat.ZakatDateUiState
import com.mk.daftar.feature.journal.zakat.ZakatDateViewModel
import com.mk.daftar.feature.journal.zakat.ZakatHistoryIntent
import com.mk.daftar.feature.journal.zakat.ZakatHistoryUiState
import com.mk.daftar.feature.journal.zakat.ZakatHistoryViewModel
import com.mk.daftar.feature.journal.zakat.ZakatPaymentRow
import com.mk.daftar.feature.journal.zakat.ZakatYearAddIntent
import com.mk.daftar.feature.journal.zakat.ZakatYearAddUiState
import com.mk.daftar.feature.journal.zakat.ZakatYearAddViewModel
import com.mk.daftar.feature.journal.zakat.ZakatYearError
import com.mk.daftar.feature.journal.zakat.ZakatYearIntent
import com.mk.daftar.feature.journal.zakat.ZakatYearSummary
import com.mk.daftar.feature.journal.zakat.ZakatYearUiState
import com.mk.daftar.feature.journal.zakat.ZakatYearViewModel
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The declaration of one year: its status, the checkbox, its date, its payments and its links. */
@Composable
fun ZakatYearScreen(hijriYear: Int, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: ZakatYearViewModel = koinViewModel(
        key = "zakat-year-$hijriYear",
        parameters = { parametersOf(hijriYear) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect is JournalEffect.Navigate) navigator.goTo(effect.route)
    }
    ZakatYearContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The year as it is drawn. Amounts and payment notes are removed from rendering and semantics while values are hidden. */
@Composable
fun ZakatYearContent(
    state: ZakatYearUiState,
    onIntent: (ZakatYearIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hiddenText = stringResource(DesignRes.string.balance_hidden)
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(Res.string.journal_zakat),
                onBack = onBack,
                backLabel = stringResource(DesignRes.string.btn_back),
                actions = { VisibilityToggle(hidden = state.hidden) { onIntent(ZakatYearIntent.ToggleHidden) } },
            )
            Column(
                modifier = Modifier
                    .desktopPage(DesktopWidths.Page)
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            ) {
                when (state.phase) {
                    JournalPhase.Loading -> DaftarSkeleton(size = DaftarSkeletonSize.Big)

                    JournalPhase.Locked, JournalPhase.Empty -> Unit

                    JournalPhase.Missing -> DaftarResultState(
                        title = stringResource(Res.string.journal_missing_title),
                        message = stringResource(Res.string.journal_missing_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    is JournalPhase.Failed -> DaftarResultState(
                        title = stringResource(Res.string.journal_load_failed_title),
                        message = stringResource(Res.string.journal_load_failed_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    JournalPhase.Ready -> ZakatYearBody(state = state, onIntent = onIntent, hiddenText = hiddenText)
                }
            }
        }
    }
    if (state.confirmUnmark) {
        JournalConfirmDialog(
            title = stringResource(Res.string.zakat_reopen_title),
            message = stringResource(Res.string.zakat_reopen_message),
            primaryLabel = stringResource(Res.string.zakat_keep_mark),
            onPrimary = { onIntent(ZakatYearIntent.DismissUnmark) },
            onDismiss = { onIntent(ZakatYearIntent.DismissUnmark) },
            destructiveLabel = stringResource(Res.string.zakat_remove_mark),
            onDestructive = { onIntent(ZakatYearIntent.ConfirmUnmark) },
        )
    }
}

@Composable
private fun ZakatYearBody(state: ZakatYearUiState, onIntent: (ZakatYearIntent) -> Unit, hiddenText: String) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val months = stringArrayResource(Res.array.journal_months)
    val status = state.status ?: return
    val date = state.date ?: return
    val gregorian = state.gregorianYear ?: return
    val yearLabel = zakatYearLabel(state.hijriYear, gregorian)

    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Text(
            text = stringResource(Res.string.zakat_year_heading),
            style = typography.screenHeading,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = yearLabel, style = typography.rowSubtitle, color = colors.muted)
        ZakatStatusTag(status = status)
        if (state.adjusted) {
            DaftarStatusTag(label = stringResource(Res.string.zakat_adjusted_date), tone = DaftarStatusTone.Positive)
        }
        Text(
            text = stringResource(
                Res.string.zakat_your_date,
                stringResource(Res.string.zakat_ramadan_on, journalDateText(date, months)),
            ),
            style = typography.fineText,
            color = colors.muted,
        )
        ZakatDeclarationCheckbox(
            label = stringResource(Res.string.zakat_check_label),
            helper = stringResource(Res.string.zakat_check_helper),
            description = stringResource(Res.string.zakat_check_a11y, state.hijriYear, gregorian),
            checked = state.checked,
            enabled = !state.saving,
            onCheckedChange = { onIntent(ZakatYearIntent.SetChecked(it)) },
        )
        if (state.saving) {
            Text(text = stringResource(Res.string.zakat_saving), style = typography.fineText, color = colors.muted)
        }
        if (state.firstUse) {
            DaftarNotePanel(tone = DaftarNoteTone.Context) {
                Text(stringResource(Res.string.zakat_first_use, yearLabel))
            }
        }
        when {
            state.committed -> DaftarNotePanel(tone = DaftarNoteTone.Context) {
                Text(stringResource(Res.string.zakat_completion_help))
            }

            status == ZakatYearStatus.DUE -> DaftarNotePanel(tone = DaftarNoteTone.Warning, icon = DaftarIcons.Clock) {
                Text(stringResource(Res.string.zakat_reminder))
            }
        }
        state.justSaved?.let { paid ->
            DaftarNotePanel(tone = DaftarNoteTone.Context, icon = DaftarIcons.Check) {
                Text(stringResource(if (paid) Res.string.zakat_saved_message else Res.string.zakat_reopened_message))
            }
        }
        if (state.saveFailed) {
            DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
                Text(stringResource(Res.string.zakat_save_error))
            }
            PrimaryButton(onClick = { onIntent(ZakatYearIntent.RetrySave) }, fullWidth = true) {
                Text(stringResource(Res.string.journal_retry))
            }
        }

        DaftarLedgerRow(
            title = stringResource(Res.string.journal_zakat_calculator),
            subtitle = stringResource(Res.string.journal_zakat_calculator_help),
            icon = DaftarIcons.Heart,
            onClick = { onIntent(ZakatYearIntent.OpenCalculator) },
        )
        DaftarLedgerRow(
            title = stringResource(Res.string.zakat_history_title),
            subtitle = stringResource(Res.string.zakat_history_help),
            icon = DaftarIcons.Book,
            onClick = { onIntent(ZakatYearIntent.OpenHistory) },
        )

        Text(
            text = stringResource(Res.string.zakat_recorded_payments),
            style = typography.sectionHeading,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (state.linked.isEmpty() && state.unassigned.isEmpty()) {
            DaftarNotePanel(tone = DaftarNoteTone.Context) {
                Text(stringResource(Res.string.zakat_empty_title))
                Text(stringResource(Res.string.zakat_empty_log_action))
                Text(stringResource(Res.string.zakat_empty_log_help))
            }
        }
        if (state.linked.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.zakat_payments_for_year, yearLabel),
                style = typography.fineText,
                color = colors.muted,
            )
            DaftarAmount(
                amount = if (state.hidden) null else state.linkedTotal.moneyText(),
                unit = egpUnit(),
                masked = state.hidden,
                maskedDescription = hiddenText,
                style = typography.ledgerValue,
                color = colors.ink,
            )
            state.linked.forEach { payment ->
                PaymentRow(payment, state.hidden, months, hiddenText) {
                    onIntent(ZakatYearIntent.OpenPayment(payment.id))
                }
            }
        }
        if (state.unassigned.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.zakat_unassigned),
                style = typography.fineText,
                color = colors.muted,
            )
            state.unassigned.forEach { payment ->
                PaymentRow(payment, state.hidden, months, hiddenText) {
                    onIntent(ZakatYearIntent.OpenPayment(payment.id))
                }
            }
        }
        PrimaryButton(
            onClick = { onIntent(ZakatYearIntent.RecordPayment) },
            fullWidth = true,
            leadingIcon = DaftarIcons.Plus,
        ) {
            Text(stringResource(Res.string.zakat_record_payment))
        }
        DaftarTextButton(onClick = { onIntent(ZakatYearIntent.OpenDate) }, fullWidth = true) {
            Text(stringResource(Res.string.zakat_adjust_ramadan))
        }
    }
}

@Composable
private fun PaymentRow(
    payment: ZakatPaymentRow,
    hidden: Boolean,
    months: List<String>,
    hiddenText: String,
    onClick: () -> Unit,
) {
    DaftarLedgerRow(
        title = journalDateText(payment.paidOn, months),
        subtitle = if (hidden) null else payment.note,
        icon = DaftarIcons.Heart,
        onClick = onClick,
        value = {
            DaftarAmount(
                amount = if (hidden) null else payment.amount.moneyText(),
                unit = egpUnit(),
                masked = hidden,
                maskedDescription = hiddenText,
                style = DaftarTheme.typography.ledgerValue,
                color = DaftarTheme.colors.ink,
            )
        },
    )
}

/** The tracked years, newest first, with their status. Shows no amounts. */
@Composable
fun ZakatHistoryScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: ZakatHistoryViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect is JournalEffect.Navigate) navigator.goTo(effect.route)
    }
    ZakatHistoryContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The yearly status as it is drawn: each year opens its declaration. */
@Composable
fun ZakatHistoryContent(
    state: ZakatHistoryUiState,
    onIntent: (ZakatHistoryIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val months = stringArrayResource(Res.array.journal_months)
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(Res.string.zakat_history_title),
                onBack = onBack,
                backLabel = stringResource(DesignRes.string.btn_back),
            )
            Column(
                modifier = Modifier
                    .desktopPage(DesktopWidths.Editor)
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.zakat_history_heading),
                    style = typography.screenHeading,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                when (state.phase) {
                    JournalPhase.Loading -> DaftarSkeleton(size = DaftarSkeletonSize.Big)

                    JournalPhase.Locked, JournalPhase.Missing -> Unit

                    is JournalPhase.Failed -> DaftarResultState(
                        title = stringResource(Res.string.journal_load_failed_title),
                        message = stringResource(Res.string.journal_load_failed_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    JournalPhase.Empty -> DaftarEmptyState(
                        title = stringResource(Res.string.zakat_empty_title),
                        message = stringResource(Res.string.zakat_empty_message),
                        actionLabel = stringResource(Res.string.zakat_add_past_year),
                        onAction = { onIntent(ZakatHistoryIntent.AddPastYear) },
                        icon = DaftarIcons.Heart,
                    )

                    JournalPhase.Ready -> {
                        state.years.forEach { year ->
                            DaftarLedgerRow(
                                title = zakatYearLabel(year.hijriYear, year.gregorianYear),
                                subtitle = historyStatusText(year, months),
                                icon = historyIcon(year.status),
                                onClick = { onIntent(ZakatHistoryIntent.OpenYear(year.hijriYear)) },
                            )
                        }
                        OutlineButton(
                            onClick = { onIntent(ZakatHistoryIntent.AddPastYear) },
                            fullWidth = true,
                            leadingIcon = DaftarIcons.Plus,
                        ) {
                            Text(stringResource(Res.string.zakat_add_past_year))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun historyStatusText(year: ZakatYearSummary, months: List<String>): String = when (year.status) {
    ZakatYearStatus.DUE -> stringResource(Res.string.zakat_row_due)
    ZakatYearStatus.PAID -> stringResource(Res.string.zakat_row_paid)
    ZakatYearStatus.UPCOMING -> stringResource(Res.string.zakat_row_upcoming, journalDateText(year.date, months))
}

private fun historyIcon(status: ZakatYearStatus) = when (status) {
    ZakatYearStatus.DUE -> DaftarIcons.Heart
    ZakatYearStatus.PAID -> DaftarIcons.Check
    ZakatYearStatus.UPCOMING -> DaftarIcons.Clock
}

/** Adds a past or advance year to tracking. Validation runs as the user types; errors appear after a save attempt. */
@Composable
fun ZakatYearAddScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: ZakatYearAddViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect == JournalEffect.Completed) navigator.goBack()
    }
    ZakatYearAddContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

@Composable
fun ZakatYearAddContent(
    state: ZakatYearAddUiState,
    onIntent: (ZakatYearAddIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editable = state.phase == JournalPhase.Ready && !state.saving
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(Res.string.zakat_add_past_year),
                onBack = onBack,
                backLabel = stringResource(DesignRes.string.btn_back),
            )
            Column(
                modifier = Modifier
                    .desktopPage(DesktopWidths.Editor)
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            ) {
                DaftarTextField(
                    value = state.text,
                    onValueChange = { onIntent(ZakatYearAddIntent.TextChanged(it)) },
                    label = stringResource(Res.string.zakat_hijri_year),
                    kind = DaftarFieldKind.Decimal,
                    helper = yearHelper(state),
                    invalid = state.shownError != null,
                    enabled = editable,
                )
                state.gregorianYear?.let { gregorian ->
                    Text(
                        text = stringResource(Res.string.zakat_ramadan_falls, gregorian),
                        style = DaftarTheme.typography.fineText,
                        color = DaftarTheme.colors.muted,
                    )
                }
                DaftarNotePanel(tone = DaftarNoteTone.Context) {
                    Text(stringResource(Res.string.zakat_year_add_help))
                }
                if (state.failed) {
                    DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
                        Text(stringResource(Res.string.zakat_save_error))
                    }
                }
            }
            if (state.phase == JournalPhase.Ready) {
                DaftarFooterAction {
                    PrimaryButton(
                        onClick = { onIntent(ZakatYearAddIntent.Save) },
                        fullWidth = true,
                        enabled = editable,
                        loading = state.saving,
                    ) {
                        Text(
                            stringResource(
                                if (state.saving) DesignRes.string.btn_saving else Res.string.zakat_add_year_action,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun yearHelper(state: ZakatYearAddUiState): String? = when (state.shownError) {
    null -> state.maxYear?.let { stringResource(Res.string.zakat_year_range, it) }
    ZakatYearError.Duplicate -> stringResource(Res.string.zakat_error_year_duplicate)
    ZakatYearError.OutOfRange, ZakatYearError.Required -> stringResource(Res.string.zakat_error_year_range)
}

/** Moves one year's Ramadan date by one day or back to the calculated date. The declaration is never changed. */
@Composable
fun ZakatDateScreen(hijriYear: Int, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: ZakatDateViewModel = koinViewModel(
        key = "zakat-date-$hijriYear",
        parameters = { parametersOf(hijriYear) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect == JournalEffect.Completed) navigator.goBack()
    }
    ZakatDateContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

@Composable
fun ZakatDateContent(
    state: ZakatDateUiState,
    onIntent: (ZakatDateIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val months = stringArrayResource(Res.array.journal_months)
    val ready = state.phase == JournalPhase.Ready
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(Res.string.zakat_date_title),
                onBack = onBack,
                backLabel = stringResource(DesignRes.string.btn_back),
            )
            Column(
                modifier = Modifier
                    .desktopPage(DesktopWidths.Editor)
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.zakat_date_heading),
                    style = typography.screenHeading,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                if (ready) {
                    val committedOption = state.options.firstOrNull { it.overrideDays == state.committed }
                    committedOption?.let { option ->
                        Text(
                            text = zakatYearLabel(state.hijriYear, option.date.year),
                            style = typography.rowSubtitle,
                            color = colors.muted,
                        )
                    }
                }
                DaftarNotePanel(tone = DaftarNoteTone.Context) {
                    Text(stringResource(Res.string.zakat_date_note))
                }
                Text(
                    text = stringResource(Res.string.zakat_date_field),
                    style = typography.fieldLabel,
                    color = colors.muted,
                )
                state.options.forEach { option ->
                    DaftarLedgerRow(
                        title = dateOptionText(option, months),
                        selected = option.overrideDays == state.selected,
                        onClick = { onIntent(ZakatDateIntent.Select(option.overrideDays)) },
                    )
                }
                Text(
                    text = stringResource(Res.string.zakat_date_help),
                    style = typography.fineText,
                    color = colors.muted,
                )
                if (state.failed) {
                    DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
                        Text(stringResource(Res.string.zakat_save_error))
                    }
                }
            }
            if (ready) {
                DaftarFooterAction {
                    PrimaryButton(
                        onClick = { onIntent(ZakatDateIntent.Save) },
                        fullWidth = true,
                        enabled = state.dirty && !state.saving,
                        loading = state.saving,
                    ) {
                        val saveLabel =
                            if (state.saving) DesignRes.string.btn_saving else Res.string.zakat_save_date
                        Text(stringResource(saveLabel))
                    }
                    DaftarTextButton(
                        onClick = { onIntent(ZakatDateIntent.Restore) },
                        fullWidth = true,
                        enabled = state.committed != 0 && !state.saving,
                    ) {
                        Text(stringResource(Res.string.zakat_restore_date))
                    }
                    Text(
                        text = stringResource(Res.string.zakat_date_restore_help),
                        style = typography.fineText,
                        color = colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun dateOptionText(option: ZakatDateOption, months: List<String>): String {
    val date = journalDateText(option.date, months)
    return when (option.overrideDays) {
        -1 -> stringResource(Res.string.zakat_date_earlier, date)
        1 -> stringResource(Res.string.zakat_date_later, date)
        else -> stringResource(Res.string.zakat_date_calculated, date)
    }
}
