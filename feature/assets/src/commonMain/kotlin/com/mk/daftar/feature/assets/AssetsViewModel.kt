package com.mk.daftar.feature.assets

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.ObserveBanksUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.domain.valuation.AssetCategory
import com.mk.daftar.core.navigation.BanksRoute
import com.mk.daftar.core.navigation.CashRoute
import com.mk.daftar.core.navigation.GoldRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.mvi.UiEffect
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the overview shows: nothing yet, a storage failure, or the figures of one generation. */
sealed interface AssetsLoad {
    data object Loading : AssetsLoad

    data class Failed(val error: StorageError) : AssetsLoad

    data class Ready(val figures: AssetsFigures) : AssetsLoad
}

data class AssetsUiState(val load: AssetsLoad = AssetsLoad.Loading, val valuesHidden: Boolean = false) : UiState

sealed interface AssetsIntent : UiIntent {
    data object ToggleValues : AssetsIntent

    data class OpenCategory(val category: AssetCategory) : AssetsIntent
}

sealed interface AssetsEffect : UiEffect {
    data class Open(val route: NavKey) : AssetsEffect
}

/** The Assets overview. It reads the same valuation and bank count as Home, and the session's privacy state. */
class AssetsOverviewViewModel(
    observePortfolio: ObservePortfolioValuationUseCase,
    observeBanks: ObserveBanksUseCase,
    private val visibility: ValuesVisibility,
) : MviViewModel<AssetsUiState, AssetsIntent, AssetsEffect>(AssetsUiState()) {

    init {
        viewModelScope.launch {
            combine(
                observePortfolio(),
                observeBanks().map { it.countReady() },
                visibility.visible,
            ) { valuation, bankCount, visible ->
                AssetsUiState(
                    load = assetsLoadOf(valuation, bankCount),
                    valuesHidden = !visible,
                )
            }.collect { next -> reduce { next } }
        }
    }

    override fun onIntent(intent: AssetsIntent) {
        when (intent) {
            AssetsIntent.ToggleValues -> visibility.toggle()
            is AssetsIntent.OpenCategory -> emitEffect(AssetsEffect.Open(intent.category.route()))
        }
    }

    private fun AssetCategory.route(): NavKey = when (this) {
        AssetCategory.GOLD -> GoldRoute
        AssetCategory.BANKS -> BanksRoute
        AssetCategory.CASH -> CashRoute
    }
}
