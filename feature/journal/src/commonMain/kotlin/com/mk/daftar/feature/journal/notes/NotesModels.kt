package com.mk.daftar.feature.journal.notes

import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.model.EntryLimits
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import com.mk.daftar.feature.journal.JournalBusy
import com.mk.daftar.feature.journal.JournalFailure
import com.mk.daftar.feature.journal.JournalPhase
import kotlinx.datetime.LocalDate

/** One personal note as the list shows it. The body keeps its line breaks. */
data class NoteRow(val id: NoteId, val title: String, val body: String, val updatedOn: LocalDate)

/** What the note editor opens for: a new note, or one recorded note by its ID. */
sealed interface NoteEditorTarget {
    data object New : NoteEditorTarget

    data class Existing(val noteId: String) : NoteEditorTarget
}

/** The text the user typed. The title is trimmed when it is compared; the body is kept exactly as typed. */
data class NoteDraft(val title: String = "", val body: String = "")

/** A draft in its normalized meaning: a trimmed title and an untouched body. */
data class NoteValues(val title: String, val body: String)

internal fun NoteDraft.values(): NoteValues = NoteValues(title = title.trim(), body = body)

internal fun NoteValues.toDraft(): NoteDraft = NoteDraft(title = title, body = body)

internal fun PersonalNote.toValues(): NoteValues = NoteValues(title = title, body = body)

enum class NoteTitleError { Required, TooLong }

enum class NoteBodyError { Required, TooLong }

data class NoteErrors(val title: NoteTitleError?, val body: NoteBodyError?) {
    val any: Boolean get() = title != null || body != null
}

internal fun NoteDraft.errors(): NoteErrors {
    val trimmed = title.trim()
    val titleError = when {
        trimmed.isEmpty() -> NoteTitleError.Required
        !EntryLimits.isValidName(trimmed) -> NoteTitleError.TooLong
        else -> null
    }
    val bodyError = when {
        body.isBlank() -> NoteBodyError.Required
        !EntryLimits.isValidBody(body) -> NoteBodyError.TooLong
        else -> null
    }
    return NoteErrors(titleError, bodyError)
}

/** The confirmations a note editor can raise. */
enum class NoteConfirmation { Delete, Discard, Reload }

sealed interface NotesListIntent : UiIntent {
    data object OpenAdd : NotesListIntent

    data class OpenNote(val noteId: NoteId) : NotesListIntent

    /** Shows the titles and text, then opens the editor the user asked for while they were hidden. */
    data object RevealAndOpen : NotesListIntent

    data object CancelReveal : NotesListIntent

    data object ToggleHidden : NotesListIntent
}

data class NotesListUiState(
    val phase: JournalPhase = JournalPhase.Loading,
    val notes: List<NoteRow> = emptyList(),
    val hidden: Boolean = false,
    /** The editor the user asked for while titles are hidden. It opens only after an explicit reveal. */
    val pendingRoute: NavKey? = null,
) : UiState

sealed interface NoteEditorIntent : UiIntent {
    data class TitleChanged(val text: String) : NoteEditorIntent

    data class BodyChanged(val text: String) : NoteEditorIntent

    data object Save : NoteEditorIntent

    data object DismissFailure : NoteEditorIntent

    data object RequestDelete : NoteEditorIntent

    data object ConfirmDelete : NoteEditorIntent

    data object RequestReload : NoteEditorIntent

    data object ConfirmReload : NoteEditorIntent

    data object ConfirmDiscard : NoteEditorIntent

    data object DismissConfirmation : NoteEditorIntent

    data object RevealForm : NoteEditorIntent
}

/**
 * The note editor's state. [baseline] holds the normalized values the note had when it was last loaded. [errors] and
 * [dirty] are derived from the draft, so they cannot disagree with it.
 */
data class NoteEditorUiState(
    val phase: JournalPhase = JournalPhase.Loading,
    val isNew: Boolean = true,
    val draft: NoteDraft = NoteDraft(),
    val baseline: NoteValues? = null,
    val attempted: Boolean = false,
    val conflict: Boolean = false,
    val busy: JournalBusy? = null,
    val failure: JournalFailure? = null,
    val confirmation: NoteConfirmation? = null,
    val concealed: Boolean = false,
    val completed: Boolean = false,
) : UiState {
    val errors: NoteErrors
        get() = draft.errors()

    val dirty: Boolean
        get() = baseline?.let { draft.values() != it } ?: false
}
