package com.mk.daftar.feature.journal.zakat

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.domain.hijri.HijriCalendar
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveZakatDayUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.domain.zakat.ZakatEstimate
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.navigation.PricesRoute
import com.mk.daftar.core.navigation.ZakatAddRoute
import com.mk.daftar.core.navigation.ZakatDateRoute
import com.mk.daftar.core.navigation.ZakatHistoryRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.hiddenFlow
import com.mk.daftar.feature.journal.nonReadyPhase
import com.mk.daftar.feature.journal.reveal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** The next Ramadan: its Hijri year, its adjusted civil date and the civil days from today. */
data class NextRamadanUi(val hijriYear: Int, val date: LocalDate, val daysRemaining: Int, val adjusted: Boolean)

/**
 * The calculator. The estimate is always current: it reads the same valuation as Home and never a stored snapshot.
 * [estimate] is null while the valuation is loading.
 */
data class ZakatCalculatorUiState(
    val phase: JournalPhase = JournalPhase.Loading,
    val nextRamadan: NextRamadanUi? = null,
    /** Today is past the last supported Ramadan, so no next date exists. */
    val nextUnavailable: Boolean = false,
    val estimate: ZakatEstimate? = null,
    val hidden: Boolean = false,
) : UiState {
    /**
     * The amount offered to the payment editor, or null when no positive estimate is available. Never offered while
     * values are hidden, because the draft would reveal a private figure.
     */
    val prefill: Decimal?
        get() = (estimate as? ZakatEstimate.Available)?.prefillEgp?.takeIf { !hidden }
}

sealed interface ZakatCalculatorIntent : UiIntent {
    data object ToggleHidden : ZakatCalculatorIntent

    /** Shows the values, which is the only way to record an estimate while they are hidden. */
    data object RevealForRecord : ZakatCalculatorIntent

    /** Opens the payment editor with the displayed rounded estimate as an editable draft. */
    data object RecordWithEstimate : ZakatCalculatorIntent

    data object AdjustNextRamadan : ZakatCalculatorIntent

    data object ReviewPrices : ZakatCalculatorIntent

    data object OpenYearlyStatus : ZakatCalculatorIntent
}

class ZakatCalculatorViewModel(
    /** The live estimate, from the same valuation Home observes. Read once per collection, never cached. */
    estimates: () -> Flow<VaultRead<ZakatEstimate>>,
    observeTracking: ObserveZakatTrackingUseCase,
    observeDay: ObserveZakatDayUseCase,
    private val visibility: ValuesVisibility,
) : MviViewModel<ZakatCalculatorUiState, ZakatCalculatorIntent, JournalEffect>(ZakatCalculatorUiState()) {

    init {
        viewModelScope.launch {
            combine(estimates(), observeTracking(), observeDay(), visibility.hiddenFlow()) {
                    estimate,
                    tracking,
                    day,
                    hidden,
                ->
                calculatorStateOf(estimate, tracking, day, hidden)
            }.collect { next -> reduce { next } }
        }
    }

    override fun onIntent(intent: ZakatCalculatorIntent) {
        when (intent) {
            ZakatCalculatorIntent.ToggleHidden -> visibility.toggle()

            ZakatCalculatorIntent.RevealForRecord -> visibility.reveal()

            ZakatCalculatorIntent.RecordWithEstimate -> {
                val amount = currentState.prefill ?: return
                emitEffect(JournalEffect.Navigate(ZakatAddRoute(prefillAmount = amount.toPlainString())))
            }

            ZakatCalculatorIntent.AdjustNextRamadan -> currentState.nextRamadan?.let {
                emitEffect(JournalEffect.Navigate(ZakatDateRoute(it.hijriYear)))
            }

            ZakatCalculatorIntent.ReviewPrices -> emitEffect(JournalEffect.Navigate(PricesRoute))

            ZakatCalculatorIntent.OpenYearlyStatus -> emitEffect(JournalEffect.Navigate(ZakatHistoryRoute))
        }
    }
}

internal fun calculatorStateOf(
    estimate: VaultRead<ZakatEstimate>,
    tracking: VaultRead<ZakatTrackingState>,
    today: LocalDate,
    hidden: Boolean,
): ZakatCalculatorUiState {
    val blocking = blockingPhaseOf(estimate)
    if (blocking != null) return ZakatCalculatorUiState(phase = blocking, hidden = hidden)
    val adjustments = (tracking as? VaultRead.Ready)?.value.adjustments()
    val next = nextRamadanOf(today, adjustments)
    return ZakatCalculatorUiState(
        phase = JournalPhase.Ready,
        nextRamadan = next?.let {
            NextRamadanUi(
                hijriYear = it.hijriYear,
                date = it.date,
                daysRemaining = HijriCalendar.daysUntil(today, it.date).toInt(),
                adjusted = (adjustments[it.hijriYear] ?: 0) != 0,
            )
        },
        nextUnavailable = next == null,
        estimate = (estimate as VaultRead.Ready).value,
        hidden = hidden,
    )
}
