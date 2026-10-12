package com.mk.daftar.feature.banks

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.BankDetail
import com.mk.daftar.core.domain.repository.BankInput
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.CreateBankUseCase
import com.mk.daftar.core.domain.usecase.DeleteBankUseCase
import com.mk.daftar.core.domain.usecase.ObserveBankDetailUseCase
import com.mk.daftar.core.domain.usecase.ObserveBanksUseCase
import com.mk.daftar.core.domain.usecase.UpdateBankUseCase
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.core.ui.presentation.moneyText
import kotlinx.coroutines.launch

/**
 * Adds, renames and deletes a bank. The record is loaded once; later emissions follow it only while the name is clean, and a
 * change under unsaved edits becomes a conflict that waits for an explicit discard. Deleting a bank that still holds
 * accounts is refused and lists them. A failed write keeps the draft, and a lock drops it without asking.
 */
class BankEditorViewModel(
    private val target: BankEditorTarget,
    observeBanks: ObserveBanksUseCase,
    observeDetail: ObserveBankDetailUseCase,
    private val createBank: CreateBankUseCase,
    private val updateBank: UpdateBankUseCase,
    private val deleteBank: DeleteBankUseCase,
    private val visibility: ValuesVisibility,
) : MviViewModel<BankEditorUiState, BankEditorIntent, BankEffect>(BankEditorUiState()) {

    private val bankId: BankId? = (target as? BankEditorTarget.Existing)?.let { BankId(it.bankId) }
    private var initialized = false
    private var loadedRevision: Long? = null
    private var latest: BankDetail? = null

    /** Advances on every lock. A write that started before a lock reports nothing once the lock has happened. */
    private var lockEpoch = 0

    /** Completes a pending leave. The flag says whether the draft was discarded to allow it. */
    private var pendingLeave: ((Boolean) -> Unit)? = null

    init {
        if (bankId == null) {
            reduce { copy(phase = BankPhase.Ready, isNew = true) }
        } else {
            reduce { copy(isNew = false) }
            viewModelScope.launch {
                observeDetail(bankId).collect { read -> onRead(read) }
            }
        }
        // Every editor follows the session: a lock drops the draft even when the record was never observed.
        viewModelScope.launch {
            observeBanks().collect { read -> if (read == VaultRead.Locked) onLocked() }
        }
        viewModelScope.launch {
            visibility.hiddenFlow().collect { hidden -> reduce { copy(hidden = hidden) } }
        }
    }

    override fun onIntent(intent: BankEditorIntent) {
        when (intent) {
            is BankEditorIntent.NameChanged -> edit { copy(draft = BankDraft(intent.text)) }
            BankEditorIntent.Save -> save()
            BankEditorIntent.DismissFailure -> reduce { copy(failure = null) }
            BankEditorIntent.RequestDelete -> requestConfirmation(BankConfirmation.Delete)
            BankEditorIntent.ConfirmDelete -> confirmDelete()
            BankEditorIntent.RequestReload -> requestConfirmation(BankConfirmation.Reload)
            BankEditorIntent.ConfirmReload -> confirmReload()
            BankEditorIntent.ConfirmDiscard -> confirmDiscard()
            BankEditorIntent.DismissConfirmation -> dismissConfirmation()
            BankEditorIntent.ReviewAccounts -> reviewAccounts()
            BankEditorIntent.DismissBlocked -> reduce { copy(blocked = false) }
        }
    }

    /**
     * The shell asks before it leaves this editor. Without unsaved edits it proceeds at once and [proceed] receives false.
     * Otherwise the discard confirmation opens, and [proceed] receives true once the user discards. A running write
     * ignores the request, because its outcome decides what happens next.
     */
    fun onLeaveRequested(proceed: (discarded: Boolean) -> Unit) {
        val state = currentState
        if (state.busy != null) return
        if (state.completed || !state.dirty) {
            proceed(false)
            return
        }
        pendingLeave = proceed
        reduce { copy(confirmation = BankConfirmation.Discard) }
    }

    private fun onRead(read: VaultRead<BankDetail?>) {
        when (read) {
            VaultRead.Locked -> onLocked()

            VaultRead.Loading -> if (!initialized) reduce { copy(phase = BankPhase.Loading) }

            is VaultRead.Failed -> if (!initialized) reduce { copy(phase = BankPhase.Failed(read.error)) }

            is VaultRead.Ready -> {
                val detail = read.value
                if (detail == null) {
                    if (!currentState.completed) reduce { copy(phase = BankPhase.Missing) }
                } else {
                    onStored(detail)
                }
            }
        }
    }

    private fun onStored(detail: BankDetail) {
        latest = detail
        val dependents = detail.accounts.map {
            DependentAccount(
                name = it.value.name,
                currency = it.value.balance.currency,
                balanceText = it.value.balance.amount.moneyText(),
            )
        }
        when {
            !initialized -> {
                initialized = true
                adopt(detail)
            }

            !currentState.dirty -> adopt(detail)

            // A change under unsaved edits is reported, never merged: the draft and the loaded revision stay as they were.
            detail.bank.revision != loadedRevision -> reduce { copy(conflict = true) }
        }
        reduce { copy(dependents = dependents) }
    }

    /** Takes a stored bank as the baseline and, when the draft allows it, as the draft. */
    private fun adopt(detail: BankDetail) {
        val stored = detail.bank
        loadedRevision = stored.revision
        val name = stored.value.name
        reduce {
            copy(
                phase = BankPhase.Ready,
                isNew = false,
                draft = BankDraft(name),
                baselineName = name,
                storedName = name,
                conflict = false,
            )
        }
    }

    private fun edit(change: BankEditorUiState.() -> BankEditorUiState) {
        val state = currentState
        if (state.busy != null || state.completed) return
        reduce { change() }
    }

    private fun save() {
        val state = currentState
        if (state.busy != null || state.completed || state.conflict) return
        if (state.phase != BankPhase.Ready) return
        reduce { copy(attempted = true) }
        if (state.nameError != null) return
        val input = BankInput(name = state.draft.nameText.trim())
        val id = bankId
        val revision = loadedRevision
        if (id != null && revision == null) return
        reduce { copy(busy = BankBusy.Saving, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = if (id == null || revision == null) {
                createBank(input)
            } else {
                updateBank(id, revision, input)
            }
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> onWritten(outcome.data.value.name, outcome.data.revision)
                is Outcome.Failure -> onWriteFailed(BankAction.Save, outcome.error)
            }
        }
    }

    private fun onWritten(name: String, revision: Long) {
        loadedRevision = revision
        reduce {
            copy(
                busy = null,
                baselineName = name,
                storedName = name,
                draft = BankDraft(name),
                conflict = false,
                completed = true,
                isNew = false,
            )
        }
        emitEffect(BankEffect.Close)
    }

    /**
     * A conflict or a missing record changes what the editor shows. A refused deletion lists the accounts that still hold
     * the bank. Anything else keeps the draft and offers a retry. [StorageError.SessionEnded] is not retried.
     */
    private fun onWriteFailed(action: BankAction, error: StorageError) {
        when (error) {
            StorageError.Conflict -> reduce { copy(busy = null, conflict = true) }
            StorageError.NotFound -> reduce { copy(busy = null, phase = BankPhase.Missing) }
            StorageError.Locked -> onLocked()
            StorageError.BankHasAccounts -> reduce { copy(busy = null, confirmation = null, blocked = true) }
            else -> reduce { copy(busy = null, failure = BankFailure(action, error)) }
        }
    }

    private fun requestConfirmation(confirmation: BankConfirmation) {
        val state = currentState
        if (state.busy != null || state.completed) return
        if (confirmation == BankConfirmation.Delete && state.isNew) return
        if (confirmation == BankConfirmation.Reload && !state.conflict) return
        reduce { copy(confirmation = confirmation) }
    }

    private fun confirmDelete() {
        val state = currentState
        val id = bankId ?: return
        val revision = loadedRevision ?: return
        if (state.busy != null || state.completed || state.confirmation != BankConfirmation.Delete) return
        reduce { copy(confirmation = null, busy = BankBusy.Deleting, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = deleteBank(id, revision)
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> {
                    reduce { copy(busy = null, completed = true) }
                    emitEffect(BankEffect.CloseToList)
                }

                is Outcome.Failure -> onWriteFailed(BankAction.Delete, outcome.error)
            }
        }
    }

    /** Leaves for the bank's detail, where its accounts are listed beside the refusal's explanation. */
    private fun reviewAccounts() {
        val state = currentState
        val id = bankId ?: return
        if (state.busy != null || !state.blocked) return
        reduce { copy(blocked = false) }
        emitEffect(BankEffect.CloseToBank(id.value))
    }

    /** Discards the draft and proceeds with the pending leave. Guarded while a write runs. */
    private fun confirmDiscard() {
        if (currentState.busy != null) return
        val proceed = pendingLeave
        pendingLeave = null
        val stored = currentState.storedName
        reduce {
            copy(
                confirmation = null,
                draft = if (isNew) BankDraft() else BankDraft(stored),
                attempted = false,
                failure = null,
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
            BankEditorUiState(
                phase = BankPhase.Locked,
                isNew = target == BankEditorTarget.New,
                hidden = hidden,
            )
        }
    }
}
