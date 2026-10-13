package com.mk.daftar.feature.journal.zakat

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.hijri.HijriCalendar
import com.mk.daftar.core.domain.repository.ZakatPaymentInput
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.CreateZakatPaymentUseCase
import com.mk.daftar.core.domain.usecase.DeleteZakatPaymentUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatPaymentUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatPaymentsUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.domain.usecase.UpdateZakatPaymentUseCase
import com.mk.daftar.core.domain.zakat.ZakatYearStatus
import com.mk.daftar.core.domain.zakat.zakatToday
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.journal.JournalAction
import com.mk.daftar.feature.journal.JournalBusy
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalFailure
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.hiddenFlow
import com.mk.daftar.feature.journal.isHidden
import com.mk.daftar.feature.journal.isoText
import com.mk.daftar.feature.journal.localDate
import com.mk.daftar.feature.journal.parseIsoDate
import com.mk.daftar.feature.journal.reveal
import kotlin.time.Clock
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * Adds and edits a zakat payment with a draft that is never replaced silently.
 *
 * The record is loaded once. Later emissions follow it only while the draft is clean; a change under a dirty draft becomes
 * a conflict, and reloading it needs an explicit discard. Writes carry the loaded revision. A failed write keeps the draft.
 * Leaving with unsaved edits asks first; a lock drops the draft without asking.
 */
