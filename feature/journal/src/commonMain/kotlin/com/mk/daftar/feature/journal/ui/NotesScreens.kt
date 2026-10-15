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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarCard
import com.mk.daftar.core.designsystem.component.DaftarEmptyState
import com.mk.daftar.core.designsystem.component.DaftarFooterAction
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarSkeletonSize
import com.mk.daftar.core.designsystem.component.DaftarTextArea
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTextField
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.resources.btn_edit
import com.mk.daftar.core.designsystem.resources.btn_saving
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.shell.DesktopWidths
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.desktopPage
import com.mk.daftar.feature.journal.JournalAction
import com.mk.daftar.feature.journal.JournalBusy
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.notes.NoteBodyError
import com.mk.daftar.feature.journal.notes.NoteConfirmation
import com.mk.daftar.feature.journal.notes.NoteEditorIntent
import com.mk.daftar.feature.journal.notes.NoteEditorTarget
import com.mk.daftar.feature.journal.notes.NoteEditorUiState
import com.mk.daftar.feature.journal.notes.NoteEditorViewModel
import com.mk.daftar.feature.journal.notes.NoteErrors
import com.mk.daftar.feature.journal.notes.NoteRow
import com.mk.daftar.feature.journal.notes.NoteTitleError
import com.mk.daftar.feature.journal.notes.NotesListIntent
import com.mk.daftar.feature.journal.notes.NotesListUiState
import com.mk.daftar.feature.journal.notes.NotesListViewModel
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
import com.mk.daftar.feature.journal.resources.journal_hidden_body
import com.mk.daftar.feature.journal.resources.journal_hidden_note
import com.mk.daftar.feature.journal.resources.journal_load_failed_message
import com.mk.daftar.feature.journal.resources.journal_load_failed_title
import com.mk.daftar.feature.journal.resources.journal_missing_message
import com.mk.daftar.feature.journal.resources.journal_missing_title
import com.mk.daftar.feature.journal.resources.journal_months
import com.mk.daftar.feature.journal.resources.journal_reload
import com.mk.daftar.feature.journal.resources.note_add_title
import com.mk.daftar.feature.journal.resources.note_body_label
import com.mk.daftar.feature.journal.resources.note_delete
import com.mk.daftar.feature.journal.resources.note_edit_title
import com.mk.daftar.feature.journal.resources.note_error_body_long
import com.mk.daftar.feature.journal.resources.note_error_body_required
import com.mk.daftar.feature.journal.resources.note_error_title_long
import com.mk.daftar.feature.journal.resources.note_error_title_required
import com.mk.daftar.feature.journal.resources.note_private
import com.mk.daftar.feature.journal.resources.note_save
import com.mk.daftar.feature.journal.resources.note_title_label
import com.mk.daftar.feature.journal.resources.notes_context
import com.mk.daftar.feature.journal.resources.notes_empty_message
import com.mk.daftar.feature.journal.resources.notes_empty_title
import com.mk.daftar.feature.journal.resources.notes_heading
import com.mk.daftar.feature.journal.resources.notes_new
import com.mk.daftar.feature.journal.resources.notes_title
import com.mk.daftar.feature.journal.resources.notes_updated
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The personal notes list, in last-updated order. Titles and text are hidden until an explicit reveal. */
@Composable
fun NotesListScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: NotesListViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect is JournalEffect.Navigate) navigator.goTo(effect.route)
    }
    NotesListContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The notes list as it is drawn. Titles, text and dates are masked in both rendering and semantics while hidden. */
