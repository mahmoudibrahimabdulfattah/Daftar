package com.mk.daftar.core.ui.presentation

import com.mk.daftar.core.domain.valuation.QuoteFreshness
import com.mk.daftar.core.domain.valuation.QuoteOrigin
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** One freshness statement per board: the common state, the oldest time, and markers only where a price differs. */
class QuoteBoardSummaryTest {
    private val key = QuoteKey.Gold(GoldKarat.K24, MerchantSide.SELLS_TO_CUSTOMER)

    private fun remote(at: Long, kind: QuoteKind = QuoteKind.CACHED, freshness: QuoteFreshness = QuoteFreshness.FRESH) =
        SelectedQuote(
            key = key,
            value = Decimal.parseCanonical("7000"),
            origin = QuoteOrigin.REMOTE,
            provenance = QuoteProvenance(
                provider = QuoteProvider.ISAGHA,
                sourceUrl = "https://market.isagha.com/prices",
                sourceTime = SourceTime.Unknown,
                fetchedAt = Instant.fromEpochSeconds(at),
                recordedAt = Instant.fromEpochSeconds(at),
                kind = kind,
                manualPinned = false,
                note = null,
            ),
            referenceAt = Instant.fromEpochSeconds(at),
            freshness = freshness,
            nextUpdateAt = null,
            attributionUrl = null,
        )

    private fun manual(at: Long) = SelectedQuote(
        key = key,
        value = Decimal.parseCanonical("7100"),
        origin = QuoteOrigin.PINNED_MANUAL,
        provenance = QuoteProvenance(
            provider = QuoteProvider.USER,
            sourceUrl = null,
            sourceTime = SourceTime.Unknown,
            fetchedAt = null,
            recordedAt = Instant.fromEpochSeconds(at),
            kind = QuoteKind.MANUAL,
            manualPinned = true,
            note = null,
        ),
        referenceAt = Instant.fromEpochSeconds(at),
        freshness = QuoteFreshness.FRESH,
        nextUpdateAt = null,
        attributionUrl = null,
    )

    @Test
    fun aUniformBoardHasNoMarkersAndTheOldestTime() {
        val summary = quoteBoardSummaryOf(listOf(remote(200), remote(100), remote(300)))
        assertEquals(QuoteStatus.CACHED, summary.norm)
        assertEquals(Instant.fromEpochSeconds(100), summary.oldest)
        assertFalse(summary.someDiffer)
        assertEquals(listOf(null, null, null), summary.markers)
    }

    @Test
    fun onlyTheDifferentPriceIsMarked() {
        val summary = quoteBoardSummaryOf(listOf(remote(100), manual(100), remote(100, QuoteKind.LIVE)))
        assertEquals(QuoteStatus.CACHED, summary.norm, "ties go to the more cautious state")
        assertEquals(listOf(null, QuoteMarker.MANUAL, QuoteMarker.LIVE), summary.markers)
        assertTrue(summary.someDiffer)
    }

    @Test
    fun aMissingPriceIsReportedButNeverMarked() {
        val summary = quoteBoardSummaryOf(listOf(remote(100), null))
        assertTrue(summary.someMissing)
        assertEquals(listOf(null, null), summary.markers)
    }

    @Test
    fun anAllManualBoardSaysManual() {
        val summary = quoteBoardSummaryOf(listOf(manual(100), manual(200)))
        assertEquals(QuoteStatus.MANUAL, summary.norm)
        assertFalse(summary.someDiffer)
    }

    @Test
    fun nothingAvailableIsWaiting() {
        val summary = quoteBoardSummaryOf(listOf(null, null))
        assertTrue(summary.empty)
    }
}