class ZakatEditorViewModel(
    private val target: ZakatEditorTarget,
    observePayment: ObserveZakatPaymentUseCase,
    observePayments: ObserveZakatPaymentsUseCase,
    observeTracking: ObserveZakatTrackingUseCase,
    private val createPayment: CreateZakatPaymentUseCase,
    private val updatePayment: UpdateZakatPaymentUseCase,
    private val deletePayment: DeleteZakatPaymentUseCase,
    private val visibility: ValuesVisibility,
    private val clock: Clock,
    private val timeZone: TimeZone,
    /** What the route asked for: an estimate draft and a preselected year. Both are optional. */
    private val launch: ZakatEditorLaunch = ZakatEditorLaunch(),
) : MviViewModel<ZakatEditorUiState, ZakatEditorIntent, JournalEffect>(ZakatEditorUiState()) {

    private val paymentId: ZakatPaymentId? = (target as? ZakatEditorTarget.Existing)?.let {
        ZakatPaymentId(it.paymentId)
    }
    private var initialized = false
    private var loadedRevision: Long? = null
    private var latest: Stored<ZakatPayment>? = null

    /** Advances on every lock. A write that started before a lock reports nothing once the lock has happened. */
    private var lockEpoch = 0

    /** Completes a pending leave. The flag says whether the draft was discarded to allow it. */
    private var pendingLeave: ((Boolean) -> Unit)? = null

    /** The year the route chose, or null when the editor chooses the default. */
    private val initialYear: Int? = launch.initialYear

    init {
        val today = clock.now().localDate(timeZone)
        if (paymentId == null) {
            val iso = today.isoText()
            // A prefilled amount is an editable draft: it differs from the baseline, so leaving asks before discarding it.
            // It is taken only while values are visible, so a concealed amount never reaches the form.
            val prefill = launch.prefillAmount?.takeIf { !visibility.isHidden() }
            reduce {
                copy(
                    phase = JournalPhase.Ready,
                    isNew = true,
                    today = today,
                    draft = ZakatDraft(dateText = iso, amountText = prefill.orEmpty(), forHijriYear = initialYear),
                    baseline = ZakatValues(date = iso, amount = "", note = null, forHijriYear = initialYear),
                    yearReady = initialYear != null,
                    yearTouched = initialYear != null,
                    prefilled = prefill != null,
                )
            }
            viewModelScope.launch {
                observeTracking().collect { read -> onTracking(read) }
            }
            // A new payment has no record to observe, so it follows the session through the list: a lock drops its draft.
            viewModelScope.launch {
                observePayments().collect { read -> if (read == VaultRead.Locked) onLocked() }
            }
        } else {
            reduce { copy(isNew = false, today = today) }
            viewModelScope.launch {
                observePayment(paymentId).collect { read -> onRead(read) }
            }
            viewModelScope.launch {
                observeTracking().collect { read -> onTracking(read) }
            }
        }
        viewModelScope.launch {
            visibility.hiddenFlow().collect { hidden -> reduce { copy(concealed = hidden) } }
        }
    }

    override fun onIntent(intent: ZakatEditorIntent) {
        when (intent) {
            is ZakatEditorIntent.DateChanged -> edit { copy(dateText = intent.text) }
            is ZakatEditorIntent.AmountChanged -> edit { copy(amountText = intent.text) }
            is ZakatEditorIntent.NoteChanged -> edit { copy(noteText = intent.text) }
            is ZakatEditorIntent.YearSelected -> selectYear(intent.hijriYear)
            ZakatEditorIntent.Save -> save()
            ZakatEditorIntent.DismissFailure -> reduce { copy(failure = null) }
            ZakatEditorIntent.RequestDelete -> requestConfirmation(ZakatConfirmation.Delete)
            ZakatEditorIntent.ConfirmDelete -> confirmDelete()
            ZakatEditorIntent.RequestReload -> requestConfirmation(ZakatConfirmation.Reload)
            ZakatEditorIntent.ConfirmReload -> confirmReload()
            ZakatEditorIntent.ConfirmDiscard -> confirmDiscard()
            ZakatEditorIntent.DismissConfirmation -> dismissConfirmation()
            ZakatEditorIntent.RevealForm -> visibility.reveal()
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
        reduce { copy(confirmation = ZakatConfirmation.Discard) }
    }

    /**
     * Learns the declared years for the picker. A new payment also takes its default year from the first read that
     * succeeds: the explicit year, else the oldest due year, else the next Ramadan year. A user's own choice is kept.
     */
    private fun onTracking(read: VaultRead<ZakatTrackingState>) {
        val tracking = (read as? VaultRead.Ready)?.value
        val state = currentState
        if (tracking == null) {
            if (!state.yearReady && read !is VaultRead.Loading) reduce { copy(yearReady = true) }
            return
        }
        val today = zakatToday(clock.now())
        reduce {
            val declared = tracking.summariesNewestFirst(today)
            val linked = draft.forHijriYear?.takeIf { year -> declared.none { it.hijriYear == year } }
            val choices = (declared + listOfNotNull(linked?.let { unrecordedSummary(it) }))
                .sortedByDescending { it.hijriYear }
            if (!yearReady && !yearTouched && isNew) {
                val default = tracking.dueYears(today).firstOrNull()
                    ?: nextRamadanOf(today, tracking.adjustments())?.hijriYear
                copy(
                    yearChoices = choices,
                    yearReady = true,
                    draft = draft.copy(forHijriYear = default),
                    baseline = baseline?.copy(forHijriYear = default),
                )
            } else {
                copy(yearChoices = choices, yearReady = true)
            }
        }
    }

    private fun selectYear(hijriYear: Int?) {
        val state = currentState
        if (state.busy != null || state.completed || state.concealed) return
        reduce { copy(draft = draft.copy(forHijriYear = hijriYear), yearTouched = true) }
    }

    private fun onRead(read: VaultRead<Stored<ZakatPayment>?>) {
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

    private fun onStored(stored: Stored<ZakatPayment>) {
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
    private fun adopt(stored: Stored<ZakatPayment>) {
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

    private fun edit(change: ZakatDraft.() -> ZakatDraft) {
        val state = currentState
        if (state.busy != null || state.completed) return
        reduce { copy(draft = draft.change()) }
    }

    private fun save() {
        val state = currentState
        val today = state.today ?: return
        if (state.busy != null || state.completed || state.concealed || state.conflict) return
        if (state.phase != JournalPhase.Ready || !state.yearReady) return
        reduce { copy(attempted = true) }
        if (state.draft.errors(today).any) return
        val draft = state.draft
        val date = parseIsoDate(draft.dateText) ?: return
        val amount = draft.paymentAmount() ?: return
        val input = ZakatPaymentInput(
            paidOn = date,
            amount = Money(amount, Currency.EGP),
            note = draft.noteText.trim().ifEmpty { null },
            forHijriYear = draft.forHijriYear,
        )
        val id = paymentId
        val revision = loadedRevision
        if (id != null && revision == null) return
        reduce { copy(busy = JournalBusy.Saving, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = if (id == null || revision == null) {
                createPayment(input)
            } else {
                updatePayment(id, revision, input)
            }
            if (epoch != lockEpoch) return@launch
            when (outcome) {
                is Outcome.Success -> onWritten(outcome.data)
                is Outcome.Failure -> onWriteFailed(JournalAction.Save, outcome.error)
            }
        }
    }

    private fun onWritten(stored: Stored<ZakatPayment>) {
        latest = stored
        loadedRevision = stored.revision
        val values = stored.value.toValues()
        reduce { copy(busy = null, baseline = values, conflict = false, completed = true, isNew = false) }
        emitEffect(JournalEffect.Completed)
    }

    /**
     * A conflict or a missing record changes what the editor shows; anything else keeps the draft and offers a retry.
     * [StorageError.SessionEnded] keeps its uncertain outcome: it is not retried automatically, and the lock follows.
     */
    private fun onWriteFailed(action: JournalAction, error: StorageError) {
        when (error) {
            StorageError.Conflict -> reduce { copy(busy = null, conflict = true) }
            StorageError.NotFound -> reduce { copy(busy = null, phase = JournalPhase.Missing) }
            StorageError.Locked -> onLocked()
            else -> reduce { copy(busy = null, failure = JournalFailure(action, error)) }
        }
    }

    private fun requestConfirmation(confirmation: ZakatConfirmation) {
        val state = currentState
        if (state.busy != null || state.completed || state.isNew) return
        if (confirmation == ZakatConfirmation.Reload && !state.conflict) return
        reduce { copy(confirmation = confirmation) }
    }

    private fun confirmDelete() {
        val state = currentState
        val id = paymentId ?: return
        val revision = loadedRevision ?: return
        if (state.busy != null || state.completed || state.confirmation != ZakatConfirmation.Delete) return
        reduce { copy(confirmation = null, busy = JournalBusy.Deleting, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = deletePayment(id, revision)
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
            ZakatEditorUiState(
                phase = JournalPhase.Locked,
                isNew = target == ZakatEditorTarget.New,
                concealed = concealed,
            )
        }
    }
}

internal fun ZakatPayment.toValues(): ZakatValues = ZakatValues(
    date = paidOn.isoText(),
    amount = amount.amount.toPlainString(),
    note = note,
    forHijriYear = forHijriYear,
)

/** A year a payment is linked to but the declarations do not list. It is shown once, unchecked, with its calculated date. */
internal fun unrecordedSummary(hijriYear: Int): ZakatYearSummary {
    val date = checkNotNull(HijriCalendar.ramadanStart(hijriYear)) { "linked years are validated" }
    return ZakatYearSummary(
        hijriYear = hijriYear,
        gregorianYear = date.year,
        status = ZakatYearStatus.UPCOMING,
        date = date,
        adjusted = false,
    )
}
