package com.mk.daftar.core.domain.zakat

import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.testing.NOW
import com.mk.daftar.core.domain.testing.USD_EGP
import com.mk.daftar.core.domain.testing.account
import com.mk.daftar.core.domain.testing.buy
import com.mk.daftar.core.domain.testing.cashBalance
import com.mk.daftar.core.domain.testing.dec
import com.mk.daftar.core.domain.testing.egp
import com.mk.daftar.core.domain.testing.holding
import com.mk.daftar.core.domain.testing.manual
import com.mk.daftar.core.domain.testing.remote
import com.mk.daftar.core.domain.testing.sell
import com.mk.daftar.core.domain.testing.snapshot
import com.mk.daftar.core.domain.testing.usd
import com.mk.daftar.core.domain.valuation.PortfolioInput
import com.mk.daftar.core.domain.valuation.PortfolioValuation
import com.mk.daftar.core.domain.valuation.QUOTE_STALE_AFTER
import com.mk.daftar.core.domain.valuation.selectQuotes
import com.mk.daftar.core.domain.valuation.valuePortfolio
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ZakatEstimateTest {

    private fun valuationOf(input: PortfolioInput, quotes: QuoteSnapshot): PortfolioValuation =
        valuePortfolio(input, selectQuotes(quotes, NOW))

    private fun cashOnly(vararg balances: String): PortfolioInput = PortfolioInput(
        gold = emptyList(),
        bankAccounts = emptyList(),
        cash = balances.mapIndexed { index, amount -> cashBalance("cash-$index", "Cash $index", egp(amount)) },
    )

    private fun available(input: PortfolioInput, quotes: QuoteSnapshot = snapshot()): ZakatEstimate.Available =
        assertIs<ZakatEstimate.Available>(estimateZakat(valuationOf(input, quotes)))

    @Test
    fun designFixtureRoundsOnlyAfterMultiplying() {
        val input = PortfolioInput(
            gold = listOf(holding("bar", GoldKarat.K21, GoldForm.BAR, "1")),
            bankAccounts = listOf(account("bank", "Bank", egp("388500"))),
            cash = listOf(cashBalance("cash", "Cash", egp("9900"))),
        )
        val quotes = snapshot(remote = listOf(remote(sell(GoldKarat.K21), "677812.74", NOW)))
        val estimate = available(input, quotes)
        assertEquals(dec("1076212.74"), estimate.exactTotalEgp)
        assertEquals(dec("26905.3185"), estimate.exactEstimateEgp)
        assertEquals(dec("26905.32"), estimate.estimateEgp)
        assertEquals(dec("26905.32"), estimate.prefillEgp)
    }

    @Test
    fun roundingIsHalfUpAfterTheExactMultiplication() {
        assertEquals(dec("2500"), available(cashOnly("100000")).estimateEgp)
        assertEquals(dec("0.03"), available(cashOnly("1")).estimateEgp)
        assertEquals(dec("0.01"), available(cashOnly("0.20")).estimateEgp)
        assertEquals(dec("0"), available(cashOnly("0.19")).estimateEgp)
        assertNull(available(cashOnly("0.19")).prefillEgp)
    }

    @Test
    fun estimateIsComputedOnTheSumOfExactCategories() {
        val estimate = available(cashOnly("0.10", "0.10"))
        assertEquals(dec("0.20"), estimate.exactTotalEgp)
        assertEquals(dec("0.01"), estimate.estimateEgp, "not the sum of category-rounded estimates")
    }

    @Test
    fun dollarHoldingsUseTheSelectedRateAndNeedNoGoldQuote() {
        val input = PortfolioInput(
            gold = emptyList(),
            bankAccounts = listOf(account("usd", "USD", usd("100"))),
            cash = listOf(cashBalance("egp", "EGP", egp("150"))),
        )
        val estimate = available(input, snapshot(remote = listOf(remote(USD_EGP, "48.50", NOW))))
        assertEquals(dec("5000"), estimate.exactTotalEgp)
        assertEquals(dec("125.00"), estimate.estimateEgp)
    }

    @Test
    fun missingRequiredGoldQuoteMakesTheEstimateUnavailableWithoutZakatOnTheSubtotal() {
        val input = PortfolioInput(
            gold = listOf(holding("bar", GoldKarat.K21, GoldForm.BAR, "2")),
            bankAccounts = listOf(account("bank", "Bank", egp("388500"))),
            cash = listOf(cashBalance("cash", "Cash", egp("9900"))),
        )
        val unavailable = assertIs<ZakatEstimate.Unavailable>(estimateZakat(valuationOf(input, snapshot())))
        assertEquals(ZakatUnavailableReason.MISSING_QUOTE, unavailable.reason)
        assertEquals(listOf<QuoteKey>(sell(GoldKarat.K21)), unavailable.missingQuotes)
        assertEquals(dec("398400.00"), unavailable.availableSubtotalEgp)
    }

    @Test
    fun missingDollarRateMattersOnlyWhenDollarsAreHeld() {
        val withDollars =
            PortfolioInput(
                gold = emptyList(),
                bankAccounts = listOf(account("usd", "USD", usd("1"))),
                cash = emptyList(),
            )
        val unavailable = assertIs<ZakatEstimate.Unavailable>(estimateZakat(valuationOf(withDollars, snapshot())))
        assertEquals(listOf<QuoteKey>(USD_EGP), unavailable.missingQuotes)

        val zeroDollars =
            PortfolioInput(
                gold = emptyList(),
                bankAccounts = listOf(account("usd", "USD", usd("0"))),
                cash = emptyList(),
            )
        assertEquals(dec("0"), available(zeroDollars).estimateEgp)
    }

    @Test
    fun emptyVaultAndZeroHoldingsGiveZeroWithoutAPrefill() {
        val empty = available(PortfolioInput(emptyList(), emptyList(), emptyList()))
        assertEquals(dec("0"), empty.estimateEgp)
        assertTrue(empty.isEmpty)
        assertNull(empty.prefillEgp)
        assertNull(empty.quoteSource)

        val zero = available(cashOnly("0"))
        assertEquals(dec("0"), zero.estimateEgp)
        assertFalse(zero.isEmpty)
        assertNull(zero.prefillEgp)
    }

    @Test
    fun exactlyTwentyFourHoursOldIsNotStaleAndOneSecondMoreIs() {
        val boundary = NOW - QUOTE_STALE_AFTER
        val dollars = PortfolioInput(
            gold = emptyList(),
            bankAccounts = listOf(account("usd", "USD", usd("100"))),
            cash = emptyList(),
        )
        val notStale = available(dollars, snapshot(remote = listOf(remote(USD_EGP, "48.50", boundary))))
        assertFalse(notStale.stale)
        val stale = available(dollars, snapshot(remote = listOf(remote(USD_EGP, "48.50", boundary - 1.seconds))))
        assertTrue(stale.stale)
    }

    @Test
    fun pinnedManualRateWinsAndIsLabelledManual() {
        val input = PortfolioInput(
            gold = emptyList(),
            bankAccounts = listOf(account("usd", "USD", usd("100"))),
            cash = emptyList(),
        )
        val quotes = snapshot(
            remote = listOf(remote(USD_EGP, "48.50", NOW)),
            manual = listOf(manual(USD_EGP, "50", NOW, pinned = true)),
        )
        val estimate = available(input, quotes)
        assertEquals(dec("5000"), estimate.exactTotalEgp)
        assertEquals(dec("125.00"), estimate.estimateEgp)
        assertEquals(ZakatQuoteSource.MANUAL, estimate.quoteSource)
    }

    @Test
    fun mixedSourcesAreReportedAsMixed() {
        val input = PortfolioInput(
            gold = listOf(holding("bar", GoldKarat.K21, GoldForm.BAR, "1")),
            bankAccounts = listOf(account("usd", "USD", usd("10"))),
            cash = emptyList(),
        )
        val quotes = snapshot(
            remote = listOf(remote(sell(GoldKarat.K21), "6000", NOW)),
            manual = listOf(manual(USD_EGP, "50", NOW)),
        )
        assertEquals(ZakatQuoteSource.MIXED, available(input, quotes).quoteSource)
    }

    @Test
    fun cachedQuotesKeepTheirOriginalTimeAndSource() {
        val earlier = NOW - 3600.seconds
        val input = PortfolioInput(
            gold = emptyList(),
            bankAccounts = listOf(account("usd", "USD", usd("10"))),
            cash = emptyList(),
        )
        val estimate = available(
            input,
            snapshot(remote = listOf(remote(USD_EGP, "48.50", earlier, cached = true))),
        )
        assertEquals(ZakatQuoteSource.CACHED, estimate.quoteSource)
        assertEquals(earlier, estimate.oldestQuoteAt)
    }

    @Test
    fun aQuoteAheadOfTheDeviceClockIsFlaggedNotFresh() {
        val ahead = NOW + 3600.seconds
        val estimate = available(
            PortfolioInput(
                gold = emptyList(),
                bankAccounts = listOf(account("usd", "USD", usd("10"))),
                cash = emptyList(),
            ),
            snapshot(remote = listOf(remote(USD_EGP, "48.50", ahead))),
        )
        assertTrue(estimate.clockAhead)
        assertFalse(estimate.stale)
    }

    @Test
    fun unpinnedManualRateIsLabelledManualAndCountsAsAvailable() {
        val estimate = available(
            PortfolioInput(
                gold = emptyList(),
                bankAccounts = listOf(account("usd", "USD", usd("100"))),
                cash = emptyList(),
            ),
            snapshot(manual = listOf(manual(USD_EGP, "50", NOW))),
        )
        assertEquals(dec("125.00"), estimate.estimateEgp)
        assertEquals(ZakatQuoteSource.MANUAL, estimate.quoteSource)
    }

    @Test
    fun unusedBuySideGoldPricesNeverValueHoldings() {
        val input = PortfolioInput(
            gold = listOf(holding("bar", GoldKarat.K21, GoldForm.BAR, "1")),
            bankAccounts = emptyList(),
            cash = emptyList(),
        )
        val quotes = snapshot(remote = listOf(remote(buy(GoldKarat.K21), "100", NOW)))
        assertIs<ZakatEstimate.Unavailable>(estimateZakat(valuationOf(input, quotes)))
    }
}
