package com.mk.daftar.feature.cash

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.usecase.ObserveCashBalancesUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.navigation.CashAddRoute
import com.mk.daftar.core.navigation.CashEditRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The cash and wallet list. Balances come from the cash repository; the valuation adds EGP figures by typed ID. Opening
 * an editor while values are hidden stores the request and waits for an explicit reveal, so a sensitive record is never
 * opened implicitly.
 */
class CashListViewModel(
    observeBalances: ObserveCashBalancesUseCase,
    observePortfolio: ObservePortfolioValuationUseCase,
    private val visibility: ValuesVisibility,
) : MviViewModel<CashListUiState, CashListIntent, CashEffect>(CashListUiState()) {

    init {
        viewModelScope.launch {
            combine(observeBalances(), observePortfolio(), visibility.hiddenFlow()) { balances, valuation, hidden ->
                cashListStateOf(balances, valuation, hidden)
            }.collect { next ->
                reduce { next.copy(pendingRoute = if (next.phase == CashPhase.Locked) null else pendingRoute) }
            }
        }
    }

    override fun onIntent(intent: CashListIntent) {
        when (intent) {
            CashListIntent.OpenAdd -> open(CashAddRoute)

            is CashListIntent.OpenBalance -> open(CashEditRoute(intent.balanceId.value))

            CashListIntent.RevealAndOpen -> {
                val route = currentState.pendingRoute ?: return
                visibility.reveal()
                reduce { copy(pendingRoute = null) }
                emitEffect(CashEffect.Navigate(route))
            }

            CashListIntent.CancelReveal -> reduce { copy(pendingRoute = null) }

            CashListIntent.ToggleHidden -> visibility.toggle()
        }
    }

    private fun open(route: NavKey) {
        if (visibility.isHidden()) {
            reduce { copy(pendingRoute = route) }
        } else {
            emitEffect(CashEffect.Navigate(route))
        }
    }
}
