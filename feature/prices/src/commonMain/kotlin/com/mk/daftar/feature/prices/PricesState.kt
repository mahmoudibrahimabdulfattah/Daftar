package com.mk.daftar.feature.prices

import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.navigation.ManualQuoteRoute
import com.mk.daftar.core.ui.mvi.UiEffect
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState

/** The karats in the merchant table, in display order. */
val PRICE_KARATS: List<GoldKarat> = listOf(GoldKarat.K24, GoldKarat.K21, GoldKarat.K18)

/**
 * The badge one quote carries. It applies the same rule as the home strip, to one quote at a time: freshness first,
 * then whether the value was entered by hand, then live versus cached.
 */
enum class PriceBadge { LIVE, CACHED, MANUAL, STALE, CLOCK_UNCERTAIN }

/** Why the last refresh left some prices unchanged. [PARTIAL] is a success for some prices and a failure for others. */
enum class RefreshNotice { PARTIAL, RATE_LIMITED, INVALID_PAYLOAD, OFFLINE, MISSING, FAILED }

/** One karat of the merchant table. [sourceSell] is the remote price beneath a pinned override, shown separately. */
data class KaratPriceRow(
    val karat: GoldKarat,
    val sell: SelectedQuote?,
    val sourceSell: SelectedQuote?,
    val buy: SelectedQuote?,
)

/** Holdings that cannot be valued, grouped by karat and form, with their exact weight. */
data class UnvaluedGold(val karat: GoldKarat, val form: GoldForm, val count: Int, val grams: Decimal)

/** What is still unvalued, and what is valued. [availableSubtotal] is the portfolio's valued sum, never a total. */
data class MissingDetails(val availableSubtotal: Decimal, val gold: List<UnvaluedGold>, val usdBalances: Int)

/** Everything the prices screen shows for one open generation. */
data class PricesBoard(
    val karats: List<KaratPriceRow>,
    val usd: SelectedQuote?,
    val usdSource: SelectedQuote?,
    /** Sell keys and USD with no usable quote, in display order. */
    val missing: List<QuoteKey>,
    /** Effective quotes past their freshness window. */
    val stale: List<QuoteKey>,
    /** Present only when some asset is unvalued. */
    val details: MissingDetails?,
)

sealed interface PricesLoad {
    /** The first read has not completed. Neutral placeholders, nothing private. */
    data object Loading : PricesLoad

    /** No session is open. Nothing is shown. */
    data object Locked : PricesLoad

    data class Failed(val error: StorageError) : PricesLoad

    data class Ready(val board: PricesBoard) : PricesLoad
}

/**
 * The prices screen state. [refreshing] and [notice] describe the refresh of the generation whose prices are shown; a
 * refresh never changes those prices or their times.
 */
data class PricesUiState(
    val load: PricesLoad = PricesLoad.Loading,
    val valuesHidden: Boolean = false,
    val refreshing: Boolean = false,
    val notice: RefreshNotice? = null,
) : UiState

sealed interface PricesIntent : UiIntent {
    /** Asks for a refresh. Also the retry action. Joins a refresh already in flight. */
    data object Refresh : PricesIntent

    /** Opens Sources and details. */
    data object OpenDetails : PricesIntent

    /** Opens the manual editor. A null quote lets the user choose one there. */
    data class OpenManual(val route: ManualQuoteRoute) : PricesIntent
}

sealed interface PricesEffect : UiEffect {
    data class Open(val route: NavKey) : PricesEffect
}
