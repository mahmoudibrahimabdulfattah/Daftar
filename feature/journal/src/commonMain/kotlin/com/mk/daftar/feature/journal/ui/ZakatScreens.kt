package com.mk.daftar.feature.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.mk.daftar.core.designsystem.component.DaftarSelectField
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarSkeletonSize
import com.mk.daftar.core.designsystem.component.DaftarTextArea
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTextField
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.balance_hidden
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.resources.btn_saving
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.navigation.Navigator
import com.mk.daftar.core.navigation.ZakatYearRoute
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.DesktopPageColumn
import com.mk.daftar.core.ui.shell.DesktopWidths
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.desktopPage
import com.mk.daftar.core.ui.shell.pageScroll
import com.mk.daftar.feature.journal.JournalAction
import com.mk.daftar.feature.journal.JournalBusy
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.isoText
import com.mk.daftar.feature.journal.parseIsoDate
import com.mk.daftar.feature.journal.resources.Res
import com.mk.daftar.feature.journal.resources.journal_confirm_delete_action
import com.mk.daftar.feature.journal.resources.journal_confirm_delete_cancel
import com.mk.daftar.feature.journal.resources.journal_confirm_delete_message
import com.mk.daftar.feature.journal.resources.journal_confirm_delete_title
import com.mk.daftar.feature.journal.resources.journal_confirm_discard_action
import com.mk.daftar.feature.journal.resources.journal_confirm_discard_message
import com.mk.daftar.feature.journal.resources.journal_confirm_discard_title
import com.mk.daftar.feature.journal.resources.journal_confirm_keep
import com.mk.daftar.feature.journal.resources.journal_conflict_message
import com.mk.daftar.feature.journal.resources.journal_load_failed_message
import com.mk.daftar.feature.journal.resources.journal_load_failed_title
import com.mk.daftar.feature.journal.resources.journal_missing_message
import com.mk.daftar.feature.journal.resources.journal_missing_title
import com.mk.daftar.feature.journal.resources.journal_months
import com.mk.daftar.feature.journal.resources.journal_reload
import com.mk.daftar.feature.journal.resources.journal_unit_short
import com.mk.daftar.feature.journal.resources.zakat_add
import com.mk.daftar.feature.journal.resources.zakat_add_title
import com.mk.daftar.feature.journal.resources.zakat_amount
import com.mk.daftar.feature.journal.resources.zakat_date
import com.mk.daftar.feature.journal.resources.zakat_date_pick
import com.mk.daftar.feature.journal.resources.zakat_delete
import com.mk.daftar.feature.journal.resources.zakat_edit_title
import com.mk.daftar.feature.journal.resources.zakat_empty_message
import com.mk.daftar.feature.journal.resources.zakat_empty_title
import com.mk.daftar.feature.journal.resources.zakat_error_amount_invalid
import com.mk.daftar.feature.journal.resources.zakat_error_amount_required
import com.mk.daftar.feature.journal.resources.zakat_error_date_future
import com.mk.daftar.feature.journal.resources.zakat_error_date_invalid
import com.mk.daftar.feature.journal.resources.zakat_error_date_required
import com.mk.daftar.feature.journal.resources.zakat_error_note_long
import com.mk.daftar.feature.journal.resources.zakat_for_year
import com.mk.daftar.feature.journal.resources.zakat_heading
import com.mk.daftar.feature.journal.resources.zakat_note
import com.mk.daftar.feature.journal.resources.zakat_payment_help
import com.mk.daftar.feature.journal.resources.zakat_prefill_help
import com.mk.daftar.feature.journal.resources.zakat_record
import com.mk.daftar.feature.journal.resources.zakat_recording_only
import com.mk.daftar.feature.journal.resources.zakat_save
import com.mk.daftar.feature.journal.resources.zakat_title
import com.mk.daftar.feature.journal.resources.zakat_unassigned
import com.mk.daftar.feature.journal.resources.zakat_year_label
import com.mk.daftar.feature.journal.resources.zakat_year_recorded
import com.mk.daftar.feature.journal.zakat.ZakatAmountError
import com.mk.daftar.feature.journal.zakat.ZakatConfirmation
import com.mk.daftar.feature.journal.zakat.ZakatDateError
import com.mk.daftar.feature.journal.zakat.ZakatEditorIntent
import com.mk.daftar.feature.journal.zakat.ZakatEditorLaunch
import com.mk.daftar.feature.journal.zakat.ZakatEditorTarget
import com.mk.daftar.feature.journal.zakat.ZakatEditorUiState
import com.mk.daftar.feature.journal.zakat.ZakatEditorViewModel
import com.mk.daftar.feature.journal.zakat.ZakatErrors
import com.mk.daftar.feature.journal.zakat.ZakatListIntent
import com.mk.daftar.feature.journal.zakat.ZakatListUiState
import com.mk.daftar.feature.journal.zakat.ZakatListViewModel
import com.mk.daftar.feature.journal.zakat.ZakatNoteError
import com.mk.daftar.feature.journal.zakat.ZakatYear
import com.mk.daftar.feature.journal.zakat.unrecordedSummary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The currency unit beside every zakat amount: the short unit Home uses, EGP or ج.م. */
@Composable
internal fun egpUnit(): String = stringResource(Res.string.journal_unit_short)

