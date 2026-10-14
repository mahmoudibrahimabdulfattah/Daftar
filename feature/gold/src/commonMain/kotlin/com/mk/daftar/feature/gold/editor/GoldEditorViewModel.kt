package com.mk.daftar.feature.gold.editor

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.GoldHoldingInput
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.CreateGoldHoldingUseCase
import com.mk.daftar.core.domain.usecase.DeleteGoldHoldingUseCase
import com.mk.daftar.core.domain.usecase.ObserveGoldHoldingUseCase
import com.mk.daftar.core.domain.usecase.ObserveGoldHoldingsUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.domain.usecase.UpdateGoldHoldingUseCase
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.gold.GoldAction
import com.mk.daftar.feature.gold.GoldBusy
import com.mk.daftar.feature.gold.GoldEffect
import com.mk.daftar.feature.gold.GoldFailure
import com.mk.daftar.feature.gold.GoldPhase
import com.mk.daftar.feature.gold.hiddenFlow
import com.mk.daftar.feature.gold.reveal
import kotlinx.coroutines.launch

/**
 * Adds and edits a gold holding with a draft that is never replaced silently.
 *
 * The record is loaded once. Later emissions follow it only while the draft is clean; a change under a dirty draft becomes
 * a conflict, and reloading it needs an explicit discard. Writes carry the loaded revision. A failed write keeps the draft.
 * Leaving with unsaved edits asks first; a lock drops the draft without asking.
 */
