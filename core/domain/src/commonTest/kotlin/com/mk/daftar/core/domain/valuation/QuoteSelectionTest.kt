package com.mk.daftar.core.domain.valuation

import com.mk.daftar.core.domain.testing.NOW
import com.mk.daftar.core.domain.testing.USD_EGP
import com.mk.daftar.core.domain.testing.dec
import com.mk.daftar.core.domain.testing.manual
import com.mk.daftar.core.domain.testing.remote
import com.mk.daftar.core.domain.testing.sell
import com.mk.daftar.core.domain.testing.snapshot
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class QuoteSelectionTest {

    private val gold21 = sell(GoldKarat.K21)

    @Test
    fun pinnedManualBeatsFreshRemote() {
        val selected = selectQuotes(
            snapshot(
                remote = listOf(remote(gold21, "6185", NOW)),
                manual = listOf(manual(gold21, "6000", NOW, pinned = true)),
            ),
            NOW,
        )[gold21]!!

        assertEquals(QuoteOrigin.PINNED_MANUAL, selected.origin)
        assertEquals(dec("6000"), selected.value)
    }

    @Test
    fun pinnedManualStillBeatsStaleRemote() {
        val selected = selectQuotes(
            snapshot(
                remote = listOf(remote(gold21, "6185", NOW - 30.hours)),
                manual = listOf(manual(gold21, "6000", NOW - 40.hours, pinned = true)),
            ),
            NOW,
        )[gold21]!!

        assertEquals(QuoteOrigin.PINNED_MANUAL, selected.origin)
        assertEquals(QuoteFreshness.STALE, selected.freshness)
        assertTrue(QuoteWarning.STALE in selected.warnings)
        assertTrue(QuoteWarning.PINNED_MANUAL in selected.warnings)
    }

    @Test
    fun freshRemoteBeatsUnpinnedManual() {
        val selected = selectQuotes(
            snapshot(
                remote = listOf(remote(gold21, "6185", NOW)),
                manual = listOf(manual(gold21, "6000", NOW, pinned = false)),
            ),
            NOW,
        )[gold21]!!

        assertEquals(QuoteOrigin.REMOTE, selected.origin)
        assertEquals(dec("6185"), selected.value)
    }

    @Test
    fun staleRemoteStillPrecedesUnpinnedManual() {
        val selected = selectQuotes(
            snapshot(
                remote = listOf(remote(gold21, "6185", NOW - 30.hours)),
                manual = listOf(manual(gold21, "6000", NOW, pinned = false)),
            ),
            NOW,
        )[gold21]!!

        assertEquals(QuoteOrigin.REMOTE, selected.origin)
        assertEquals(QuoteFreshness.STALE, selected.freshness)
    }

    @Test
    fun unpinnedManualIsTheLastResortBeforeMissing() {
        val selected = selectQuotes(
            snapshot(manual = listOf(manual(gold21, "6000", NOW, pinned = false))),
            NOW,
        )[gold21]!!

        assertEquals(QuoteOrigin.UNPINNED_MANUAL, selected.origin)
        assertEquals(listOf(QuoteWarning.UNPINNED_MANUAL), selected.warnings)
    }

    @Test
    fun aKeyWithNoCandidatesIsMissing() {
        val selected = selectQuotes(snapshot(), NOW)

        assertNull(selected[gold21])
        assertNull(selected[USD_EGP])
    }

    @Test
    fun estimatesAreNeverEligible() {
        val onlyEstimate = selectQuotes(
            snapshot(manual = listOf(manual(gold21, "6000", NOW, kind = QuoteKind.ESTIMATE))),
            NOW,
        )
        assertNull(onlyEstimate[gold21])

        val estimateBesideManual = selectQuotes(
            snapshot(
                manual = listOf(
                    manual(gold21, "1", NOW, kind = QuoteKind.ESTIMATE),
                    manual(gold21, "6000", NOW - 2.hours, pinned = false),
                ),
            ),
            NOW,
        )[gold21]!!
        assertEquals(dec("6000"), estimateBesideManual.value)
    }

    @Test
    fun nonstaleRemoteBeatsStaleRemoteEvenWhenOlder() {
        val usd = selectQuotes(
            snapshot(
                remote = listOf(
                    remote(USD_EGP, "48", NOW - 30.hours, provider = QuoteProvider.EXCHANGE_RATE_API),
                    remote(USD_EGP, "49", NOW - 2.hours, provider = QuoteProvider.FAWAZ_CURRENCY_API),
                ),
            ),
            NOW,
        )[USD_EGP]!!

        assertEquals(dec("49"), usd.value)
        assertEquals(QuoteFreshness.FRESH, usd.freshness)
    }

    @Test
    fun withinTheSameFreshnessTheNewestFetchWins() {
        val usd = selectQuotes(
            snapshot(
                remote = listOf(
                    remote(USD_EGP, "48", NOW - 2.hours, provider = QuoteProvider.EXCHANGE_RATE_API),
                    remote(USD_EGP, "49", NOW - 1.hours, provider = QuoteProvider.FAWAZ_CURRENCY_API),
                ),
            ),
            NOW,
        )[USD_EGP]!!

        assertEquals(dec("49"), usd.value)
    }

    @Test
    fun exactTiesPreferExchangeRateApi() {
        val usd = selectQuotes(
            snapshot(
                remote = listOf(
                    remote(USD_EGP, "49", NOW, provider = QuoteProvider.FAWAZ_CURRENCY_API),
                    remote(USD_EGP, "48", NOW, provider = QuoteProvider.EXCHANGE_RATE_API),
                ),
            ),
            NOW,
        )[USD_EGP]!!

        assertEquals(QuoteProvider.EXCHANGE_RATE_API, usd.provenance.provider)
        assertEquals(dec("48"), usd.value)
    }

    @Test
    fun liveIsNotInherentlyFresherThanCached() {
        val gold = selectQuotes(
            snapshot(
                remote = listOf(
                    remote(gold21, "6100", NOW - 3.hours),
                    remote(gold21, "6200", NOW - 1.hours, cached = true),
                ),
            ),
            NOW,
        )[gold21]!!

        assertEquals(dec("6200"), gold.value)
        assertEquals(com.mk.daftar.core.model.QuoteKind.CACHED, gold.provenance.kind)
    }

    @Test
    fun ageIsMeasuredFromFetchNotFromTheProvidersSourceTime() {
        val gold = selectQuotes(
            snapshot(
                remote = listOf(
                    remote(
                        gold21,
                        "6185",
                        fetchedAt = NOW - 1.hours,
                        sourceTime = SourceTime.DateOnly(kotlinx.datetime.LocalDate(2020, 1, 1)),
                    ),
                ),
            ),
            NOW,
        )[gold21]!!

        assertEquals(QuoteFreshness.FRESH, gold.freshness)
        assertEquals(NOW - 1.hours, gold.referenceAt)
        assertEquals(SourceTime.DateOnly(kotlinx.datetime.LocalDate(2020, 1, 1)), gold.provenance.sourceTime)
    }

    @Test
    fun stalenessBoundaryIsExactlyTwentyFourHours() {
        val atBoundary = selectQuotes(snapshot(remote = listOf(remote(gold21, "1", NOW - 24.hours))), NOW)[gold21]!!
        assertEquals(QuoteFreshness.FRESH, atBoundary.freshness)
        assertTrue(atBoundary.warnings.isEmpty())

        val justPast = selectQuotes(
            snapshot(remote = listOf(remote(gold21, "1", NOW - 24.hours - 1.milliseconds))),
            NOW,
        )[gold21]!!
        assertEquals(QuoteFreshness.STALE, justPast.freshness)
        assertEquals(listOf(QuoteWarning.STALE), justPast.warnings)
    }

    @Test
    fun manualReferenceAgesFromRecordedAt() {
        val manualStale = selectQuotes(
            snapshot(manual = listOf(manual(gold21, "6000", NOW - 25.hours, pinned = true))),
            NOW,
        )[gold21]!!

        assertEquals(QuoteFreshness.STALE, manualStale.freshness)
        assertEquals(NOW - 25.hours, manualStale.referenceAt)
    }

    @Test
    fun aFutureReferenceIsClockUncertainNotFresh() {
        val future = selectQuotes(
            snapshot(remote = listOf(remote(gold21, "6185", NOW + 5.minutes))),
            NOW,
        )[gold21]!!

        assertEquals(QuoteFreshness.CLOCK_UNCERTAIN, future.freshness)
        assertEquals(listOf(QuoteWarning.CLOCK_UNCERTAIN), future.warnings)
    }

    @Test
    fun clockUncertainRemoteRanksAfterNormallyDatedFreshRemote() {
        val gold = selectQuotes(
            snapshot(
                remote = listOf(
                    remote(gold21, "9999", NOW + 5.minutes),
                    remote(gold21, "6185", NOW - 1.hours),
                ),
            ),
            NOW,
        )[gold21]!!

        assertEquals(dec("6185"), gold.value)
        assertEquals(QuoteFreshness.FRESH, gold.freshness)
    }

    @Test
    fun clockUncertainRemoteStillPrecedesStaleRemote() {
        // Clock-uncertain is not stale, so it keeps its place ahead of a quote that is known to be stale.
        val gold = selectQuotes(
            snapshot(
                remote = listOf(
                    remote(gold21, "6185", NOW - 30.hours),
                    remote(gold21, "6300", NOW + 5.minutes),
                ),
            ),
            NOW,
        )[gold21]!!

        assertEquals(dec("6300"), gold.value)
        assertEquals(QuoteFreshness.CLOCK_UNCERTAIN, gold.freshness)
    }

    @Test
    fun aFutureManualReferenceIsClockUncertain() {
        val gold = selectQuotes(
            snapshot(manual = listOf(manual(gold21, "6000", NOW + 1.minutes, pinned = true))),
            NOW,
        )[gold21]!!

        assertEquals(QuoteFreshness.CLOCK_UNCERTAIN, gold.freshness)
        assertTrue(QuoteWarning.PINNED_MANUAL in gold.warnings)
        assertTrue(QuoteWarning.CLOCK_UNCERTAIN in gold.warnings)
    }

    @Test
    fun attributionAndNextUpdateTravelWithTheSelectedRemoteQuote() {
        val next = NOW + 1.hours
        val gold = selectQuotes(
            snapshot(
                remote = listOf(remote(gold21, "6185", NOW, nextUpdateAt = next, attributionUrl = "https://a.test/x")),
            ),
            NOW,
        )[gold21]!!

        assertEquals(next, gold.nextUpdateAt)
        assertEquals("https://a.test/x", gold.attributionUrl)
        assertEquals(QuoteOrigin.REMOTE, gold.origin)
        assertTrue(gold.warnings.isEmpty())
    }

    @Test
    fun buySideAndSellSideAreSelectedIndependently() {
        val buy = com.mk.daftar.core.domain.testing.buy(GoldKarat.K21)
        val selected = selectQuotes(
            snapshot(remote = listOf(remote(buy, "6900", NOW), remote(gold21, "6185", NOW))),
            NOW,
        )

        assertEquals(dec("6185"), selected[gold21]!!.value)
        assertEquals(dec("6900"), selected[buy]!!.value)
    }
}
