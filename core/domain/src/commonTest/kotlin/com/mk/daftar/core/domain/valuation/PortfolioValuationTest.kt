package com.mk.daftar.core.domain.valuation

import com.mk.daftar.core.domain.testing.NOW
import com.mk.daftar.core.domain.testing.USD_EGP
import com.mk.daftar.core.domain.testing.account
import com.mk.daftar.core.domain.testing.buy
import com.mk.daftar.core.domain.testing.cashBalance
import com.mk.daftar.core.domain.testing.dec
import com.mk.daftar.core.domain.testing.designInput
import com.mk.daftar.core.domain.testing.designQuotes
import com.mk.daftar.core.domain.testing.egp
import com.mk.daftar.core.domain.testing.holding
import com.mk.daftar.core.domain.testing.investment
import com.mk.daftar.core.domain.testing.remote
import com.mk.daftar.core.domain.testing.sell
import com.mk.daftar.core.domain.testing.snapshot
import com.mk.daftar.core.domain.testing.usd
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortfolioValuationTest {

    private fun valueDesign(includeEighteen: Boolean = true): PortfolioValuation =
        valuePortfolio(designInput(), selectQuotes(designQuotes(includeEighteen), NOW))

    @Test
    fun designFixtureReproducesEachCategoryAndTheTotal() {
        val portfolio = valueDesign()

        assertEquals(dec("677812.74"), portfolio.gold.totalEgp)
        assertEquals(dec("388500.00"), portfolio.banks.totalEgp)
        assertEquals(dec("9900.00"), portfolio.cash.totalEgp)
        assertEquals(dec("1076212.74"), portfolio.totalEgp)
        assertEquals(dec("1076212.74"), portfolio.subtotalEgp)
        assertTrue(portfolio.unvaluedAssets.isEmpty())
        assertFalse(portfolio.isEmpty)
    }

    @Test
    fun designFixtureRowsAreExact() {
        val gold = valueDesign().gold.assets.associate { it.asset to it }
        val bars = gold.getValue(assetId("bars-21"))
        val jewelry21 = gold.getValue(assetId("jewelry-21"))
        val jewelry18 = gold.getValue(assetId("jewelry-18"))

        assertEquals(dec("494800"), assertIs<AssetValuation.Valued>(bars).exactEgp)
        assertEquals(dec("165572.45"), assertIs<AssetValuation.Valued>(jewelry21).exactEgp)
        assertEquals(dec("17440.29"), assertIs<AssetValuation.Valued>(jewelry18).exactEgp)
    }

    @Test
    fun removingTheEighteenKaratQuoteLeavesExactlyOneUnvaluedHolding() {
        val portfolio = valueDesign(includeEighteen = false)

        assertNull(portfolio.totalEgp)
        assertNull(portfolio.exactTotalEgp)
        assertEquals(dec("1058772.45"), portfolio.subtotalEgp)
        assertEquals(1, portfolio.unvaluedAssets.size)
        val unvalued = portfolio.unvaluedAssets.single()
        assertEquals(assetId("jewelry-18"), unvalued.asset)
        assertEquals(sell(GoldKarat.K18), unvalued.requiredQuoteKey)
        // The category total is absent too, but the other categories stay complete.
        assertNull(portfolio.gold.totalEgp)
        assertEquals(dec("388500.00"), portfolio.banks.totalEgp)
        assertEquals(dec("9900.00"), portfolio.cash.totalEgp)
    }

    @Test
    fun goldUsesTheSellPriceForItsKarat() {
        val quotes = selectQuotes(
            snapshot(
                remote = listOf(
                    remote(sell(GoldKarat.K21), "6185", NOW),
                    remote(buy(GoldKarat.K21), "6900", NOW),
                ),
            ),
            NOW,
        )
        val input = PortfolioInput(
            gold = listOf(holding("bars", GoldKarat.K21, GoldForm.BAR, "10")),
            bankAccounts = emptyList(),
            cash = emptyList(),
        )

        val portfolio = valuePortfolio(input, quotes)

        assertEquals(dec("61850"), portfolio.gold.totalEgp)
    }

    @Test
    fun buySideQuotesAloneNeverValueGold() {
        val quotes = selectQuotes(snapshot(remote = listOf(remote(buy(GoldKarat.K21), "6900", NOW))), NOW)
        val input = PortfolioInput(
            gold = listOf(holding("bars", GoldKarat.K21, GoldForm.BAR, "1")),
            bankAccounts = emptyList(),
            cash = emptyList(),
        )

        val portfolio = valuePortfolio(input, quotes)

        assertNull(portfolio.totalEgp)
        assertEquals(sell(GoldKarat.K21), portfolio.unvaluedAssets.single().requiredQuoteKey)
    }

    @Test
    fun multiplicationStaysExactAndOnlyPresentationIsRounded() {
        // 3.33 g × 5301.1234 EGP/g = 17652.740922 exactly; presentation is 17652.74.
        val quotes = selectQuotes(snapshot(remote = listOf(remote(sell(GoldKarat.K18), "5301.1234", NOW))), NOW)
        val input = PortfolioInput(
            gold = listOf(holding("jewelry", GoldKarat.K18, GoldForm.JEWELRY, "3.33")),
            bankAccounts = emptyList(),
            cash = emptyList(),
        )

        val portfolio = valuePortfolio(input, quotes)

        assertEquals(dec("17652.740922"), portfolio.exactTotalEgp)
        assertEquals(dec("17652.74"), portfolio.totalEgp)
        assertEquals(dec("17652.74"), portfolio.subtotalEgp)
    }

    @Test
    fun halfUpRoundsExactlyAtTheBoundary() {
        // 0.01 g × 0.5 EGP/g = 0.005 exactly, which HALF_UP presents as 0.01.
        val half = valuePortfolio(
            PortfolioInput(listOf(holding("half", GoldKarat.K24, GoldForm.BAR, "0.01")), emptyList(), emptyList()),
            selectQuotes(snapshot(remote = listOf(remote(sell(GoldKarat.K24), "0.5", NOW))), NOW),
        )
        assertEquals(dec("0.005"), half.exactTotalEgp)
        assertEquals(dec("0.01"), half.totalEgp)

        // 0.01 g × 0.49 EGP/g = 0.0049, which rounds down to 0.00.
        val below = valuePortfolio(
            PortfolioInput(listOf(holding("below", GoldKarat.K24, GoldForm.BAR, "0.01")), emptyList(), emptyList()),
            selectQuotes(snapshot(remote = listOf(remote(sell(GoldKarat.K24), "0.49", NOW))), NOW),
        )
        assertEquals(dec("0.0049"), below.exactTotalEgp)
        assertEquals(Decimal.ZERO, below.totalEgp)
    }

    @Test
    fun rowsAreSummedExactlyNotAsRoundedRows() {
        // Three USD balances of 0.01 at 0.5 EGP/USD: each row is 0.005 exactly. Summed exactly it is 0.015, which
        // presents as 0.02. Summing rounded rows would wrongly give 0.03.
        val quotes = selectQuotes(snapshot(remote = listOf(remote(USD_EGP, "0.5", NOW))), NOW)
        val input = PortfolioInput(
            gold = emptyList(),
            bankAccounts = listOf(
                account("a", "A", usd("0.01")),
                account("b", "B", usd("0.01")),
                account("c", "C", usd("0.01")),
            ),
            cash = emptyList(),
        )

        val portfolio = valuePortfolio(input, quotes)

        assertEquals(dec("0.015"), portfolio.exactSubtotalEgp)
        assertEquals(dec("0.02"), portfolio.subtotalEgp)
        assertEquals(dec("0.02"), portfolio.totalEgp)
    }

    @Test
    fun usdBalanceIsConvertedWithoutMutatingTheRecordedBalance() {
        val recorded = account("misr-usd", "Misr savings", usd("3000"))
        val quotes = selectQuotes(designQuotes(), NOW)

        val portfolio = valuePortfolio(
            PortfolioInput(gold = emptyList(), bankAccounts = listOf(recorded), cash = emptyList()),
            quotes,
        )

        assertEquals(dec("145500"), portfolio.banks.totalEgp)
        assertEquals(Currency.USD, recorded.balance.currency)
        assertEquals(dec("3000"), recorded.balance.amount)
    }

    @Test
    fun mixedCurrenciesKeepEgpBalancesUnchanged() {
        val quotes = selectQuotes(designQuotes(), NOW)
        val portfolio = valuePortfolio(
            PortfolioInput(
                gold = emptyList(),
                bankAccounts = listOf(
                    account("egp", "EGP", egp("12.34")),
                    account("usd", "USD", usd("10")),
                ),
                cash = listOf(cashBalance("cash", "Cash", egp("0.01"))),
            ),
            quotes,
        )

        assertEquals(dec("497.34"), portfolio.banks.totalEgp)
        assertEquals(dec("0.01"), portfolio.cash.totalEgp)
        assertEquals(dec("497.35"), portfolio.totalEgp)
    }

    @Test
    fun emptyPortfolioIsExactZeroAndComplete() {
        val portfolio = valuePortfolio(PortfolioInput(emptyList(), emptyList(), emptyList()), SelectedQuotes.EMPTY)

        assertTrue(portfolio.isEmpty)
        assertEquals(Decimal.ZERO, portfolio.totalEgp)
        assertEquals(Decimal.ZERO, portfolio.exactTotalEgp)
        assertEquals(Decimal.ZERO, portfolio.subtotalEgp)
        assertTrue(portfolio.unvaluedAssets.isEmpty())
        assertTrue(portfolio.warnings.isEmpty())
    }

    @Test
    fun zeroBalancesAreValuedWithoutAnyQuote() {
        val portfolio = valuePortfolio(
            PortfolioInput(
                gold = emptyList(),
                bankAccounts = listOf(account("usd0", "USD zero", usd("0"))),
                cash = listOf(cashBalance("cash0", "Cash zero", egp("0"))),
            ),
            SelectedQuotes.EMPTY,
        )

        assertFalse(portfolio.isEmpty)
        assertEquals(Decimal.ZERO, portfolio.totalEgp)
        assertTrue(portfolio.unvaluedAssets.isEmpty())
        val valued = assertIs<AssetValuation.Valued>(portfolio.banks.assets.single())
        assertNull(valued.selectedQuote)
    }

    @Test
    fun usdWithoutFxIsUnvaluedUntilTheRateExists() {
        val input = PortfolioInput(emptyList(), listOf(account("usd", "USD", usd("1"))), emptyList())

        val withoutRate = valuePortfolio(input, SelectedQuotes.EMPTY)

        assertNull(withoutRate.totalEgp)
        assertEquals(USD_EGP, withoutRate.unvaluedAssets.single().requiredQuoteKey)
        assertEquals(Decimal.ZERO, withoutRate.subtotalEgp)
    }

    @Test
    fun aMissingFxQuoteDoesNotMakeEgpBalancesIncomplete() {
        val input = PortfolioInput(
            gold = emptyList(),
            bankAccounts = listOf(account("egp", "EGP", egp("100")), account("usd", "USD", usd("1"))),
            cash = emptyList(),
        )

        val portfolio = valuePortfolio(input, SelectedQuotes.EMPTY)

        assertEquals(dec("100"), portfolio.subtotalEgp)
        assertEquals(1, portfolio.unvaluedAssets.size)
        assertNull(portfolio.totalEgp)
    }

    @Test
    fun valuationCategoriesAreGoldBanksInvestmentsAndCashOnly() {
        // Journal entries (zakat payments and personal notes) have no place in the valuation input. The categories
        // below are the only ones that can count towards wealth.
        assertEquals(
            listOf(AssetCategory.GOLD, AssetCategory.BANKS, AssetCategory.INVESTMENTS, AssetCategory.CASH),
            AssetCategory.entries,
        )
        assertEquals(dec("1076212.74"), valueDesign().totalEgp)
    }

    @Test
    fun investmentsAddTheirExactAmountToTheTotalAndTheirOwnCategory() {
        val input = PortfolioInput(
            gold = emptyList(),
            bankAccounts = emptyList(),
            cash = listOf(cashBalance("cash", "Cash", egp("1076212.74"))),
            investments = listOf(investment("broker", "157000"), investment("other", "0.10")),
        )

        val portfolio = valuePortfolio(input, SelectedQuotes.EMPTY)

        assertEquals(dec("1233212.84"), portfolio.totalEgp)
        assertEquals(dec("157000.10"), portfolio.investments.totalEgp)
        assertEquals(2, portfolio.investments.assets.size)
        assertTrue(portfolio.unvaluedAssets.isEmpty(), "an investment never waits for a quote")
    }

    @Test
    fun aZeroInvestmentIsValuedAndMakesThePortfolioNonEmpty() {
        val portfolio = valuePortfolio(
            PortfolioInput(emptyList(), emptyList(), emptyList(), listOf(investment("zero", "0"))),
            SelectedQuotes.EMPTY,
        )

        assertEquals(dec("0"), portfolio.totalEgp)
        assertFalse(portfolio.isEmpty)
    }

    @Test
    fun twoInvestmentsSumExactlyBeforeAnyRounding() {
        val portfolio = valuePortfolio(
            PortfolioInput(
                emptyList(),
                emptyList(),
                emptyList(),
                listOf(investment("a", "0.10"), investment("b", "0.10")),
            ),
            SelectedQuotes.EMPTY,
        )

        assertEquals(dec("0.2"), portfolio.exactTotalEgp)
        assertEquals(dec("0.2"), portfolio.totalEgp)
    }

    @Test
    fun fxFromTheFallbackProviderIsUsedWhenThePrimaryIsAbsent() {
        val quotes = selectQuotes(
            snapshot(remote = listOf(remote(USD_EGP, "50", NOW, provider = QuoteProvider.FAWAZ_CURRENCY_API))),
            NOW,
        )

        val portfolio = valuePortfolio(
            PortfolioInput(emptyList(), listOf(account("usd", "USD", usd("2"))), emptyList()),
            quotes,
        )

        assertEquals(dec("100"), portfolio.totalEgp)
    }

    private fun assetId(id: String): AssetRef = AssetRef.Gold(GoldHoldingId(id))
}