class GoldEditorViewModel(
    private val target: GoldEditorTarget,
    observeHolding: ObserveGoldHoldingUseCase,
    observeHoldings: ObserveGoldHoldingsUseCase,
    observeQuotes: ObserveSelectedQuotesUseCase,
    private val createHolding: CreateGoldHoldingUseCase,
    private val updateHolding: UpdateGoldHoldingUseCase,
    private val deleteHolding: DeleteGoldHoldingUseCase,
    private val visibility: ValuesVisibility,
) : MviViewModel<GoldEditorUiState, GoldEditorIntent, GoldEffect>(GoldEditorUiState()) {

    private val holdingId: GoldHoldingId? = (target as? GoldEditorTarget.Existing)?.let {
        GoldHoldingId(it.holdingId)
    }
    private var initialized = false
    private var loadedRevision: Long? = null
    private var latest: Stored<GoldHolding>? = null

    /** Advances on every lock. A write that started before a lock reports nothing once the lock has happened. */
    private var lockEpoch = 0

    /** Completes a pending leave. The flag says whether the draft was discarded to allow it. */
    private var pendingLeave: ((Boolean) -> Unit)? = null

    init {
        if (holdingId == null) {
            reduce {
                copy(
                    phase = GoldPhase.Ready,
                    isNew = true,
                    draft = GoldDraft(),
                    baseline = NEW_GOLD_VALUES,
                )
            }
            // A new holding has no record to observe, so it follows the session through the list: a lock drops its draft.
            // The gold hint shows while the vault has never saved gold, so it reads the same list.
            viewModelScope.launch {
                observeHoldings().collect { read ->
                    when (read) {
                        VaultRead.Locked -> onLocked()
                        is VaultRead.Ready -> reduce { copy(goldHint = read.value.isEmpty()) }
                        else -> Unit
                    }
                }
            }
        } else {
            reduce { copy(isNew = false) }
            viewModelScope.launch {
                observeHolding(holdingId).collect { read -> onRead(read) }
            }
        }
        // Quotes only feed the preview. Their absence or lock never changes the editor's phase or its draft.
        viewModelScope.launch {
            observeQuotes().collect { read ->
                reduce { copy(quotes = (read as? VaultRead.Ready)?.value ?: SelectedQuotes.EMPTY) }
            }
        }
        viewModelScope.launch {
            visibility.hiddenFlow().collect { hidden -> reduce { copy(concealed = hidden) } }
        }
    }

    override fun onIntent(intent: GoldEditorIntent) {
        when (intent) {
            is GoldEditorIntent.KaratChanged -> edit { copy(karat = intent.karat) }
            is GoldEditorIntent.FormChanged -> edit { copy(form = intent.form) }
            is GoldEditorIntent.WeightChanged -> edit { copy(weightText = intent.text) }
            is GoldEditorIntent.NoteChanged -> edit { copy(noteText = intent.text) }
            GoldEditorIntent.Save -> save()
            GoldEditorIntent.DismissFailure -> reduce { copy(failure = null) }
            GoldEditorIntent.RequestDelete -> requestConfirmation(GoldConfirmation.Delete)
            GoldEditorIntent.ConfirmDelete -> confirmDelete()
            GoldEditorIntent.RequestReload -> requestConfirmation(GoldConfirmation.Reload)
            GoldEditorIntent.ConfirmReload -> confirmReload()
            GoldEditorIntent.ConfirmDiscard -> confirmDiscard()
            GoldEditorIntent.DismissConfirmation -> dismissConfirmation()
            GoldEditorIntent.RevealForm -> visibility.reveal()
        }
    }

    /**
     * The shell asks before it leaves this editor. Without unsaved edits it proceeds at once, and [proceed] receives
     * false. Otherwise the discard confirmation opens and [proceed] receives true once the user discards. While a write
     * runs the request is ignored, because the write's outcome decides what happens next.
     */
    fun onLeaveRequested(proceed: (discarded: Boolean) -> Unit) {
        val state = currentState
        if (state.busy != null) return
        if (state.completed || !state.dirty) {
            proceed(false)
            return
        }
        pendingLeave = proceed
        reduce { copy(confirmation = GoldConfirmation.Discard) }
    }

    private fun onRead(read: VaultRead<Stored<GoldHolding>?>) {
        when (read) {
            VaultRead.Locked -> onLocked()

            VaultRead.Loading -> if (!initialized) reduce { copy(phase = GoldPhase.Loading) }

            is VaultRead.Failed -> if (!initialized) reduce { copy(phase = GoldPhase.Failed(read.error)) }

            is VaultRead.Ready -> {
                val stored = read.value
                if (stored == null) {
                    if (!currentState.completed) reduce { copy(phase = GoldPhase.Missing) }
                } else {
                    onStored(stored)
                }
            }
        }
    }

    private fun onStored(stored: Stored<GoldHolding>) {
        latest = stored
        when {
            !initialized -> {
                initialized = true
                adopt(stored)
            }

            !currentState.dirty -> adopt(stored)

            // A change under unsaved edits is reported, never merged: the draft and the loaded revision stay as they were.
            stored.revision != loadedRevision -> reduce { copy(conflict = true) }
        }
    }

    /** Takes a stored record as the new baseline and, while the draft is clean, as the draft. */
    private fun adopt(stored: Stored<GoldHolding>) {
        loadedRevision = stored.revision
        val values = stored.value.toValues()
        reduce {
            copy(
                phase = GoldPhase.Ready,
                isNew = false,
                draft = values.toDraft(),
                baseline = values,
                conflict = false,
            )
        }
    }

    private fun edit(change: GoldDraft.() -> GoldDraft) {
        val state = currentState
        if (state.busy != null || state.completed) return
        reduce { copy(draft = draft.change()) }
    }

    private fun save() {
        val state = currentState
        if (state.busy != null || state.completed || state.concealed || state.conflict) return
        if (state.phase != GoldPhase.Ready) return
        reduce { copy(attempted = true) }
        val draft = state.draft
        if (draft.errors().any) return
        val weight = draft.weightOrNull() ?: return
        val input = GoldHoldingInput(
            karat = draft.karat,
            form = draft.form,
            weight = weight,
            note = draft.noteText.trim().ifEmpty { null },
        )
        val id = holdingId
        val revision = loadedRevision
        if (id != null && revision == null) return
        reduce { copy(busy = GoldBusy.Saving, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = if (id == null || revision == null) {
                createHolding(input)
            } else {
                updateHolding(id, revision, input)
            }
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> onWritten(outcome.data)
                is Outcome.Failure -> onWriteFailed(GoldAction.Save, outcome.error)
            }
        }
    }

    private fun onWritten(stored: Stored<GoldHolding>) {
        latest = stored
        loadedRevision = stored.revision
        val values = stored.value.toValues()
        reduce { copy(busy = null, baseline = values, conflict = false, completed = true, isNew = false) }
        emitEffect(GoldEffect.Completed)
    }

    /**
     * A conflict or a missing record changes what the editor shows; anything else keeps the draft and offers a retry.
     * [StorageError.SessionEnded] keeps its uncertain outcome: it is not retried automatically, and the lock follows.
     */
    private fun onWriteFailed(action: GoldAction, error: StorageError) {
        when (error) {
            StorageError.Conflict -> reduce { copy(busy = null, conflict = true) }
            StorageError.NotFound -> reduce { copy(busy = null, phase = GoldPhase.Missing) }
            StorageError.Locked -> onLocked()
            else -> reduce { copy(busy = null, failure = GoldFailure(action, error)) }
        }
    }

    private fun requestConfirmation(confirmation: GoldConfirmation) {
        val state = currentState
        if (state.busy != null || state.completed || state.isNew) return
        if (confirmation == GoldConfirmation.Reload && !state.conflict) return
        reduce { copy(confirmation = confirmation) }
    }

    private fun confirmDelete() {
        val state = currentState
        val id = holdingId ?: return
        val revision = loadedRevision ?: return
        if (state.busy != null || state.completed || state.confirmation != GoldConfirmation.Delete) return
        reduce { copy(confirmation = null, busy = GoldBusy.Deleting, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = deleteHolding(id, revision)
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> {
                    reduce { copy(busy = null, completed = true) }
                    emitEffect(GoldEffect.Completed)
                }

                is Outcome.Failure -> onWriteFailed(GoldAction.Delete, outcome.error)
            }
        }
    }

    /** Discards the draft and proceeds with the pending leave. Guarded while a write runs. */
    private fun confirmDiscard() {
        if (currentState.busy != null || currentState.confirmation != GoldConfirmation.Discard) return
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

    /** Replaces the draft with the latest stored record, and only after the user has discarded their edits. */
    private fun confirmReload() {
        if (currentState.busy != null || currentState.confirmation != GoldConfirmation.Reload) return
        reduce { copy(confirmation = null) }
        latest?.let { adopt(it) }
    }

    private fun dismissConfirmation() {
        pendingLeave = null
        reduce { copy(confirmation = null) }
    }

    /** A lock drops the draft, the confirmations and any pending leave without asking. */
    private fun onLocked() {
        lockEpoch++
        pendingLeave = null
        latest = null
        loadedRevision = null
        initialized = false
        reduce {
            GoldEditorUiState(
                phase = GoldPhase.Locked,
                isNew = target == GoldEditorTarget.New,
                concealed = concealed,
            )
        }
    }
}
