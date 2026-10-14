package com.mk.daftar.feature.home

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.refresh.PriceRefreshController
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.CompleteFirstRunGuideUseCase
import com.mk.daftar.core.domain.usecase.FirstRunGuideState
import com.mk.daftar.core.domain.usecase.ObserveBanksUseCase
import com.mk.daftar.core.domain.usecase.ObserveFirstRunGuideUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatDayUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.domain.zakat.ZakatYearRules
import com.mk.daftar.core.navigation.AssetsRoute
import com.mk.daftar.core.navigation.BankAddRoute
import com.mk.daftar.core.navigation.BanksRoute
import com.mk.daftar.core.navigation.CashAddRoute
import com.mk.daftar.core.navigation.CashRoute
import com.mk.daftar.core.navigation.GoldAddRoute
import com.mk.daftar.core.navigation.GoldRoute
import com.mk.daftar.core.navigation.InvestmentAddRoute
import com.mk.daftar.core.navigation.InvestmentsRoute
import com.mk.daftar.core.navigation.PricesRoute
import com.mk.daftar.core.navigation.ZakatCalculatorRoute
import com.mk.daftar.core.navigation.ZakatHistoryRoute
import com.mk.daftar.core.navigation.ZakatYearRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * The Home screen. It combines the valuation, the selected quotes, the bank count, the privacy state and the refresh
 * status into one [HomeUiState]. Nothing here recalculates a value: figures come from the domain, and a read from another
 * generation or a lock yields loading rather than a mixed state.
 */
class HomeViewModel(
    observePortfolio: ObservePortfolioValuationUseCase,
    observeQuotes: ObserveSelectedQuotesUseCase,
    observeBanks: ObserveBanksUseCase,
    observeTracking: ObserveZakatTrackingUseCase,
    observeDay: ObserveZakatDayUseCase,
    private val visibility: ValuesVisibility,
    private val refresh: PriceRefreshController,
    observeFirstRun: ObserveFirstRunGuideUseCase,
    private val completeFirstRun: CompleteFirstRunGuideUseCase,
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
            }.collect { next -> reduce { next.copy(zakatDue = zakatDue, firstRun = firstRun) } }
        }
        viewModelScope.launch {
            combine(observeTracking(), observeDay()) { tracking, day -> dueOf(tracking, day) }
                .collect { due -> reduce { copy(zakatDue = due) } }
        }
        viewModelScope.launch {
            observeFirstRun().collect { guide ->
                reduce { copy(firstRun = guide) }
                // The first record completes the guide, so a later emptying of the vault does not bring it back.
                if (guide is FirstRunGuideState.HasRecords && !guide.doneRecorded) completeFirstRun()
            }
        }
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.ToggleValues -> visibility.toggle()

            HomeIntent.Refresh -> refresh.refresh()

            is HomeIntent.OpenCategory -> emitEffect(HomeEffect.Open(intent.category.route()))

            HomeIntent.OpenPrices -> emitEffect(HomeEffect.Open(PricesRoute))

            HomeIntent.AddFirstAsset -> emitEffect(HomeEffect.Open(AssetsRoute))

            HomeIntent.OpenZakatCalculator -> emitEffect(HomeEffect.Open(ZakatCalculatorRoute))

            is HomeIntent.OpenFirstRunEntry -> emitEffect(HomeEffect.Open(intent.entry.route()))

            HomeIntent.DismissFirstRunGuide -> viewModelScope.launch { completeFirstRun() }

            HomeIntent.OpenZakatReview -> {
                val due = currentState.zakatDue
                val route: NavKey = if (due.size == 1) ZakatYearRoute(due.single().hijriYear) else ZakatHistoryRoute
                if (due.isNotEmpty()) emitEffect(HomeEffect.Open(route))
            }
        }
    }

    private fun FirstRunEntry.route(): NavKey = when (this) {
        FirstRunEntry.GOLD -> GoldAddRoute
        FirstRunEntry.BANK -> BankAddRoute
        FirstRunEntry.CASH -> CashAddRoute
        FirstRunEntry.INVESTMENT -> InvestmentAddRoute
    }

    private fun HomeCategory.route(): NavKey = when (this) {
        HomeCategory.GOLD -> GoldRoute
        HomeCategory.BANKS -> BanksRoute
        HomeCategory.CASH -> CashRoute
        HomeCategory.INVESTMENTS -> InvestmentsRoute
    }
}

/** The years awaiting confirmation on the civil day, oldest first. Nothing is due while tracking is not read. */
private fun dueOf(tracking: VaultRead<ZakatTrackingState>, today: LocalDate): List<ZakatDueYear> {
    val ready = tracking as? VaultRead.Ready ?: return emptyList()
    return ZakatYearRules.dueYears(ready.value.years, today).map { year ->
        ZakatDueYear(year.hijriYear, ZakatYearRules.dateOf(year).year)
    }
}
