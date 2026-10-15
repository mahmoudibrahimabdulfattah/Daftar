package com.mk.daftar.feature.cash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.mk.daftar.core.designsystem.component.DaftarSegmentedControl
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarSkeletonSize
import com.mk.daftar.core.designsystem.component.DaftarStatusTag
import com.mk.daftar.core.designsystem.component.DaftarStatusTone
import com.mk.daftar.core.designsystem.component.DaftarSwitch
import com.mk.daftar.core.designsystem.component.DaftarTextArea
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTextField
import com.mk.daftar.core.designsystem.component.DaftarTileTone
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.balance_hidden
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.resources.btn_saving
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.valuation.QuoteWarning
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.DesktopWidths
import com.mk.daftar.core.ui.shell.PriceRefreshAction
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.desktopPage
import com.mk.daftar.feature.cash.CashAction
import com.mk.daftar.feature.cash.CashBalanceError
import com.mk.daftar.feature.cash.CashBusy
import com.mk.daftar.feature.cash.CashConfirmation
import com.mk.daftar.feature.cash.CashEditorIntent
import com.mk.daftar.feature.cash.CashEditorTarget
import com.mk.daftar.feature.cash.CashEditorUiState
import com.mk.daftar.feature.cash.CashEditorViewModel
import com.mk.daftar.feature.cash.CashEffect
import com.mk.daftar.feature.cash.CashListIntent
import com.mk.daftar.feature.cash.CashListUiState
import com.mk.daftar.feature.cash.CashListViewModel
import com.mk.daftar.feature.cash.CashNameError
import com.mk.daftar.feature.cash.CashPhase
import com.mk.daftar.feature.cash.CashRow
import com.mk.daftar.feature.cash.resources.Res
import com.mk.daftar.feature.cash.resources.cash_add
import com.mk.daftar.feature.cash.resources.cash_add_title
import com.mk.daftar.feature.cash.resources.cash_available
import com.mk.daftar.feature.cash.resources.cash_balance
import com.mk.daftar.feature.cash.resources.cash_balance_helper
import com.mk.daftar.feature.cash.resources.cash_confirm_delete_action
import com.mk.daftar.feature.cash.resources.cash_confirm_delete_cancel
import com.mk.daftar.feature.cash.resources.cash_confirm_delete_message
import com.mk.daftar.feature.cash.resources.cash_confirm_delete_title
import com.mk.daftar.feature.cash.resources.cash_confirm_discard_action
import com.mk.daftar.feature.cash.resources.cash_confirm_discard_message
import com.mk.daftar.feature.cash.resources.cash_confirm_discard_title
import com.mk.daftar.feature.cash.resources.cash_confirm_keep
import com.mk.daftar.feature.cash.resources.cash_conflict_message
import com.mk.daftar.feature.cash.resources.cash_convert_investment
import com.mk.daftar.feature.cash.resources.cash_currency
import com.mk.daftar.feature.cash.resources.cash_currency_ack
import com.mk.daftar.feature.cash.resources.cash_currency_ack_required
import com.mk.daftar.feature.cash.resources.cash_currency_change
import com.mk.daftar.feature.cash.resources.cash_currency_egp_name
import com.mk.daftar.feature.cash.resources.cash_currency_usd_name
import com.mk.daftar.feature.cash.resources.cash_delete
import com.mk.daftar.feature.cash.resources.cash_edit_cash_title
import com.mk.daftar.feature.cash.resources.cash_edit_wallet_title
import com.mk.daftar.feature.cash.resources.cash_egp_equivalent
import com.mk.daftar.feature.cash.resources.cash_empty_action
import com.mk.daftar.feature.cash.resources.cash_empty_message
import com.mk.daftar.feature.cash.resources.cash_empty_title
import com.mk.daftar.feature.cash.resources.cash_error_balance_invalid
import com.mk.daftar.feature.cash.resources.cash_error_balance_required
import com.mk.daftar.feature.cash.resources.cash_error_name_long
import com.mk.daftar.feature.cash.resources.cash_error_name_required
import com.mk.daftar.feature.cash.resources.cash_error_note_long
import com.mk.daftar.feature.cash.resources.cash_kind_cash
import com.mk.daftar.feature.cash.resources.cash_kind_wallet
import com.mk.daftar.feature.cash.resources.cash_load_failed_message
import com.mk.daftar.feature.cash.resources.cash_load_failed_title
import com.mk.daftar.feature.cash.resources.cash_missing_message
import com.mk.daftar.feature.cash.resources.cash_missing_title
import com.mk.daftar.feature.cash.resources.cash_name
import com.mk.daftar.feature.cash.resources.cash_note
import com.mk.daftar.feature.cash.resources.cash_rate_missing
import com.mk.daftar.feature.cash.resources.cash_reload
import com.mk.daftar.feature.cash.resources.cash_save
import com.mk.daftar.feature.cash.resources.cash_tag_manual
import com.mk.daftar.feature.cash.resources.cash_tag_stale
import com.mk.daftar.feature.cash.resources.cash_title
import com.mk.daftar.feature.cash.resources.cash_total
import com.mk.daftar.feature.cash.resources.cash_type
import com.mk.daftar.feature.cash.resources.cash_unit_egp
import com.mk.daftar.feature.cash.resources.cash_valuation_incomplete
import com.mk.daftar.feature.cash.resources.cash_wallet_subtitle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Cash and wallet balances: the total, each balance in its own currency, and the way to add one. */
@Composable
fun CashListScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: CashListViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect is CashEffect.Navigate) navigator.goTo(effect.route)
    }
    CashListContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The list as it is drawn. Amounts are removed from rendering and semantics while values are hidden. */
