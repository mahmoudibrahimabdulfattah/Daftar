package com.mk.daftar.feature.prices

import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.refresh.RefreshOutcome
import com.mk.daftar.core.domain.refresh.showsProgress
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RefreshRejection
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.valuation.AssetRef
import com.mk.daftar.core.domain.valuation.PortfolioValuation
import com.mk.daftar.core.domain.valuation.QuoteFreshness
import com.mk.daftar.core.domain.valuation.QuoteOrigin
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.domain.valuation.selectRemoteQuotes
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import kotlin.time.Instant

/** The badge of one selected quote. Matches the home strip's rule for a single quote. */
fun SelectedQuote.priceBadge(): PriceBadge = when {
    freshness == QuoteFreshness.CLOCK_UNCERTAIN -> PriceBadge.CLOCK_UNCERTAIN
    freshness == QuoteFreshness.STALE -> PriceBadge.STALE
    origin != QuoteOrigin.REMOTE -> PriceBadge.MANUAL
    provenance.kind == QuoteKind.LIVE -> PriceBadge.LIVE
    else -> PriceBadge.CACHED
}

/** The notice for a finished refresh. A success and a stale-only rejection leave no notice. */
fun refreshNoticeOf(outcome: RefreshOutcome, rejections: Set<RefreshRejection>): RefreshNotice? = when (outcome) {
    RefreshOutcome.Succeeded -> null

    RefreshOutcome.PartiallyFailed -> RefreshNotice.PARTIAL

    RefreshOutcome.Failed -> when {
        RefreshRejection.RateLimited in rejections -> RefreshNotice.RATE_LIMITED
        RefreshRejection.InvalidPayload in rejections -> RefreshNotice.INVALID_PAYLOAD
        RefreshRejection.Unreachable in rejections -> RefreshNotice.OFFLINE
        RefreshRejection.Missing in rejections -> RefreshNotice.MISSING
        else -> RefreshNotice.FAILED
    }
}

/**
 * The prices state for one combination of reads. Every read must belong to the same open generation. A lock is
 * reported as [PricesLoad.Locked], a failure as [PricesLoad.Failed]; any other mix is loading, never a partial board.
 */
fun pricesStateOf(
    snapshot: VaultRead<QuoteSnapshot>,
    selected: VaultRead<SelectedQuotes>,
    valuation: VaultRead<PortfolioValuation>,
    holdings: VaultRead<List<Stored<GoldHolding>>>,
    now: Instant,
    valuesHidden: Boolean,
    refresh: PriceRefreshStatus,
): PricesUiState {
    val reads = listOf(snapshot, selected, valuation, holdings)
    reads.filterIsInstance<VaultRead.Failed>().firstOrNull()?.let {
        return PricesUiState(load = PricesLoad.Failed(it.error), valuesHidden = valuesHidden)
    }
    if (reads.any {
            it == VaultRead.Locked
        }
    ) {
        return PricesUiState(load = PricesLoad.Locked, valuesHidden = valuesHidden)
    }
    val s = snapshot as? VaultRead.Ready
    val q = selected as? VaultRead.Ready
    val v = valuation as? VaultRead.Ready
    val h = holdings as? VaultRead.Ready
    if (s == null || q == null || v == null || h == null) {
        return PricesUiState(load = PricesLoad.Loading, valuesHidden = valuesHidden)
    }
    val generation = s.generation
    if (listOf(q.generation, v.generation, h.generation).any { it != generation }) {
        return PricesUiState(load = PricesLoad.Loading, valuesHidden = valuesHidden)
    }
    val board = priceBoardOf(s.value, q.value, v.value, h.value.map { it.value }, now)
    val finished = refresh as? PriceRefreshStatus.Finished
    return PricesUiState(
        load = PricesLoad.Ready(board),
        valuesHidden = valuesHidden,
        refreshing = refresh.showsProgress && refresh.generation == generation,
        notice = finished?.takeIf { it.generation == generation }?.let { refreshNoticeOf(it.outcome, it.rejections) },
        failedAssets = finished?.takeIf { it.generation == generation }?.failedAssets.orEmpty(),
    )
}