/** The zakat log: payments grouped by calendar year, each year with its exact EGP total. */
@Composable
fun ZakatListScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: ZakatListViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect is JournalEffect.Navigate) navigator.goTo(effect.route)
    }
    ZakatListContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The zakat log as it is drawn. Amounts and notes are removed from rendering and semantics while values are hidden. */
@Composable
fun ZakatListContent(
    state: ZakatListUiState,
    onIntent: (ZakatListIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val months = stringArrayResource(Res.array.journal_months)
    val hiddenText = stringResource(DesignRes.string.balance_hidden)
    ShellScreen(modifier = modifier) {
        DesktopPageColumn(
            cap = DesktopWidths.Page,
            topBar = {
                DaftarTopBar(
                    title = stringResource(Res.string.zakat_title),
                    onBack = onBack,
                    backLabel = stringResource(DesignRes.string.btn_back),
                    actions = { VisibilityToggle(hidden = state.hidden) { onIntent(ZakatListIntent.ToggleHidden) } },
                )
            },
        ) {
            Text(
                text = stringResource(Res.string.zakat_heading),
                style = typography.screenHeading,
                color = colors.ink,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.zakat_recording_only),
                style = typography.fineText,
                color = colors.muted,
            )
            state.pendingRoute?.let {
                RevealPrompt(
                    onReveal = { onIntent(ZakatListIntent.RevealAndOpen) },
                    onCancel = { onIntent(ZakatListIntent.CancelReveal) },
                )
            }
            when (state.phase) {
                JournalPhase.Loading -> DaftarSkeleton(size = DaftarSkeletonSize.Big)

                JournalPhase.Locked, JournalPhase.Missing -> Unit

                is JournalPhase.Failed -> DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
                    Text(stringResource(Res.string.journal_load_failed_title))
                    Text(stringResource(Res.string.journal_load_failed_message))
                }

                JournalPhase.Empty -> DaftarEmptyState(
                    title = stringResource(Res.string.zakat_empty_title),
                    message = stringResource(Res.string.zakat_empty_message),
                    actionLabel = stringResource(Res.string.zakat_add),
                    onAction = { onIntent(ZakatListIntent.OpenAdd) },
                    icon = DaftarIcons.Heart,
                )

                JournalPhase.Ready -> {
                    state.years.forEach { year ->
                        ZakatYearSummary(year = year, hidden = state.hidden, hiddenText = hiddenText)
                        year.payments.forEach { payment ->
                            DaftarLedgerRow(
                                title = journalDateText(payment.paidOn, months),
                                subtitle = if (state.hidden) null else payment.note,
                                icon = DaftarIcons.Heart,
                                onClick = { onIntent(ZakatListIntent.OpenPayment(payment.id)) },
                                value = {
                                    DaftarAmount(
                                        amount = if (state.hidden) null else (payment.amount).moneyText(),
                                        unit = egpUnit(),
                                        masked = state.hidden,
                                        maskedDescription = hiddenText,
                                        style = typography.ledgerValue,
                                        color = colors.ink,
                                    )
                                },
                            )
                        }
                    }
                    PrimaryButton(
                        onClick = { onIntent(ZakatListIntent.OpenAdd) },
                        fullWidth = true,
                        leadingIcon = DaftarIcons.Plus,
                    ) {
                        Text(stringResource(Res.string.zakat_add))
                    }
                }
            }
        }
    }
}

