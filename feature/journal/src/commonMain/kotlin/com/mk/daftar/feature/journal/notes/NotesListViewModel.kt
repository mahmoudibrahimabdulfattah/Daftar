package com.mk.daftar.feature.journal.notes

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObservePersonalNotesUseCase
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.navigation.NoteAddRoute
import com.mk.daftar.core.navigation.NoteEditRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.hiddenFlow
import com.mk.daftar.feature.journal.isHidden
import com.mk.daftar.feature.journal.localDate
import com.mk.daftar.feature.journal.reveal
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The personal notes list, in the repository's last-updated order. Notes never feed the wealth totals. Opening an editor
 * while titles are hidden waits for an explicit reveal.
 */
class NotesListViewModel(
    observeNotes: ObservePersonalNotesUseCase,
    private val visibility: ValuesVisibility,
    private val timeZone: TimeZone,
) : MviViewModel<NotesListUiState, NotesListIntent, JournalEffect>(NotesListUiState()) {

    init {
        viewModelScope.launch {
            combine(observeNotes(), visibility.hiddenFlow()) { read, hidden -> listStateOf(read, hidden, timeZone) }
                .collect { next ->
                    reduce { next.copy(pendingRoute = if (next.phase == JournalPhase.Locked) null else pendingRoute) }
                }
        }
    }

    override fun onIntent(intent: NotesListIntent) {
        when (intent) {
            NotesListIntent.OpenAdd -> open(NoteAddRoute)

            is NotesListIntent.OpenNote -> open(NoteEditRoute(intent.noteId.value))

            NotesListIntent.RevealAndOpen -> {
                val route = currentState.pendingRoute ?: return
                visibility.reveal()
                reduce { copy(pendingRoute = null) }
                emitEffect(JournalEffect.Navigate(route))
            }

            NotesListIntent.CancelReveal -> reduce { copy(pendingRoute = null) }

            NotesListIntent.ToggleHidden -> visibility.toggle()
        }
    }

    private fun open(route: NavKey) {
        if (visibility.isHidden()) {
            reduce { copy(pendingRoute = route) }
        } else {
            emitEffect(JournalEffect.Navigate(route))
        }
    }
}

internal fun listStateOf(
    read: VaultRead<List<Stored<PersonalNote>>>,
    hidden: Boolean,
    timeZone: TimeZone,
): NotesListUiState = when (read) {
    VaultRead.Locked -> NotesListUiState(phase = JournalPhase.Locked, hidden = hidden)

    VaultRead.Loading -> NotesListUiState(phase = JournalPhase.Loading, hidden = hidden)

    is VaultRead.Failed -> NotesListUiState(phase = JournalPhase.Failed(read.error), hidden = hidden)

    is VaultRead.Ready -> {
        val rows = read.value.map { stored ->
            NoteRow(
                id = stored.value.id,
                title = stored.value.title,
                body = stored.value.body,
                updatedOn = stored.value.updatedAt.localDate(timeZone),
            )
        }
        NotesListUiState(
            phase = if (rows.isEmpty()) JournalPhase.Empty else JournalPhase.Ready,
            notes = rows,
            hidden = hidden,
        )
    }
}
