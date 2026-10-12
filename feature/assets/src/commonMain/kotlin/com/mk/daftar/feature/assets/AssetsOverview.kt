package com.mk.daftar.feature.assets

import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.valuation.AssetCategory
import com.mk.daftar.core.domain.valuation.AssetValuation
import com.mk.daftar.core.domain.valuation.CategoryValuation
import com.mk.daftar.core.domain.valuation.PortfolioValuation
import com.mk.daftar.core.domain.valuation.QuoteOrigin
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteProvider

/** One category row of the overview. [total] is null while not fully valued, or when the category holds no records. */
data class AssetsCategoryFigures(val category: AssetCategory, val count: Int, val total: Decimal?)

/** What the overview shows about the portfolio. The bank count is the number of bank records, not of accounts. */
data class AssetsFigures(
    val gold: AssetsCategoryFigures,
    val banks: AssetsCategoryFigures,
    val cash: AssetsCategoryFigures,
    val bankCount: Int,
    val incomplete: Boolean,
    val fxAttributionUrl: String?,
)

/** The provider's required attribution target when the quote carries none. */
internal const val ASSETS_FX_ATTRIBUTION_URL: String = "https://www.exchangerate-api.com/docs/free"

/** Overview figures from the domain's category totals. Nothing here values or sums a balance itself. */
fun assetsFiguresOf(valuation: PortfolioValuation, bankCount: Int): AssetsFigures {
    val fx = valuation.gold.assets + valuation.banks.assets + valuation.cash.assets
    return AssetsFigures(
        gold = valuation.gold.toFigures(),
        banks = valuation.banks.toFigures(),
        cash = valuation.cash.toFigures(),
        bankCount = bankCount,
        incomplete = valuation.totalEgp == null,
        fxAttributionUrl = fxAttributionAmong(fx),
    )
}

private fun CategoryValuation.toFigures(): AssetsCategoryFigures = AssetsCategoryFigures(
    category = category,
    count = assets.size,
    total = if (assets.isEmpty()) null else totalEgp,
)

/** Attribution is owed when a valued balance depends on the exchange-rate provider's remote USD rate. */
private fun fxAttributionAmong(assets: List<AssetValuation>): String? {
    val usd = assets.asSequence()
        .filterIsInstance<AssetValuation.Valued>()
        .mapNotNull { it.selectedQuote }
        .firstOrNull { it.key == QuoteKey.UsdEgp } ?: return null
    if (usd.origin != QuoteOrigin.REMOTE || usd.provenance.provider != QuoteProvider.EXCHANGE_RATE_API) return null
    return usd.attributionUrl ?: ASSETS_FX_ATTRIBUTION_URL
}

/** The overview state for one combination of reads. Mixed generations and locks are loading, never partial figures. */
fun assetsLoadOf(valuation: VaultRead<PortfolioValuation>, bankCount: VaultRead<Int>): AssetsLoad {
    val failure = listOf(valuation, bankCount).filterIsInstance<VaultRead.Failed>().firstOrNull()
    if (failure != null) return AssetsLoad.Failed(failure.error)
    val v = valuation as? VaultRead.Ready ?: return AssetsLoad.Loading
    val b = bankCount as? VaultRead.Ready ?: return AssetsLoad.Loading
    if (v.generation != b.generation) return AssetsLoad.Loading
    return AssetsLoad.Ready(assetsFiguresOf(v.value, b.value))
}

internal fun <T> VaultRead<List<T>>.countReady(): VaultRead<Int> = when (this) {
    is VaultRead.Ready -> VaultRead.Ready(generation, value.size)
    is VaultRead.Failed -> this
    VaultRead.Loading -> VaultRead.Loading
    VaultRead.Locked -> VaultRead.Locked
}
