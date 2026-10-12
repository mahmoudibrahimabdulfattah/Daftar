package com.mk.daftar.feature.gold.list

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.usecase.ObserveGoldHoldingsUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.navigation.GoldAddRoute
import com.mk.daftar.core.navigation.GoldEditRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.gold.GoldEffect
import com.mk.daftar.feature.gold.GoldPhase
import com.mk.daftar.feature.gold.hiddenFlow
import com.mk.daftar.feature.gold.isHidden
import com.mk.daftar.feature.gold.reveal
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The gold holdings list with its category valuation. Opening an editor while values are hidden stores the request and
 * waits for an explicit reveal, so a sensitive holding is never opened implicitly.
 */
class GoldListViewModel(
    observeHoldings: ObserveGoldHoldingsUseCase,
    observePortfolio: ObservePortfolioValuationUseCase,
    observeQuotes: ObserveSelectedQuotesUseCase,
    private val visibility: ValuesVisibility,
    private val timeZone: TimeZone,
) : MviViewModel<GoldListUiState, GoldListIntent, GoldEffect>(GoldListUiState()) {

    init {
        viewModelScope.launch {
            combine(
                observeHoldings(),
                observePortfolio(),
                observeQuotes(),
                visibility.hiddenFlow(),
            ) { holdings, portfolio, quotes, hidden ->
                goldListStateOf(holdings, portfolio, quotes, hidden, timeZone)
            }.collect { next ->
                reduce { next.copy(pendingRoute = if (next.phase == GoldPhase.Locked) null else pendingRoute) }
            }
        }
    }

    override fun onIntent(intent: GoldListIntent) {
        when (intent) {
            GoldListIntent.OpenAdd -> open(GoldAddRoute)

            is GoldListIntent.OpenHolding -> open(GoldEditRoute(intent.holdingId.value))

            GoldListIntent.RevealAndOpen -> {
                val route = currentState.pendingRoute ?: return
                visibility.reveal()
                reduce { copy(pendingRoute = null) }
                emitEffect(GoldEffect.Navigate(route))
            }

            GoldListIntent.CancelReveal -> reduce { copy(pendingRoute = null) }

            GoldListIntent.ToggleHidden -> visibility.toggle()
        }
    }

    private fun open(route: NavKey) {
        if (visibility.isHidden()) {
            reduce { copy(pendingRoute = route) }
        } else {
            emitEffect(GoldEffect.Navigate(route))
        }
    }
}
