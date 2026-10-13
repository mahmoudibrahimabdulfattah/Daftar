package com.mk.daftar.feature.banks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarCurrencyBadge
import com.mk.daftar.core.designsystem.component.DaftarFieldKind
import com.mk.daftar.core.designsystem.component.DaftarFooterAction
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
import com.mk.daftar.core.designsystem.component.DaftarSegmentedControl
import com.mk.daftar.core.designsystem.component.DaftarSelectField
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarSwitch
import com.mk.daftar.core.designsystem.component.DaftarTextArea
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTextField
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.resources.btn_saving
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.feature.banks.AccountBalanceError
import com.mk.daftar.feature.banks.AccountBankError
import com.mk.daftar.feature.banks.AccountEditorIntent
import com.mk.daftar.feature.banks.AccountEditorTarget
import com.mk.daftar.feature.banks.AccountEditorUiState
import com.mk.daftar.feature.banks.AccountEditorViewModel
import com.mk.daftar.feature.banks.AccountNameError
import com.mk.daftar.feature.banks.BankAction
import com.mk.daftar.feature.banks.BankBusy
import com.mk.daftar.feature.banks.BankConfirmation
import com.mk.daftar.feature.banks.BankPhase
import com.mk.daftar.feature.banks.resources.Res
import com.mk.daftar.feature.banks.resources.account_ack_label
import com.mk.daftar.feature.banks.resources.account_ack_required
import com.mk.daftar.feature.banks.resources.account_add
import com.mk.daftar.feature.banks.resources.account_add_title
import com.mk.daftar.feature.banks.resources.account_balance_helper
import com.mk.daftar.feature.banks.resources.account_balance_invalid
import com.mk.daftar.feature.banks.resources.account_balance_label
import com.mk.daftar.feature.banks.resources.account_balance_required
import com.mk.daftar.feature.banks.resources.account_bank_label
import com.mk.daftar.feature.banks.resources.account_bank_required
import com.mk.daftar.feature.banks.resources.account_currency_label
import com.mk.daftar.feature.banks.resources.account_currency_warning
import com.mk.daftar.feature.banks.resources.account_delete
import com.mk.daftar.feature.banks.resources.account_edit_title
import com.mk.daftar.feature.banks.resources.account_name_label
import com.mk.daftar.feature.banks.resources.account_name_long
import com.mk.daftar.feature.banks.resources.account_name_required
import com.mk.daftar.feature.banks.resources.account_note_label
import com.mk.daftar.feature.banks.resources.account_note_long
import com.mk.daftar.feature.banks.resources.account_save
import com.mk.daftar.feature.banks.resources.banks_failed_message
import com.mk.daftar.feature.banks.resources.banks_failed_title
import com.mk.daftar.feature.banks.resources.banks_missing_message
import com.mk.daftar.feature.banks.resources.banks_missing_title
import com.mk.daftar.feature.banks.resources.banks_reveal_message
import com.mk.daftar.feature.banks.resources.confirm_delete_action
import com.mk.daftar.feature.banks.resources.confirm_delete_cancel
import com.mk.daftar.feature.banks.resources.confirm_delete_message
import com.mk.daftar.feature.banks.resources.confirm_delete_title
import com.mk.daftar.feature.banks.resources.confirm_discard_action
import com.mk.daftar.feature.banks.resources.confirm_discard_message
import com.mk.daftar.feature.banks.resources.confirm_discard_title
import com.mk.daftar.feature.banks.resources.confirm_keep
import com.mk.daftar.feature.banks.resources.conflict_message
import com.mk.daftar.feature.banks.resources.conflict_reload
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The account editor: a new account in a bank, or one account. Sensitive values stay concealed until revealed. */
@Composable
fun AccountEditorScreen(target: AccountEditorTarget, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: AccountEditorViewModel = koinViewModel(
        key = "account-editor-$target",
        parameters = { parametersOf(target) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect -> handleBankEffect(navigator, effect) }
    LeaveGuardRegistration(navigator, viewModel) { viewModel.onLeaveRequested(it) }
    AccountEditorContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/**
 * The account editor as it is drawn. A concealed form shows no field and no keyboard. Save sits in the footer, above the
 * keyboard, because the shell applies the IME inset to the whole screen.
 */
@Composable
fun AccountEditorContent(
    state: AccountEditorUiState,
    onIntent: (AccountEditorIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var choosingBank by remember { mutableStateOf(false) }
    LaunchedEffect(state.concealed) {
        if (state.concealed) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    val editable = state.phase == BankPhase.Ready && !state.concealed && !state.completed && state.busy == null
    val showsForm = state.phase == BankPhase.Ready && !state.concealed && !state.completed
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(
                    if (state.isNew) Res.string.account_add_title else Res.string.account_edit_title,
                ),
                onBack = onBack,
                backLabel = stringResource(DesignRes.string.btn_back),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            ) {
                when (state.phase) {
                    BankPhase.Loading -> repeat(3) { DaftarSkeleton() }

                    BankPhase.Locked, BankPhase.Empty -> Unit

                    BankPhase.Missing -> DaftarResultState(
                        title = stringResource(Res.string.banks_missing_title),
                        message = stringResource(Res.string.banks_missing_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    is BankPhase.Failed -> DaftarResultState(
                        title = stringResource(Res.string.banks_failed_title),
                        message = stringResource(Res.string.banks_failed_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    BankPhase.Ready -> when {
                        state.concealed -> RevealPrompt(
                            onReveal = { onIntent(AccountEditorIntent.RevealForm) },
                            onCancel = onBack,
                        )

                        state.completed -> Unit

                        else -> AccountForm(
                            state = state,
                            editable = editable,
                            onIntent = onIntent,
                            onChooseBank = { choosingBank = true },
                        )
                    }
                }
            }
            if (showsForm) {
                DaftarFooterAction {
                    PrimaryButton(
                        onClick = { onIntent(AccountEditorIntent.Save) },
                        fullWidth = true,
                        enabled = editable && !state.conflict,
                        loading = state.busy == BankBusy.Saving,
                    ) {
                        Text(
                            stringResource(
                                when {
                                    state.busy == BankBusy.Saving -> DesignRes.string.btn_saving
                                    state.isNew -> Res.string.account_add
                                    else -> Res.string.account_save
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
    AccountEditorDialogs(state = state, onIntent = onIntent)
    if (choosingBank) {
        BankPickerDialog(
            options = state.banks,
            selectedId = state.draft.bankId,
            onPick = { id ->
                onIntent(AccountEditorIntent.BankChanged(id))
                choosingBank = false
            },
            onDismiss = { choosingBank = false },
        )
    }
}

@Composable
private fun AccountForm(
    state: AccountEditorUiState,
    editable: Boolean,
    onIntent: (AccountEditorIntent) -> Unit,
    onChooseBank: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        val errors = state.errors
        val reveal = state.attempted
        if (state.conflict) {
            DaftarNotePanel(
                tone = DaftarNoteTone.Warning,
                icon = DaftarIcons.Alert,
                action = {
                    DaftarTextButton(
                        onClick = { onIntent(AccountEditorIntent.RequestReload) },
                        enabled = state.busy == null,
                    ) {
                        Text(stringResource(Res.string.conflict_reload))
                    }
                },
            ) {
                Text(stringResource(Res.string.conflict_message))
            }
        }
        state.failure?.let { failure ->
            WriteFailureNote(
                failure = failure,
                onRetry = {
                    onIntent(
                        if (failure.action == BankAction.Save) {
                            AccountEditorIntent.Save
                        } else {
                            AccountEditorIntent.RequestDelete
                        },
                    )
                },
                onBackToEditing = { onIntent(AccountEditorIntent.DismissFailure) },
            )
        }
        val bankName = state.banks.firstOrNull { it.id == state.draft.bankId }?.name
        DaftarSelectField(
            label = stringResource(Res.string.account_bank_label),
            selectedText = bankName,
            onClick = onChooseBank,
            helper = if (reveal && errors.bank != null) stringResource(bankMessage(errors.bank)) else null,
            invalid = reveal && errors.bank != null,
            enabled = editable && state.banks.isNotEmpty(),
        )
        DaftarTextField(
            label = stringResource(Res.string.account_name_label),
            value = state.draft.name,
            onValueChange = { onIntent(AccountEditorIntent.NameChanged(it)) },
            helper = if (reveal) errors.name?.let { stringResource(nameMessage(it)) } else null,
            invalid = reveal && errors.name != null,
            enabled = editable,
        )
        Text(
            text = stringResource(Res.string.account_currency_label),
            style = DaftarTheme.typography.fieldLabel,
            color = DaftarTheme.colors.muted,
        )
        val currencies = listOf(Currency.EGP, Currency.USD)
        // The selected currency's badge sits beside the choice, as on the account rows.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val glyph = currencyGlyphOf(state.draft.currency)
            DaftarCurrencyBadge(glyph = glyph.text, arabicGlyph = glyph.arabic)
            DaftarSegmentedControl(
                options = currencies.map { it.name },
                selectedIndex = currencies.indexOf(state.draft.currency),
                onSelect = { index -> onIntent(AccountEditorIntent.CurrencyChanged(currencies[index])) },
                enabled = editable,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.currencyChanged) {
            DaftarNotePanel(
                tone = DaftarNoteTone.Warning,
                icon = DaftarIcons.Alert,
            ) {
                Text(stringResource(Res.string.account_currency_warning))
            }
            // The switch's label is for accessibility only; the same text is shown beside it.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.md),
            ) {
                Text(
                    text = stringResource(Res.string.account_ack_label),
                    style = DaftarTheme.typography.body,
                    color = DaftarTheme.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                DaftarSwitch(
                    checked = state.acknowledged,
                    onCheckedChange = { onIntent(AccountEditorIntent.AcknowledgeCurrency(it)) },
                    label = stringResource(Res.string.account_ack_label),
                    enabled = editable,
                )
            }
            if (reveal && errors.acknowledgment) {
                Text(
                    text = stringResource(Res.string.account_ack_required),
                    style = DaftarTheme.typography.fineText,
                    color = DaftarTheme.colors.error,
                )
            }
        }
        DaftarTextField(
            label = stringResource(Res.string.account_balance_label),
            value = state.draft.balanceText,
            onValueChange = { onIntent(AccountEditorIntent.BalanceChanged(it)) },
            kind = DaftarFieldKind.Decimal,
            helper = when {
                reveal && errors.balance != null -> stringResource(balanceMessage(errors.balance))
                else -> stringResource(Res.string.account_balance_helper)
            },
            invalid = reveal && errors.balance != null,
            enabled = editable,
        )
        DaftarTextArea(
            label = stringResource(Res.string.account_note_label),
            value = state.draft.noteText,
            onValueChange = { onIntent(AccountEditorIntent.NoteChanged(it)) },
            helper = if (reveal && errors.note) stringResource(Res.string.account_note_long) else null,
            invalid = reveal && errors.note,
            enabled = editable,
        )
        if (!state.isNew) {
            DaftarTextButton(
                onClick = { onIntent(AccountEditorIntent.RequestDelete) },
                fullWidth = true,
                enabled = editable,
            ) {
                Text(stringResource(Res.string.account_delete))
            }
        }
    }
}

@Composable
private fun AccountEditorDialogs(state: AccountEditorUiState, onIntent: (AccountEditorIntent) -> Unit) {
    when (state.confirmation) {
        BankConfirmation.Delete -> BankConfirmDialog(
            title = stringResource(Res.string.confirm_delete_title),
            message = stringResource(Res.string.confirm_delete_message),
            primaryLabel = stringResource(Res.string.confirm_delete_cancel),
            onPrimary = { onIntent(AccountEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(AccountEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.confirm_delete_action),
            onDestructive = { onIntent(AccountEditorIntent.ConfirmDelete) },
        )

        BankConfirmation.Discard -> BankConfirmDialog(
            title = stringResource(Res.string.confirm_discard_title),
            message = stringResource(Res.string.confirm_discard_message),
            primaryLabel = stringResource(Res.string.confirm_keep),
            onPrimary = { onIntent(AccountEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(AccountEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.confirm_discard_action),
            onDestructive = { onIntent(AccountEditorIntent.ConfirmDiscard) },
        )

        BankConfirmation.Reload -> BankConfirmDialog(
            title = stringResource(Res.string.confirm_discard_title),
            message = stringResource(Res.string.confirm_discard_message),
            primaryLabel = stringResource(Res.string.confirm_keep),
            onPrimary = { onIntent(AccountEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(AccountEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.conflict_reload),
            onDestructive = { onIntent(AccountEditorIntent.ConfirmReload) },
        )

        null -> Unit
    }
}

private fun bankMessage(error: AccountBankError): StringResource = when (error) {
    AccountBankError.Required -> Res.string.account_bank_required
}

private fun nameMessage(error: AccountNameError): StringResource = when (error) {
    AccountNameError.Required -> Res.string.account_name_required
    AccountNameError.TooLong -> Res.string.account_name_long
}

private fun balanceMessage(error: AccountBalanceError): StringResource = when (error) {
    AccountBalanceError.Required -> Res.string.account_balance_required
    AccountBalanceError.Invalid -> Res.string.account_balance_invalid
}