@Composable
fun NotesListContent(
    state: NotesListUiState,
    onIntent: (NotesListIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val months = stringArrayResource(Res.array.journal_months)
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
                title = stringResource(Res.string.notes_title),
                onBack = onBack,
                backLabel = stringResource(DesignRes.string.btn_back),
                actions = { VisibilityToggle(hidden = state.hidden) { onIntent(NotesListIntent.ToggleHidden) } },
            )
            Text(
                text = stringResource(Res.string.notes_heading),
                style = typography.screenHeading,
                color = colors.ink,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            state.pendingRoute?.let {
                RevealPrompt(
                    onReveal = { onIntent(NotesListIntent.RevealAndOpen) },
                    onCancel = { onIntent(NotesListIntent.CancelReveal) },
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
                    title = stringResource(Res.string.notes_empty_title),
                    message = stringResource(Res.string.notes_empty_message),
                    actionLabel = stringResource(Res.string.notes_new),
                    onAction = { onIntent(NotesListIntent.OpenAdd) },
                    icon = DaftarIcons.Note,
                )

                JournalPhase.Ready -> {
                    DaftarNotePanel { Text(stringResource(Res.string.notes_context)) }
                    state.notes.forEach { note ->
                        NoteCard(note = note, hidden = state.hidden, months = months) {
                            onIntent(NotesListIntent.OpenNote(note.id))
                        }
                    }
                    PrimaryButton(
                        onClick = { onIntent(NotesListIntent.OpenAdd) },
                        fullWidth = true,
                        leadingIcon = DaftarIcons.Plus,
                    ) {
                        Text(stringResource(Res.string.notes_new))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteRow, hidden: Boolean, months: List<String>, onEdit: () -> Unit) {
    DaftarCard(
        title = if (hidden) stringResource(Res.string.journal_hidden_note) else note.title,
        time = if (hidden) null else stringResource(Res.string.notes_updated, journalDateText(note.updatedOn, months)),
        actions = {
            DaftarTextButton(onClick = onEdit) { Text(stringResource(DesignRes.string.btn_edit)) }
        },
    ) {
        Text(
            text = if (hidden) stringResource(Res.string.journal_hidden_body) else note.body,
            style = DaftarTheme.typography.noteBody,
            color = DaftarTheme.colors.ink,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The note editor: a title and a body that are kept as typed. Leaves follow the same confirmed rules as zakat. */
@Composable
fun NoteEditorScreen(target: NoteEditorTarget, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: NoteEditorViewModel = koinViewModel(
        key = "note-editor-$target",
        parameters = { parametersOf(target) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect == JournalEffect.Completed) navigator.goBack()
    }
    DisposableEffect(viewModel, navigator) {
        val unregister = navigator.leaveGuard.register(viewModel) { _, proceed ->
            viewModel.onLeaveRequested { _ ->
                proceed()
            }
        }
        onDispose { unregister() }
    }
    NoteEditorContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The note editor as it is drawn. A concealed form shows no field and no keyboard. */
@Composable
fun NoteEditorContent(
    state: NoteEditorUiState,
    onIntent: (NoteEditorIntent) -> Unit,
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
    val editable = state.phase == JournalPhase.Ready && !state.concealed && !state.completed && state.busy == null
    val showsForm = state.phase == JournalPhase.Ready && !state.concealed && !state.completed
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(if (state.isNew) Res.string.note_add_title else Res.string.note_edit_title),
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
                            onReveal = { onIntent(NoteEditorIntent.RevealForm) },
                            onCancel = onBack,
                        )

                        state.completed -> Unit

                        else -> NoteForm(state = state, editable = editable, onIntent = onIntent)
                    }
                }
            }
            if (showsForm) {
                DaftarFooterAction {
                    PrimaryButton(
                        onClick = { onIntent(NoteEditorIntent.Save) },
                        fullWidth = true,
                        enabled = editable && !state.conflict,
                        loading = state.busy == JournalBusy.Saving,
                    ) {
                        Text(
                            stringResource(
                                if (state.busy ==
                                    JournalBusy.Saving
                                ) {
                                    DesignRes.string.btn_saving
                                } else {
                                    Res.string.note_save
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
    when (state.confirmation) {
        NoteConfirmation.Delete -> JournalConfirmDialog(
            title = stringResource(Res.string.journal_confirm_delete_title),
            message = stringResource(Res.string.journal_confirm_delete_message),
            primaryLabel = stringResource(Res.string.journal_confirm_delete_cancel),
            onPrimary = { onIntent(NoteEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(NoteEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.journal_confirm_delete_action),
            onDestructive = { onIntent(NoteEditorIntent.ConfirmDelete) },
        )

        NoteConfirmation.Discard -> JournalConfirmDialog(
            title = stringResource(Res.string.journal_confirm_discard_title),
            message = stringResource(Res.string.journal_confirm_discard_message),
            primaryLabel = stringResource(Res.string.journal_confirm_keep),
            onPrimary = { onIntent(NoteEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(NoteEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.journal_confirm_discard_action),
            onDestructive = { onIntent(NoteEditorIntent.ConfirmDiscard) },
        )

        NoteConfirmation.Reload -> JournalConfirmDialog(
            title = stringResource(Res.string.journal_confirm_discard_title),
            message = stringResource(Res.string.journal_confirm_discard_message),
            primaryLabel = stringResource(Res.string.journal_confirm_keep),
            onPrimary = { onIntent(NoteEditorIntent.DismissConfirmation) },
            onDismiss = { onIntent(NoteEditorIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.journal_reload),
            onDestructive = { onIntent(NoteEditorIntent.ConfirmReload) },
        )

        null -> Unit
    }
}

@Composable
private fun NoteForm(state: NoteEditorUiState, editable: Boolean, onIntent: (NoteEditorIntent) -> Unit) {
    val errors = state.errors
    val reveal = state.attempted
    if (state.conflict) {
        DaftarNotePanel(
            tone = DaftarNoteTone.Warning,
            icon = DaftarIcons.Alert,
            action = {
                DaftarTextButton(
                    onClick = { onIntent(NoteEditorIntent.RequestReload) },
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
                    onIntent(NoteEditorIntent.Save)
                } else {
                    onIntent(NoteEditorIntent.RequestDelete)
                }
            },
            onBackToEditing = { onIntent(NoteEditorIntent.DismissFailure) },
        )
    }
    DaftarTextField(
        label = stringResource(Res.string.note_title_label),
        value = state.draft.title,
        onValueChange = { onIntent(NoteEditorIntent.TitleChanged(it)) },
        helper = if (reveal) titleHelper(errors)?.let { stringResource(it) } else null,
        invalid = reveal && errors.title != null,
        enabled = editable,
    )
    DaftarTextArea(
        label = stringResource(Res.string.note_body_label),
        value = state.draft.body,
        onValueChange = { onIntent(NoteEditorIntent.BodyChanged(it)) },
        helper = if (reveal) bodyHelper(errors)?.let { stringResource(it) } else null,
        invalid = reveal && errors.body != null,
        enabled = editable,
    )
    Text(
        text = stringResource(Res.string.note_private),
        style = DaftarTheme.typography.fineText,
        color = DaftarTheme.colors.muted,
    )
    if (!state.isNew) {
        DaftarTextButton(
            onClick = { onIntent(NoteEditorIntent.RequestDelete) },
            fullWidth = true,
            enabled = editable,
        ) {
            Text(stringResource(Res.string.note_delete))
        }
    }
}

private fun titleHelper(errors: NoteErrors): StringResource? = when (errors.title) {
    null -> null
    NoteTitleError.Required -> Res.string.note_error_title_required
    NoteTitleError.TooLong -> Res.string.note_error_title_long
}

private fun bodyHelper(errors: NoteErrors): StringResource? = when (errors.body) {
    null -> null
    NoteBodyError.Required -> Res.string.note_error_body_required
    NoteBodyError.TooLong -> Res.string.note_error_body_long
}
