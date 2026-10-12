package com.mk.daftar.feature.home

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.refresh.PriceRefreshController
import com.mk.daftar.core.domain.usecase.ObserveBanksUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.navigation.AssetsRoute
import com.mk.daftar.core.navigation.BanksRoute
import com.mk.daftar.core.navigation.CashRoute
import com.mk.daftar.core.navigation.GoldRoute
import com.mk.daftar.core.navigation.PricesRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The Home screen. It combines the valuation, the selected quotes, the bank count, the privacy state and the refresh
 * status into one [HomeUiState]. Nothing here recalculates a value: figures come from the domain, and a read from another
 * generation or a lock yields loading rather than a mixed state.
 */
class HomeViewModel(
    observePortfolio: ObservePortfolioValuationUseCase,
    observeQuotes: ObserveSelectedQuotesUseCase,
    observeBanks: ObserveBanksUseCase,
    private val visibility: ValuesVisibility,
    private val refresh: PriceRefreshController,
) : MviViewModel<HomeUiState, HomeIntent, HomeEffect>(HomeUiState()) {

    init {
        viewModelScope.launch {
            combine(
                observePortfolio(),
                observeQuotes(),
                observeBanks().map { it.countReady() },
                visibility.visible,
                refresh.status,
            ) { valuation, quotes, bankCount, visible, status ->
                homeStateOf(valuation, quotes, bankCount, valuesHidden = !visible, refresh = status)
            }.collect { next -> reduce { next } }
        }
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.ToggleValues -> visibility.toggle()
            HomeIntent.Refresh -> refresh.refresh()
            is HomeIntent.OpenCategory -> emitEffect(HomeEffect.Open(intent.category.route()))
            HomeIntent.OpenPrices -> emitEffect(HomeEffect.Open(PricesRoute))
            HomeIntent.AddFirstAsset -> emitEffect(HomeEffect.Open(AssetsRoute))
        }
    }

    private fun HomeCategory.route(): NavKey = when (this) {
        HomeCategory.GOLD -> GoldRoute
        HomeCategory.BANKS -> BanksRoute
        HomeCategory.CASH -> CashRoute
    }
}