@Composable
fun CashListContent(
    state: CashListUiState,
    onIntent: (CashListIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hiddenText = stringResource(DesignRes.string.balance_hidden)
    ShellScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .desktopPage(DesktopWidths.Page)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DaftarSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            DaftarTopBar(
                title = stringResource(Res.string.cash_title),
                onBack = onBack,
                backLabel = stringResource(DesignRes.string.btn_back),
                actions = {
                    VisibilityToggle(hidden = state.hidden) { onIntent(CashListIntent.ToggleHidden) }
                    PriceRefreshAction()
                },
            )
            state.pendingRoute?.let {
                RevealPrompt(
                    onReveal = { onIntent(CashListIntent.RevealAndOpen) },
                    onCancel = { onIntent(CashListIntent.CancelReveal) },
                )
            }
            when (state.phase) {
                CashPhase.Loading -> DaftarSkeleton(size = DaftarSkeletonSize.Big)

                CashPhase.Locked, CashPhase.Missing -> Unit

                is CashPhase.Failed -> DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
                    Text(stringResource(Res.string.cash_load_failed_title))
                    Text(stringResource(Res.string.cash_load_failed_message))
                }

                CashPhase.Empty -> DaftarEmptyState(
                    title = stringResource(Res.string.cash_empty_title),
                    message = stringResource(Res.string.cash_empty_message),
                    actionLabel = stringResource(Res.string.cash_empty_action),
                    onAction = { onIntent(CashListIntent.OpenAdd) },
                    icon = DaftarIcons.Wallet,
                )

                CashPhase.Ready -> {
                    CashTotal(state = state, hiddenText = hiddenText)
                    state.rows.forEach { row ->
                        CashBalanceRow(
                            row = row,
                            hidden = state.hidden,
                            hiddenText = hiddenText,
                            onOpen = { onIntent(CashListIntent.OpenBalance(row.id)) },
                        )
                    }
                    PrimaryButton(
                        onClick = { onIntent(CashListIntent.OpenAdd) },
                        fullWidth = true,
                        leadingIcon = DaftarIcons.Plus,
                    ) {
                        Text(stringResource(Res.string.cash_add))
                    }
                }
            }
        }
    }
}

/**
 * The EGP total when every balance is valued. Otherwise the available subtotal, labelled as such, with the total
 * shown as unavailable. Nothing is shown in place of a total while values are hidden.
 */
