package com.mk.daftar.feature.home

import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.refresh.RefreshOutcome
import com.mk.daftar.core.domain.refresh.showsProgress
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.valuation.AssetCategory
import com.mk.daftar.core.domain.valuation.CategoryValuation
import com.mk.daftar.core.domain.valuation.PortfolioValuation
import com.mk.daftar.core.domain.valuation.QuoteFreshness
import com.mk.daftar.core.domain.valuation.QuoteOrigin
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxBasis
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.ui.presentation.QuoteBoardSummary
import com.mk.daftar.core.ui.presentation.allocationPercents
import com.mk.daftar.core.ui.presentation.quoteBoardSummaryOf
import kotlin.time.Instant

/** Where a displayed quote came from, weakest assurance last. The worst badge in a group is the one shown. */
enum class QuoteBadge { LIVE, CACHED, MANUAL, STALE, CLOCK_UNCERTAIN }

/** The badge for [SelectedQuote]: domain freshness first, then whether the value was pinned or entered by hand. */
fun SelectedQuote.badge(): QuoteBadge = when {
    freshness == QuoteFreshness.CLOCK_UNCERTAIN -> QuoteBadge.CLOCK_UNCERTAIN
    freshness == QuoteFreshness.STALE -> QuoteBadge.STALE
    origin != QuoteOrigin.REMOTE -> QuoteBadge.MANUAL
    provenance.kind == QuoteKind.LIVE -> QuoteBadge.LIVE
    else -> QuoteBadge.CACHED
}

/** One category of the portfolio. Totals are the domain's; a null total means the category is not fully valued. */
data class CategoryFigures(
    val category: AssetCategory,
    val count: Int,
    /** The presentation total. Null while any asset of the category is unvalued, and never a zero placeholder. */
    val total: Decimal?,
    /** The exact total, present only when [total] is. Used for proportions. */
    val exactTotal: Decimal?,
    /** The sum of valued assets only; never a complete total. */
    val subtotal: Decimal,
)

/** One share of the allocation strip. [exact] is the category's exact amount; [percent] is its deterministic label. */
data class AllocationPart(val category: AssetCategory, val percent: Int, val exact: Decimal)

/** One quote in the strip. A null [value] means no quote is available, which the strip shows as a dash. */
data class QuoteFigure(val key: QuoteKey, val value: Decimal?)

/** The quote strip: the sell prices in karat order, then USD. [badge] and [referenceAt] describe the shown ones. */
data class QuoteStripFigures(
    val quotes: List<QuoteFigure>,
    val badge: QuoteBadge?,
    /** The earliest reference time among the shown quotes: the time the oldest of them was fetched or recorded. */
    val referenceAt: Instant?,
    /** What the USD rate is: Banque Misr's transfer buy, a mid-market estimate, or manual. Null when unknown or absent. */
    val usdBasis: FxBasis? = null,
    /** One freshness statement for the four shown prices, with markers for the ones that differ. */
    val summary: QuoteBoardSummary = quoteBoardSummaryOf(emptyList()),
)

/** Everything the Home screen shows about the portfolio, derived from one valuation and one quote set. */
data class HomeFigures(
    val isEmpty: Boolean,
    /** The portfolio total, or null while incomplete. Suppressed (null) when empty. */
    val total: Decimal?,
    val subtotal: Decimal,
    val incomplete: Boolean,
    /** Distinct quote keys that unvalued assets are waiting for, in order of first appearance. */
    val missingQuotes: List<QuoteKey>,
    /** Gold, banks, investments, cash in that order. */
    val categories: List<CategoryFigures>,
    /** The allocation strip, when the total is complete and positive. */
    val allocation: List<AllocationPart>?,
    /** The number of bank records, which is not the number of accounts. */
    val bankCount: Int,
    val quotes: QuoteStripFigures,
    /** Where to attribute the FX rate, when a remote USD rate from the exchange-rate provider is shown. */
    val fxAttributionUrl: String?,
)

/** The provider's required attribution target when the quote carries none. */
const val EXCHANGE_RATE_ATTRIBUTION_URL: String = "https://www.exchangerate-api.com/docs/free"

