package com.mk.daftar.feature.gold.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarFieldKind
import com.mk.daftar.core.designsystem.component.DaftarFooterAction
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
import com.mk.daftar.core.designsystem.component.DaftarSegmentedControl
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
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
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.DesktopWidths
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.desktopPage
import com.mk.daftar.feature.gold.GoldAction
import com.mk.daftar.feature.gold.GoldBusy
import com.mk.daftar.feature.gold.GoldEffect
import com.mk.daftar.feature.gold.GoldPhase
import com.mk.daftar.feature.gold.editor.GoldConfirmation
import com.mk.daftar.feature.gold.editor.GoldEditorIntent
import com.mk.daftar.feature.gold.editor.GoldEditorTarget
import com.mk.daftar.feature.gold.editor.GoldEditorUiState
import com.mk.daftar.feature.gold.editor.GoldEditorViewModel
import com.mk.daftar.feature.gold.editor.GoldNoteError
import com.mk.daftar.feature.gold.editor.GoldWeightError
import com.mk.daftar.feature.gold.resources.Res
import com.mk.daftar.feature.gold.resources.gold_add_holding
import com.mk.daftar.feature.gold.resources.gold_add_title
import com.mk.daftar.feature.gold.resources.gold_back_to_editing
import com.mk.daftar.feature.gold.resources.gold_bars
import com.mk.daftar.feature.gold.resources.gold_cancel
import com.mk.daftar.feature.gold.resources.gold_confirm_delete_action
import com.mk.daftar.feature.gold.resources.gold_confirm_delete_cancel
import com.mk.daftar.feature.gold.resources.gold_confirm_delete_message
import com.mk.daftar.feature.gold.resources.gold_confirm_delete_title
import com.mk.daftar.feature.gold.resources.gold_confirm_discard_action
import com.mk.daftar.feature.gold.resources.gold_confirm_discard_message
import com.mk.daftar.feature.gold.resources.gold_confirm_discard_title
import com.mk.daftar.feature.gold.resources.gold_confirm_keep
import com.mk.daftar.feature.gold.resources.gold_conflict_message
import com.mk.daftar.feature.gold.resources.gold_delete_holding
import com.mk.daftar.feature.gold.resources.gold_edit_bar_title
import com.mk.daftar.feature.gold.resources.gold_edit_title
import com.mk.daftar.feature.gold.resources.gold_fees_notice
import com.mk.daftar.feature.gold.resources.gold_form_field
import com.mk.daftar.feature.gold.resources.gold_intro
import com.mk.daftar.feature.gold.resources.gold_jewelry
import com.mk.daftar.feature.gold.resources.gold_karat_field
import com.mk.daftar.feature.gold.resources.gold_karat_label
import com.mk.daftar.feature.gold.resources.gold_load_failed_message
import com.mk.daftar.feature.gold.resources.gold_load_failed_title
import com.mk.daftar.feature.gold.resources.gold_missing_message
import com.mk.daftar.feature.gold.resources.gold_missing_title
import com.mk.daftar.feature.gold.resources.gold_new_title
import com.mk.daftar.feature.gold.resources.gold_note
import com.mk.daftar.feature.gold.resources.gold_note_long
import com.mk.daftar.feature.gold.resources.gold_preview_needs_weight
import com.mk.daftar.feature.gold.resources.gold_preview_quote
import com.mk.daftar.feature.gold.resources.gold_preview_unavailable
import com.mk.daftar.feature.gold.resources.gold_preview_value
import com.mk.daftar.feature.gold.resources.gold_reload
import com.mk.daftar.feature.gold.resources.gold_save_changes
import com.mk.daftar.feature.gold.resources.gold_weight
import com.mk.daftar.feature.gold.resources.gold_weight_helper
import com.mk.daftar.feature.gold.resources.gold_weight_invalid
import com.mk.daftar.feature.gold.resources.p25_gold_hint
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The gold editor, opened for a new holding or for one recorded holding by its ID. It leaves only through a confirmed path. */
@Composable
fun GoldEditorScreen(target: GoldEditorTarget, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: GoldEditorViewModel = koinViewModel(
        key = "gold-editor-$target",
        parameters = { parametersOf(target) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect == GoldEffect.Completed) navigator.goBack()
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
    GoldEditorContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/**
 * The gold editor as it is drawn. A concealed form shows no field and no keyboard. Save sits in the footer, above the
 * keyboard, because the shell applies the IME inset to the whole screen. Confirmation callbacks are ignored while a
 * write runs, because the confirmation component has no busy state.
 */
@Composable
fun GoldEditorContent(
    state: GoldEditorUiState,
    onIntent: (GoldEditorIntent) -> Unit,
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
    val editable = state.phase == GoldPhase.Ready && !state.concealed && !state.completed && state.busy == null
    val showsForm = state.phase == GoldPhase.Ready && !state.concealed && !state.completed
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(if (state.isNew) Res.string.gold_add_title else Res.string.gold_edit_bar_title),
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
                    GoldPhase.Loading -> repeat(3) { DaftarSkeleton() }

                    GoldPhase.Locked, GoldPhase.Empty -> Unit

                    GoldPhase.Missing -> DaftarResultState(
                        title = stringResource(Res.string.gold_missing_title),
                        message = stringResource(Res.string.gold_missing_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    is GoldPhase.Failed -> DaftarResultState(
                        title = stringResource(Res.string.gold_load_failed_title),
                        message = stringResource(Res.string.gold_load_failed_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    GoldPhase.Ready -> when {
                        state.concealed -> RevealPrompt(
                            onReveal = { onIntent(GoldEditorIntent.RevealForm) },
                            onCancel = onBack,
                        )

                        state.completed -> Unit

                        else -> GoldEditorForm(
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
                        onClick = { onIntent(GoldEditorIntent.Save) },
                        fullWidth = true,
                        enabled = editable && !state.conflict,
                        loading = state.busy == GoldBusy.Saving,
                    ) {
                        Text(
                            stringResource(
                                when {
                                    state.busy == GoldBusy.Saving -> DesignRes.string.btn_saving
                                    state.isNew -> Res.string.gold_add_holding
                                    else -> Res.string.gold_save_changes
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
    when (state.confirmation) {
        GoldConfirmation.Delete -> GoldConfirmDialog(
            title = stringResource(Res.string.gold_confirm_delete_title),
            message = stringResource(Res.string.gold_confirm_delete_message),
            primaryLabel = stringResource(Res.string.gold_confirm_delete_cancel),
            onPrimary = { onIntent(GoldEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(GoldEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.gold_confirm_delete_action),
            onDestructive = { onIntent(GoldEditorIntent.ConfirmDelete) },
        )

        GoldConfirmation.Discard -> GoldConfirmDialog(
            title = stringResource(Res.string.gold_confirm_discard_title),
            message = stringResource(Res.string.gold_confirm_discard_message),
            primaryLabel = stringResource(Res.string.gold_confirm_keep),
            onPrimary = { onIntent(GoldEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(GoldEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.gold_confirm_discard_action),
            onDestructive = { onIntent(GoldEditorIntent.ConfirmDiscard) },
        )

        GoldConfirmation.Reload -> GoldConfirmDialog(
            title = stringResource(Res.string.gold_confirm_discard_title),
            message = stringResource(Res.string.gold_confirm_discard_message),
            primaryLabel = stringResource(Res.string.gold_confirm_keep),
            onPrimary = { onIntent(GoldEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(GoldEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.gold_reload),
            onDestructive = { onIntent(GoldEditorIntent.ConfirmReload) },
        )

        null -> Unit
    }
}

@Composable
private fun GoldEditorForm(state: GoldEditorUiState, editable: Boolean, onIntent: (GoldEditorIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        val errors = state.errors
        val reveal = state.attempted
        val colors = DaftarTheme.colors
        val typography = DaftarTheme.typography
        if (state.conflict) {
            DaftarNotePanel(
                tone = DaftarNoteTone.Warning,
                icon = DaftarIcons.Alert,
                action = {
                    DaftarTextButton(
                        onClick = { onIntent(GoldEditorIntent.RequestReload) },
                        enabled = state.busy == null,
                    ) {
                        Text(stringResource(Res.string.gold_reload))
                    }
                },
            ) {
                Text(stringResource(Res.string.gold_conflict_message))
            }
        }
        state.failure?.let { failure ->
            GoldFailureNote(
                failure = failure,
                onRetry = {
                    if (failure.action == GoldAction.Save) {
                        onIntent(GoldEditorIntent.Save)
                    } else {
                        onIntent(GoldEditorIntent.RequestDelete)
                    }
                },
                onBackToEditing = { onIntent(GoldEditorIntent.DismissFailure) },
            )
        }
        Text(
            text = stringResource(if (state.isNew) Res.string.gold_new_title else Res.string.gold_edit_title),
            style = typography.screenHeading,
            color = colors.ink,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        if (state.goldHint) {
            Text(
                text = stringResource(Res.string.p25_gold_hint),
                style = typography.supportingBody,
                color = colors.muted,
            )
        }
        Text(text = stringResource(Res.string.gold_intro), style = typography.fineText, color = colors.muted)
        Text(text = stringResource(Res.string.gold_karat_field), style = typography.fineText, color = colors.muted)
        val karats = GoldKarat.entries
        DaftarSegmentedControl(
            options = karats.map { stringResource(Res.string.gold_karat_label, it.carats) },
            selectedIndex = karats.indexOf(state.draft.karat),
            onSelect = { onIntent(GoldEditorIntent.KaratChanged(karats[it])) },
            enabled = editable,
        )
        Text(text = stringResource(Res.string.gold_form_field), style = typography.fineText, color = colors.muted)
        DaftarSegmentedControl(
            options = listOf(stringResource(Res.string.gold_bars), stringResource(Res.string.gold_jewelry)),
            selectedIndex = if (state.draft.form == GoldForm.BAR) 0 else 1,
            onSelect = { onIntent(GoldEditorIntent.FormChanged(if (it == 0) GoldForm.BAR else GoldForm.JEWELRY)) },
            enabled = editable,
        )
        val weightInvalid = reveal && errors.weight == GoldWeightError.Invalid
        DaftarTextField(
            label = stringResource(Res.string.gold_weight),
            value = state.draft.weightText,
            onValueChange = { onIntent(GoldEditorIntent.WeightChanged(it)) },
            kind = DaftarFieldKind.Decimal,
            helper = stringResource(
                if (weightInvalid) Res.string.gold_weight_invalid else Res.string.gold_weight_helper,
            ),
            invalid = weightInvalid,
            enabled = editable,
        )
        DaftarTextArea(
            label = stringResource(Res.string.gold_note),
            value = state.draft.noteText,
            onValueChange = { onIntent(GoldEditorIntent.NoteChanged(it)) },
            helper = if (reveal &&
                errors.note == GoldNoteError.TooLong
            ) {
                stringResource(Res.string.gold_note_long)
            } else {
                null
            },
            invalid = reveal && errors.note == GoldNoteError.TooLong,
            enabled = editable,
        )
        PreviewNote(state = state)
        if (!state.isNew) {
            DaftarTextButton(
                onClick = { onIntent(GoldEditorIntent.RequestDelete) },
                fullWidth = true,
                enabled = editable,
            ) {
                Text(stringResource(Res.string.gold_delete_holding))
            }
        }
    }
}

/**
 * The value of the holding at its karat's merchant sell price. With no sell quote the preview is unavailable, and saving
 * stays open. The valuation excludes making charges and fees, and the note says so.
 */
@Composable
private fun PreviewNote(state: GoldEditorUiState) {
    val preview = state.preview
    val karat = stringResource(Res.string.gold_karat_label, state.draft.karat.carats)
    DaftarNotePanel(tone = DaftarNoteTone.Context) {
        val quote = preview.quote
        if (quote == null) {
            Text(stringResource(Res.string.gold_preview_unavailable, karat))
        } else {
            Text(stringResource(Res.string.gold_preview_quote, karat, quote.value.moneyText()))
            val egp = preview.egp
            Text(
                text = if (egp == null) {
                    stringResource(Res.string.gold_preview_needs_weight)
                } else {
                    stringResource(Res.string.gold_preview_value, egp.moneyText())
                },
            )
        }
        Text(stringResource(Res.string.gold_fees_notice))
    }
}
