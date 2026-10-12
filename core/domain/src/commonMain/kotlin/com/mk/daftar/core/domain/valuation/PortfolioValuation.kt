package com.mk.daftar.core.domain.valuation

import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.DecimalRounding
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.QuoteKey

/** Records that count towards wealth. Journal entries are not part of this input by design. */
data class PortfolioInput(
    val gold: List<GoldHolding>,
    val bankAccounts: List<BankAccount>,
    val cash: List<CashBalance>,
)

/** A typed reference to one valued record. */
sealed interface AssetRef {
    data class Gold(val id: GoldHoldingId) : AssetRef

    data class Account(val id: BankAccountId) : AssetRef

    data class Cash(val id: CashBalanceId) : AssetRef
}

sealed interface AssetValuation {
    val asset: AssetRef

    /** [exactEgp] is exact and unrounded. [selectedQuote] is null when no quote was needed (EGP or a zero USD balance). */
    data class Valued(override val asset: AssetRef, val exactEgp: Decimal, val selectedQuote: SelectedQuote?) :
        AssetValuation

    /** The asset cannot be valued until [requiredQuoteKey] has a usable quote. */
    data class Unvalued(override val asset: AssetRef, val requiredQuoteKey: QuoteKey) : AssetValuation
}

enum class AssetCategory { GOLD, BANKS, CASH }

/**
 * One category. [subtotalEgp] sums the valued assets only and is never a complete total. [totalEgp] is present only
 * when every asset in the category is valued. The exact sums are kept; the presentation values are rounded HALF_UP to
 * two places after summing, never by summing rounded rows.
 */
data class CategoryValuation(
    val category: AssetCategory,
    val assets: List<AssetValuation>,
    val exactSubtotalEgp: Decimal,
    val subtotalEgp: Decimal,
    val exactTotalEgp: Decimal?,
    val totalEgp: Decimal?,
    val unvaluedAssets: List<AssetValuation.Unvalued>,
    val warnings: List<SelectedQuoteWarning>,
)

data class PortfolioValuation(
    val gold: CategoryValuation,
    val banks: CategoryValuation,
    val cash: CategoryValuation,
    val exactSubtotalEgp: Decimal,
    val subtotalEgp: Decimal,
    val exactTotalEgp: Decimal?,
    /** Present only when every asset in every category is valued. */
    val totalEgp: Decimal?,
    val unvaluedAssets: List<AssetValuation.Unvalued>,
    val warnings: List<SelectedQuoteWarning>,
    /** True when there are no gold holdings, bank accounts or cash balances. Totals are then exact zero and complete. */
    val isEmpty: Boolean,
)

/**
 * Values [input] with [quotes]. Pure: no I/O and no clock.
 *
 * Gold uses the sell-side price for the holding's karat. EGP balances are taken unchanged, and USD balances are
 * multiplied by the USD to EGP rate; the recorded balance is never mutated. Products and sums stay exact. Only the
 * presentation values are rounded.
 */
fun valuePortfolio(input: PortfolioInput, quotes: SelectedQuotes): PortfolioValuation {
    val gold = categoryOf(AssetCategory.GOLD, input.gold.map { valueGold(it, quotes) })
    val banks =
        categoryOf(
            AssetCategory.BANKS,
            input.bankAccounts.map {
                valueMoney(AssetRef.Account(it.id), it.balance, quotes)
            },
        )
    val cash = categoryOf(AssetCategory.CASH, input.cash.map { valueMoney(AssetRef.Cash(it.id), it.balance, quotes) })

    val all = gold.assets + banks.assets + cash.assets
    val exactSubtotal = sumValued(all)
    val unvalued = all.filterIsInstance<AssetValuation.Unvalued>()
    val exactTotal = if (unvalued.isEmpty()) exactSubtotal else null
    return PortfolioValuation(
        gold = gold,
        banks = banks,
        cash = cash,
        exactSubtotalEgp = exactSubtotal,
        subtotalEgp = exactSubtotal.presentation(),
        exactTotalEgp = exactTotal,
        totalEgp = exactTotal?.presentation(),
        unvaluedAssets = unvalued,
        warnings = (gold.warnings + banks.warnings + cash.warnings).distinct(),
        isEmpty = input.gold.isEmpty() && input.bankAccounts.isEmpty() && input.cash.isEmpty(),
    )
}

private fun valueGold(holding: GoldHolding, quotes: SelectedQuotes): AssetValuation {
    val ref = AssetRef.Gold(holding.id)
    val key = goldSellKey(holding.karat)
    val quote = quotes[key] ?: return AssetValuation.Unvalued(ref, key)
    return AssetValuation.Valued(ref, holding.weight.grams * quote.value, quote)
}

private fun valueMoney(ref: AssetRef, money: Money, quotes: SelectedQuotes): AssetValuation = when (money.currency) {
    Currency.EGP -> AssetValuation.Valued(ref, money.amount, selectedQuote = null)
    Currency.USD -> valueUsd(ref, money.amount, quotes)
}

private fun valueUsd(ref: AssetRef, amount: Decimal, quotes: SelectedQuotes): AssetValuation {
    // A zero balance is worth zero whatever the rate is, so it never waits for FX.
    if (amount == Decimal.ZERO) return AssetValuation.Valued(ref, Decimal.ZERO, selectedQuote = null)
    val quote = quotes[QuoteKey.UsdEgp] ?: return AssetValuation.Unvalued(ref, QuoteKey.UsdEgp)
    return AssetValuation.Valued(ref, amount * quote.value, quote)
}

private fun categoryOf(category: AssetCategory, assets: List<AssetValuation>): CategoryValuation {
    val exactSubtotal = sumValued(assets)
    val unvalued = assets.filterIsInstance<AssetValuation.Unvalued>()
    val exactTotal = if (unvalued.isEmpty()) exactSubtotal else null
    val warnings = assets.filterIsInstance<AssetValuation.Valued>()
        .mapNotNull { it.selectedQuote }
        .flatMap { quote -> quote.warnings.map { SelectedQuoteWarning(quote.key, it) } }
        .distinct()
    return CategoryValuation(
        category = category,
        assets = assets,
        exactSubtotalEgp = exactSubtotal,
        subtotalEgp = exactSubtotal.presentation(),
        exactTotalEgp = exactTotal,
        totalEgp = exactTotal?.presentation(),
        unvaluedAssets = unvalued,
        warnings = warnings,
    )
}

private fun sumValued(assets: List<AssetValuation>): Decimal = assets
    .filterIsInstance<AssetValuation.Valued>()
    .fold(Decimal.ZERO) { sum, valued -> sum + valued.exactEgp }

/** Presentation rounding: two places, HALF_UP. Applied only after exact summation. */
internal fun Decimal.presentation(): Decimal = roundToScale(2, DecimalRounding.HALF_UP)
