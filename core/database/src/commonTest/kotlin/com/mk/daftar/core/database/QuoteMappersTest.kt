package com.mk.daftar.core.database

import com.mk.daftar.core.database.entity.ManualQuoteEntity
import com.mk.daftar.core.database.mapper.CorruptRowException
import com.mk.daftar.core.database.mapper.toEntity
import com.mk.daftar.core.database.mapper.toRecord
import com.mk.daftar.core.database.mapper.toRemoteEntity
import com.mk.daftar.core.database.mapper.toStored
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.FxRate
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldPrice
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class QuoteMappersTest {

    private val fetched = Instant.fromEpochSeconds(1_750_000_000, 500_000_000)
    private val sourceTimestamp = Instant.fromEpochSeconds(1_749_999_000, 0)
    private val nextUpdate = Instant.fromEpochSeconds(1_750_003_600, 0)

    private fun liveGold(side: MerchantSide, price: String) = GoldQuote(
        key = QuoteKey.Gold(GoldKarat.K24, side),
        price = GoldPrice(Decimal.parseCanonical(price)),
        provenance = QuoteProvenance.live(
            provider = QuoteProvider.ISAGHA,
            sourceUrl = "https://example.com/gold",
            sourceTime = SourceTime.Timestamp(sourceTimestamp),
            fetchedAt = fetched,
        ),
    )

    @Test
    fun remoteGoldQuoteIsStoredAndReadBackAsCachedAndUnpinned() {
        val live = liveGold(MerchantSide.SELLS_TO_CUSTOMER, "4123.5")
        val row = live.toRemoteEntity(nextUpdateAt = nextUpdate, attributionUrl = "https://example.com/terms")
        assertEquals("GOLD_24_SELL", row.quoteKey)
        assertEquals("4123.5", row.value)
        assertEquals("TIMESTAMP", row.sourceTimeKind)

        val record = row.toRecord()
        val quote = assertIs<GoldQuote>(record.quote)
        assertEquals(live.key, quote.key)
        assertEquals(live.price, quote.price)
        assertEquals(QuoteKind.CACHED, quote.provenance.kind)
        assertFalse(quote.provenance.manualPinned)
        assertEquals(fetched, quote.provenance.fetchedAt)
        assertEquals(SourceTime.Timestamp(sourceTimestamp), quote.provenance.sourceTime)
        assertEquals(nextUpdate, record.nextUpdateAt)
        assertEquals("https://example.com/terms", record.attributionUrl)
    }

    @Test
    fun dateOnlyAndUnknownSourceTimesUseOnlyTheirOwnColumns() {
        val dateOnly = FxQuote(
            rate = FxRate(Decimal.parseCanonical("50.25")),
            provenance = QuoteProvenance.live(
                provider = QuoteProvider.FAWAZ_CURRENCY_API,
                sourceUrl = "https://example.com/fx",
                sourceTime = SourceTime.DateOnly(LocalDate(2026, 6, 1)),
                fetchedAt = fetched,
            ),
        )
        val row = dateOnly.toRemoteEntity(null, null)
        assertEquals("DATE_ONLY", row.sourceTimeKind)
        assertNull(row.sourceSeconds)
        assertNull(row.sourceNanos)
        assertEquals("2026-06-01", row.sourceDate)
        val read = assertIs<FxQuote>(row.toRecord().quote)
        assertEquals(SourceTime.DateOnly(LocalDate(2026, 6, 1)), read.provenance.sourceTime)

        val unknown = dateOnly.copy(
            provenance = dateOnly.provenance.copy(sourceTime = SourceTime.Unknown),
        )
        val unknownRow = unknown.toRemoteEntity(null, null)
        assertEquals("UNKNOWN", unknownRow.sourceTimeKind)
        assertNull(unknownRow.sourceDate)
        assertEquals(SourceTime.Unknown, assertIs<FxQuote>(unknownRow.toRecord().quote).provenance.sourceTime)
    }

    @Test
    fun remoteRowsWithInconsistentFieldsAreRejected() {
        val row = liveGold(MerchantSide.BUYS_FROM_CUSTOMER, "4000").toRemoteEntity(null, null)
        assertFailsWith<CorruptRowException> { row.copy(provider = "EXCHANGE_RATE_API").toRecord() }
        assertFailsWith<CorruptRowException> { row.copy(provider = "USER").toRecord() }
        assertFailsWith<CorruptRowException> { row.copy(quoteKey = "GOLD_99_SELL").toRecord() }
        assertFailsWith<CorruptRowException> { row.copy(value = "4000.0").toRecord() }
        assertFailsWith<CorruptRowException> { row.copy(sourceSeconds = null).toRecord() }
        assertFailsWith<CorruptRowException> { row.copy(sourceTimeKind = "UNKNOWN").toRecord() }
        assertFailsWith<CorruptRowException> { row.copy(sourceUrl = "http://example.com").toRecord() }
        assertFailsWith<CorruptRowException> { row.copy(nextUpdateSeconds = 5, nextUpdateNanos = null).toRecord() }
        assertFailsWith<CorruptRowException> { row.copy(fetchedNanos = -1).toRecord() }
        val usd = FxQuote(
            FxRate(Decimal.parseCanonical("50")),
            QuoteProvenance.live(
                QuoteProvider.EXCHANGE_RATE_API,
                "https://example.com/fx",
                SourceTime.Unknown,
                fetched,
            ),
        ).toRemoteEntity(null, null)
        assertFailsWith<CorruptRowException> { usd.copy(provider = "ISAGHA").toRecord() }
    }

    @Test
    fun estimatesAndManualQuotesNeverReachRemoteStorage() {
        val estimate = FxQuote(
            FxRate(Decimal.parseCanonical("50")),
            QuoteProvenance(
                provider = QuoteProvider.USER,
                sourceUrl = null,
                sourceTime = SourceTime.Unknown,
                fetchedAt = null,
                recordedAt = fetched,
                kind = QuoteKind.ESTIMATE,
                manualPinned = false,
                note = null,
            ),
        )
        assertFailsWith<IllegalArgumentException> { estimate.toRemoteEntity(null, null) }
    }

    @Test
    fun providersAreKeptIndependentlyPerKey() {
        val primary = FxQuote(
            FxRate(Decimal.parseCanonical("50")),
            QuoteProvenance.live(QuoteProvider.EXCHANGE_RATE_API, "https://example.com/a", SourceTime.Unknown, fetched),
        ).toRemoteEntity(null, null)
        val fallback = primary.copy(provider = "FAWAZ_CURRENCY_API", value = "51")
        assertEquals("EXCHANGE_RATE_API", primary.provider)
        assertEquals("FAWAZ_CURRENCY_API", fallback.provider)
        assertTrue(primary.toRecord().quote.provenance.provider != fallback.toRecord().quote.provenance.provider)
    }

    @Test
    fun manualQuoteRoundTripsWithPinnedStateAndRevision() {
        val input = ManualQuoteInput(
            key = QuoteKey.UsdEgp,
            value = Decimal.parseCanonical("51.2"),
            note = "  from the bank  ",
        )
        val entity = input.toEntity(enteredAt = fetched, pinned = true, revision = 4)
        assertEquals("USD_EGP", entity.quoteKey)
        assertEquals("from the bank", entity.note)
        val stored = entity.toStored()
        assertEquals(4L, stored.revision)
        val quote = assertIs<FxQuote>(stored.value)
        assertEquals(QuoteKind.MANUAL, quote.provenance.kind)
        assertEquals(QuoteProvider.USER, quote.provenance.provider)
        assertTrue(quote.provenance.manualPinned)
        assertNull(quote.provenance.sourceUrl)
        assertNull(quote.provenance.fetchedAt)
        assertEquals(SourceTime.Timestamp(fetched), quote.provenance.sourceTime)
        assertEquals(fetched, quote.provenance.recordedAt)
    }

    @Test
    fun unpinnedManualValueIsKeptAsAFallback() {
        val entity = ManualQuoteEntity("GOLD_21_SELL", "3000", 1, 0, pinned = false, note = null, revision = 2)
        assertFalse(assertIs<GoldQuote>(entity.toStored().value).provenance.manualPinned)
    }

    @Test
    fun manualRowsForBuySidesOrRemoteKeysAreRejected() {
        val entity = ManualQuoteEntity("GOLD_21_BUY", "3000", 1, 0, pinned = true, note = null, revision = 1)
        assertFailsWith<CorruptRowException> { entity.toStored() }
        assertFailsWith<CorruptRowException> { entity.copy(quoteKey = "GOLD_21_SELL", revision = 0).toStored() }
        assertFailsWith<CorruptRowException> { entity.copy(quoteKey = "GOLD_21_SELL", value = "0").toStored() }
    }

    @Test
    fun buySideManualInputIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            ManualQuoteInput(
                key = QuoteKey.Gold(GoldKarat.K18, MerchantSide.BUYS_FROM_CUSTOMER),
                value = Decimal.parseCanonical("2500"),
                note = null,
            )
        }
    }
}
