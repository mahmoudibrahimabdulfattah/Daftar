package com.mk.daftar.feature.home

import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.refresh.RefreshOutcome
import com.mk.daftar.core.domain.repository.RefreshRejection
import com.mk.daftar.core.domain.repository.RefreshReport
import com.mk.daftar.core.domain.repository.RefreshStepResult
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.valuation.AssetCategory
import com.mk.daftar.core.domain.valuation.PortfolioInput
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.model.FxBasis
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class HomeSummaryTest {

    @Test
    fun readyFixtureReproducesTheApprovedFigures() {
        val quotes = readyQuotes()
        val figures = summarizeHome(valuationOf(readyInput(), quotes), quotes, bankCount = 3)

        assertEquals("1076212.74", figures.total!!.toPlainString())
        assertEquals("677812.74", figures.categoryOf(AssetCategory.GOLD).total!!.toPlainString())
        assertEquals("388500", figures.categoryOf(AssetCategory.BANKS).total!!.toPlainString())
        assertEquals("9900", figures.categoryOf(AssetCategory.CASH).total!!.toPlainString())
        assertEquals(
            listOf(63, 36, 0, 1),
            figures.allocation!!.map {
                it.percent
            },
            "investments take no share when none exist",
        )
        assertEquals(3, figures.categoryOf(AssetCategory.GOLD).count)
        assertEquals(3, figures.categoryOf(AssetCategory.BANKS).count, "the bank count is the account count here")
        assertEquals(3, figures.bankCount)
        assertFalse(figures.incomplete)
        assertFalse(figures.isEmpty)
        assertEquals(QuoteProvider.EXCHANGE_RATE_API, quotes[QuoteKey.UsdEgp]!!.provenance.provider)
        assertEquals("https://www.exchangerate-api.com/docs/free", figures.fxAttributionUrl)
    }

    @Test
    fun missingEighteenKaratLeavesTheTotalUnknownAndKeepsTheAvailableSubtotal() {
        val quotes = quotesOf(remoteGold(GoldKarat.K24, "7069"), remoteGold(GoldKarat.K21, "6185"), remoteUsd("48.50"))
        val figures = summarizeHome(valuationOf(readyInput(), quotes), quotes, bankCount = 3)

        assertNull(figures.total, "an incomplete valuation never shows a total")
        assertTrue(figures.incomplete)
        assertEquals("1058772.45", figures.subtotal.toPlainString())
        assertEquals(listOf<QuoteKey>(sellKey(GoldKarat.K18)), figures.missingQuotes)
        assertNull(figures.categoryOf(AssetCategory.GOLD).total)
        assertNull(figures.allocation, "no allocation strip while incomplete")
        assertNull(
            figures.quotes.quotes.first {
                it.key == sellKey(GoldKarat.K18)
            }.value,
            "a missing quote is null, never zero",
        )
    }

    @Test
    fun noRecordsIsEmptyAndExposesNoTotal() {
        val empty = PortfolioInput(gold = emptyList(), bankAccounts = emptyList(), cash = emptyList())
        val figures = summarizeHome(valuationOf(empty, SelectedQuotes.EMPTY), SelectedQuotes.EMPTY, bankCount = 0)

        assertTrue(figures.isEmpty)
        assertNull(figures.total, "the empty zero total is suppressed")
        assertFalse(figures.incomplete)
        assertNull(figures.allocation)
    }

    @Test
    fun allZeroBalancesAreAValuedZeroTotalWithoutAnAllocationStrip() {
        val zero = PortfolioInput(
            gold = emptyList(),
            bankAccounts = listOf(bankAccount("a1", "b", egpAmount("0"))),
            cash = listOf(cashBalance("c1", egpAmount("0"))),
        )
        val figures = summarizeHome(valuationOf(zero, SelectedQuotes.EMPTY), SelectedQuotes.EMPTY, bankCount = 1)

        assertFalse(figures.isEmpty)
        assertEquals("0", figures.total!!.toPlainString())
        assertNull(figures.allocation)
    }

    @Test
    fun usdBalancesAreConvertedWithTheSelectedRateOnly() {
        val input = PortfolioInput(
            gold = emptyList(),
            bankAccounts = listOf(bankAccount("a1", "b", usdAmount("100.5"))),
            cash = emptyList(),
        )
        val quotes = quotesOf(remoteUsd("48.50"))
        val figures = summarizeHome(valuationOf(input, quotes), quotes, bankCount = 1)

        assertEquals("4874.25", figures.total!!.toPlainString())
    }

    @Test
    fun theStripNamesWhatTheUsdRateIs() {
        val bank = quotesOf(remoteUsd("52.77", provider = QuoteProvider.BANQUE_MISR))
        assertEquals(FxBasis.BANK_TRANSFER_BUY, summarizeHome(valuationOf(readyInput(), bank), bank, 1).quotes.usdBasis)
        val estimate = quotesOf(remoteUsd("48.50"))
        assertEquals(
            FxBasis.MID_MARKET_ESTIMATE,
            summarizeHome(valuationOf(readyInput(), estimate), estimate, 1).quotes.usdBasis,
        )
    }

    @Test
    fun manualPinnedGoldIsBadgedManualAndNoFxAttributionIsOwedForIt() {
        val quotes = quotesOf(
            remoteUsd("48.50"),
            manual = listOf(pinnedManualGold(GoldKarat.K21, "6000", FIXTURE_NOW)),
        )
        val figures = summarizeHome(valuationOf(readyInput(), quotes), quotes, bankCount = 3)

        assertEquals(QuoteBadge.MANUAL, quotes[sellKey(GoldKarat.K21)]!!.badge())
        assertEquals(QuoteBadge.MANUAL, figures.quotes.badge)
    }

    @Test
    fun aQuoteOlderThanADayIsStaleAndStillUsable() {
        val old = FIXTURE_NOW - 30.hours
        val quotes = quotesOf(remoteGold(GoldKarat.K21, "6185", fetchedAt = old), remoteUsd("48.50"))
        val selected = quotes[sellKey(GoldKarat.K21)]!!

        assertEquals(QuoteBadge.STALE, selected.badge())
        assertEquals("6185", selected.value.toPlainString(), "stale values remain usable")
    }

    @Test
    fun aLiveFetchIsLiveAndAStoredOneIsCached() {
        val live = quotesOf(remoteGold(GoldKarat.K21, "6185"))
        assertEquals(QuoteBadge.LIVE, live[sellKey(GoldKarat.K21)]!!.badge())

        val cached = quotesOf(
            com.mk.daftar.core.domain.repository.RemoteQuoteRecord(
                quote = (remoteGold(GoldKarat.K21, "6185").quote as com.mk.daftar.core.model.GoldQuote)
                    .let { it.copy(provenance = it.provenance.restoredFromStorage()) },
                nextUpdateAt = null,
                attributionUrl = null,
            ),
        )
        assertEquals(QuoteBadge.CACHED, cached[sellKey(GoldKarat.K21)]!!.badge())
    }

    @Test
    fun readsFromAnotherGenerationNeverMix() {
        val valuation = VaultRead.Ready(generation = 2, value = valuationOf(readyInput(), readyQuotes()))
        val staleQuotes = VaultRead.Ready(generation = 1, value = readyQuotes())
        val banks = VaultRead.Ready(generation = 2, value = 3)

        val state = homeStateOf(valuation, staleQuotes, banks, valuesHidden = false, refresh = PriceRefreshStatus.Idle)

        assertEquals(HomeLoad.Loading, state.load)
    }

    @Test
    fun sameGenerationReadsAreReadyAndRefreshStatusCountsOnlyForThatGeneration() {
        val valuation = VaultRead.Ready(generation = 5, value = valuationOf(readyInput(), readyQuotes()))
        val quotes = VaultRead.Ready(generation = 5, value = readyQuotes())
        val banks = VaultRead.Ready(generation = 5, value = 3)

        val refreshing = homeStateOf(valuation, quotes, banks, false, PriceRefreshStatus.Refreshing(5))
        assertIs<HomeLoad.Ready>(refreshing.load)
        assertTrue(refreshing.refreshing)

        val otherGeneration = homeStateOf(valuation, quotes, banks, false, PriceRefreshStatus.Refreshing(4))
        assertFalse(otherGeneration.refreshing)

        val partial = homeStateOf(
            valuation,
            quotes,
            banks,
            false,
            PriceRefreshStatus.Finished(5, RefreshOutcome.PartiallyFailed),
        )
        assertTrue(partial.refreshFailed)
        assertIs<HomeLoad.Ready>(partial.load, "a failed refresh keeps the last valid values")
    }

    @Test
    fun storageFailureIsAnErrorNotAnEmptyValuation() {
        val failed = homeStateOf(
            valuation = VaultRead.Failed(StorageError.Io),
            quotes = VaultRead.Ready(1, readyQuotes()),
            bankCount = VaultRead.Ready(1, 3),
            valuesHidden = false,
            refresh = PriceRefreshStatus.Idle,
        )
        assertEquals(HomeLoad.Failed(StorageError.Io), failed.load)
    }

    @Test
    fun countsOfAReadyListKeepTheGenerationAndAreNotInferredFromAccounts() {
        val banks = VaultRead.Ready(generation = 3, value = listOf(Stored("x", 1), Stored("y", 1)))
        assertEquals(VaultRead.Ready(3, 2), banks.countReady())
    }

    private fun HomeFigures.categoryOf(category: AssetCategory): CategoryFigures =
        categories.first { it.category == category }
}
