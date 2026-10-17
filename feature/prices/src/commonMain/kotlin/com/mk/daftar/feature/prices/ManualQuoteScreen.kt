package com.mk.daftar.feature.prices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarConfirmationContent
import com.mk.daftar.core.designsystem.component.DaftarFieldKind
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarSelectField
import com.mk.daftar.core.designsystem.component.DaftarTextArea
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTextField
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.navigation.ManualQuoteRoute
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.pageScroll
import com.mk.daftar.feature.prices.resources.Res
import com.mk.daftar.feature.prices.resources.manual_choose
import com.mk.daftar.feature.prices.resources.manual_conflict
import com.mk.daftar.feature.prices.resources.manual_delete
import com.mk.daftar.feature.prices.resources.manual_delete_confirm
import com.mk.daftar.feature.prices.resources.manual_delete_message
import com.mk.daftar.feature.prices.resources.manual_delete_title
import com.mk.daftar.feature.prices.resources.manual_discard
import com.mk.daftar.feature.prices.resources.manual_discard_message
import com.mk.daftar.feature.prices.resources.manual_discard_title
import com.mk.daftar.feature.prices.resources.manual_error_empty
import com.mk.daftar.feature.prices.resources.manual_error_grouping
import com.mk.daftar.feature.prices.resources.manual_error_not_number
import com.mk.daftar.feature.prices.resources.manual_error_not_positive
import com.mk.daftar.feature.prices.resources.manual_error_note
import com.mk.daftar.feature.prices.resources.manual_error_precision
import com.mk.daftar.feature.prices.resources.manual_error_range
import com.mk.daftar.feature.prices.resources.manual_failed
import com.mk.daftar.feature.prices.resources.manual_intro
import com.mk.daftar.feature.prices.resources.manual_keep
import com.mk.daftar.feature.prices.resources.manual_keep_editing
import com.mk.daftar.feature.prices.resources.manual_key_18
import com.mk.daftar.feature.prices.resources.manual_key_21
import com.mk.daftar.feature.prices.resources.manual_key_24
import com.mk.daftar.feature.prices.resources.manual_key_usd
import com.mk.daftar.feature.prices.resources.manual_note
import com.mk.daftar.feature.prices.resources.manual_quote
import com.mk.daftar.feature.prices.resources.manual_reload
import com.mk.daftar.feature.prices.resources.manual_save
import com.mk.daftar.feature.prices.resources.manual_saving
import com.mk.daftar.feature.prices.resources.manual_source_none
import com.mk.daftar.feature.prices.resources.manual_source_price
import com.mk.daftar.feature.prices.resources.manual_timestamp
import com.mk.daftar.feature.prices.resources.manual_title
import com.mk.daftar.feature.prices.resources.manual_unknown
import com.mk.daftar.feature.prices.resources.manual_unpin
import com.mk.daftar.feature.prices.resources.manual_unpinned_note
import com.mk.daftar.feature.prices.resources.manual_value
import com.mk.daftar.feature.prices.resources.manual_value_helper
import com.mk.daftar.feature.prices.resources.prices_load_failed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The manual editor screen: the view model's state, with the navigator returning when the editor closes. */
@Composable
fun ManualQuoteScreen(route: ManualQuoteRoute, modifier: Modifier = Modifier) {
    val viewModel: ManualQuoteViewModel = koinViewModel(parameters = { parametersOf(route) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ManualQuoteEffect.Close -> navigator.goBack()
        }
    }
    ManualQuoteContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/**
 * The manual override editor. It renders [state] only. The screen's one IME inset owner is the scrolling column, so
 * the focused field stays visible without a second padding. System Back asks before a dirty draft is dropped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualQuoteContent(
    state: ManualQuoteUiState,
    onIntent: (ManualQuoteIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    DraftBackGuard(enabled = state.dirty && !state.closed) { onIntent(ManualQuoteIntent.RequestBack) }
    ShellScreen(modifier = modifier) {
        if (state.closed) {
            Box(modifier = Modifier.fillMaxSize())
            return@ShellScreen
        }
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(Res.string.manual_title),
                onBack = { onIntent(ManualQuoteIntent.RequestBack) },
                backLabel = stringResource(DesignRes.string.btn_back),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .pageScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md)
                    .padding(bottom = DaftarSpacing.xl),
            ) {
                Column(modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth()) {
                    EditorBody(state = state, onIntent = onIntent)
                }
            }
        }
    }
    if (state.confirmDelete) {
        ModalBottomSheet(onDismissRequest = { onIntent(ManualQuoteIntent.DismissDelete) }) {
            DaftarConfirmationContent(
                title = stringResource(Res.string.manual_delete_title),
                message = stringResource(Res.string.manual_delete_message),
                primaryLabel = stringResource(Res.string.manual_keep),
                onPrimaryClick = { onIntent(ManualQuoteIntent.DismissDelete) },
                modifier = Modifier.padding(horizontal = DaftarSpacing.lg).padding(bottom = DaftarSpacing.lg),
                destructiveLabel = stringResource(Res.string.manual_delete_confirm),
                onDestructiveClick = { onIntent(ManualQuoteIntent.ConfirmDelete) },
            )
        }
    }
    if (state.confirmDiscard) {
        ModalBottomSheet(onDismissRequest = { onIntent(ManualQuoteIntent.KeepEditing) }) {
            DaftarConfirmationContent(
                title = stringResource(Res.string.manual_discard_title),
                message = stringResource(Res.string.manual_discard_message),
                primaryLabel = stringResource(Res.string.manual_keep_editing),
                onPrimaryClick = { onIntent(ManualQuoteIntent.KeepEditing) },
                modifier = Modifier.padding(horizontal = DaftarSpacing.lg).padding(bottom = DaftarSpacing.lg),
                destructiveLabel = stringResource(Res.string.manual_discard),
                onDestructiveClick = { onIntent(ManualQuoteIntent.ConfirmDiscard) },
            )
        }
    }
}

@Composable
private fun EditorBody(state: ManualQuoteUiState, onIntent: (ManualQuoteIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DaftarNotePanel(tone = DaftarNoteTone.Warning) {
            Text(stringResource(Res.string.manual_intro))
        }
        Spacer(Modifier.height(DaftarSpacing.md))
        if (state.unknownQuote) {
            DaftarNotePanel(tone = DaftarNoteTone.Error) {
                Text(stringResource(Res.string.manual_unknown))
            }
            return
        }
        val key = state.key
        DaftarSelectField(
            label = stringResource(Res.string.manual_quote),
            selectedText = key?.let { stringResource(keyResource(it)) },
            onClick = { onIntent(ManualQuoteIntent.ToggleChoices) },
            placeholder = stringResource(Res.string.manual_choose),
            expanded = state.choosing,
            enabled = !state.busy && !(state.dirty && key != null),
        )
        if (state.choosing) {
            ENTERABLE_QUOTE_KEYS.forEach { choice ->
                DaftarTextButton(
                    onClick = { onIntent(ManualQuoteIntent.ChooseQuote(choice)) },
                    fullWidth = true,
                ) {
                    Text(stringResource(keyResource(choice)))
                }
            }
        }
        if (key != null) {
            Spacer(Modifier.height(DaftarSpacing.xs))
            SourceNote(state = state)
        }
        Spacer(Modifier.height(DaftarSpacing.sm))
        val problem = state.validation
        val valueProblem = problem.takeUnless { it == ManualValueProblem.NOTE_TOO_LONG }
        DaftarTextField(
            value = state.draftValue,
            onValueChange = { onIntent(ManualQuoteIntent.DraftValueChanged(it)) },
            label = stringResource(Res.string.manual_value),
            kind = DaftarFieldKind.Decimal,
            helper =
                valueProblem?.let { stringResource(messageResource(it)) }
                    ?: stringResource(Res.string.manual_value_helper),
            invalid = valueProblem != null,
            enabled = !state.busy && key != null,
        )
        Spacer(Modifier.height(DaftarSpacing.sm))
        DaftarTextArea(
            value = state.draftNote,
            onValueChange = { onIntent(ManualQuoteIntent.DraftNoteChanged(it)) },
            label = stringResource(Res.string.manual_note),
            helper = if (problem ==
                ManualValueProblem.NOTE_TOO_LONG
            ) {
                stringResource(Res.string.manual_error_note)
            } else {
                null
            },
            invalid = problem == ManualValueProblem.NOTE_TOO_LONG,
            enabled = !state.busy && key != null,
        )
        Spacer(Modifier.height(DaftarSpacing.xs))
        Text(
            stringResource(Res.string.manual_timestamp),
            style = DaftarTheme.typography.fineText,
            color = DaftarTheme.colors.muted,
        )
        Spacer(Modifier.height(DaftarSpacing.md))
        if (state.conflict) {
            DaftarNotePanel(tone = DaftarNoteTone.Warning) {
                Text(stringResource(Res.string.manual_conflict))
            }
            Spacer(Modifier.height(DaftarSpacing.xs))
            OutlineButton(onClick = { onIntent(ManualQuoteIntent.Reload) }, fullWidth = true) {
                Text(stringResource(Res.string.manual_reload))
            }
            Spacer(Modifier.height(DaftarSpacing.xs))
        }
        if (state.failure != null) {
            DaftarNotePanel(tone = DaftarNoteTone.Error) {
                Text(stringResource(Res.string.manual_failed))
            }
            Spacer(Modifier.height(DaftarSpacing.xs))
        }
        state.readError?.let {
            DaftarNotePanel(tone = DaftarNoteTone.Error) {
                Text(stringResource(Res.string.prices_load_failed))
            }
            Spacer(Modifier.height(DaftarSpacing.xs))
        }
        val saveEnabled = key != null && !state.busy && !state.conflict && !state.unknownQuote
        PrimaryButton(
            onClick = { onIntent(ManualQuoteIntent.Save) },
            enabled = saveEnabled,
            loading = state.busy,
            fullWidth = true,
        ) {
            Text(stringResource(if (state.busy) Res.string.manual_saving else Res.string.manual_save))
        }
        val slot = state.slot
        if (slot != null && slot.pinned) {
            DaftarTextButton(
                onClick = { onIntent(ManualQuoteIntent.ReturnToSource) },
                enabled = !state.busy && !state.conflict,
                fullWidth = true,
            ) {
                Text(stringResource(Res.string.manual_unpin))
            }
        }
        if (slot != null) {
            DaftarTextButton(
                onClick = { onIntent(ManualQuoteIntent.RequestDelete) },
                enabled = !state.busy && !state.conflict,
                fullWidth = true,
            ) {
                Text(stringResource(Res.string.manual_delete))
            }
        }
        Spacer(Modifier.height(DaftarSpacing.lg))
    }
}

/**
 * What the source is beneath this quote. A pinned value shows the remote price it overrides; with no remote price, the
 * manual value is only the last resort, and nothing promises a source price.
 */
@Composable
private fun SourceNote(state: ManualQuoteUiState) {
    val slot = state.slot
    val source = state.source
    val text = when {
        slot == null -> null
        !slot.pinned -> stringResource(Res.string.manual_unpinned_note)
        source != null -> stringResource(Res.string.manual_source_price, "⁦${source.value.moneyText()}⁩")
        else -> stringResource(Res.string.manual_source_none)
    }
    text?.let {
        DaftarNotePanel(tone = DaftarNoteTone.Context) {
            Text(it)
        }
    }
}

private fun keyResource(key: QuoteKey): StringResource = when (key) {
    is QuoteKey.Gold -> when (key.karat) {
        GoldKarat.K24 -> Res.string.manual_key_24
        GoldKarat.K21 -> Res.string.manual_key_21
        GoldKarat.K18 -> Res.string.manual_key_18
    }

    QuoteKey.UsdEgp -> Res.string.manual_key_usd
}

private fun messageResource(problem: ManualValueProblem): StringResource = when (problem) {
    ManualValueProblem.EMPTY -> Res.string.manual_error_empty
    ManualValueProblem.NOT_A_NUMBER -> Res.string.manual_error_not_number
    ManualValueProblem.GROUPING -> Res.string.manual_error_grouping
    ManualValueProblem.NOT_POSITIVE -> Res.string.manual_error_not_positive
    ManualValueProblem.TOO_PRECISE -> Res.string.manual_error_precision
    ManualValueProblem.TOO_LARGE -> Res.string.manual_error_range
    ManualValueProblem.NOTE_TOO_LONG -> Res.string.manual_error_note
}

private val CONTENT_MAX_WIDTH = 640.dp
