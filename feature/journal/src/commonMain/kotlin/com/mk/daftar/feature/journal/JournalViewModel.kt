package com.mk.daftar.feature.journal

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObservePersonalNotesUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatDayUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatPaymentsUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.navigation.NotesRoute
import com.mk.daftar.core.navigation.ZakatCalculatorRoute
import com.mk.daftar.core.navigation.ZakatHistoryRoute
import com.mk.daftar.core.navigation.ZakatYearRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.journal.zakat.ReviewDestination
import com.mk.daftar.feature.journal.zakat.ZakatYearSummary
import com.mk.daftar.feature.journal.zakat.dueSummaries
import com.mk.daftar.feature.journal.zakat.reviewDestinationOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * The journal overview: the last zakat payment date and the note count, from the journal alone, and the years awaiting
 * the user's confirmation. [dueYears] is oldest first and is empty while tracking has not been read.
 */
data class JournalUiState(
    val phase: JournalPhase = JournalPhase.Loading,
    val lastPaymentOn: LocalDate? = null,
    val noteCount: Int = 0,
    val hidden: Boolean = false,
    val dueYears: List<ZakatYearSummary> = emptyList(),
) : UiState

sealed interface JournalIntent : UiIntent {
    /** Opens the review of the year that awaits confirmation, or the yearly status when several do. */
    data object OpenReview : JournalIntent

    data object OpenCalculator : JournalIntent

    data object OpenNotes : JournalIntent

    data object ToggleHidden : JournalIntent
}

/**
 * Combines the two journal reads. Only the journal's own records feed it, so it never reads asset balances. Any locked,
 * failed or loading source decides the phase, in that order of precedence.
 */
class JournalViewModel(
    observePayments: ObserveZakatPaymentsUseCase,
    observeNotes: ObservePersonalNotesUseCase,
    observeTracking: ObserveZakatTrackingUseCase,
    observeDay: ObserveZakatDayUseCase,
    private val visibility: ValuesVisibility,
) : MviViewModel<JournalUiState, JournalIntent, JournalEffect>(JournalUiState()) {

    init {
        viewModelScope.launch {
            combine(observePayments(), observeNotes(), visibility.hiddenFlow()) { payments, notes, hidden ->
                overviewOf(payments, notes, hidden)
            }.collect { next -> reduce { next.copy(dueYears = dueYears) } }
        }
        viewModelScope.launch {
            combine(observeTracking(), observeDay()) { tracking, day ->
                (tracking as? VaultRead.Ready)?.value?.dueSummaries(day).orEmpty()
            }.collect { due -> reduce { copy(dueYears = due) } }
        }
    }

    override fun onIntent(intent: JournalIntent) {
        when (intent) {
            JournalIntent.OpenReview -> when (
                val destination = reviewDestinationOf(
                    currentState.dueYears.map {
                        it.hijriYear
                    },
                )
            ) {
                is ReviewDestination.Year -> emitEffect(JournalEffect.Navigate(ZakatYearRoute(destination.hijriYear)))
                ReviewDestination.History -> emitEffect(JournalEffect.Navigate(ZakatHistoryRoute))
                null -> Unit
            }

            JournalIntent.OpenCalculator -> emitEffect(JournalEffect.Navigate(ZakatCalculatorRoute))

            JournalIntent.OpenNotes -> emitEffect(JournalEffect.Navigate(NotesRoute))

            JournalIntent.ToggleHidden -> visibility.toggle()
        }
    }
}

internal fun overviewOf(
    payments: VaultRead<List<Stored<ZakatPayment>>>,
    notes: VaultRead<List<Stored<PersonalNote>>>,
    hidden: Boolean,
): JournalUiState {
    val phases = listOfNotNull(payments.nonReadyPhase(), notes.nonReadyPhase())
    val blocking = phases.firstOrNull { it == JournalPhase.Locked }
        ?: phases.firstOrNull { it is JournalPhase.Failed }
        ?: phases.firstOrNull()
    if (blocking != null) return JournalUiState(phase = blocking, hidden = hidden)
    val paymentList = (payments as? VaultRead.Ready)?.value.orEmpty()
    val noteList = (notes as? VaultRead.Ready)?.value.orEmpty()
    return JournalUiState(
        phase = JournalPhase.Ready,
        lastPaymentOn = paymentList.maxOfOrNull { it.value.paidOn },
        noteCount = noteList.size,
        hidden = hidden,
    )
}

/** The phase a read imposes, or null when its value is ready to show. */
internal fun VaultRead<*>.nonReadyPhase(): JournalPhase? = when (this) {
    VaultRead.Locked -> JournalPhase.Locked
    VaultRead.Loading -> JournalPhase.Loading
    is VaultRead.Failed -> JournalPhase.Failed(error)
    is VaultRead.Ready -> null
}