@Composable
private fun CashTotal(state: CashListUiState, hiddenText: String) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val complete = state.totalEgp
    val shown = complete ?: state.availableEgp
    val label = if (complete == null && state.availableEgp != null) {
        Res.string.cash_available
    } else {
        Res.string.cash_total
    }
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xxs)) {
        Text(text = stringResource(label), style = typography.fineText, color = colors.muted)
        DaftarAmount(
            amount = if (state.hidden) null else shown?.moneyText(),
            unit = stringResource(Res.string.cash_unit_egp),
            masked = state.hidden,
            maskedDescription = hiddenText,
            style = typography.heroAmount,
            color = colors.ink,
        )
        if (complete == null) {
            Text(
                text = stringResource(Res.string.cash_valuation_incomplete),
                style = typography.fineText,
                color = colors.muted,
            )
        }
    }
}

/**
 * One balance in its own currency. A USD balance also shows its EGP equivalent when a rate exists. Without one the
 * stored USD amount still shows, and the missing rate is named instead.
 */
@Composable
private fun CashBalanceRow(row: CashRow, hidden: Boolean, hiddenText: String, onOpen: () -> Unit) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val statusLabel = row.warnings.statusResource()
    DaftarLedgerRow(
        title = row.name,
        subtitle = rowSubtitle(row = row, hidden = hidden),
        icon = DaftarIcons.Wallet,
        tone = DaftarTileTone.Cash,
        onClick = onOpen,
        value = {
            Column(horizontalAlignment = Alignment.End) {
                DaftarAmount(
                    amount = if (hidden) null else row.amount.moneyText(),
                    unit = if (row.currency ==
                        Currency.EGP
                    ) {
                        stringResource(Res.string.cash_unit_egp)
                    } else {
                        row.currency.name
                    },
                    masked = hidden,
                    maskedDescription = hiddenText,
                    style = typography.ledgerValue,
                    color = colors.ink,
                )
                if (statusLabel != null) {
                    DaftarStatusTag(label = stringResource(statusLabel), tone = DaftarStatusTone.Warning)
                }
            }
        },
    )
}

@Composable
private fun rowSubtitle(row: CashRow, hidden: Boolean): String {
    val base = when {
        row.kind == CashKind.WALLET -> stringResource(Res.string.cash_wallet_subtitle)
        row.currency == Currency.USD -> stringResource(Res.string.cash_currency_usd_name)
        else -> stringResource(Res.string.cash_currency_egp_name)
    }
    if (hidden || row.currency != Currency.USD) return base
    val equivalent = row.egp?.let { stringResource(Res.string.cash_egp_equivalent, "⁦${it.moneyText()}⁩") }
        ?: stringResource(Res.string.cash_rate_missing)
    return "$base · $equivalent"
}

/** The manual or stale state of a valued balance's rate, shown beside it so the figure is never taken as fresh. */
private fun List<QuoteWarning>.statusResource(): StringResource? = when {
    any { it == QuoteWarning.PINNED_MANUAL || it == QuoteWarning.UNPINNED_MANUAL } -> Res.string.cash_tag_manual
    any { it == QuoteWarning.STALE || it == QuoteWarning.CLOCK_UNCERTAIN } -> Res.string.cash_tag_stale
    else -> null
}

