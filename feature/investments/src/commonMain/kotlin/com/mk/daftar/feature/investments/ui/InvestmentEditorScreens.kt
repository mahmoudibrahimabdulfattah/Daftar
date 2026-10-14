package com.mk.daftar.feature.investments.ui

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarDateField
import com.mk.daftar.core.designsystem.component.DaftarFieldKind
import com.mk.daftar.core.designsystem.component.DaftarFooterAction
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarTextArea
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTextField
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.resources.btn_saving
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.bank.BankDirectory
import com.mk.daftar.core.domain.broker.BrokerDirectory
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.model.bank.BankInstitution
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.feature.investments.InvestmentAction
import com.mk.daftar.feature.investments.InvestmentBusy
import com.mk.daftar.feature.investments.InvestmentConfirmation
import com.mk.daftar.feature.investments.InvestmentDraft
import com.mk.daftar.feature.investments.InvestmentEditorIntent
import com.mk.daftar.feature.investments.InvestmentEditorTarget
import com.mk.daftar.feature.investments.InvestmentEditorUiState
import com.mk.daftar.feature.investments.InvestmentEditorViewModel
import com.mk.daftar.feature.investments.InvestmentEffect
import com.mk.daftar.feature.investments.InvestmentPhase
import com.mk.daftar.feature.investments.ValueDateChoice
import com.mk.daftar.feature.investments.resources.Res
import com.mk.daftar.feature.investments.resources.investments_add_title
import com.mk.daftar.feature.investments.resources.investments_amount
import com.mk.daftar.feature.investments.resources.investments_amount_helper
import com.mk.daftar.feature.investments.resources.investments_as_of
import com.mk.daftar.feature.investments.resources.investments_as_of_now
import com.mk.daftar.feature.investments.resources.investments_cairo_time
import com.mk.daftar.feature.investments.resources.investments_choose_datetime
import com.mk.daftar.feature.investments.resources.investments_conflict
import com.mk.daftar.feature.investments.resources.investments_delete_action
import com.mk.daftar.feature.investments.resources.investments_delete_keep
import com.mk.daftar.feature.investments.resources.investments_delete_message
import com.mk.daftar.feature.investments.resources.investments_delete_title
import com.mk.daftar.feature.investments.resources.investments_discard_action
import com.mk.daftar.feature.investments.resources.investments_discard_message
import com.mk.daftar.feature.investments.resources.investments_discard_title
import com.mk.daftar.feature.investments.resources.investments_edit_title
import com.mk.daftar.feature.investments.resources.investments_error_amount
import com.mk.daftar.feature.investments.resources.investments_error_date
import com.mk.daftar.feature.investments.resources.investments_error_name
import com.mk.daftar.feature.investments.resources.investments_error_note
import com.mk.daftar.feature.investments.resources.investments_keep_editing
import com.mk.daftar.feature.investments.resources.investments_load_failed
import com.mk.daftar.feature.investments.resources.investments_missing_message
import com.mk.daftar.feature.investments.resources.investments_missing_title
import com.mk.daftar.feature.investments.resources.investments_name
import com.mk.daftar.feature.investments.resources.investments_no_duplicate
import com.mk.daftar.feature.investments.resources.investments_note
import com.mk.daftar.feature.investments.resources.investments_provider_change
import com.mk.daftar.feature.investments.resources.investments_provider_other
import com.mk.daftar.feature.investments.resources.investments_provider_title
import com.mk.daftar.feature.investments.resources.investments_reload_action
import com.mk.daftar.feature.investments.resources.investments_save
import com.mk.daftar.feature.investments.resources.investments_set_to_now
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The editor for a new account or one recorded account. It leaves only through a confirmed path. */
@Composable
fun InvestmentEditorScreen(target: InvestmentEditorTarget, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: InvestmentEditorViewModel = koinViewModel(
        key = "investment-editor-$target",
        parameters = { parametersOf(target) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect == InvestmentEffect.Completed) navigator.goBack()
    }
    // The shell asks before it leaves: a back action, a gesture or a tab change. Only unsaved edits make it ask.
    DisposableEffect(viewModel, navigator) {
        val unregister = navigator.leaveGuard.register(viewModel) { _, proceed ->
            viewModel.onLeaveRequested { _ -> proceed() }
        }
        onDispose { unregister() }
    }
    InvestmentEditorContent(
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
fun InvestmentEditorContent(
    state: InvestmentEditorUiState,
    onIntent: (InvestmentEditorIntent) -> Unit,
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
    val editable = state.phase == InvestmentPhase.Ready && !state.concealed && !state.completed &&
        state.busy == null && !state.picking
    val showsForm = state.phase == InvestmentPhase.Ready && !state.concealed && !state.completed && !state.picking
    val pickerBack: () -> Unit = if (state.isNew) onBack else ({ onIntent(InvestmentEditorIntent.ClosePicker) })
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(
                    when {
                        state.picking -> Res.string.investments_provider_title
                        state.isNew -> Res.string.investments_add_title
                        else -> Res.string.investments_edit_title
                    },
                ),
                onBack = if (state.picking) pickerBack else onBack,
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
                    InvestmentPhase.Loading -> repeat(3) { DaftarSkeleton() }

                    InvestmentPhase.Locked, InvestmentPhase.Empty -> Unit

                    InvestmentPhase.Missing -> DaftarResultState(
                        title = stringResource(Res.string.investments_missing_title),
                        message = stringResource(Res.string.investments_missing_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    is InvestmentPhase.Failed -> DaftarResultState(
                        title = stringResource(Res.string.investments_load_failed),
                        message = stringResource(Res.string.investments_load_failed),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    InvestmentPhase.Ready -> when {
                        state.concealed -> RevealPrompt(
                            onReveal = { onIntent(InvestmentEditorIntent.RevealForm) },
                            onCancel = onBack,
                        )

                        state.completed -> Unit

                        state.picking -> BrokerPicker(onIntent = onIntent)

                        else -> InvestmentForm(state = state, editable = editable, onIntent = onIntent)
                    }
                }
            }
            if (showsForm) {
                DaftarFooterAction {
                    PrimaryButton(
                        onClick = { onIntent(InvestmentEditorIntent.Save) },
                        fullWidth = true,
                        enabled = editable && !state.conflict,
                        loading = state.busy == InvestmentBusy.Saving,
                    ) {
                        Text(
                            stringResource(
                                if (state.busy ==
                                    InvestmentBusy.Saving
                                ) {
                                    DesignRes.string.btn_saving
                                } else {
                                    Res.string.investments_save
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
        InvestmentConfirmation.Delete -> InvestmentConfirmDialog(
            title = stringResource(Res.string.investments_delete_title),
            message = stringResource(Res.string.investments_delete_message),
            primaryLabel = stringResource(Res.string.investments_delete_keep),
            onPrimary = { onIntent(InvestmentEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(InvestmentEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.investments_delete_action),
            onDestructive = { if (idle) onIntent(InvestmentEditorIntent.ConfirmDelete) },
        )

        InvestmentConfirmation.Discard -> InvestmentConfirmDialog(
            title = stringResource(Res.string.investments_discard_title),
            message = stringResource(Res.string.investments_discard_message),
            primaryLabel = stringResource(Res.string.investments_keep_editing),
            onPrimary = { onIntent(InvestmentEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(InvestmentEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.investments_discard_action),
            onDestructive = { if (idle) onIntent(InvestmentEditorIntent.ConfirmDiscard) },
        )

        InvestmentConfirmation.Reload -> InvestmentConfirmDialog(
            title = stringResource(Res.string.investments_discard_title),
            message = stringResource(Res.string.investments_discard_message),
            primaryLabel = stringResource(Res.string.investments_keep_editing),
            onPrimary = { onIntent(InvestmentEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(InvestmentEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.investments_reload_action),
            onDestructive = { if (idle) onIntent(InvestmentEditorIntent.ConfirmReload) },
        )

        null -> Unit
    }
}

/** The broker choice: the two offline options. Choosing one only fills the form; nothing is saved here. */
@Composable
private fun BrokerPicker(onIntent: (InvestmentEditorIntent) -> Unit) {
    val arabic = LocalLayoutDirection.current == LayoutDirection.Rtl
    val language = if (arabic) AppLanguage.AR else AppLanguage.EN
    val brokers = BrokerDirectory.search("", language)
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        brokers.forEach { institution ->
            BrokerChoice(institution = institution, language = language, onIntent = onIntent)
        }
        OutlineButton(
            onClick = { onIntent(InvestmentEditorIntent.ProviderPicked(providerId = null, suggestedName = null)) },
            fullWidth = true,
        ) {
            Text(stringResource(Res.string.investments_provider_other))
        }
    }
}

@Composable
private fun BrokerChoice(
    institution: BankInstitution,
    language: AppLanguage,
    onIntent: (InvestmentEditorIntent) -> Unit,
) {
    val name = BankDirectory.localizedName(institution, language)
    val otherName = BankDirectory.localizedName(
        institution,
        if (language == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR,
    )
    DaftarLedgerRow(
        title = name,
        subtitle = otherName,
        leading = { BrokerTile(institution.id.value) },
        onClick = {
            onIntent(InvestmentEditorIntent.ProviderPicked(providerId = institution.id, suggestedName = name))
        },
    )
}

/** The form: broker, display name, value, value date, note and the secondary delete. */
@Composable
private fun InvestmentForm(
    state: InvestmentEditorUiState,
    editable: Boolean,
    onIntent: (InvestmentEditorIntent) -> Unit,
) {
    val colors = DaftarTheme.colors
    val errors = state.errors
    val reveal = state.attempted
    var pickingDate by remember { mutableStateOf(false) }
    val now = Clock.System.now()
    val draft: InvestmentDraft = state.draft
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        if (state.conflict) {
            DaftarNotePanel(
                tone = DaftarNoteTone.Warning,
                icon = DaftarIcons.Alert,
                action = {
                    DaftarTextButton(
                        onClick = { onIntent(InvestmentEditorIntent.RequestReload) },
                        enabled = state.busy == null,
                    ) {
                        Text(stringResource(Res.string.investments_reload_action))
                    }
                },
            ) {
                Text(stringResource(Res.string.investments_conflict))
            }
        }
        state.failure?.let { failure ->
            WriteFailureNote(
                failure = failure,
                onRetry = {
                    if (failure.action == InvestmentAction.Save) {
                        onIntent(InvestmentEditorIntent.Save)
                    } else {
                        onIntent(InvestmentEditorIntent.RequestDelete)
                    }
                },
                onBackToEditing = { onIntent(InvestmentEditorIntent.DismissFailure) },
            )
        }
        DaftarLedgerRow(
            title = brokerNameText(draft.providerId?.value),
            subtitle = stringResource(Res.string.investments_provider_change),
            leading = { BrokerTile(draft.providerId?.value) },
            onClick = if (editable) ({ onIntent(InvestmentEditorIntent.ReopenPicker) }) else null,
        )
        DaftarTextField(
            label = stringResource(Res.string.investments_name),
            value = draft.name,
            onValueChange = { onIntent(InvestmentEditorIntent.NameChanged(it)) },
            helper = if (reveal && errors.name != null) stringResource(Res.string.investments_error_name) else null,
            invalid = reveal && errors.name != null,
            enabled = editable,
        )
        DaftarTextField(
            label = stringResource(Res.string.investments_amount),
            value = draft.amountText,
            onValueChange = { onIntent(InvestmentEditorIntent.AmountChanged(it)) },
            kind = DaftarFieldKind.Decimal,
            helper = stringResource(
                if (reveal &&
                    errors.amount
                ) {
                    Res.string.investments_error_amount
                } else {
                    Res.string.investments_amount_helper
                },
            ),
            invalid = reveal && errors.amount,
            enabled = editable,
        )
        DaftarNotePanel(tone = DaftarNoteTone.Context) {
            Text(text = stringResource(Res.string.investments_no_duplicate), style = DaftarTheme.typography.fineText)
        }
        Text(
            text = "${stringResource(
                Res.string.investments_as_of,
            )} · ${stringResource(Res.string.investments_cairo_time)}",
            style = DaftarTheme.typography.fineText,
            color = colors.muted,
        )
        DaftarDateField(
            label = stringResource(Res.string.investments_as_of),
            selectedText = when (val choice = draft.asOf) {
                ValueDateChoice.Now -> stringResource(Res.string.investments_as_of_now)
                is ValueDateChoice.At -> cairoDateText(choice.instant)
            },
            onClick = { pickingDate = true },
            helper = if (state.dateInvalid) stringResource(Res.string.investments_error_date) else null,
            invalid = state.dateInvalid,
            enabled = editable,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
            DaftarTextButton(
                onClick = { onIntent(InvestmentEditorIntent.SetToNow) },
                enabled = editable,
            ) {
                Text(stringResource(Res.string.investments_set_to_now))
            }
            DaftarTextButton(
                onClick = { pickingDate = true },
                enabled = editable,
            ) {
                Text(stringResource(Res.string.investments_choose_datetime))
            }
        }
        DaftarTextArea(
            label = stringResource(Res.string.investments_note),
            value = draft.noteText,
            onValueChange = { onIntent(InvestmentEditorIntent.NoteChanged(it)) },
            helper = if (reveal && errors.note) stringResource(Res.string.investments_error_note) else null,
            invalid = reveal && errors.note,
            enabled = editable,
        )
        if (!state.isNew) {
            DaftarTextButton(
                onClick = { onIntent(InvestmentEditorIntent.RequestDelete) },
                fullWidth = true,
                enabled = editable,
            ) {
                Text(stringResource(Res.string.investments_delete_action))
            }
        }
    }
    if (pickingDate) {
        val initial = (draft.asOf as? ValueDateChoice.At)?.instant ?: now
        CairoDateTimePicker(
            initial = initial,
            now = now,
            onPick = { picked ->
                pickingDate = false
                onIntent(InvestmentEditorIntent.DateTimeChosen(picked))
            },
            onDismiss = { pickingDate = false },
        )
    }
}
