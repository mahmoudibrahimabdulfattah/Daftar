package com.mk.daftar.feature.cash

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.CashBalanceInput
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.CreateCashBalanceUseCase
import com.mk.daftar.core.domain.usecase.DeleteCashBalanceUseCase
import com.mk.daftar.core.domain.usecase.ObserveCashBalanceUseCase
import com.mk.daftar.core.domain.usecase.ObserveCashBalancesUseCase
import com.mk.daftar.core.domain.usecase.UpdateCashBalanceUseCase
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlinx.coroutines.launch

/**
 * Adds and edits a cash or wallet balance with a draft that is never replaced silently.
 *
 * The record is loaded once. Later emissions follow it only while the draft is clean; a change under a dirty draft becomes
 * a conflict, and reloading it needs an explicit discard. Writes carry the loaded revision. A failed write keeps the draft.
 * Leaving with unsaved edits asks first; a lock drops the draft without asking. A relabelled balance needs the user's
 * acknowledgement before it is saved.
 */
class CashEditorViewModel(
    private val target: CashEditorTarget,
    observeBalance: ObserveCashBalanceUseCase,
    observeBalances: ObserveCashBalancesUseCase,
    private val createBalance: CreateCashBalanceUseCase,
    private val updateBalance: UpdateCashBalanceUseCase,
    private val deleteBalance: DeleteCashBalanceUseCase,
    private val visibility: ValuesVisibility,
) : MviViewModel<CashEditorUiState, CashEditorIntent, CashEffect>(CashEditorUiState()) {

    private val balanceId: CashBalanceId? = (target as? CashEditorTarget.Existing)?.let {
        CashBalanceId(it.balanceId)
    }
    private var initialized = false
    private var loadedRevision: Long? = null
    private var latest: Stored<CashBalance>? = null

    /** Advances on every lock. A write that started before a lock reports nothing once the lock has happened. */
    private var lockEpoch = 0

    /** Completes a pending leave. The flag says whether the draft was discarded to allow it. */
    private var pendingLeave: ((Boolean) -> Unit)? = null

    init {
        if (balanceId == null) {
            reduce { copy(phase = CashPhase.Ready, isNew = true, baseline = CashDraft().values()) }
            // A new balance has no record to observe, so it follows the session through the list: a lock drops its draft.
            viewModelScope.launch {
                observeBalances().collect { read -> if (read == VaultRead.Locked) onLocked() }
            }
        } else {
            reduce { copy(isNew = false) }
            viewModelScope.launch {
                observeBalance(balanceId).collect { read -> onRead(read) }
            }
        }
        viewModelScope.launch {
            visibility.hiddenFlow().collect { hidden -> reduce { copy(concealed = hidden) } }
        }
    }

    override fun onIntent(intent: CashEditorIntent) {
        when (intent) {
            is CashEditorIntent.NameChanged -> edit { copy(name = intent.text) }

            is CashEditorIntent.KindChanged -> edit { copy(kind = intent.kind) }

            is CashEditorIntent.CurrencyChanged -> {
                edit { copy(currency = intent.currency) }
                // A different currency is a different relabelling, so it must be acknowledged again.
                reduce { copy(currencyAcknowledged = false) }
            }

            is CashEditorIntent.CurrencyAcknowledged -> {
                val state = currentState
                if (state.busy == null && !state.completed) reduce { copy(currencyAcknowledged = intent.checked) }
            }

            is CashEditorIntent.BalanceChanged -> edit { copy(balanceText = intent.text) }

            is CashEditorIntent.NoteChanged -> edit { copy(noteText = intent.text) }

            CashEditorIntent.Save -> save()

            CashEditorIntent.DismissFailure -> reduce { copy(failure = null) }

            CashEditorIntent.RequestDelete -> requestConfirmation(CashConfirmation.Delete)

            CashEditorIntent.ConfirmDelete -> confirmDelete()

            CashEditorIntent.RequestReload -> requestConfirmation(CashConfirmation.Reload)

            CashEditorIntent.ConfirmReload -> confirmReload()

            CashEditorIntent.ConfirmDiscard -> confirmDiscard()

            CashEditorIntent.DismissConfirmation -> dismissConfirmation()

            CashEditorIntent.RevealForm -> visibility.reveal()
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
        reduce { copy(confirmation = CashConfirmation.Discard) }
    }

    private fun onRead(read: VaultRead<Stored<CashBalance>?>) {
        when (read) {
            VaultRead.Locked -> onLocked()

            VaultRead.Loading -> if (!initialized) reduce { copy(phase = CashPhase.Loading) }

            is VaultRead.Failed -> if (!initialized) reduce { copy(phase = CashPhase.Failed(read.error)) }

            is VaultRead.Ready -> {
                val stored = read.value
                if (stored == null) {
                    if (!currentState.completed) reduce { copy(phase = CashPhase.Missing) }
                } else {
                    onStored(stored)
                }
            }
        }
    }

    private fun onStored(stored: Stored<CashBalance>) {
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

    /** Takes a stored record as the new baseline and, when the draft allows it, as the draft. */
    private fun adopt(stored: Stored<CashBalance>) {
        loadedRevision = stored.revision
        val values = stored.value.toValues()
        reduce {
            copy(
                phase = CashPhase.Ready,
                isNew = false,
                draft = values.toDraft(),
                baseline = values,
                conflict = false,
                currencyAcknowledged = false,
            )
        }
    }

    /** A concealed form takes no edits: its fields are not shown, so nothing can reach the draft while values are hidden. */
    private fun edit(change: CashDraft.() -> CashDraft) {
        val state = currentState
        if (state.busy != null || state.completed || state.concealed) return
        reduce { copy(draft = draft.change()) }
    }

    private fun save() {
        val state = currentState
        if (state.busy != null || state.completed || state.concealed || state.conflict) return
        if (state.phase != CashPhase.Ready) return
        reduce { copy(attempted = true) }
        if (state.draft.errors().any || state.needsCurrencyAcknowledgement) return
        val draft = state.draft
        val amount = draft.balanceAmount() ?: return
        val input = CashBalanceInput(
            name = draft.name.trim(),
            kind = draft.kind,
            balance = Money(amount, draft.currency),
            note = draft.noteText.trim().ifEmpty { null },
        )
        val id = balanceId
        val revision = loadedRevision
        if (id != null && revision == null) return
        reduce { copy(busy = CashBusy.Saving, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = if (id == null || revision == null) {
                createBalance(input)
            } else {
                updateBalance(id, revision, input)
            }
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> onWritten(outcome.data)
                is Outcome.Failure -> onWriteFailed(CashAction.Save, outcome.error)
            }
        }
    }

    private fun onWritten(stored: Stored<CashBalance>) {
        latest = stored
        loadedRevision = stored.revision
        val values = stored.value.toValues()
        reduce {
            copy(
                busy = null,
                baseline = values,
                conflict = false,
                completed = true,
                isNew = false,
                currencyAcknowledged = false,
            )
        }
        emitEffect(CashEffect.Completed)
    }

    /**
     * A conflict or a missing record changes what the editor shows; anything else keeps the draft and offers a retry.
     * [StorageError.SessionEnded] keeps its uncertain outcome: it is not retried automatically, and the lock follows.
     */
    private fun onWriteFailed(action: CashAction, error: StorageError) {
        when (error) {
            StorageError.Conflict -> reduce { copy(busy = null, conflict = true) }
            StorageError.NotFound -> reduce { copy(busy = null, phase = CashPhase.Missing) }
            StorageError.Locked -> onLocked()
            else -> reduce { copy(busy = null, failure = CashFailure(action, error)) }
        }
    }

    private fun requestConfirmation(confirmation: CashConfirmation) {
        val state = currentState
        if (state.busy != null || state.completed || state.isNew) return
        if (confirmation == CashConfirmation.Reload && !state.conflict) return
        reduce { copy(confirmation = confirmation) }
    }

    private fun confirmDelete() {
        val state = currentState
        val id = balanceId ?: return
        val revision = loadedRevision ?: return
        if (state.busy != null || state.completed || state.confirmation != CashConfirmation.Delete) return
        reduce { copy(confirmation = null, busy = CashBusy.Deleting, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = deleteBalance(id, revision)
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> {
                    reduce { copy(busy = null, completed = true) }
                    emitEffect(CashEffect.Completed)
                }

                is Outcome.Failure -> onWriteFailed(CashAction.Delete, outcome.error)
            }
        }
    }

    /** Discards the draft and proceeds with the pending leave. Guarded while a write runs. */
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
                currencyAcknowledged = false,
            )
        }
        proceed?.invoke(true)
    }

    /** Replaces the draft with the latest stored record, and only after the user has discarded their edits. */
    private fun confirmReload() {
        if (currentState.busy != null) return
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
            CashEditorUiState(
                phase = CashPhase.Locked,
                isNew = balanceId == null,
                concealed = concealed,
            )
        }
    }
}

internal fun CashBalance.toValues(): CashValues = CashValues(
    name = name,
    kind = kind,
    currency = balance.currency,
    balance = balance.amount.toPlainString(),
    note = note,
)
