@file:OptIn(ExperimentalMaterial3Api::class)

package com.mk.daftar.feature.banks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarAmount
import com.mk.daftar.core.designsystem.component.DaftarBankTile
import com.mk.daftar.core.designsystem.component.DaftarFooterAction
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
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
import com.mk.daftar.core.domain.bank.BankDirectory
import com.mk.daftar.core.model.bank.BankDirectoryData
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.navigation.Navigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.shell.DesktopWidths
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.desktopPage
import com.mk.daftar.core.ui.shell.pageScroll
import com.mk.daftar.feature.banks.BankAction
import com.mk.daftar.feature.banks.BankBusy
import com.mk.daftar.feature.banks.BankConfirmation
import com.mk.daftar.feature.banks.BankEditorIntent
import com.mk.daftar.feature.banks.BankEditorTarget
import com.mk.daftar.feature.banks.BankEditorUiState
import com.mk.daftar.feature.banks.BankEditorViewModel
import com.mk.daftar.feature.banks.BankNameError
import com.mk.daftar.feature.banks.BankPhase
import com.mk.daftar.feature.banks.DependentAccount
import com.mk.daftar.feature.banks.PickerBackGuard
import com.mk.daftar.feature.banks.resources.Res
import com.mk.daftar.feature.banks.resources.bank_add_title
import com.mk.daftar.feature.banks.resources.bank_blocked_message
import com.mk.daftar.feature.banks.resources.bank_blocked_title
import com.mk.daftar.feature.banks.resources.bank_change_identity
import com.mk.daftar.feature.banks.resources.bank_delete
import com.mk.daftar.feature.banks.resources.bank_display_name
import com.mk.daftar.feature.banks.resources.bank_edit_title
import com.mk.daftar.feature.banks.resources.bank_form_heading
import com.mk.daftar.feature.banks.resources.bank_form_message
import com.mk.daftar.feature.banks.resources.bank_identity_custom
import com.mk.daftar.feature.banks.resources.bank_name_empty_preview
import com.mk.daftar.feature.banks.resources.bank_name_label
import com.mk.daftar.feature.banks.resources.bank_name_long
import com.mk.daftar.feature.banks.resources.bank_name_preview
import com.mk.daftar.feature.banks.resources.bank_name_required
import com.mk.daftar.feature.banks.resources.bank_no_numbers
import com.mk.daftar.feature.banks.resources.bank_review_accounts
import com.mk.daftar.feature.banks.resources.bank_save
import com.mk.daftar.feature.banks.resources.bank_use_custom
import com.mk.daftar.feature.banks.resources.banks_cancel
import com.mk.daftar.feature.banks.resources.banks_failed_message
import com.mk.daftar.feature.banks.resources.banks_failed_title
import com.mk.daftar.feature.banks.resources.banks_missing_message
import com.mk.daftar.feature.banks.resources.banks_missing_title
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The bank editor: a new bank or the name of one. It leaves only through a saved, deleted or confirmed path. */
@Composable
fun BankEditorScreen(target: BankEditorTarget, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: BankEditorViewModel = koinViewModel(
        key = "bank-editor-$target",
        parameters = { parametersOf(target) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect -> handleBankEffect(navigator, effect) }
    LeaveGuardRegistration(navigator, viewModel) { viewModel.onLeaveRequested(it) }
    BankEditorContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/**
 * Registers an editor with the shell's leave guard for as long as it is composed. A switch to another tab that the user
 * confirms first pops the editor, so the tab opens without it.
 */
@Composable
internal fun LeaveGuardRegistration(
    navigator: Navigator,
    owner: Any,
    onLeaveRequest: (onDecided: (discarded: Boolean) -> Unit) -> Unit,
) {
    val leave by rememberUpdatedState(onLeaveRequest)
    DisposableEffect(owner, navigator) {
        val unregister = navigator.leaveGuard.register(owner) { _, proceed ->
            leave { _ ->
                proceed()
            }
        }
        onDispose { unregister() }
    }
}

/** The bank editor as it is drawn. Save sits in the footer, above the keyboard, because the shell applies the IME inset. */
@Composable
fun BankEditorContent(
    state: BankEditorUiState,
    onIntent: (BankEditorIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editable = state.phase == BankPhase.Ready && !state.completed && state.busy == null
    val showsForm = state.phase == BankPhase.Ready && !state.completed
    val picker = state.picker.takeIf { showsForm }
    PickerBackGuard(enabled = picker != null) { onIntent(BankEditorIntent.ClosePicker) }
    if (picker != null) {
        ShellScreen(modifier = modifier) { BankPickerContent(picker = picker, onIntent = onIntent) }
        BankEditorDialogs(state = state, onIntent = onIntent)
        return
    }
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(if (state.isNew) Res.string.bank_add_title else Res.string.bank_edit_title),
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
                    BankPhase.Loading -> repeat(2) { DaftarSkeleton() }

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

                    BankPhase.Ready -> if (!state.completed) {
                        BankForm(
                            state = state,
                            editable = editable,
                            onIntent = onIntent,
                        )
                    }
                }
            }
            if (showsForm) {
                DaftarFooterAction {
                    PrimaryButton(
                        onClick = { onIntent(BankEditorIntent.Save) },
                        fullWidth = true,
                        enabled = editable && !state.conflict,
                        loading = state.busy == BankBusy.Saving,
                    ) {
                        Text(
                            stringResource(
                                if (state.busy ==
                                    BankBusy.Saving
                                ) {
                                    DesignRes.string.btn_saving
                                } else {
                                    Res.string.bank_save
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
    BankEditorDialogs(state = state, onIntent = onIntent)
}

@Composable
private fun BankForm(state: BankEditorUiState, editable: Boolean, onIntent: (BankEditorIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        if (state.conflict) {
            DaftarNotePanel(
                tone = DaftarNoteTone.Warning,
                icon = DaftarIcons.Alert,
                action = {
                    DaftarTextButton(
                        onClick = { onIntent(BankEditorIntent.RequestReload) },
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
                            BankEditorIntent.Save
                        } else {
                            BankEditorIntent.RequestDelete
                        },
                    )
                },
                onBackToEditing = { onIntent(BankEditorIntent.DismissFailure) },
            )
        }
        if (state.isNew) {
            Text(
                text = stringResource(Res.string.bank_form_heading),
                style = DaftarTheme.typography.screenHeading,
                color = DaftarTheme.colors.ink,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.bank_form_message),
                style = DaftarTheme.typography.fineText,
                color = DaftarTheme.colors.muted,
            )
        }
        BankIdentityRow(state = state, editable = editable, onIntent = onIntent)
        val error = state.nameError.takeIf { state.attempted }
        DaftarTextField(
            label = stringResource(
                if (state.institutionId != null) Res.string.bank_display_name else Res.string.bank_name_label,
            ),
            value = state.draft.nameText,
            onValueChange = { onIntent(BankEditorIntent.NameChanged(it)) },
            helper = error?.let { stringResource(bankNameMessage(it)) },
            invalid = error != null,
            enabled = editable,
        )
        val preview = state.draft.nameText.trim()
        DaftarLedgerRow(
            title = preview.ifEmpty { stringResource(Res.string.bank_name_empty_preview) },
            subtitle = stringResource(Res.string.bank_name_preview),
            leading = { DaftarBankTile(mark = bankMarkFor(state.institutionId)) },
        )
        DaftarNotePanel(tone = DaftarNoteTone.Context) {
            Text(stringResource(Res.string.bank_no_numbers))
        }
        if (!state.isNew) {
            DaftarTextButton(
                onClick = { onIntent(BankEditorIntent.RequestDelete) },
                fullWidth = true,
                enabled = editable,
            ) {
                Text(stringResource(Res.string.bank_delete))
            }
        }
    }
}

/**
 * Which bank this is: its tile and catalog name, or Custom bank. Changing the identity reopens the directory, and a
 * catalog bank can become a custom one. The display name below is edited separately and never changes the identity.
 */
@Composable
private fun BankIdentityRow(state: BankEditorUiState, editable: Boolean, onIntent: (BankEditorIntent) -> Unit) {
    val language = appLanguage()
    val institution = state.institutionId?.let { id ->
        BankDirectoryData.institutions.firstOrNull { it.id.value == id }
    }
    DaftarLedgerRow(
        title = institution?.let { BankDirectory.localizedName(it, language) }
            ?: stringResource(Res.string.bank_identity_custom),
        leading = { DaftarBankTile(mark = bankMarkFor(state.institutionId)) },
    )
    DaftarTextButton(
        onClick = { onIntent(BankEditorIntent.OpenPicker) },
        fullWidth = true,
        enabled = editable,
    ) {
        Text(stringResource(Res.string.bank_change_identity))
    }
    if (state.institutionId != null) {
        DaftarTextButton(
            onClick = { onIntent(BankEditorIntent.PickOther) },
            fullWidth = true,
            enabled = editable,
        ) {
            Text(stringResource(Res.string.bank_use_custom))
        }
    }
}

@Composable
private fun BankEditorDialogs(state: BankEditorUiState, onIntent: (BankEditorIntent) -> Unit) {
    if (state.blocked) {
        BankBlockedDialog(
            bankName = state.storedName,
            dependents = state.dependents,
            hidden = state.hidden,
            onReview = { onIntent(BankEditorIntent.ReviewAccounts) },
            onCancel = { onIntent(BankEditorIntent.DismissBlocked) },
        )
    }
    when (state.confirmation) {
        BankConfirmation.Delete -> BankConfirmDialog(
            title = stringResource(Res.string.confirm_delete_title),
            message = stringResource(Res.string.confirm_delete_message),
            primaryLabel = stringResource(Res.string.confirm_delete_cancel),
            onPrimary = { onIntent(BankEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(BankEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.confirm_delete_action),
            onDestructive = { onIntent(BankEditorIntent.ConfirmDelete) },
        )

        BankConfirmation.Discard -> BankConfirmDialog(
            title = stringResource(Res.string.confirm_discard_title),
            message = stringResource(Res.string.confirm_discard_message),
            primaryLabel = stringResource(Res.string.confirm_keep),
            onPrimary = { onIntent(BankEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(BankEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.confirm_discard_action),
            onDestructive = { onIntent(BankEditorIntent.ConfirmDiscard) },
        )

        BankConfirmation.Reload -> BankConfirmDialog(
            title = stringResource(Res.string.confirm_discard_title),
            message = stringResource(Res.string.confirm_discard_message),
            primaryLabel = stringResource(Res.string.confirm_keep),
            onPrimary = { onIntent(BankEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(BankEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.conflict_reload),
            onDestructive = { onIntent(BankEditorIntent.ConfirmReload) },
        )

        null -> Unit
    }
}

/**
 * Deletion is refused while accounts still hold the bank. The refusal names the bank and lists its accounts, with their
 * balances hidden while values are concealed.
 */
@Composable
private fun BankBlockedDialog(
    bankName: String,
    dependents: List<DependentAccount>,
    hidden: Boolean,
    onReview: () -> Unit,
    onCancel: () -> Unit,
) {
    val hiddenText = stringResource(DesignRes.string.balance_hidden)
    BasicAlertDialog(onDismissRequest = onCancel) {
        Surface(shape = DaftarTheme.shapes.surface, color = DaftarTheme.colors.surface) {
            Column(
                modifier = Modifier.padding(DaftarSpacing.md).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.bank_blocked_title, "⁨$bankName⁩"),
                    style = DaftarTheme.typography.sectionHeading,
                    color = DaftarTheme.colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = pluralStringResource(
                        Res.plurals.bank_blocked_message,
                        dependents.size,
                        dependents.size,
                    ),
                    style = DaftarTheme.typography.supportingBody,
                    color = DaftarTheme.colors.muted,
                )
                dependents.forEach { account ->
                    DaftarLedgerRow(
                        title = account.name,
                        subtitle = account.currency.name,
                        icon = DaftarIcons.Wallet,
                        value = {
                            DaftarAmount(
                                amount = if (hidden) null else account.balanceText,
                                unit = account.currency.name,
                                masked = hidden,
                                maskedDescription = hiddenText,
                                style = DaftarTheme.typography.ledgerValue,
                                color = DaftarTheme.colors.ink,
                            )
                        },
                    )
                }
                PrimaryButton(onClick = onReview, fullWidth = true) {
                    Text(stringResource(Res.string.bank_review_accounts))
                }
                DaftarTextButton(onClick = onCancel, fullWidth = true) {
                    Text(stringResource(Res.string.banks_cancel))
                }
            }
        }
    }
}

internal fun bankNameMessage(error: BankNameError): org.jetbrains.compose.resources.StringResource = when (error) {
    BankNameError.Required -> Res.string.bank_name_required
    BankNameError.TooLong -> Res.string.bank_name_long
}
