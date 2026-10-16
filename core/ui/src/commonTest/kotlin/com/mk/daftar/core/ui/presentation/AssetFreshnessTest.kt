package com.mk.daftar.core.ui.presentation

import com.mk.daftar.core.domain.valuation.QuoteFreshness
import com.mk.daftar.core.domain.valuation.QuoteOrigin
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Each asset says when its source updated and when Daftar last checked it; neither replaces the other. */
class AssetFreshnessTest {
    private fun remote(source: SourceTime, fetched: Long, freshness: QuoteFreshness = QuoteFreshness.FRESH) =
        SelectedQuote(
            key = QuoteKey.UsdEgp,
            value = Decimal.parseCanonical("53.42"),
            origin = QuoteOrigin.REMOTE,
            provenance = QuoteProvenance(
                provider = QuoteProvider.BANQUE_MISR,
                sourceUrl = "https://www.banquemisr.com",
                sourceTime = source,
                fetchedAt = Instant.fromEpochSeconds(fetched),
                recordedAt = Instant.fromEpochSeconds(fetched),
                kind = QuoteKind.CACHED,
                manualPinned = false,
                note = null,
            ),
            referenceAt = (source as? SourceTime.Timestamp)?.value ?: Instant.fromEpochSeconds(fetched),
            freshness = freshness,
            nextUpdateAt = null,
            attributionUrl = null,
        )

    private fun manual(at: Long) = SelectedQuote(
        key = QuoteKey.UsdEgp,
        value = Decimal.parseCanonical("50"),
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
    fun aSourcePublishedEarlierKeepsItsTimeBesideTheCheck() {
        val published = SourceTime.Timestamp(Instant.fromEpochSeconds(1_000))
        assertEquals(
            AssetFreshness.Remote(published, Instant.fromEpochSeconds(90_000)),
            assetFreshnessOf(listOf(remote(published, fetched = 90_000)), failed = false),
        )
    }

    @Test
    fun theOldestCheckSpeaksForTheGroup() {
        val newer = remote(SourceTime.Unknown, fetched = 5_000)
        val older = remote(SourceTime.Unknown, fetched = 2_000)
        val freshness = assetFreshnessOf(listOf(newer, null, older), failed = true)
        assertEquals(
            AssetFreshness.Remote(SourceTime.Unknown, Instant.fromEpochSeconds(2_000), failed = true),
            freshness,
        )
    }

    @Test
    fun manualOnlyAndUncertainAndMissing() {
        assertEquals(
            AssetFreshness.Manual(Instant.fromEpochSeconds(7)),
            assetFreshnessOf(listOf(manual(7)), failed = false),
        )
        val uncertain = remote(SourceTime.Unknown, fetched = 1, freshness = QuoteFreshness.CLOCK_UNCERTAIN)
        assertEquals(AssetFreshness.TimeUncertain, assetFreshnessOf(listOf(uncertain), failed = false))
        assertNull(assetFreshnessOf(listOf(null), failed = true))
    }

    @Test
    fun aRemotePriceSpeaksEvenBesideAManualOne() {
        val freshness = assetFreshnessOf(listOf(manual(1), remote(SourceTime.Unknown, fetched = 9)), failed = false)
        assertEquals(AssetFreshness.Remote(SourceTime.Unknown, Instant.fromEpochSeconds(9)), freshness)
    }
}
