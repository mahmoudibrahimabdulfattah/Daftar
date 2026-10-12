package com.mk.daftar.feature.prices

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.domain.refresh.PriceRefreshController
import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveGoldHoldingsUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.domain.usecase.ObserveQuoteSnapshotUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.domain.valuation.PortfolioValuation
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlin.time.Clock
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The prices screen. It combines the raw quote snapshot, the selected quotes, the portfolio's unvalued assets, the
 * holdings behind them, the privacy state and the shared refresh into one [PricesUiState]. Nothing here fetches or
 * writes; refresh is the session's one controller, shared with Home.
 *
 * A lock clears the board at once. While a new read is loading, the last ready board stays on screen.
 */
class PricesViewModel(
    observeSnapshot: ObserveQuoteSnapshotUseCase,
    observeSelected: ObserveSelectedQuotesUseCase,
    observePortfolio: ObservePortfolioValuationUseCase,
    observeGold: ObserveGoldHoldingsUseCase,
    private val visibility: ValuesVisibility,
    private val refresh: PriceRefreshController,
    private val clock: Clock,
) : MviViewModel<PricesUiState, PricesIntent, PricesEffect>(PricesUiState()) {

    init {
        val reads =
            combine(observeSnapshot(), observeSelected(), observePortfolio(), observeGold()) {
                    snapshot,
                    selected,
                    valuation,
                    holdings,
                ->
                PriceReads(snapshot, selected, valuation, holdings)
            }
        viewModelScope.launch {
            combine(reads, visibility.visible, refresh.status) { read, visible, status ->
                pricesStateOf(
                    snapshot = read.snapshot,
                    selected = read.selected,
                    valuation = read.valuation,
                    holdings = read.holdings,
                    now = clock.now(),
                    valuesHidden = !visible,
                    refresh = status,
                )
            }.collect { next -> reduce { retainingBoard(next) } }
        }
    }

    override fun onIntent(intent: PricesIntent) {
        when (intent) {
            PricesIntent.Refresh -> refresh.refresh()
            is PricesIntent.OpenManual -> emitEffect(PricesEffect.Open(intent.route))
        }
    }

    /** Keeps the last ready board while a read is only loading. Locked, failed and new states replace it. */
    private fun PricesUiState.retainingBoard(next: PricesUiState): PricesUiState =
        if (next.load == PricesLoad.Loading && load is PricesLoad.Ready) next.copy(load = load) else next

    private class PriceReads(
        val snapshot: VaultRead<QuoteSnapshot>,
        val selected: VaultRead<SelectedQuotes>,
        val valuation: VaultRead<PortfolioValuation>,
        val holdings: VaultRead<List<Stored<GoldHolding>>>,
    )
}
