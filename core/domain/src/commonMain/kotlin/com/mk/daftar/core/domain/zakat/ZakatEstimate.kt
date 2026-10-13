package com.mk.daftar.core.domain.zakat

import com.mk.daftar.core.domain.valuation.AssetCategory
import com.mk.daftar.core.domain.valuation.AssetValuation
import com.mk.daftar.core.domain.valuation.PortfolioValuation
import com.mk.daftar.core.domain.valuation.QuoteFreshness
import com.mk.daftar.core.domain.valuation.QuoteOrigin
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.domain.valuation.presentation
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.DecimalRounding
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import kotlin.time.Instant

/** The planning rate, 2.5 percent, as an exact decimal. The estimate multiplies by it; no division or Double is used. */
private val ZAKAT_RATE: Decimal = Decimal.parseCanonical("0.025")

/** Where the quotes behind an estimate came from. [MIXED] means at least two of the other three. */
enum class ZakatQuoteSource { LIVE, CACHED, MANUAL, MIXED }

enum class ZakatUnavailableReason {
    /** At least one asset needs a quote that is missing. */
    MISSING_QUOTE,

    /** A total or estimate would exceed the amount digits the notebook stores. Nothing is truncated. */
    OUT_OF_RANGE,
}

/** One category's share of the total. Both values are null while the category has an unvalued asset. */
data class ZakatCategoryBasis(val category: AssetCategory, val exactTotalEgp: Decimal?, val totalEgp: Decimal?)

/**
 * The planning estimate of zakat on the whole Home valuation. It is never a debt or a remaining balance, and it is
 * never computed on a partial total: a missing required quote yields [Unavailable] with the available subtotal only.
 */
sealed interface ZakatEstimate {
    /**
     * [exactTotalEgp] is the unrounded valuation total, and [exactEstimateEgp] is `exactTotalEgp × 0.025`, unrounded.
     * [prefillEgp] is the rounded estimate offered as an editable draft, and null when it is zero, because a payment
     * amount must be positive. [oldestQuoteAt] is the oldest used quote's reference time, and null when no quote is used.
     */
    data class Available(
        val exactTotalEgp: Decimal,
        val exactEstimateEgp: Decimal,
        val totalEgp: Decimal,
        val estimateEgp: Decimal,
        val prefillEgp: Decimal?,
        val categories: List<ZakatCategoryBasis>,
        val quoteSource: ZakatQuoteSource?,
        val oldestQuoteAt: Instant?,
        val stale: Boolean,
        val clockAhead: Boolean,
        val isEmpty: Boolean,
    ) : ZakatEstimate

    data class Unavailable(
        val reason: ZakatUnavailableReason,
        val missingQuotes: List<QuoteKey>,
        val availableSubtotalEgp: Decimal,
    ) : ZakatEstimate
}

/**
 * Estimates zakat from the same [PortfolioValuation] that Home shows. Quote selection is not repeated here: the
 * valuation already chose each quote, and this only reads its provenance. Pure: no clock and no I/O.
 */
fun estimateZakat(valuation: PortfolioValuation): ZakatEstimate {
    val exactTotal = valuation.exactTotalEgp
    if (exactTotal == null) {
        val missing = valuation.unvaluedAssets.map { it.requiredQuoteKey }.distinct()
        return ZakatEstimate.Unavailable(ZakatUnavailableReason.MISSING_QUOTE, missing, valuation.subtotalEgp)
    }
    val exactEstimate = exactTotal * ZAKAT_RATE
    val total = exactTotal.presentation()
    val estimate = exactEstimate.roundToScale(2, DecimalRounding.HALF_UP)
    if (total.integerDigits() > Decimal.MAX_INTEGER_DIGITS || estimate.integerDigits() > Decimal.MAX_INTEGER_DIGITS) {
        return ZakatEstimate.Unavailable(ZakatUnavailableReason.OUT_OF_RANGE, emptyList(), valuation.subtotalEgp)
    }
    val used = listOf(valuation.gold, valuation.banks, valuation.cash)
        .flatMap { category -> category.assets }
        .filterIsInstance<AssetValuation.Valued>()
        .mapNotNull { it.selectedQuote }
        .distinctBy { it.key }
    val sources = used.map { it.sourceClass() }.toSet()
    return ZakatEstimate.Available(
        exactTotalEgp = exactTotal,
        exactEstimateEgp = exactEstimate,
        totalEgp = total,
        estimateEgp = estimate,
        prefillEgp = estimate.takeIf { it > Decimal.ZERO },
        categories = listOf(valuation.gold, valuation.banks, valuation.cash).map {
            ZakatCategoryBasis(it.category, it.exactTotalEgp, it.totalEgp)
        },
        quoteSource = when {
            sources.isEmpty() -> null
            sources.size == 1 -> sources.single()
            else -> ZakatQuoteSource.MIXED
        },
        oldestQuoteAt = used.minOfOrNull { it.referenceAt },
        stale = used.any { it.freshness == QuoteFreshness.STALE },
        clockAhead = used.any { it.freshness == QuoteFreshness.CLOCK_UNCERTAIN },
        isEmpty = valuation.isEmpty,
    )
}

private fun SelectedQuote.sourceClass(): ZakatQuoteSource = when (origin) {
    QuoteOrigin.PINNED_MANUAL, QuoteOrigin.UNPINNED_MANUAL -> ZakatQuoteSource.MANUAL
    QuoteOrigin.REMOTE -> if (provenance.kind == QuoteKind.LIVE) ZakatQuoteSource.LIVE else ZakatQuoteSource.CACHED
}

/** Integer digits before the decimal point, ignoring the sign. */
private fun Decimal.integerDigits(): Int = toPlainString().removePrefix("-").substringBefore('.').length
