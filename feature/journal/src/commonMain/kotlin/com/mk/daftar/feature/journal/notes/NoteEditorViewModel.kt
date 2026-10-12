package com.mk.daftar.feature.journal.notes

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.PersonalNoteInput
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.CreatePersonalNoteUseCase
import com.mk.daftar.core.domain.usecase.DeletePersonalNoteUseCase
import com.mk.daftar.core.domain.usecase.ObservePersonalNoteUseCase
import com.mk.daftar.core.domain.usecase.ObservePersonalNotesUseCase
import com.mk.daftar.core.domain.usecase.UpdatePersonalNoteUseCase
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.journal.JournalAction
import com.mk.daftar.feature.journal.JournalBusy
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalFailure
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.hiddenFlow
import com.mk.daftar.feature.journal.reveal
import kotlinx.coroutines.launch

/**
 * Adds and edits a personal note. It follows the zakat editor's rules: one load, a conflict instead of a silent merge,
 * a draft kept after any failed write, a confirmed leave, and a lock that drops the draft without asking.
 */
class NoteEditorViewModel(
    private val target: NoteEditorTarget,
    observeNote: ObservePersonalNoteUseCase,
    observeNotes: ObservePersonalNotesUseCase,
    private val createNote: CreatePersonalNoteUseCase,
    private val updateNote: UpdatePersonalNoteUseCase,
    private val deleteNote: DeletePersonalNoteUseCase,
    private val visibility: ValuesVisibility,
) : MviViewModel<NoteEditorUiState, NoteEditorIntent, JournalEffect>(NoteEditorUiState()) {

    private val noteId: NoteId? = (target as? NoteEditorTarget.Existing)?.let { NoteId(it.noteId) }
    private var initialized = false
    private var loadedRevision: Long? = null
    private var latest: Stored<PersonalNote>? = null

    /** Advances on every lock. A write that started before a lock reports nothing once the lock has happened. */
    private var lockEpoch = 0

    /** Completes a pending leave. The flag says whether the draft was discarded to allow it. */
    private var pendingLeave: ((Boolean) -> Unit)? = null

    init {
        if (noteId == null) {
            reduce { copy(phase = JournalPhase.Ready, isNew = true) }
            // A new note has no record to observe, so it follows the session through the list: a lock drops its draft.
            viewModelScope.launch {
                observeNotes().collect { read -> if (read == VaultRead.Locked) onLocked() }
            }
        } else {
            reduce { copy(isNew = false) }
            viewModelScope.launch {
                observeNote(noteId).collect { read -> onRead(read) }
            }
        }
        viewModelScope.launch {
            visibility.hiddenFlow().collect { hidden -> reduce { copy(concealed = hidden) } }
        }
    }

    override fun onIntent(intent: NoteEditorIntent) {
        when (intent) {
            is NoteEditorIntent.TitleChanged -> edit { copy(title = intent.text) }
            is NoteEditorIntent.BodyChanged -> edit { copy(body = intent.text) }
            NoteEditorIntent.Save -> save()
            NoteEditorIntent.DismissFailure -> reduce { copy(failure = null) }
            NoteEditorIntent.RequestDelete -> requestConfirmation(NoteConfirmation.Delete)
            NoteEditorIntent.ConfirmDelete -> confirmDelete()
            NoteEditorIntent.RequestReload -> requestConfirmation(NoteConfirmation.Reload)
            NoteEditorIntent.ConfirmReload -> confirmReload()
            NoteEditorIntent.ConfirmDiscard -> confirmDiscard()
            NoteEditorIntent.DismissConfirmation -> dismissConfirmation()
            NoteEditorIntent.RevealForm -> visibility.reveal()
        }
    }

    /** See [ZakatEditorViewModel.onLeaveRequested]: the same rules apply to notes. */
    fun onLeaveRequested(proceed: (discarded: Boolean) -> Unit) {
        val state = currentState
        if (state.busy != null) return
        if (state.completed || !state.dirty) {
            proceed(false)
            return
        }
        pendingLeave = proceed
        reduce { copy(confirmation = NoteConfirmation.Discard) }
    }

    private fun onRead(read: VaultRead<Stored<PersonalNote>?>) {
        when (read) {
            VaultRead.Locked -> onLocked()

            VaultRead.Loading -> if (!initialized) reduce { copy(phase = JournalPhase.Loading) }

            is VaultRead.Failed -> if (!initialized) reduce { copy(phase = JournalPhase.Failed(read.error)) }

            is VaultRead.Ready -> {
                val stored = read.value
                if (stored == null) {
                    if (!currentState.completed) reduce { copy(phase = JournalPhase.Missing) }
                } else {
                    onStored(stored)
                }
            }
        }
    }

    private fun onStored(stored: Stored<PersonalNote>) {
        latest = stored
        when {
            !initialized -> {
                initialized = true
                adopt(stored)
            }

            !currentState.dirty -> adopt(stored)

            stored.revision != loadedRevision -> reduce { copy(conflict = true) }
        }
    }

    private fun adopt(stored: Stored<PersonalNote>) {
        loadedRevision = stored.revision
        val values = stored.value.toValues()
        reduce {
            copy(
                phase = JournalPhase.Ready,
                isNew = false,
                draft = values.toDraft(),
                baseline = values,
                conflict = false,
            )
        }
    }

    private fun edit(change: NoteDraft.() -> NoteDraft) {
        val state = currentState
        if (state.busy != null || state.completed) return
        reduce { copy(draft = draft.change()) }
    }

    private fun save() {
        val state = currentState
        if (state.busy != null || state.completed || state.concealed || state.conflict) return
        if (state.phase != JournalPhase.Ready) return
        reduce { copy(attempted = true) }
        if (state.draft.errors().any) return
        val values = state.draft.values()
        val input = PersonalNoteInput(title = values.title, body = values.body)
        val id = noteId
        val revision = loadedRevision
        if (id != null && revision == null) return
        reduce { copy(busy = JournalBusy.Saving, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = if (id == null || revision == null) {
                createNote(input)
            } else {
                updateNote(id, revision, input)
            }
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> onWritten(outcome.data)
                is Outcome.Failure -> onWriteFailed(JournalAction.Save, outcome.error)
            }
        }
    }

    private fun onWritten(stored: Stored<PersonalNote>) {
        latest = stored
        loadedRevision = stored.revision
        val values = stored.value.toValues()
        reduce { copy(busy = null, baseline = values, conflict = false, completed = true, isNew = false) }
        emitEffect(JournalEffect.Completed)
    }

    private fun onWriteFailed(action: JournalAction, error: StorageError) {
        when (error) {
            StorageError.Conflict -> reduce { copy(busy = null, conflict = true) }
            StorageError.NotFound -> reduce { copy(busy = null, phase = JournalPhase.Missing) }
            StorageError.Locked -> onLocked()
            else -> reduce { copy(busy = null, failure = JournalFailure(action, error)) }
        }
    }

    private fun requestConfirmation(confirmation: NoteConfirmation) {
        val state = currentState
        if (state.busy != null || state.completed || state.isNew) return
        if (confirmation == NoteConfirmation.Reload && !state.conflict) return
        reduce { copy(confirmation = confirmation) }
    }

    private fun confirmDelete() {
        val state = currentState
        val id = noteId ?: return
        val revision = loadedRevision ?: return
        if (state.busy != null || state.completed || state.confirmation != NoteConfirmation.Delete) return
        reduce { copy(confirmation = null, busy = JournalBusy.Deleting, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = deleteNote(id, revision)
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> {
                    reduce { copy(busy = null, completed = true) }
                    emitEffect(JournalEffect.Completed)
                }

                is Outcome.Failure -> onWriteFailed(JournalAction.Delete, outcome.error)
            }
        }
    }

    private fun confirmDiscard() {
        if (currentState.busy != null) return
        val proceed = pendingLeave
        pendingLeave = null
        val baseline = currentState.baseline
        reduce {
            copy(
                confirmation = null,
                draft = baseline?.toDraft() ?: draft,
                attempted = false,
                failure = null,
            )
        }
        proceed?.invoke(true)
    }

    private fun confirmReload() {
        if (currentState.busy != null) return
        reduce { copy(confirmation = null) }
        latest?.let { adopt(it) }
    }

    private fun dismissConfirmation() {
        pendingLeave = null
        reduce { copy(confirmation = null) }
    }

    private fun onLocked() {
        lockEpoch++
        pendingLeave = null
        latest = null
        loadedRevision = null
        initialized = false
        reduce {
            NoteEditorUiState(
                phase = JournalPhase.Locked,
                isNew = target == NoteEditorTarget.New,
                concealed = concealed,
            )
        }
    }
}
