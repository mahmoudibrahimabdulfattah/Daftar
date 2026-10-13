package com.mk.daftar.feature.journal.zakat

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveZakatDayUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatPaymentsUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.domain.usecase.SetZakatYearPaidUseCase
import com.mk.daftar.core.domain.zakat.ZakatYearStatus
import com.mk.daftar.core.domain.zakat.linkedTotalEgp
import com.mk.daftar.core.domain.zakat.unassigned
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId
import com.mk.daftar.core.navigation.ZakatAddRoute
import com.mk.daftar.core.navigation.ZakatCalculatorRoute
import com.mk.daftar.core.navigation.ZakatDateRoute
import com.mk.daftar.core.navigation.ZakatEditRoute
import com.mk.daftar.core.navigation.ZakatHistoryRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.hiddenFlow
import com.mk.daftar.feature.journal.nonReadyPhase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** The declaration of one year, its linked payments and the unassigned payments. Amounts are shown only when visible. */
data class ZakatYearUiState(
    val phase: JournalPhase = JournalPhase.Loading,
    val hijriYear: Int,
    val gregorianYear: Int? = null,
    val status: ZakatYearStatus? = null,
    val date: LocalDate? = null,
    val adjusted: Boolean = false,
    /** The year tracking began with, so its first-use explanation shows here. */
    val firstUse: Boolean = false,
    /** The declaration as the store has it. */
    val committed: Boolean = false,
    /** What the checkbox shows: the committed value, or the value being saved. */
    val checked: Boolean = false,
    val saving: Boolean = false,
    /** A save failed; the committed value is unchanged and the save can be retried. */
    val saveFailed: Boolean = false,
    /** Set after a save commits: true for a mark, false for a reopen. Null otherwise. */
    val justSaved: Boolean? = null,
    val confirmUnmark: Boolean = false,
    val linked: List<ZakatPaymentRow> = emptyList(),
    val linkedTotal: Decimal = Decimal.ZERO,
    val unassigned: List<ZakatPaymentRow> = emptyList(),
    val hidden: Boolean = false,
) : UiState

sealed interface ZakatYearIntent : UiIntent {
    /** The checkbox was toggled to [checked]. Unchecking asks for confirmation first. */
    data class SetChecked(val checked: Boolean) : ZakatYearIntent

    data object ConfirmUnmark : ZakatYearIntent

    data object DismissUnmark : ZakatYearIntent

    data object RetrySave : ZakatYearIntent

    data object ToggleHidden : ZakatYearIntent

    data object RecordPayment : ZakatYearIntent

    data class OpenPayment(val paymentId: ZakatPaymentId) : ZakatYearIntent

    data object OpenHistory : ZakatYearIntent

    data object OpenCalculator : ZakatYearIntent

    data object OpenDate : ZakatYearIntent
}

/**
 * One declared year. The declaration is written only through [SetZakatYearPaidUseCase]: a failed write keeps the committed
 * value and offers a retry, and nothing reports success before the write commits. Payments never change the declaration.
 */
