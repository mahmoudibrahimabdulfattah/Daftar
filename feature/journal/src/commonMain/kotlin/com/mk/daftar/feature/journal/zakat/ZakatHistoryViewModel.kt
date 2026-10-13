package com.mk.daftar.feature.journal.zakat

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveZakatDayUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.navigation.ZakatYearAddRoute
import com.mk.daftar.core.navigation.ZakatYearRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.nonReadyPhase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Every tracked year, newest first. Years are never closed automatically, so each keeps its own status. */
data class ZakatHistoryUiState(
    val phase: JournalPhase = JournalPhase.Loading,
    val years: List<ZakatYearSummary> = emptyList(),
) : UiState

sealed interface ZakatHistoryIntent : UiIntent {
    data class OpenYear(val hijriYear: Int) : ZakatHistoryIntent

    data object AddPastYear : ZakatHistoryIntent
}

class ZakatHistoryViewModel(observeTracking: ObserveZakatTrackingUseCase, observeDay: ObserveZakatDayUseCase) :
    MviViewModel<ZakatHistoryUiState, ZakatHistoryIntent, JournalEffect>(ZakatHistoryUiState()) {

    init {
        viewModelScope.launch {
            combine(observeTracking(), observeDay()) { tracking, day -> historyStateOf(tracking, day) }
                .collect { next -> reduce { next } }
        }
    }

    override fun onIntent(intent: ZakatHistoryIntent) {
        when (intent) {
            is ZakatHistoryIntent.OpenYear -> emitEffect(JournalEffect.Navigate(ZakatYearRoute(intent.hijriYear)))
            ZakatHistoryIntent.AddPastYear -> emitEffect(JournalEffect.Navigate(ZakatYearAddRoute))
        }
    }
}

internal fun historyStateOf(tracking: VaultRead<ZakatTrackingState>, today: LocalDate): ZakatHistoryUiState {
    val blocking = tracking.nonReadyPhase()
    if (blocking != null) return ZakatHistoryUiState(phase = blocking)
    val years = (tracking as VaultRead.Ready).value.summariesNewestFirst(today)
    return ZakatHistoryUiState(phase = if (years.isEmpty()) JournalPhase.Empty else JournalPhase.Ready, years = years)
}