/** The per-year heading and its total. The total is never shown while values are hidden. */
@Composable
private fun ZakatYearSummary(year: ZakatYear, hidden: Boolean, hiddenText: String) {
    val colors = DaftarTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xxs)) {
        Text(
            text = stringResource(Res.string.zakat_year_recorded, year.year),
            style = DaftarTheme.typography.fineText,
            color = colors.muted,
        )
        DaftarAmount(
            amount = if (hidden) null else (year.total).moneyText(),
            unit = egpUnit(),
            masked = hidden,
            maskedDescription = hiddenText,
            style = DaftarTheme.typography.heroAmount,
            color = colors.ink,
        )
    }
}

/** The zakat editor: opened for a new payment or for one recorded payment. It leaves only through a confirmed path. */
@Composable
fun ZakatEditorScreen(
    target: ZakatEditorTarget,
    modifier: Modifier = Modifier,
    launch: ZakatEditorLaunch = ZakatEditorLaunch(),
) {
    val navigator = LocalNavigator.current
    val viewModel: ZakatEditorViewModel = koinViewModel(
        key = "zakat-editor-$target-$launch",
        parameters = { parametersOf(target, launch) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect == JournalEffect.Completed) returnAfterSave(navigator, state.draft.forHijriYear)
    }
    // The shell asks before it leaves: a back action, a gesture or a tab change. Only unsaved edits make it ask.
    DisposableEffect(viewModel, navigator) {
        val unregister = navigator.leaveGuard.register(viewModel) { _, proceed ->
            viewModel.onLeaveRequested { _ ->
                proceed()
            }
        }
        onDispose { unregister() }
    }
    ZakatEditorContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/**
 * After a confirmed write the editor closes. A payment linked to a year returns to that year, so its declaration is at hand;
 * an unassigned payment simply goes back.
 */
private fun returnAfterSave(navigator: Navigator, hijriYear: Int?) {
    if (hijriYear == null) {
        navigator.goBack()
    } else if (!navigator.returnTo { it == ZakatYearRoute(hijriYear) }) {
        navigator.goBack()
        navigator.goTo(ZakatYearRoute(hijriYear))
    }
}

/**
 * The zakat editor as it is drawn. A concealed form shows no field and no keyboard. Save sits in the footer, above the
 * keyboard, because the shell applies the IME inset to the whole screen.
 */
@Composable
fun ZakatEditorContent(
    state: ZakatEditorUiState,
    onIntent: (ZakatEditorIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var pickingDate by remember { mutableStateOf(false) }
    var pickingYear by remember { mutableStateOf(false) }
    LaunchedEffect(state.concealed) {
        if (state.concealed) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    val editable = state.phase == JournalPhase.Ready && !state.concealed && !state.completed && state.busy == null
    val showsForm = state.phase == JournalPhase.Ready && !state.concealed && !state.completed
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(if (state.isNew) Res.string.zakat_add_title else Res.string.zakat_edit_title),
                onBack = onBack,
                backLabel = stringResource(DesignRes.string.btn_back),
            )
            Column(
                modifier = Modifier
                    .desktopPage(DesktopWidths.Editor)
                    .weight(1f)
                    .fillMaxWidth()
                    .pageScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            ) {
                when (state.phase) {
                    JournalPhase.Loading -> repeat(3) { DaftarSkeleton() }

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

                    JournalPhase.Ready -> when {
                        state.concealed -> RevealPrompt(
                            onReveal = { onIntent(ZakatEditorIntent.RevealForm) },
                            onCancel = onBack,
                        )

                        state.completed -> Unit

                        else -> ZakatForm(
                            state = state,
                            editable = editable,
                            onIntent = onIntent,
                            onPickDate = { pickingDate = true },
                            onPickYear = { pickingYear = true },
                        )
                    }
                }
            }
            if (showsForm) {
                DaftarFooterAction {
                    PrimaryButton(
                        onClick = { onIntent(ZakatEditorIntent.Save) },
                        fullWidth = true,
                        enabled = editable && !state.conflict,
                        loading = state.busy == JournalBusy.Saving,
                    ) {
                        Text(
                            stringResource(
                                when {
                                    state.busy == JournalBusy.Saving -> DesignRes.string.btn_saving
                                    state.isNew -> Res.string.zakat_record
                                    else -> Res.string.zakat_save
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
    when (state.confirmation) {
        ZakatConfirmation.Delete -> JournalConfirmDialog(
            title = stringResource(Res.string.journal_confirm_delete_title),
            message = stringResource(Res.string.journal_confirm_delete_message),
            primaryLabel = stringResource(Res.string.journal_confirm_delete_cancel),
            onPrimary = { onIntent(ZakatEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(ZakatEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.journal_confirm_delete_action),
            onDestructive = { onIntent(ZakatEditorIntent.ConfirmDelete) },
        )

        ZakatConfirmation.Discard -> JournalConfirmDialog(
            title = stringResource(Res.string.journal_confirm_discard_title),
            message = stringResource(Res.string.journal_confirm_discard_message),
            primaryLabel = stringResource(Res.string.journal_confirm_keep),
            onPrimary = { onIntent(ZakatEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(ZakatEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.journal_confirm_discard_action),
            onDestructive = { onIntent(ZakatEditorIntent.ConfirmDiscard) },
        )

        ZakatConfirmation.Reload -> JournalConfirmDialog(
            title = stringResource(Res.string.journal_confirm_discard_title),
            message = stringResource(Res.string.journal_confirm_discard_message),
            primaryLabel = stringResource(Res.string.journal_confirm_keep),
            onPrimary = { onIntent(ZakatEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(ZakatEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.journal_reload),
            onDestructive = { onIntent(ZakatEditorIntent.ConfirmReload) },
        )

        null -> Unit
    }
    if (pickingYear) {
        ZakatYearPickerDialog(
            choices = state.yearChoices,
            selected = state.draft.forHijriYear,
            onSelect = { year ->
                onIntent(ZakatEditorIntent.YearSelected(year))
                pickingYear = false
            },
            onDismiss = { pickingYear = false },
        )
    }
    val today = state.today
    if (pickingDate && today != null) {
        JournalDatePicker(
            initial = parseIsoDate(state.draft.dateText) ?: today,
            latest = today,
            onPick = { picked ->
                onIntent(ZakatEditorIntent.DateChanged(picked.isoText()))
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@Composable
private fun ZakatForm(
    state: ZakatEditorUiState,
    editable: Boolean,
    onIntent: (ZakatEditorIntent) -> Unit,
    onPickDate: () -> Unit,
    onPickYear: () -> Unit,
) {
    val errors = state.errors
    val reveal = state.attempted
    if (state.conflict) {
        DaftarNotePanel(
            tone = DaftarNoteTone.Warning,
            icon = DaftarIcons.Alert,
            action = {
                DaftarTextButton(
                    onClick = { onIntent(ZakatEditorIntent.RequestReload) },
                    enabled = state.busy == null,
                ) {
                    Text(stringResource(Res.string.journal_reload))
                }
            },
        ) {
            Text(stringResource(Res.string.journal_conflict_message))
        }
    }
    state.failure?.let { failure ->
        WriteFailureNote(
            failure = failure,
            onRetry = {
                if (failure.action == JournalAction.Save) {
                    onIntent(ZakatEditorIntent.Save)
                } else {
                    onIntent(ZakatEditorIntent.RequestDelete)
                }
            },
            onBackToEditing = { onIntent(ZakatEditorIntent.DismissFailure) },
        )
    }
    DaftarTextField(
        label = stringResource(Res.string.zakat_date),
        value = state.draft.dateText,
        onValueChange = { onIntent(ZakatEditorIntent.DateChanged(it)) },
        helper = if (reveal) dateHelper(errors)?.let { stringResource(it) } else null,
        invalid = reveal && errors.date != null,
        enabled = editable,
    )
    DaftarTextButton(
        onClick = onPickDate,
        leadingIcon = DaftarIcons.Calendar,
        enabled = editable,
    ) {
        Text(stringResource(Res.string.zakat_date_pick))
    }
    DaftarTextField(
        label = stringResource(Res.string.zakat_amount),
        value = state.draft.amountText,
        onValueChange = { onIntent(ZakatEditorIntent.AmountChanged(it)) },
        kind = DaftarFieldKind.Decimal,
        helper = when {
            reveal && errors.amount != null -> amountHelper(errors)?.let { stringResource(it) }
            state.prefilled -> stringResource(Res.string.zakat_prefill_help)
            else -> null
        },
        invalid = reveal && errors.amount != null,
        enabled = editable,
    )
    DaftarSelectField(
        label = stringResource(Res.string.zakat_for_year),
        selectedText = yearSelectionText(state),
        onClick = onPickYear,
        enabled = editable && state.yearReady,
    )
    DaftarTextArea(
        label = stringResource(Res.string.zakat_note),
        value = state.draft.noteText,
        onValueChange = { onIntent(ZakatEditorIntent.NoteChanged(it)) },
        helper = if (reveal && errors.note != null) stringResource(Res.string.zakat_error_note_long) else null,
        invalid = reveal && errors.note != null,
        enabled = editable,
    )
    DaftarNotePanel(tone = DaftarNoteTone.Context) {
        Text(stringResource(Res.string.zakat_payment_help))
    }
    if (!state.isNew) {
        DaftarTextButton(
            onClick = { onIntent(ZakatEditorIntent.RequestDelete) },
            fullWidth = true,
            enabled = editable,
        ) {
            Text(stringResource(Res.string.zakat_delete))
        }
    }
}

private fun dateHelper(errors: ZakatErrors): StringResource? = when (errors.date) {
    null -> null
    ZakatDateError.Required -> Res.string.zakat_error_date_required
    ZakatDateError.Invalid -> Res.string.zakat_error_date_invalid
    ZakatDateError.Future -> Res.string.zakat_error_date_future
}

private fun amountHelper(errors: ZakatErrors): StringResource? = when (errors.amount) {
    null -> null
    ZakatAmountError.Required -> Res.string.zakat_error_amount_required
    ZakatAmountError.Invalid -> Res.string.zakat_error_amount_invalid
}

/** The picker's text: the chosen year's label, or Unassigned. Empty while the default is still being read. */
@Composable
private fun yearSelectionText(state: ZakatEditorUiState): String? {
    val year =
        state.draft.forHijriYear ?: return if (state.yearReady) stringResource(Res.string.zakat_unassigned) else null
    val summary = state.yearChoices.firstOrNull { it.hijriYear == year } ?: unrecordedSummary(year)
    return stringResource(Res.string.zakat_year_label, summary.hijriYear, summary.gregorianYear)
}