/** The editor for a new balance or one recorded balance. It leaves only through a confirmed path. */
@Composable
fun CashEditorScreen(target: CashEditorTarget, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: CashEditorViewModel = koinViewModel(
        key = "cash-editor-$target",
        parameters = { parametersOf(target) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            CashEffect.Completed -> navigator.goBack()
            is CashEffect.Navigate -> navigator.goTo(effect.route)
        }
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
    CashEditorContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/**
 * The editor as it is drawn. A concealed form shows no field and no keyboard. Save sits in the footer, above the keyboard,
 * because the shell applies the IME inset to the whole screen.
 */
@Composable
fun CashEditorContent(
    state: CashEditorUiState,
    onIntent: (CashEditorIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.concealed) {
        if (state.concealed) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    val editable = state.phase == CashPhase.Ready && !state.concealed && !state.completed && state.busy == null
    val showsForm = state.phase == CashPhase.Ready && !state.concealed && !state.completed
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(editorTitle(state)),
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
                when (state.phase) {
                    CashPhase.Loading -> repeat(3) { DaftarSkeleton() }

                    CashPhase.Locked, CashPhase.Empty -> Unit

                    CashPhase.Missing -> DaftarResultState(
                        title = stringResource(Res.string.cash_missing_title),
                        message = stringResource(Res.string.cash_missing_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    is CashPhase.Failed -> DaftarResultState(
                        title = stringResource(Res.string.cash_load_failed_title),
                        message = stringResource(Res.string.cash_load_failed_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    CashPhase.Ready -> when {
                        state.concealed -> RevealPrompt(
                            onReveal = { onIntent(CashEditorIntent.RevealForm) },
                            onCancel = onBack,
                        )

                        state.completed -> Unit

                        else -> CashForm(state = state, editable = editable, onIntent = onIntent)
                    }
                }
            }
            if (showsForm) {
                DaftarFooterAction {
                    PrimaryButton(
                        onClick = { onIntent(CashEditorIntent.Save) },
                        fullWidth = true,
                        enabled = editable && !state.conflict,
                        loading = state.busy == CashBusy.Saving,
                    ) {
                        Text(
                            stringResource(
                                if (state.busy ==
                                    CashBusy.Saving
                                ) {
                                    DesignRes.string.btn_saving
                                } else {
                                    Res.string.cash_save
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
    // The confirmations take no busy state, so their callbacks are guarded here while a write runs.
    val idle = state.busy == null
    when (state.confirmation) {
        CashConfirmation.Delete -> CashConfirmDialog(
            title = stringResource(Res.string.cash_confirm_delete_title),
            message = stringResource(Res.string.cash_confirm_delete_message),
            primaryLabel = stringResource(Res.string.cash_confirm_delete_cancel),
            onPrimary = { onIntent(CashEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(CashEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.cash_confirm_delete_action),
            onDestructive = { if (idle) onIntent(CashEditorIntent.ConfirmDelete) },
        )

        CashConfirmation.Discard -> CashConfirmDialog(
            title = stringResource(Res.string.cash_confirm_discard_title),
            message = stringResource(Res.string.cash_confirm_discard_message),
            primaryLabel = stringResource(Res.string.cash_confirm_keep),
            onPrimary = { onIntent(CashEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(CashEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.cash_confirm_discard_action),
            onDestructive = { if (idle) onIntent(CashEditorIntent.ConfirmDiscard) },
        )

        CashConfirmation.Reload -> CashConfirmDialog(
            title = stringResource(Res.string.cash_confirm_discard_title),
            message = stringResource(Res.string.cash_confirm_discard_message),
            primaryLabel = stringResource(Res.string.cash_confirm_keep),
            onPrimary = { onIntent(CashEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(CashEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.cash_reload),
            onDestructive = { if (idle) onIntent(CashEditorIntent.ConfirmReload) },
        )

        null -> Unit
    }
}

@Composable
private fun CashForm(state: CashEditorUiState, editable: Boolean, onIntent: (CashEditorIntent) -> Unit) {
    val colors = DaftarTheme.colors
    val errors = state.errors
    val reveal = state.attempted
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        if (state.conflict) {
            DaftarNotePanel(
                tone = DaftarNoteTone.Warning,
                icon = DaftarIcons.Alert,
                action = {
                    DaftarTextButton(
                        onClick = { onIntent(CashEditorIntent.RequestReload) },
                        enabled = state.busy == null,
                    ) {
                        Text(stringResource(Res.string.cash_reload))
                    }
                },
            ) {
                Text(stringResource(Res.string.cash_conflict_message))
            }
        }
        state.failure?.let { failure ->
            WriteFailureNote(
                failure = failure,
                onRetry = {
                    if (failure.action == CashAction.Save) {
                        onIntent(CashEditorIntent.Save)
                    } else {
                        onIntent(CashEditorIntent.RequestDelete)
                    }
                },
                onBackToEditing = { onIntent(CashEditorIntent.DismissFailure) },
            )
        }
        DaftarTextField(
            label = stringResource(Res.string.cash_name),
            value = state.draft.name,
            onValueChange = { onIntent(CashEditorIntent.NameChanged(it)) },
            helper = if (reveal) nameHelper(errors.name)?.let { stringResource(it) } else null,
            invalid = reveal && errors.name != null,
            enabled = editable,
        )
        Text(text = stringResource(Res.string.cash_type), style = DaftarTheme.typography.fineText, color = colors.muted)
        DaftarSegmentedControl(
            options = listOf(stringResource(Res.string.cash_kind_cash), stringResource(Res.string.cash_kind_wallet)),
            selectedIndex = if (state.draft.kind == CashKind.WALLET) 1 else 0,
            onSelect = { index ->
                onIntent(CashEditorIntent.KindChanged(if (index == 1) CashKind.WALLET else CashKind.CASH))
            },
            enabled = editable,
        )
        Text(
            text = stringResource(Res.string.cash_currency),
            style = DaftarTheme.typography.fineText,
            color = colors.muted,
        )
        DaftarSegmentedControl(
            options = listOf(Currency.EGP.name, Currency.USD.name),
            selectedIndex = if (state.draft.currency == Currency.USD) 1 else 0,
            onSelect = { index ->
                onIntent(CashEditorIntent.CurrencyChanged(if (index == 1) Currency.USD else Currency.EGP))
            },
            enabled = editable,
        )
        if (state.currencyChanged) {
            CurrencyRelabelNotice(
                acknowledged = state.currencyAcknowledged,
                showRequired = reveal && state.needsCurrencyAcknowledgement,
                editable = editable,
                onAcknowledge = { onIntent(CashEditorIntent.CurrencyAcknowledged(it)) },
            )
        }
        DaftarTextField(
            label = stringResource(Res.string.cash_balance),
            value = state.draft.balanceText,
            onValueChange = { onIntent(CashEditorIntent.BalanceChanged(it)) },
            kind = DaftarFieldKind.Decimal,
            helper = if (reveal && errors.balance != null) {
                stringResource(balanceHelper(errors.balance) ?: Res.string.cash_balance_helper)
            } else {
                stringResource(Res.string.cash_balance_helper)
            },
            invalid = reveal && errors.balance != null,
            enabled = editable,
        )
        DaftarTextArea(
            label = stringResource(Res.string.cash_note),
            value = state.draft.noteText,
            onValueChange = { onIntent(CashEditorIntent.NoteChanged(it)) },
            helper = if (reveal && errors.note != null) stringResource(Res.string.cash_error_note_long) else null,
            invalid = reveal && errors.note != null,
            enabled = editable,
        )
        if (!state.isNew) {
            // A saved, clean entry can be reclassified as an investment; unsaved edits must be saved or discarded first.
            if (!state.dirty) {
                DaftarTextButton(
                    onClick = { onIntent(CashEditorIntent.ConvertToInvestment) },
                    fullWidth = true,
                    enabled = editable,
                ) {
                    Text(stringResource(Res.string.cash_convert_investment))
                }
            }
            DaftarTextButton(
                onClick = { onIntent(CashEditorIntent.RequestDelete) },
                fullWidth = true,
                enabled = editable,
            ) {
                Text(stringResource(Res.string.cash_delete))
            }
        }
    }
}

/** The approved relabelling warning for an existing balance, and the acknowledgement it needs before saving. */
@Composable
private fun CurrencyRelabelNotice(
    acknowledged: Boolean,
    showRequired: Boolean,
    editable: Boolean,
    onAcknowledge: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
        DaftarNotePanel(tone = DaftarNoteTone.Warning, icon = DaftarIcons.Alert) {
            Text(stringResource(Res.string.cash_currency_change))
        }
        DaftarSwitch(
            checked = acknowledged,
            onCheckedChange = onAcknowledge,
            label = stringResource(Res.string.cash_currency_ack),
            enabled = editable,
        )
        if (showRequired) {
            Text(
                text = stringResource(Res.string.cash_currency_ack_required),
                style = DaftarTheme.typography.fineText,
                color = DaftarTheme.colors.error,
            )
        }
    }
}

private fun editorTitle(state: CashEditorUiState): StringResource = when {
    state.isNew -> Res.string.cash_add_title
    state.draft.kind == CashKind.WALLET -> Res.string.cash_edit_wallet_title
    else -> Res.string.cash_edit_cash_title
}

private fun nameHelper(error: CashNameError?): StringResource? = when (error) {
    null -> null
    CashNameError.Required -> Res.string.cash_error_name_required
    CashNameError.TooLong -> Res.string.cash_error_name_long
}

private fun balanceHelper(error: CashBalanceError?): StringResource? = when (error) {
    null -> null
    CashBalanceError.Required -> Res.string.cash_error_balance_required
    CashBalanceError.Invalid -> Res.string.cash_error_balance_invalid
}
