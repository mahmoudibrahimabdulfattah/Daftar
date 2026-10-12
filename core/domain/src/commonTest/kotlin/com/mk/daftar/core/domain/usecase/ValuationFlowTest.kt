@file:OptIn(ExperimentalCoroutinesApi::class)

package com.mk.daftar.core.domain.usecase

import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.testing.FakeBankRepository
import com.mk.daftar.core.domain.testing.FakeCashRepository
import com.mk.daftar.core.domain.testing.FakeClock
import com.mk.daftar.core.domain.testing.FakeGoldRepository
import com.mk.daftar.core.domain.testing.FakePriceRepository
import com.mk.daftar.core.domain.testing.FakeSession
import com.mk.daftar.core.domain.testing.NOW
import com.mk.daftar.core.domain.testing.USD_EGP
import com.mk.daftar.core.domain.testing.dec
import com.mk.daftar.core.domain.testing.designInput
import com.mk.daftar.core.domain.testing.remote
import com.mk.daftar.core.domain.testing.sell
import com.mk.daftar.core.domain.testing.snapshot
import com.mk.daftar.core.domain.valuation.PortfolioValuation
import com.mk.daftar.core.domain.valuation.QuoteFreshness
import com.mk.daftar.core.domain.valuation.QuoteWarning
import com.mk.daftar.core.model.GoldKarat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ValuationFlowTest {

    private inner class Harness(now: Instant) {
        val session = FakeSession()
        val clock = FakeClock(now)
        val gold = FakeGoldRepository()
        val banks = FakeBankRepository()
        val cash = FakeCashRepository()
        val prices = FakePriceRepository()

        fun portfolio() = ObservePortfolioValuationUseCase(session, gold, banks, cash, prices, clock)()

        fun selectedQuotes() = ObserveSelectedQuotesUseCase(session, prices, clock)()

        /** Every input ready for [generation], with the design quotes fetched at [quoteFetchedAt]. */
        fun readyAll(generation: Long, quoteFetchedAt: Instant = NOW) {
            val input = designInput()
            gold.holdings.value = VaultRead.Ready(generation, input.gold.map { Stored(it, 1L) })
            banks.accounts.value = VaultRead.Ready(generation, input.bankAccounts.map { Stored(it, 1L) })
            cash.balances.value = VaultRead.Ready(generation, input.cash.map { Stored(it, 1L) })
            prices.quotes.value = VaultRead.Ready(generation, designQuotes(quoteFetchedAt))
        }
    }

    private fun designQuotes(fetchedAt: Instant) = snapshot(
        remote = listOf(
            remote(sell(GoldKarat.K21), "6185", fetchedAt),
            remote(sell(GoldKarat.K18), "5301", fetchedAt),
            remote(USD_EGP, "48.50", fetchedAt),
        ),
    )

    private fun <T> TestScope.collect(flow: Flow<T>): MutableList<T> {
        val values = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { values += it } }
        return values
    }

    private fun <T> readyOf(read: VaultRead<T>): VaultRead.Ready<T> = assertIs<VaultRead.Ready<T>>(read)

    @Test
    fun readyPortfolioTotalsForTheOpenGeneration() = runTest {
        val h = Harness(NOW)
        h.readyAll(generation = 1)

        val values = collect(h.portfolio())
        runCurrent()

        val last = readyOf(values.last())
        assertEquals(1L, last.generation)
        assertEquals(dec("1076212.74"), last.value.totalEgp)
    }

    @Test
    fun lockClearsValuesAndLaterRepositoryEmissionsDoNotRevealThem() = runTest {
        val h = Harness(NOW)
        h.readyAll(generation = 1)
        val values = collect(h.portfolio())
        runCurrent()
        readyOf(values.last())

        h.session.lock()
        runCurrent()
        assertEquals(VaultRead.Locked, values.last())

        h.readyAll(generation = 1)
        runCurrent()
        assertEquals(VaultRead.Locked, values.last())
    }

    @Test
    fun mixedGenerationsProduceLoadingNeverASubtotal() = runTest {
        val h = Harness(NOW)
        h.session.current.value = VaultSessionState.Open(2)
        h.readyAll(generation = 2)
        h.gold.holdings.value = VaultRead.Ready(1, designInput().gold.map { Stored(it, 1L) })

        val values = collect(h.portfolio())
        runCurrent()

        assertEquals(VaultRead.Loading, values.last())
        assertTrue(values.none { it is VaultRead.Ready && it.generation == 1L })

        h.gold.holdings.value = VaultRead.Ready(2, designInput().gold.map { Stored(it, 1L) })
        runCurrent()
        assertEquals(2L, readyOf(values.last()).generation)
    }

    @Test
    fun generationChangeCannotReuseTheOldSessionsValues() = runTest {
        val h = Harness(NOW)
        h.readyAll(generation = 1)
        val values = collect(h.portfolio())
        runCurrent()
        readyOf(values.last())

        // The session moves to generation 2 before any repository has caught up.
        h.session.current.value = VaultSessionState.Open(2)
        runCurrent()
        assertEquals(VaultRead.Loading, values.last())

        h.readyAll(generation = 2)
        runCurrent()
        assertEquals(2L, readyOf(values.last()).generation)
    }

    @Test
    fun openingSessionIsLoading() = runTest {
        val h = Harness(NOW)
        h.session.current.value = VaultSessionState.Opening
        h.readyAll(generation = 1)

        val values = collect(h.portfolio())
        runCurrent()

        assertEquals(VaultRead.Loading, values.last())
    }

    @Test
    fun closingSessionExposesNothing() = runTest {
        val h = Harness(NOW)
        h.readyAll(generation = 1)
        val values = collect(h.portfolio())
        runCurrent()

        h.session.current.value = VaultSessionState.Closing
        runCurrent()

        assertEquals(VaultRead.Locked, values.last())
    }

    @Test
    fun unavailableSessionReportsAFailureNotAnEmptyPortfolio() = runTest {
        val h = Harness(NOW)
        h.session.current.value = VaultSessionState.Unavailable
        h.readyAll(generation = 1)

        val values = collect(h.portfolio())
        runCurrent()

        assertEquals(VaultRead.Failed(StorageError.DatabaseUnavailable), values.last())
    }

    @Test
    fun aStorageFailureIsPropagatedAndNeverOmitsTheRow() = runTest {
        val h = Harness(NOW)
        h.readyAll(generation = 1)
        val values = collect(h.portfolio())
        runCurrent()

        h.cash.balances.value = VaultRead.Failed(StorageError.CorruptData)
        runCurrent()

        assertEquals(VaultRead.Failed(StorageError.CorruptData), values.last())
        assertTrue(values.none { it is VaultRead.Ready && it.generation == 1L && it.value.isEmpty })
    }

    @Test
    fun aFailedQuoteObservationIsPropagated() = runTest {
        val h = Harness(NOW)
        h.readyAll(generation = 1)
        h.prices.quotes.value = VaultRead.Failed(StorageError.Io)

        val values = collect(h.portfolio())
        runCurrent()

        assertEquals(VaultRead.Failed(StorageError.Io), values.last())
    }

    @Test
    fun aLockedRepositoryWhileTheSessionIsOpenIsLoadingNotEmpty() = runTest {
        val h = Harness(NOW)
        h.readyAll(generation = 1)
        h.gold.holdings.value = VaultRead.Locked

        val values = collect(h.portfolio())
        runCurrent()

        assertEquals(VaultRead.Loading, values.last())
        assertTrue(values.none { it is VaultRead.Ready })
    }

    @Test
    fun ageIsReevaluatedEachMinuteWithoutNewRepositoryEmissions() = runTest {
        val fetchedAt = NOW - 23.hours - 59.minutes
        val h = Harness(NOW)
        h.readyAll(generation = 1, quoteFetchedAt = fetchedAt)
        val values = collect(h.portfolio())
        runCurrent()
        assertTrue(readyOf(values.last()).value.warnings.none { it.warning == QuoteWarning.STALE })

        h.clock.instant = NOW + 2.minutes
        advanceTimeBy(1.minutes)
        runCurrent()

        val stale = readyOf(values.last()).value
        assertTrue(stale.warnings.any { it.warning == QuoteWarning.STALE })
        // Staleness never makes a total incomplete.
        assertEquals(dec("1076212.74"), stale.totalEgp)
    }

    @Test
    fun aClockBehindTheQuoteShowsClockUncertainty() = runTest {
        val h = Harness(NOW - 1.hours)
        h.readyAll(generation = 1, quoteFetchedAt = NOW)

        val values = collect(h.portfolio())
        runCurrent()

        val value = readyOf(values.last()).value
        assertTrue(value.warnings.any { it.warning == QuoteWarning.CLOCK_UNCERTAIN })
        assertFalse(value.warnings.any { it.warning == QuoteWarning.STALE })
    }

    @Test
    fun resumedCollectionEvaluatesAgeAtOnceWithoutWaitingForTheTicker() = runTest {
        val h = Harness(NOW)
        h.readyAll(generation = 1, quoteFetchedAt = NOW - 23.hours)
        h.clock.instant = NOW + 2.hours

        val values = collect(h.portfolio())
        runCurrent()

        val value = readyOf(values.last()).value
        assertTrue(value.warnings.any { it.warning == QuoteWarning.STALE })
    }

    @Test
    fun selectedQuotesObserveTheOpenGenerationOnly() = runTest {
        val h = Harness(NOW)
        h.readyAll(generation = 1)

        val values = collect(h.selectedQuotes())
        runCurrent()

        val selected = readyOf(values.last())
        assertEquals(1L, selected.generation)
        assertEquals(dec("6185"), selected.value[sell(GoldKarat.K21)]!!.value)
        assertEquals(QuoteFreshness.FRESH, selected.value[USD_EGP]!!.freshness)
    }

    @Test
    fun selectedQuotesAreLockedAndLoadingWhenNoSessionIsOpen() = runTest {
        val h = Harness(NOW)
        h.readyAll(generation = 1)
        val values = collect(h.selectedQuotes())
        runCurrent()

        h.session.lock()
        runCurrent()
        assertEquals(VaultRead.Locked, values.last())

        h.session.current.value = VaultSessionState.Opening
        runCurrent()
        assertEquals(VaultRead.Loading, values.last())
    }

    @Test
    fun selectedQuotesReportRepositoryFailures() = runTest {
        val h = Harness(NOW)
        h.prices.quotes.value = VaultRead.Failed(StorageError.CorruptData)

        val values = collect(h.selectedQuotes())
        runCurrent()

        assertEquals(VaultRead.Failed(StorageError.CorruptData), values.last())
    }

    @Test
    fun selectedQuotesStayLoadingForAStaleGeneration() = runTest {
        val h = Harness(NOW)
        h.session.current.value = VaultSessionState.Open(3)
        h.prices.quotes.value = VaultRead.Ready(2, designQuotes(NOW))

        val values = collect(h.selectedQuotes())
        runCurrent()

        assertEquals(VaultRead.Loading, values.last())
        assertTrue(values.none { it is VaultRead.Ready<*> })
    }

    @Test
    fun portfolioIsNotYetReadyBeforeAnyRepositoryEmits() = runTest {
        val h = Harness(NOW)
        val values: MutableList<VaultRead<PortfolioValuation>> = collect(h.portfolio())
        runCurrent()

        assertEquals(listOf<VaultRead<PortfolioValuation>>(VaultRead.Loading), values.distinct())
    }

    @Test
    fun portfolioUseCaseHasNoProcessWideCacheAcrossCollections() = runTest {
        val h = Harness(NOW)
        h.readyAll(generation = 1)
        val first = collect(h.portfolio())
        runCurrent()
        readyOf(first.last())

        h.session.lock()
        runCurrent()

        val second = collect(h.portfolio())
        runCurrent()
        assertEquals(VaultRead.Locked, second.last())
    }
}