/**
 * The prices board. Sell values are the effective selection, with the remote source beneath a pinned override. Buy
 * values come from remote quotes only. Nothing is inferred from another karat or side: a missing cell stays missing.
 */
fun priceBoardOf(
    snapshot: QuoteSnapshot,
    selected: SelectedQuotes,
    valuation: PortfolioValuation,
    holdings: List<GoldHolding>,
    now: Instant,
): PricesBoard {
    val remote = selectRemoteQuotes(snapshot, now)
    val karats = PRICE_KARATS.map { karat ->
        val sellKey = QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER)
        val sell = selected[sellKey]
        KaratPriceRow(
            karat = karat,
            sell = sell,
            sourceSell = if (sell?.origin == QuoteOrigin.PINNED_MANUAL) remote[sellKey] else null,
            buy = remote[QuoteKey.Gold(karat, MerchantSide.BUYS_FROM_CUSTOMER)],
        )
    }
    val usd = selected[QuoteKey.UsdEgp]
    val sellKeyed: List<Pair<QuoteKey, SelectedQuote?>> =
        karats.map { QuoteKey.Gold(it.karat, MerchantSide.SELLS_TO_CUSTOMER) to it.sell } +
            (QuoteKey.UsdEgp to usd)
    return PricesBoard(
        karats = karats,
        usd = usd,
        usdSource = if (usd?.origin == QuoteOrigin.PINNED_MANUAL) remote[QuoteKey.UsdEgp] else null,
        missing = sellKeyed.filter { it.second == null }.map { it.first },
        stale = sellKeyed.filter { it.second?.freshness == QuoteFreshness.STALE }.map { it.first },
        details = missingDetailsOf(valuation, holdings),
    )
}

/** The unvalued holdings and the valued subtotal. Null when every asset is valued. */
private fun missingDetailsOf(valuation: PortfolioValuation, holdings: List<GoldHolding>): MissingDetails? {
    if (valuation.unvaluedAssets.isEmpty()) return null
    val byId = holdings.associateBy { it.id }
    val gold = valuation.unvaluedAssets
        .mapNotNull { unvalued -> (unvalued.asset as? AssetRef.Gold)?.let { byId[it.id] } }
        .groupBy { it.karat to it.form }
        .map { (group, items) ->
            UnvaluedGold(
                karat = group.first,
                form = group.second,
                count = items.size,
                grams = items.fold(Decimal.ZERO) { sum, holding -> sum + holding.weight.grams },
            )
        }
        .sortedWith(compareBy({ -it.karat.carats }, { it.form.ordinal }))
    return MissingDetails(
        availableSubtotal = valuation.subtotalEgp,
        gold = gold,
        usdBalances = valuation.unvaluedAssets.count { it.requiredQuoteKey == QuoteKey.UsdEgp },
    )
}

/** The manual slot of [key] in [snapshot], if one exists. */
fun manualSlotOf(snapshot: QuoteSnapshot, key: QuoteKey): Stored<PriceQuote>? =
    snapshot.manual.firstOrNull { priceQuoteKey(it.value) == key }

/** The key a price quote is for. */
fun priceQuoteKey(quote: PriceQuote): QuoteKey = when (quote) {
    is GoldQuote -> quote.key
    is FxQuote -> quote.key
}

/** The value a price quote carries: EGP per gram or EGP per USD. */
fun priceQuoteValue(quote: PriceQuote): Decimal = when (quote) {
    is GoldQuote -> quote.price.egpPerGram
    is FxQuote -> quote.rate.egpPerUsd
}

/** The attribution target the exchange-rate provider requires, used when a quote carries none. */
const val EXCHANGE_RATE_ATTRIBUTION_URL: String = "https://www.exchangerate-api.com"

/** The sell key of [karat]. */
fun sellKeyOf(karat: GoldKarat): QuoteKey = QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER)

/** Whether [quote] is the remote freshness-stale value, which is shown with the stale state. */
fun SelectedQuote.isStale(): Boolean = freshness == QuoteFreshness.STALE