class ZakatYearViewModel(
    private val hijriYear: Int,
    observeTracking: ObserveZakatTrackingUseCase,
    observePayments: ObserveZakatPaymentsUseCase,
    observeDay: ObserveZakatDayUseCase,
    private val setPaid: SetZakatYearPaidUseCase,
    private val visibility: ValuesVisibility,
) : MviViewModel<ZakatYearUiState, ZakatYearIntent, JournalEffect>(ZakatYearUiState(hijriYear = hijriYear)) {

    /** The value of the last save attempt, retried by [ZakatYearIntent.RetrySave]. */
    private var attemptedPaid: Boolean = false

    init {
        viewModelScope.launch {
            combine(observeTracking(), observePayments(), observeDay(), visibility.hiddenFlow()) {
                    tracking,
                    payments,
                    day,
                    hidden,
                ->
                yearStateOf(hijriYear, tracking, payments, day, hidden)
            }.collect { next -> reduce { next.carriedFrom(this) } }
        }
    }

    override fun onIntent(intent: ZakatYearIntent) {
        when (intent) {
            is ZakatYearIntent.SetChecked -> setChecked(intent.checked)
            ZakatYearIntent.ConfirmUnmark -> confirmUnmark()
            ZakatYearIntent.DismissUnmark -> reduce { copy(confirmUnmark = false, checked = committed) }
            ZakatYearIntent.RetrySave -> if (currentState.saveFailed) save(attemptedPaid)
            ZakatYearIntent.ToggleHidden -> visibility.toggle()
            ZakatYearIntent.RecordPayment -> emitEffect(JournalEffect.Navigate(ZakatAddRoute(forHijriYear = hijriYear)))
            is ZakatYearIntent.OpenPayment -> emitEffect(JournalEffect.Navigate(ZakatEditRoute(intent.paymentId.value)))
            ZakatYearIntent.OpenHistory -> emitEffect(JournalEffect.Navigate(ZakatHistoryRoute))
            ZakatYearIntent.OpenCalculator -> emitEffect(JournalEffect.Navigate(ZakatCalculatorRoute))
            ZakatYearIntent.OpenDate -> emitEffect(JournalEffect.Navigate(ZakatDateRoute(hijriYear)))
        }
    }

    private fun setChecked(checked: Boolean) {
        val state = currentState
        if (state.phase != JournalPhase.Ready || state.saving || checked == state.committed) return
        if (checked) save(true) else reduce { copy(confirmUnmark = true) }
    }

    private fun confirmUnmark() {
        val state = currentState
        if (!state.confirmUnmark || state.saving) return
        reduce { copy(confirmUnmark = false) }
        save(false)
    }

    private fun save(paid: Boolean) {
        attemptedPaid = paid
        reduce { copy(saving = true, checked = paid, saveFailed = false, justSaved = null) }
        viewModelScope.launch {
            when (val outcome = setPaid(hijriYear, paid)) {
                is Outcome.Success -> reduce {
                    copy(saving = false, committed = paid, checked = paid, justSaved = paid)
                }

                is Outcome.Failure -> reduce {
                    copy(saving = false, checked = committed, saveFailed = true)
                }
            }
        }
    }
}

/** Builds the state of one year from the reads. Only a ready tracking read can place the year. */
internal fun yearStateOf(
    hijriYear: Int,
    tracking: VaultRead<ZakatTrackingState>,
    payments: VaultRead<List<Stored<ZakatPayment>>>,
    today: LocalDate,
    hidden: Boolean,
): ZakatYearUiState {
    val blocking = blockingPhaseOf(tracking, payments)
    if (blocking != null) return ZakatYearUiState(phase = blocking, hijriYear = hijriYear, hidden = hidden)
    val declared =
        (tracking as VaultRead.Ready).value.summariesNewestFirst(today).firstOrNull { it.hijriYear == hijriYear }
            ?: return ZakatYearUiState(phase = JournalPhase.Missing, hijriYear = hijriYear, hidden = hidden)
    val all = (payments as VaultRead.Ready).value.map { it.value }
    val linked = all.filter { it.forHijriYear == hijriYear }
    val committed = (tracking.value.years.first { it.hijriYear == hijriYear }).paid
    return ZakatYearUiState(
        phase = JournalPhase.Ready,
        hijriYear = hijriYear,
        gregorianYear = declared.gregorianYear,
        status = declared.status,
        date = declared.date,
        adjusted = declared.adjusted,
        firstUse = tracking.value.startYear == hijriYear,
        committed = committed,
        checked = committed,
        linked = linked.map { it.toRow() },
        linkedTotal = linked.linkedTotalEgp(hijriYear),
        unassigned = all.unassigned().map { it.toRow() },
        hidden = hidden,
    )
}

/** Keeps what a save is doing across a new read. A read that is not ready replaces the whole state. */
private fun ZakatYearUiState.carriedFrom(previous: ZakatYearUiState): ZakatYearUiState {
    if (phase != JournalPhase.Ready) return this
    return copy(
        checked = if (previous.saving) previous.checked else committed,
        saving = previous.saving,
        saveFailed = previous.saveFailed,
        justSaved = previous.justSaved,
        confirmUnmark = previous.confirmUnmark,
    )
}

private fun ZakatPayment.toRow(): ZakatPaymentRow = ZakatPaymentRow(id, paidOn, amount.amount, note)