/** Figures for the Home screen. Uses the domain's totals and subtotals only; gold and FX are never recalculated. */
fun summarizeHome(valuation: PortfolioValuation, quotes: SelectedQuotes, bankCount: Int): HomeFigures {
    val categories = listOf(valuation.gold, valuation.banks, valuation.investments, valuation.cash).map {
        it.toFigures()
    }
    val missing = valuation.unvaluedAssets.map { it.requiredQuoteKey }.distinct()
    val complete = valuation.totalEgp != null
    val exactTotal = valuation.exactTotalEgp
    val allocation = if (complete && !valuation.isEmpty && exactTotal != null && exactTotal > Decimal.ZERO) {
        val percents = allocationPercents(categories.map { it.exactTotal ?: Decimal.ZERO })
        categories.mapIndexed { index, figures ->
            AllocationPart(figures.category, percents[index], figures.exactTotal ?: Decimal.ZERO)
        }
    } else {
        null
    }
    return HomeFigures(
        isEmpty = valuation.isEmpty,
        total = if (valuation.isEmpty) null else valuation.totalEgp,
        subtotal = valuation.subtotalEgp,
        incomplete = !complete,
        missingQuotes = missing,
        categories = categories,
        allocation = allocation,
        bankCount = bankCount,
        quotes = quoteStrip(quotes),
        fxAttributionUrl = fxAttributionFor(quotes),
    )
}

private fun CategoryValuation.toFigures(): CategoryFigures = CategoryFigures(
    category = category,
    count = assets.size,
    total = totalEgp,
    exactTotal = exactTotalEgp,
    subtotal = subtotalEgp,
)

private val STRIP_KEYS: List<QuoteKey> = listOf(
    QuoteKey.Gold(GoldKarat.K24, MerchantSide.SELLS_TO_CUSTOMER),
    QuoteKey.Gold(GoldKarat.K21, MerchantSide.SELLS_TO_CUSTOMER),
    QuoteKey.Gold(GoldKarat.K18, MerchantSide.SELLS_TO_CUSTOMER),
    QuoteKey.UsdEgp,
)

private fun quoteStrip(quotes: SelectedQuotes): QuoteStripFigures {
    val figures = STRIP_KEYS.map { key -> QuoteFigure(key, quotes[key]?.value) }
    val shown = STRIP_KEYS.mapNotNull { quotes[it] }
    val badge = shown.map { it.badge() }.maxByOrNull { it.ordinal }
    val referenceAt = shown.minOfOrNull { it.referenceAt }
    val usdBasis = quotes[QuoteKey.UsdEgp]?.fxBasis?.takeIf { it != FxBasis.LEGACY_UNKNOWN }
    return QuoteStripFigures(figures, badge, referenceAt, usdBasis, quoteBoardSummaryOf(STRIP_KEYS.map { quotes[it] }))
}

/** The attribution owed when the displayed USD rate comes from the exchange-rate provider itself. */
private fun fxAttributionFor(quotes: SelectedQuotes): String? {
    val usd = quotes[QuoteKey.UsdEgp] ?: return null
    if (usd.origin != QuoteOrigin.REMOTE || usd.provenance.provider != QuoteProvider.EXCHANGE_RATE_API) return null
    return usd.attributionUrl ?: EXCHANGE_RATE_ATTRIBUTION_URL
}

/** The count of a ready read, keeping its generation. Other reads pass through unchanged. */
fun <T> VaultRead<List<T>>.countReady(): VaultRead<Int> = when (this) {
    is VaultRead.Ready -> VaultRead.Ready(generation, value.size)
    is VaultRead.Failed -> this
    VaultRead.Loading -> VaultRead.Loading
    VaultRead.Locked -> VaultRead.Locked
}

/**
 * The Home state for one combination of reads. Every read must be [VaultRead.Ready] for the same open generation; any
 * other combination is loading, so an old value is never shown next to a new generation. Refresh progress and failure
 * count only for the generation of the values shown.
 */
fun homeStateOf(
    valuation: VaultRead<PortfolioValuation>,
    quotes: VaultRead<SelectedQuotes>,
    bankCount: VaultRead<Int>,
    valuesHidden: Boolean,
    refresh: PriceRefreshStatus,
): HomeUiState {
    val failure = listOf(valuation, quotes, bankCount).filterIsInstance<VaultRead.Failed>().firstOrNull()
    if (failure != null) return HomeUiState(load = HomeLoad.Failed(failure.error), valuesHidden = valuesHidden)
    val v = valuation as? VaultRead.Ready ?: return HomeUiState(valuesHidden = valuesHidden)
    val q = quotes as? VaultRead.Ready ?: return HomeUiState(valuesHidden = valuesHidden)
    val b = bankCount as? VaultRead.Ready ?: return HomeUiState(valuesHidden = valuesHidden)
    if (v.generation != q.generation || v.generation != b.generation) return HomeUiState(valuesHidden = valuesHidden)
    val generation = v.generation
    return HomeUiState(
        load = HomeLoad.Ready(summarizeHome(v.value, q.value, b.value)),
        valuesHidden = valuesHidden,
        refreshing = refresh.showsProgress && refresh.generation == generation,
        waitingForPrices = refresh is PriceRefreshStatus.Refreshing && refresh.generation == generation,
        refreshFailed = refresh is PriceRefreshStatus.Finished &&
            refresh.generation == generation &&
            refresh.outcome != RefreshOutcome.Succeeded,
    )
}
