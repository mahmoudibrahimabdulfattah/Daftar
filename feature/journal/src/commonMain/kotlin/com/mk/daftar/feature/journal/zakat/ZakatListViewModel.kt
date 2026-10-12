package com.mk.daftar.feature.journal.zakat

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveZakatPaymentsUseCase
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.navigation.ZakatAddRoute
import com.mk.daftar.core.navigation.ZakatEditRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.hiddenFlow
import com.mk.daftar.feature.journal.isHidden
import com.mk.daftar.feature.journal.reveal
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The zakat log. Payments arrive in the repository's paid-date order, which the list keeps. Opening an editor while values
 * are hidden stores the request and waits for an explicit reveal, so a sensitive record is never opened implicitly.
 */
class ZakatListViewModel(observePayments: ObserveZakatPaymentsUseCase, private val visibility: ValuesVisibility) :
    MviViewModel<ZakatListUiState, ZakatListIntent, JournalEffect>(ZakatListUiState()) {

    init {
        viewModelScope.launch {
            combine(observePayments(), visibility.hiddenFlow()) { read, hidden -> listStateOf(read, hidden) }
                .collect { next ->
                    reduce { next.copy(pendingRoute = if (next.phase == JournalPhase.Locked) null else pendingRoute) }
                }
        }
    }

    override fun onIntent(intent: ZakatListIntent) {
        when (intent) {
            ZakatListIntent.OpenAdd -> open(ZakatAddRoute)

            is ZakatListIntent.OpenPayment -> open(ZakatEditRoute(intent.paymentId.value))

            ZakatListIntent.RevealAndOpen -> {
                val route = currentState.pendingRoute ?: return
                visibility.reveal()
                reduce { copy(pendingRoute = null) }
                emitEffect(JournalEffect.Navigate(route))
            }

            ZakatListIntent.CancelReveal -> reduce { copy(pendingRoute = null) }

            ZakatListIntent.ToggleHidden -> visibility.toggle()
        }
    }

    private fun open(route: NavKey) {
        if (visibility.isHidden()) {
            reduce { copy(pendingRoute = route) }
        } else {
            emitEffect(JournalEffect.Navigate(route))
        }
    }
}

internal fun listStateOf(read: VaultRead<List<Stored<ZakatPayment>>>, hidden: Boolean): ZakatListUiState = when (read) {
    VaultRead.Locked -> ZakatListUiState(phase = JournalPhase.Locked, hidden = hidden)

    VaultRead.Loading -> ZakatListUiState(phase = JournalPhase.Loading, hidden = hidden)

    is VaultRead.Failed -> ZakatListUiState(phase = JournalPhase.Failed(read.error), hidden = hidden)

    is VaultRead.Ready -> {
        val years = groupByYear(read.value.map { it.value })
        ZakatListUiState(
            phase = if (years.isEmpty()) JournalPhase.Empty else JournalPhase.Ready,
            years = years,
            hidden = hidden,
        )
    }
}
