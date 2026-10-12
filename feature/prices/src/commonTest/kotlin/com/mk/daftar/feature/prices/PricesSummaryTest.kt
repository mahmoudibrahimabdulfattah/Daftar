package com.mk.daftar.feature.prices

import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.refresh.RefreshOutcome
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RefreshRejection
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.valuation.PortfolioInput
import com.mk.daftar.core.domain.valuation.QuoteFreshness
import com.mk.daftar.core.domain.valuation.QuoteOrigin
import com.mk.daftar.core.domain.valuation.selectQuotes
import com.mk.daftar.core.domain.valuation.valuePortfolio
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class PricesSummaryTest {

    private val now = FIXTURE_NOW

    /** The board for one snapshot, valued against [holdings] and [cash] with the same domain rules the app uses. */
    private fun boardOf(
        snapshot: QuoteSnapshot,
        holdings: List<GoldHolding> = emptyList(),
        cash: List<CashBalance> = emptyList(),
    ): PricesBoard {
        val selected = selectQuotes(snapshot, now)
        val valuation = valuePortfolio(PortfolioInput(holdings, emptyList(), cash), selected)
        return priceBoardOf(snapshot, selected, valuation, holdings, now)
    }

    /** The screen state for a snapshot read, with every other read from the same generation. */
    private fun stateOf(
        snapshot: VaultRead<QuoteSnapshot>,
        holdings: List<GoldHolding> = emptyList(),
        refresh: PriceRefreshStatus = PriceRefreshStatus.Idle,
        valuesHidden: Boolean = false,
    ): PricesUiState {
        val ready = snapshot as? VaultRead.Ready
        val selected = if (ready != null) {
            VaultRead.Ready(ready.generation, selectQuotes(ready.value, now))
        } else {
            VaultRead.Loading
        }
        val valuation = if (ready != null) {
            VaultRead.Ready(
                ready.generation,
                valuePortfolio(PortfolioInput(holdings, emptyList(), emptyList()), selectQuotes(ready.value, now)),
            )
        } else {
            VaultRead.Loading
        }
        val held: VaultRead<List<Stored<GoldHolding>>> = if (ready != null) {
            VaultRead.Ready(ready.generation, holdings.map { Stored(it, 1) })
        } else {
            VaultRead.Loading
        }
        return pricesStateOf(snapshot, selected, valuation, held, now, valuesHidden, refresh)
    }

    private fun readyBoard(state: PricesUiState): PricesBoard = assertIs<PricesLoad.Ready>(state.load).board

    private fun ready(generation: Long, snapshot: QuoteSnapshot) = VaultRead.Ready(generation, snapshot)

    @Test
    fun theSellColumnIsTheEffectiveSelectionAndTheBuyColumnIsRemoteOnly() {
        val board = boardOf(fullSnapshot())
        val row = board.karats.first { it.karat == GoldKarat.K21 }
        assertEquals(dec("6185"), row.sell?.value)
        assertEquals(buyKey(GoldKarat.K21), row.buy?.key)
        assertEquals(dec("6135"), row.buy?.value)
        assertNull(row.sourceSell)
        assertTrue(board.missing.isEmpty())
        assertTrue(board.stale.isEmpty())
        assertNull(board.details)
    }

    @Test
    fun aPinnedOverrideIsTheSellPriceAndTheRemotePriceStaysBeneathIt() {
        val snapshot = fullSnapshot().copy(
            manual = listOf(manualGold(GoldKarat.K21, "6100", revision = 3, pinned = true)),
        )
        val row = boardOf(snapshot).karats.first { it.karat == GoldKarat.K21 }
        assertEquals(dec("6100"), row.sell?.value)
        assertEquals(QuoteOrigin.PINNED_MANUAL, row.sell?.origin)
        assertEquals(dec("6185"), row.sourceSell?.value)
        assertEquals(QuoteOrigin.REMOTE, row.sourceSell?.origin)
        // The buy column never reads the override.
        assertEquals(dec("6135"), row.buy?.value)
    }

    @Test
    fun aPinnedUsdOverrideKeepsItsRemoteRateBeneathIt() {
        val snapshot = fullSnapshot().copy(manual = listOf(manualUsd("49.10", revision = 2, pinned = true)))
        val board = boardOf(snapshot)
        assertEquals(dec("49.10"), board.usd?.value)
        assertEquals(dec("48.50"), board.usdSource?.value)
    }

    @Test
    fun anUnpinnedManualValueIsALastResortWhenNoRemotePriceExists() {
        val withoutRemote18 = fullSnapshot().remote.filterNot { priceQuoteKey(it.quote) == sellKey(GoldKarat.K18) }
        val snapshot = QuoteSnapshot(
            remote = withoutRemote18,
            manual = listOf(manualGold(GoldKarat.K18, "5200", revision = 1, pinned = false)),
        )
        val row = boardOf(snapshot).karats.first { it.karat == GoldKarat.K18 }
        assertEquals(QuoteOrigin.UNPINNED_MANUAL, row.sell?.origin)
        assertEquals(dec("5200"), row.sell?.value)
        assertNull(row.sourceSell)
    }

    @Test
    fun aMissingSellPriceIsMissingAndNeverInferredFromAnotherKaratOrSide() {
        val snapshot = fullSnapshot().copy(
            remote = fullSnapshot().remote.filterNot {
                priceQuoteKey(it.quote) ==
                    sellKey(GoldKarat.K18)
            },
        )
        val board = boardOf(snapshot)
        val row = board.karats.first { it.karat == GoldKarat.K18 }
        assertNull(row.sell)
        assertEquals(dec("5259"), row.buy?.value)
        assertEquals(listOf<QuoteKey>(sellKey(GoldKarat.K18)), board.missing)
    }

    @Test
    fun aMissingUsdRateIsListedAsMissing() {
        val snapshot = fullSnapshot().copy(
            remote = fullSnapshot().remote.filterNot {
                priceQuoteKey(it.quote) ==
                    QuoteKey.UsdEgp
            },
        )
        val board = boardOf(snapshot)
        assertNull(board.usd)
        assertEquals(listOf<QuoteKey>(QuoteKey.UsdEgp), board.missing)
    }

    @Test
    fun aQuoteOlderThanTheStaleWindowIsStaleAndListed() {
        val snapshot = fullSnapshot().copy(
            remote = fullSnapshot().remote.map {
                if (priceQuoteKey(it.quote) == sellKey(GoldKarat.K21)) {
                    remoteGold(GoldKarat.K21, "6185", fetchedAt = now - 2.days, cached = true)
                } else {
                    it
                }
            },
        )
        val board = boardOf(snapshot)
        assertEquals(QuoteFreshness.STALE, board.karats.first { it.karat == GoldKarat.K21 }.sell?.freshness)
        assertEquals(listOf<QuoteKey>(sellKey(GoldKarat.K21)), board.stale)
    }

    @Test
    fun theBadgeNamesLiveCachedManualStaleAndClockUncertainQuotes() {
        val snapshot = QuoteSnapshot(
            remote = listOf(
                remoteGold(GoldKarat.K24, "7069"),
                remoteGold(GoldKarat.K21, "6185", cached = true),
                remoteGold(GoldKarat.K18, "5301", fetchedAt = now + 1.days),
                remoteUsd("48.50", fetchedAt = now - 2.days, cached = true),
            ),
            manual = emptyList(),
        )
        val selected = selectQuotes(snapshot, now)
        assertEquals(PriceBadge.LIVE, selected[sellKey(GoldKarat.K24)]?.priceBadge())
        assertEquals(PriceBadge.CACHED, selected[sellKey(GoldKarat.K21)]?.priceBadge())
        assertEquals(PriceBadge.CLOCK_UNCERTAIN, selected[sellKey(GoldKarat.K18)]?.priceBadge())
        assertEquals(PriceBadge.STALE, selected[QuoteKey.UsdEgp]?.priceBadge())

        val manual = selectQuotes(
            QuoteSnapshot(remote = emptyList(), manual = listOf(manualGold(GoldKarat.K24, "7000", 1, pinned = true))),
            now,
        )
        assertEquals(PriceBadge.MANUAL, manual[sellKey(GoldKarat.K24)]?.priceBadge())
        assertEquals(QuoteKind.MANUAL, manual[sellKey(GoldKarat.K24)]?.provenance?.kind)
    }

    @Test
    fun unvaluedGoldIsGroupedByKaratAndFormWithItsExactWeightAndTheValuedSubtotal() {
        val snapshot = fullSnapshot().copy(
            remote = fullSnapshot().remote.filterNot {
                priceQuoteKey(it.quote) ==
                    sellKey(GoldKarat.K18)
            },
        )
        val holdings = listOf(
            goldHolding("j1", GoldKarat.K18, GoldForm.JEWELRY, "3.29"),
            goldHolding("b1", GoldKarat.K18, GoldForm.BAR, "1.00"),
            goldHolding("j2", GoldKarat.K18, GoldForm.JEWELRY, "0.71"),
            goldHolding("v1", GoldKarat.K21, GoldForm.BAR, "2"),
        )
        val details = boardOf(snapshot, holdings).details
        assertEquals(
            listOf(
                UnvaluedGold(GoldKarat.K18, GoldForm.BAR, count = 1, grams = dec("1.00")),
                UnvaluedGold(GoldKarat.K18, GoldForm.JEWELRY, count = 2, grams = dec("4.00")),
            ),
            details?.gold,
        )
        // The valued 21K bar (2 g at 6185) is the subtotal; no 18K value is in it.
        assertEquals(dec("12370"), details?.availableSubtotal)
    }

    @Test
    fun unvaluedDollarBalancesAreCountedWhenTheRateIsMissing() {
        val snapshot = fullSnapshot().copy(
            remote = fullSnapshot().remote.filterNot {
                priceQuoteKey(it.quote) ==
                    QuoteKey.UsdEgp
            },
        )
        val details = boardOf(snapshot, cash = listOf(usdCash("c1", "100"), usdCash("c2", "5"))).details
        assertEquals(2, details?.usdBalances)
    }

    @Test
    fun noDetailsAreShownWhenEveryAssetIsValued() {
        val holdings = listOf(goldHolding("g", GoldKarat.K24, GoldForm.BAR, "1"))
        assertNull(boardOf(fullSnapshot(), holdings).details)
    }

    @Test
    fun aLockIsLockedAndAFailureIsFailedAndNeitherShowsAnyPrice() {
        assertEquals(PricesLoad.Locked, stateOf(VaultRead.Locked).load)
        assertEquals(PricesLoad.Failed(StorageError.Io), stateOf(VaultRead.Failed(StorageError.Io)).load)
    }

    @Test
    fun aMixOfGenerationsIsLoadingNeverAPartialBoard() {
        val mixed = pricesStateOf(
            snapshot = ready(1, fullSnapshot()),
            selected = VaultRead.Ready(2, selectQuotes(fullSnapshot(), now)),
            valuation = VaultRead.Loading,
            holdings = VaultRead.Ready(1, emptyList()),
            now = now,
            valuesHidden = false,
            refresh = PriceRefreshStatus.Idle,
        )
        assertEquals(PricesLoad.Loading, mixed.load)
    }

    @Test
    fun aRefreshIsShownOnlyForTheGenerationOfThePricesOnScreen() {
        assertTrue(stateOf(ready(1, fullSnapshot()), refresh = PriceRefreshStatus.Refreshing(1)).refreshing)
        assertEquals(false, stateOf(ready(1, fullSnapshot()), refresh = PriceRefreshStatus.Refreshing(2)).refreshing)
    }

    @Test
    fun aFinishedRefreshGivesANoticeAndNeverChangesThePrices() {
        val before = stateOf(ready(1, fullSnapshot()))
        val failed = stateOf(
            ready(1, fullSnapshot()),
            refresh = PriceRefreshStatus.Finished(1, RefreshOutcome.Failed, setOf(RefreshRejection.Unreachable)),
        )
        assertEquals(RefreshNotice.OFFLINE, failed.notice)
        assertEquals(readyBoard(before), readyBoard(failed))
        assertNull(
            stateOf(ready(1, fullSnapshot()), refresh = PriceRefreshStatus.Finished(2, RefreshOutcome.Failed)).notice,
        )
    }

    @Test
    fun everyRefreshOutcomeHasItsOwnNoticeAndRateLimitingWinsOverOffline() {
        assertNull(refreshNoticeOf(RefreshOutcome.Succeeded, emptySet()))
        assertEquals(
            RefreshNotice.PARTIAL,
            refreshNoticeOf(RefreshOutcome.PartiallyFailed, setOf(RefreshRejection.Missing)),
        )
        assertEquals(
            RefreshNotice.RATE_LIMITED,
            refreshNoticeOf(RefreshOutcome.Failed, setOf(RefreshRejection.RateLimited)),
        )
        assertEquals(
            RefreshNotice.INVALID_PAYLOAD,
            refreshNoticeOf(RefreshOutcome.Failed, setOf(RefreshRejection.InvalidPayload)),
        )
        assertEquals(RefreshNotice.OFFLINE, refreshNoticeOf(RefreshOutcome.Failed, setOf(RefreshRejection.Unreachable)))
        assertEquals(RefreshNotice.MISSING, refreshNoticeOf(RefreshOutcome.Failed, setOf(RefreshRejection.Missing)))
        assertEquals(RefreshNotice.FAILED, refreshNoticeOf(RefreshOutcome.Failed, emptySet()))
        assertEquals(
            RefreshNotice.RATE_LIMITED,
            refreshNoticeOf(RefreshOutcome.Failed, setOf(RefreshRejection.Unreachable, RefreshRejection.RateLimited)),
        )
    }

    @Test
    fun hidingValuesChangesOnlyTheFlag() {
        val shown = stateOf(ready(1, fullSnapshot()), valuesHidden = false)
        val hidden = stateOf(ready(1, fullSnapshot()), valuesHidden = true)
        assertTrue(hidden.valuesHidden)
        assertEquals(readyBoard(shown), readyBoard(hidden))
    }

    @Test
    fun theTableHasEveryKaratInOrderWhenEverythingIsPresent() {
        val board = boardOf(fullSnapshot())
        assertEquals(listOf(GoldKarat.K24, GoldKarat.K21, GoldKarat.K18), board.karats.map { it.karat })
        assertTrue(board.karats.all { it.sell != null && it.buy != null })
    }
}
