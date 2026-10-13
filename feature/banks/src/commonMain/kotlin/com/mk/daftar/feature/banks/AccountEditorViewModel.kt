package com.mk.daftar.feature.banks

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.BankAccountInput
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.CreateBankAccountUseCase
import com.mk.daftar.core.domain.usecase.DeleteBankAccountUseCase
import com.mk.daftar.core.domain.usecase.ObserveBankAccountUseCase
import com.mk.daftar.core.domain.usecase.ObserveBanksUseCase
import com.mk.daftar.core.domain.usecase.UpdateBankAccountUseCase
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlinx.coroutines.launch

/**
 * Adds, edits, moves and deletes one bank account. Balances and notes are sensitive, so the form is concealed while values
 * are hidden. Changing an existing account's currency relabels its balance; saving then needs an explicit
 * acknowledgment. Each write names the revision it replaces, and a failed write keeps the draft.
 */
class AccountEditorViewModel(
    private val target: AccountEditorTarget,
    observeAccount: ObserveBankAccountUseCase,
    observeBanks: ObserveBanksUseCase,
    private val createAccount: CreateBankAccountUseCase,
    private val updateAccount: UpdateBankAccountUseCase,
    private val deleteAccount: DeleteBankAccountUseCase,
    private val visibility: ValuesVisibility,
) : MviViewModel<AccountEditorUiState, AccountEditorIntent, BankEffect>(AccountEditorUiState()) {

    private val accountId: BankAccountId? = (target as? AccountEditorTarget.Existing)?.let {
        BankAccountId(it.accountId)
    }
    private var initialized = false
    private var loadedRevision: Long? = null
    private var latest: Stored<BankAccount>? = null

    /** Advances on every lock. A write that started before a lock reports nothing once the lock has happened. */
    private var lockEpoch = 0

    /** Completes a pending leave. The flag says whether the draft was discarded to allow it. */
    private var pendingLeave: ((Boolean) -> Unit)? = null

    init {
        if (accountId == null) {
            val preselected = (target as AccountEditorTarget.New).bankId
            val baseline =
                AccountValues(name = "", bankId = preselected, currency = Currency.EGP, balance = "", note = null)
            reduce {
                copy(
                    phase = BankPhase.Ready,
                    isNew = true,
                    draft = AccountDraft(bankId = preselected),
                    baseline = baseline,
                )
            }
        } else {
            viewModelScope.launch {
                observeAccount(accountId).collect { read -> onRead(read) }
            }
        }
        viewModelScope.launch {
            observeBanks().collect { read -> onBanks(read) }
        }
        viewModelScope.launch {
            visibility.hiddenFlow().collect { hidden -> reduce { copy(concealed = hidden) } }
        }
    }

    override fun onIntent(intent: AccountEditorIntent) {
        when (intent) {
            is AccountEditorIntent.NameChanged -> edit { copy(draft = draft.copy(name = intent.text)) }

            is AccountEditorIntent.BankChanged -> edit { copy(draft = draft.copy(bankId = intent.bankId)) }

            is AccountEditorIntent.CurrencyChanged -> edit {
                copy(
                    draft = draft.copy(currency = intent.currency),
                    acknowledged = false,
                )
            }

            is AccountEditorIntent.BalanceChanged -> edit { copy(draft = draft.copy(balanceText = intent.text)) }

            is AccountEditorIntent.NoteChanged -> edit { copy(draft = draft.copy(noteText = intent.text)) }

            is AccountEditorIntent.AcknowledgeCurrency -> edit { copy(acknowledged = intent.checked) }

            AccountEditorIntent.Save -> save()

            AccountEditorIntent.DismissFailure -> reduce { copy(failure = null) }

            AccountEditorIntent.RequestDelete -> requestConfirmation(BankConfirmation.Delete)

            AccountEditorIntent.ConfirmDelete -> confirmDelete()

            AccountEditorIntent.RequestReload -> requestConfirmation(BankConfirmation.Reload)

            AccountEditorIntent.ConfirmReload -> confirmReload()

            AccountEditorIntent.ConfirmDiscard -> confirmDiscard()

            AccountEditorIntent.DismissConfirmation -> dismissConfirmation()

            AccountEditorIntent.RevealForm -> visibility.reveal()
        }
    }

    /** The shell asks before it leaves: same contract as the bank editor. */
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

    private fun onBanks(read: VaultRead<List<Stored<Bank>>>) {
        when (read) {
            VaultRead.Locked -> onLocked()

            VaultRead.Loading -> Unit

            is VaultRead.Failed -> Unit

            is VaultRead.Ready -> reduce {
                copy(
                    banks = read.value.map {
                        BankOption(it.value.id.value, it.value.name, it.value.institutionId?.value)
                    },
                )
            }
        }
    }

    private fun onRead(read: VaultRead<Stored<BankAccount>?>) {
        when (read) {
            VaultRead.Locked -> onLocked()

            VaultRead.Loading -> if (!initialized) reduce { copy(phase = BankPhase.Loading) }

            is VaultRead.Failed -> if (!initialized) reduce { copy(phase = BankPhase.Failed(read.error)) }

            is VaultRead.Ready -> {
                val stored = read.value
                if (stored == null) {
                    if (!currentState.completed) reduce { copy(phase = BankPhase.Missing) }
                } else {
                    onStored(stored)
                }
            }
        }
    }

    private fun onStored(stored: Stored<BankAccount>) {
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

    /** Takes a stored account as the baseline and, when the draft allows it, as the draft. */
    private fun adopt(stored: Stored<BankAccount>) {
        loadedRevision = stored.revision
        val values = stored.value.toValues()
        reduce {
            copy(
                phase = BankPhase.Ready,
                isNew = false,
                draft = values.toDraft(),
                baseline = values,
                acknowledged = false,
                conflict = false,
            )
        }
    }

    private fun edit(change: AccountEditorUiState.() -> AccountEditorUiState) {
        val state = currentState
        if (state.busy != null || state.completed) return
        reduce { change() }
    }

    private fun save() {
        val state = currentState
        if (state.busy != null || state.completed || state.concealed || state.conflict) return
        if (state.phase != BankPhase.Ready) return
        reduce { copy(attempted = true) }
        if (state.errors.any) return
        val bank = state.draft.bankId?.let { BankId(it) } ?: return
        val amount = parseBalance(state.draft.balanceText) ?: return
        val input = BankAccountInput(
            bankId = bank,
            name = state.draft.name.trim(),
            balance = Money(amount, state.draft.currency),
            note = normalizedNote(state.draft.noteText),
        )
        val id = accountId
        val revision = loadedRevision
        if (id != null && revision == null) return
        reduce { copy(busy = BankBusy.Saving, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = if (id == null || revision == null) {
                createAccount(input)
            } else {
                updateAccount(id, revision, input)
            }
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> onWritten(outcome.data)
                is Outcome.Failure -> onWriteFailed(BankAction.Save, outcome.error)
            }
        }
    }

    private fun onWritten(stored: Stored<BankAccount>) {
        latest = stored
        loadedRevision = stored.revision
        val values = stored.value.toValues()
        reduce {
            copy(
                busy = null,
                baseline = values,
                draft = values.toDraft(),
                acknowledged = false,
                conflict = false,
                completed = true,
                isNew = false,
            )
        }
        emitEffect(BankEffect.CloseToBank(stored.value.bankId.value))
    }

    /**
     * A conflict or a missing record changes what the editor shows; anything else keeps the draft and offers a retry.
     * [StorageError.SessionEnded] keeps its uncertain outcome: it is not retried automatically, and the lock follows.
     */
    private fun onWriteFailed(action: BankAction, error: StorageError) {
        when (error) {
            StorageError.Conflict -> reduce { copy(busy = null, conflict = true) }
            StorageError.NotFound -> reduce { copy(busy = null, phase = BankPhase.Missing) }
            StorageError.Locked -> onLocked()
            else -> reduce { copy(busy = null, failure = BankFailure(action, error)) }
        }
    }

    private fun requestConfirmation(confirmation: BankConfirmation) {
        val state = currentState
        if (state.busy != null || state.completed || state.isNew) return
        if (confirmation == BankConfirmation.Reload && !state.conflict) return
        reduce { copy(confirmation = confirmation) }
    }

    private fun confirmDelete() {
        val state = currentState
        val id = accountId ?: return
        val revision = loadedRevision ?: return
        if (state.busy != null || state.completed || state.confirmation != BankConfirmation.Delete) return
        val bank = state.baseline?.bankId ?: return
        reduce { copy(confirmation = null, busy = BankBusy.Deleting, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = deleteAccount(id, revision)
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> {
                    reduce { copy(busy = null, completed = true) }
                    emitEffect(BankEffect.CloseToBank(bank))
                }

                is Outcome.Failure -> onWriteFailed(BankAction.Delete, outcome.error)
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
                acknowledged = false,
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
            AccountEditorUiState(
                phase = BankPhase.Locked,
                isNew = target is AccountEditorTarget.New,
                concealed = concealed,
            )
        }
    }
}

internal fun Stored<BankAccount>.toValues(): AccountValues = value.toValues()

internal fun BankAccount.toValues(): AccountValues = AccountValues(
    name = name,
    bankId = bankId.value,
    currency = balance.currency,
    balance = balance.amount.toPlainString(),
    note = note,
)

internal fun AccountValues.toDraft(): AccountDraft = AccountDraft(
    name = name,
    bankId = bankId,
    currency = currency,
    balanceText = balance,
    noteText = note.orEmpty(),
)
