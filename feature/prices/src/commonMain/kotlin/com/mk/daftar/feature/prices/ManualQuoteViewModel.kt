package com.mk.daftar.feature.prices

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.DeleteManualQuoteUseCase
import com.mk.daftar.core.domain.usecase.ObserveQuoteSnapshotUseCase
import com.mk.daftar.core.domain.usecase.RemoveOverrideUseCase
import com.mk.daftar.core.domain.usecase.SetManualQuoteUseCase
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.domain.valuation.selectRemoteQuotes
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.navigation.ManualQuoteRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The manual override editor for one quote, or for a quote chosen inside it.
 *
 * Every write names the revision it replaces, and the editor never overwrites a value it has not shown: when the stored
 * value changes under a dirty draft, the draft stays and saving waits for a reload. A write is applied only while its
 * session generation is still open. A lock clears the draft, cancels the write in flight and closes the editor.
 */
class ManualQuoteViewModel(
    route: ManualQuoteRoute,
    observeSnapshot: ObserveQuoteSnapshotUseCase,
    private val setManual: SetManualQuoteUseCase,
    private val removeOverride: RemoveOverrideUseCase,
    private val deleteManual: DeleteManualQuoteUseCase,
    private val clock: Clock,
) : MviViewModel<ManualQuoteUiState, ManualQuoteIntent, ManualQuoteEffect>(initialEditorState(route)) {

    private var generation: Long? = null
    private var operation: Job? = null
    private var latest: Pair<Long, QuoteSnapshot>? = null

    /** The stored value and note the draft was last loaded from. The draft is dirty when it differs from these. */
    private var storedValue: String = ""
    private var storedNote: String = ""

    /** The revision of the stored value the draft is based on. Null while no manual value is stored. */
    private var baseline: Long? = null
    private var baselineLoaded: Boolean = false

    /** Set when this editor's own write succeeded; the next revision change is then expected, not a conflict. */
    private var ownWritePending: Boolean = false

    init {
        viewModelScope.launch {
            observeSnapshot().collect { read -> onRead(read) }
        }
    }

    override fun onIntent(intent: ManualQuoteIntent) {
        when (intent) {
            is ManualQuoteIntent.DraftValueChanged -> editValue(intent.text)
            is ManualQuoteIntent.DraftNoteChanged -> editNote(intent.text)
            ManualQuoteIntent.ToggleChoices -> reduce { copy(choosing = !choosing) }
            is ManualQuoteIntent.ChooseQuote -> chooseQuote(intent.key)
            ManualQuoteIntent.Save -> save()
            ManualQuoteIntent.ReturnToSource -> returnToSource()
            ManualQuoteIntent.RequestDelete -> if (currentState.slot != null) reduce { copy(confirmDelete = true) }
            ManualQuoteIntent.ConfirmDelete -> deleteSlot()
            ManualQuoteIntent.DismissDelete -> reduce { copy(confirmDelete = false) }
            ManualQuoteIntent.RequestBack -> requestBack()
            ManualQuoteIntent.ConfirmDiscard -> emitEffect(ManualQuoteEffect.Close)
            ManualQuoteIntent.KeepEditing -> reduce { copy(confirmDiscard = false) }
            ManualQuoteIntent.Reload -> reload()
        }
    }

    private fun onRead(read: VaultRead<QuoteSnapshot>) {
        // A closed editor is finished: a later generation opens a new editor, never this one.
        if (currentState.closed) return
        when (read) {
            VaultRead.Locked -> close()

            VaultRead.Loading -> Unit

            is VaultRead.Failed -> reduce { copy(readError = read.error) }

            is VaultRead.Ready -> {
                latest = read.generation to read.value
                apply(read.generation, read.value)
            }
        }
    }

    /** A lock: the draft, the confirmations and any write in flight go, and the editor is closed. */
    private fun close() {
        operation?.cancel()
        operation = null
        generation = null
        latest = null
        clearBaseline()
        reduce { ManualQuoteUiState(closed = true) }
    }

    private fun clearBaseline() {
        storedValue = ""
        storedNote = ""
        baseline = null
        baselineLoaded = false
        ownWritePending = false
    }

    private fun apply(gen: Long, snapshot: QuoteSnapshot) {
        if (gen != generation) {
            operation?.cancel()
            operation = null
            generation = gen
            clearBaseline()
            reduce { ManualQuoteUiState(key = key, unknownQuote = unknownQuote, choosing = choosing) }
        }
        val key = currentState.key
        if (key == null) {
            reduce { copy(loaded = true, readError = null) }
            return
        }
        val slot = slotStateOf(snapshot, key)
        val source = selectRemoteQuotes(snapshot, clock.now())[key]
        val moved = !baselineLoaded || slot?.revision != baseline
        when {
            !moved -> reduce { copy(loaded = true, readError = null, slot = slot, source = source) }

            // Nothing typed: the draft simply shows the stored value.
            !currentState.dirty -> adopt(slot, source)

            // The editor's own write changed the revision: the draft is kept and rebased on the new stored value.
            ownWritePending -> rebase(slot, source)

            // Someone else changed the value while this draft is unsaved: keep the draft and ask for a review.
            else -> reduce { copy(loaded = true, readError = null, slot = slot, source = source, conflict = true) }
        }
    }

    /** Takes the stored value as the draft: used when the draft holds nothing the user typed. */
    private fun adopt(slot: ManualSlotState?, source: SelectedQuote?) {
        rebase(slot, source)
        reduce { copy(draftValue = storedValue, draftNote = storedNote, dirty = false, validation = null) }
    }

    /** Moves the baseline to the stored value without touching the draft. */
    private fun rebase(slot: ManualSlotState?, source: SelectedQuote?) {
        storedValue = slot?.value?.toPlainString().orEmpty()
        storedNote = slot?.note.orEmpty()
        baseline = slot?.revision
        baselineLoaded = true
        ownWritePending = false
        reduce {
            copy(
                loaded = true,
                readError = null,
                slot = slot,
                source = source,
                conflict = false,
                dirty = draftValue != storedValue || draftNote != storedNote,
            )
        }
    }

    /** After a conflict: the stored value becomes the baseline, and the draft stays for review. */
    private fun reload() {
        val (gen, snapshot) = latest ?: return
        val key = currentState.key ?: return
        if (gen != generation || !currentState.conflict) return
        rebase(slotStateOf(snapshot, key), selectRemoteQuotes(snapshot, clock.now())[key])
    }

    private fun editValue(text: String) {
        reduce {
            copy(
                draftValue = text,
                dirty = text != storedValue || draftNote != storedNote,
                validation = (parseManualValue(text) as? ManualValue.Invalid)?.problem
                    ?.takeUnless { it == ManualValueProblem.EMPTY },
                failure = null,
            )
        }
    }

    private fun editNote(text: String) {
        reduce {
            copy(
                draftNote = text,
                dirty = draftValue != storedValue || text != storedNote,
                validation = if (isManualNoteTooLong(text)) ManualValueProblem.NOTE_TOO_LONG else null,
                failure = null,
            )
        }
    }

    /** Chooses the quote inside the editor. A dirty draft for another quote is never carried over silently. */
    private fun chooseQuote(key: QuoteKey) {
        val state = currentState
        if (state.busy || (state.key != null && state.dirty)) return
        reduce { ManualQuoteUiState(key = key, loaded = false) }
        clearBaseline()
        latest?.let { (gen, snapshot) -> if (gen == generation) apply(gen, snapshot) }
    }

    private fun save() {
        val state = currentState
        val key = state.key ?: return
        val gen = generation ?: return
        if (state.busy || state.conflict || state.unknownQuote) return
        val parsed = parseManualValue(state.draftValue)
        if (parsed is ManualValue.Invalid) {
            reduce { copy(validation = parsed.problem) }
            return
        }
        if (isManualNoteTooLong(state.draftNote)) {
            reduce { copy(validation = ManualValueProblem.NOTE_TOO_LONG) }
            return
        }
        val input = ManualQuoteInput(
            key = key,
            value = (parsed as ManualValue.Valid).value,
            note = normalizedManualNote(state.draftNote),
        )
        // The revision this save replaces is the one on screen. Null creates the slot, and fails if one already exists.
        val expected = state.slot?.revision
        writeFor(gen) { setManual(input, expected) }
    }

    private fun returnToSource() {
        val state = currentState
        val key = state.key ?: return
        val gen = generation ?: return
        val slot = state.slot ?: return
        if (!slot.pinned || state.busy || state.conflict) return
        writeFor(gen, closeOnSuccess = false) { removeOverride(key, slot.revision) }
    }

    private fun deleteSlot() {
        val state = currentState
        val key = state.key ?: return
        val gen = generation ?: return
        val slot = state.slot ?: return
        if (state.busy || state.conflict) return
        reduce { copy(confirmDelete = false) }
        writeFor(gen) { deleteManual(key, slot.revision) }
    }

    private fun requestBack() {
        if (currentState.dirty) reduce { copy(confirmDiscard = true) } else emitEffect(ManualQuoteEffect.Close)
    }

    /**
     * Runs one write for [gen], and applies its result only while [gen] is still open. Success closes the editor unless
     * [closeOnSuccess] is false, as for an unpin, which leaves the editor open. A conflict keeps the draft; any other
     * failure keeps the draft and reports it.
     */
    private fun writeFor(gen: Long, closeOnSuccess: Boolean = true, write: suspend () -> Outcome<Unit, StorageError>) {
        reduce { copy(busy = true, failure = null) }
        operation = viewModelScope.launch {
            val outcome = write()
            if (generation != gen) return@launch
            when (outcome) {
                is Outcome.Success -> {
                    // The revision change this write made is expected, whichever of its snapshot and this result arrives first.
                    ownWritePending = true
                    reduce { copy(busy = false) }
                    latest?.let { (latestGen, snapshot) -> if (latestGen == gen) apply(latestGen, snapshot) }
                    if (closeOnSuccess) emitEffect(ManualQuoteEffect.Close)
                }

                is Outcome.Failure -> reduce {
                    if (outcome.error == StorageError.Conflict) {
                        copy(busy = false, conflict = true, failure = null)
                    } else {
                        copy(busy = false, failure = outcome.error)
                    }
                }
            }
        }
    }

    private fun slotStateOf(snapshot: QuoteSnapshot, key: QuoteKey): ManualSlotState? {
        val stored = manualSlotOf(snapshot, key) ?: return null
        val provenance = stored.value.provenance
        return ManualSlotState(
            value = priceQuoteValue(stored.value),
            revision = stored.revision,
            pinned = provenance.manualPinned,
            enteredAt = provenance.recordedAt,
            note = provenance.note,
        )
    }

    private companion object {
        fun initialEditorState(route: ManualQuoteRoute): ManualQuoteUiState {
            val token = route.quoteKey
            val key = token?.let { quoteKeyOfToken(it) }
            return ManualQuoteUiState(
                key = key,
                unknownQuote = token != null && key == null,
                choosing = token == null,
            )
        }
    }
}
