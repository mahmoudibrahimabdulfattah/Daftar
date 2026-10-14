package com.mk.daftar.feature.investments

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.InvestmentInput
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.CreateInvestmentUseCase
import com.mk.daftar.core.domain.usecase.DeleteInvestmentUseCase
import com.mk.daftar.core.domain.usecase.ObserveInvestmentUseCase
import com.mk.daftar.core.domain.usecase.UpdateInvestmentUseCase
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.model.InvestmentAccountId
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch

/**
 * Adds and edits one investment account. A new account starts with the broker picker and saves only from the form.
 * The record is loaded once; later changes follow it while the draft is clean, and a change under a dirty draft becomes
 * a conflict that needs an explicit reload. Writes carry the loaded revision, and a failed write keeps the draft. Editing
 * the amount moves the value date to Now unless the user then chooses a date; formatting-only changes do not.
 */
class InvestmentEditorViewModel(
    private val target: InvestmentEditorTarget,
    observeInvestment: ObserveInvestmentUseCase,
    private val createInvestment: CreateInvestmentUseCase,
    private val updateInvestment: UpdateInvestmentUseCase,
    private val deleteInvestment: DeleteInvestmentUseCase,
    private val visibility: ValuesVisibility,
    private val clock: Clock,
) : MviViewModel<InvestmentEditorUiState, InvestmentEditorIntent, InvestmentEffect>(InvestmentEditorUiState()) {
    private val accountId: InvestmentAccountId? = (target as? InvestmentEditorTarget.Existing)?.let {
        InvestmentAccountId(it.investmentId)
    }
    private var initialized = false
    private var loadedRevision: Long? = null
    private var latest: Stored<InvestmentAccount>? = null

    /** Advances on every lock. A write that started before a lock reports nothing once the lock has happened. */
    private var lockEpoch = 0

    /** Completes a pending leave. The flag says whether the draft was discarded to allow it. */
    private var pendingLeave: ((Boolean) -> Unit)? = null

    init {
        if (accountId == null) {
            reduce { copy(phase = InvestmentPhase.Ready, isNew = true, picking = true, baseline = null) }
        } else {
            reduce { copy(isNew = false, picking = false) }
            viewModelScope.launch {
                observeInvestment(accountId).collect { read -> onRead(read) }
            }
        }
        viewModelScope.launch {
            visibility.hiddenFlow().collect { hidden -> reduce { copy(concealed = hidden) } }
        }
    }

    override fun onIntent(intent: InvestmentEditorIntent) {
        when (intent) {
            is InvestmentEditorIntent.ProviderPicked -> pickProvider(intent)

            InvestmentEditorIntent.ReopenPicker -> {
                val state = currentState
                if (state.busy == null && !state.completed && !state.concealed) reduce { copy(picking = true) }
            }

            InvestmentEditorIntent.ClosePicker -> {
                if (!currentState.isNew) reduce { copy(picking = false) }
            }

            is InvestmentEditorIntent.NameChanged -> edit { copy(name = intent.text) }

            is InvestmentEditorIntent.AmountChanged -> edit {
                // Formatting-only changes such as 100 to 100.00 keep the date; any other change dates the value now.
                val changed = canonicalAmountText(intent.text) != canonicalAmountText(amountText)
                copy(amountText = intent.text, asOf = if (changed) ValueDateChoice.Now else asOf)
            }

            is InvestmentEditorIntent.NoteChanged -> edit { copy(noteText = intent.text) }

            InvestmentEditorIntent.SetToNow -> edit { copy(asOf = ValueDateChoice.Now) }

            is InvestmentEditorIntent.DateTimeChosen -> chooseDateTime(intent.instant)

            InvestmentEditorIntent.Save -> save()

            InvestmentEditorIntent.DismissFailure -> reduce { copy(failure = null) }

            InvestmentEditorIntent.RequestDelete -> requestConfirmation(InvestmentConfirmation.Delete)

            InvestmentEditorIntent.ConfirmDelete -> confirmDelete()

            InvestmentEditorIntent.RequestReload -> requestConfirmation(InvestmentConfirmation.Reload)

            InvestmentEditorIntent.ConfirmReload -> confirmReload()

            InvestmentEditorIntent.ConfirmDiscard -> confirmDiscard()

            InvestmentEditorIntent.DismissConfirmation -> dismissConfirmation()

            InvestmentEditorIntent.RevealForm -> visibility.reveal()
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
        reduce { copy(confirmation = InvestmentConfirmation.Discard) }
    }

    private fun pickProvider(intent: InvestmentEditorIntent.ProviderPicked) {
        val state = currentState
        if (state.busy != null || state.completed || state.concealed) return
        val draft = state.draft
        // The suggestion only replaces a blank name or the previous suggestion, never a name the user typed.
        val replaceName = draft.name.isBlank() || draft.name == draft.suggestedName
        val suggestion = intent.suggestedName
        val name = if (replaceName && suggestion != null) suggestion else draft.name
        reduce {
            copy(
                picking = false,
                draft = this.draft.copy(
                    providerChosen = true,
                    providerId = intent.providerId,
                    name = name,
                    suggestedName = if (replaceName) suggestion else null,
                ),
            )
        }
    }

    private fun chooseDateTime(instant: Instant?) {
        val state = currentState
        if (state.busy != null || state.completed || state.concealed) return
        val now = clock.now()
        val chosen = instant?.takeIf { it >= InvestmentAccount.EARLIEST_VALUE_AS_OF && it <= now }
        reduce {
            copy(
                dateInvalid = chosen == null,
                draft = if (chosen != null) draft.copy(asOf = ValueDateChoice.At(chosen)) else draft,
            )
        }
    }

    private fun onRead(read: VaultRead<Stored<InvestmentAccount>?>) {
        when (read) {
            VaultRead.Locked -> onLocked()

            VaultRead.Loading -> if (!initialized) reduce { copy(phase = InvestmentPhase.Loading) }

            is VaultRead.Failed -> if (!initialized) reduce { copy(phase = InvestmentPhase.Failed(read.error)) }

            is VaultRead.Ready -> {
                val stored = read.value
                if (stored == null) {
                    if (!currentState.completed) reduce { copy(phase = InvestmentPhase.Missing) }
                } else {
                    onStored(stored)
                }
            }
        }
    }

    private fun onStored(stored: Stored<InvestmentAccount>) {
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

    /** Takes a stored account as the new baseline and, when the draft allows it, as the draft. */
    private fun adopt(stored: Stored<InvestmentAccount>) {
        loadedRevision = stored.revision
        val values = stored.value.toValues()
        reduce {
            copy(
                phase = InvestmentPhase.Ready,
                draft = values.toDraft(),
                baseline = values,
                conflict = false,
                picking = false,
            )
        }
    }

    /** A concealed form takes no edits: its fields are not shown, so nothing can reach the draft while values are hidden. */
    private fun edit(change: InvestmentDraft.() -> InvestmentDraft) {
        val state = currentState
        if (state.busy != null || state.completed || state.concealed || state.picking) return
        reduce { copy(draft = draft.change()) }
    }

    private fun save() {
        val state = currentState
        if (state.busy != null || state.completed || state.concealed || state.conflict || state.picking) return
        if (state.phase != InvestmentPhase.Ready) return
        reduce { copy(attempted = true) }
        val draft = state.draft
        if (draft.errors().any) return
        val amount = draft.amountValue() ?: return
        val now = clock.now()
        val valueAsOf = when (val choice = draft.asOf) {
            ValueDateChoice.Now -> now
            is ValueDateChoice.At -> choice.instant
        }
        if (valueAsOf < InvestmentAccount.EARLIEST_VALUE_AS_OF || valueAsOf > now) {
            reduce { copy(dateInvalid = true) }
            return
        }
        val input = InvestmentInput(
            providerId = draft.providerId,
            name = draft.name.trim(),
            amount = amount,
            note = draft.noteText.trim().ifEmpty { null },
            valueAsOf = valueAsOf,
        )
        val id = accountId
        val revision = loadedRevision
        if (id != null && revision == null) return
        reduce { copy(busy = InvestmentBusy.Saving, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = if (id == null || revision == null) {
                createInvestment(input)
            } else {
                updateInvestment(id, revision, input)
            }
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> onWritten(outcome.data)
                is Outcome.Failure -> onWriteFailed(InvestmentAction.Save, outcome.error)
            }
        }
    }

    private fun onWritten(stored: Stored<InvestmentAccount>) {
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
            )
        }
        emitEffect(InvestmentEffect.Completed)
    }

    /**
     * A conflict or a missing record changes what the editor shows; anything else keeps the draft and offers a retry.
     * [StorageError.SessionEnded] keeps its uncertain outcome: it is not retried automatically, and the lock follows.
     */
    private fun onWriteFailed(action: InvestmentAction, error: StorageError) {
        when (error) {
            StorageError.Conflict -> reduce { copy(busy = null, conflict = true) }
            StorageError.NotFound -> reduce { copy(busy = null, phase = InvestmentPhase.Missing) }
            StorageError.Locked -> onLocked()
            else -> reduce { copy(busy = null, failure = InvestmentFailure(action, error)) }
        }
    }

    private fun requestConfirmation(confirmation: InvestmentConfirmation) {
        val state = currentState
        if (state.busy != null || state.completed || state.isNew) return
        if (confirmation == InvestmentConfirmation.Reload && !state.conflict) return
        reduce { copy(confirmation = confirmation) }
    }

    private fun confirmDelete() {
        val state = currentState
        val id = accountId ?: return
        val revision = loadedRevision ?: return
        if (state.busy != null || state.completed || state.confirmation != InvestmentConfirmation.Delete) return
        reduce { copy(confirmation = null, busy = InvestmentBusy.Deleting, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = deleteInvestment(id, revision)
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> {
                    reduce { copy(busy = null, completed = true) }
                    emitEffect(InvestmentEffect.Completed)
                }

                is Outcome.Failure -> onWriteFailed(InvestmentAction.Delete, outcome.error)
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
                draft = baseline?.toDraft() ?: InvestmentDraft(),
                picking = baseline == null && isNew,
                attempted = false,
                failure = null,
                dateInvalid = false,
            )
        }
        proceed?.invoke(true)
    }

    /** Replaces the draft with the latest stored account, and only after the user has discarded their edits. */
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
            InvestmentEditorUiState(
                phase = InvestmentPhase.Locked,
                isNew = accountId == null,
                picking = accountId == null,
                concealed = concealed,
            )
        }
    }
}

internal fun InvestmentAccount.toValues(): InvestmentValues = InvestmentValues(
    providerId = providerId,
    name = name,
    amount = amount.amount.toPlainString(),
    note = note,
    asOf = ValueDateChoice.At(valueAsOf),
)
