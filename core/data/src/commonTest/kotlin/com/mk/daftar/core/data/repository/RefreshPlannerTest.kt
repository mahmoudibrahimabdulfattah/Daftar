package com.mk.daftar.core.data.repository

import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.RefreshRejection
import com.mk.daftar.core.domain.repository.RefreshStepResult
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.FxRate
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldPrice
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import com.mk.daftar.core.network.FxAttemptFailure
import com.mk.daftar.core.network.FxFetch
import com.mk.daftar.core.network.FxFetchFailure
import com.mk.daftar.core.network.GoldFetch
import com.mk.daftar.core.network.MerchantGoldQuotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class RefreshPlannerTest {

    private val url = "https://example.com/source"
    private val t0 = Instant.fromEpochSeconds(1_750_000_000, 0)
    private val t1 = Instant.fromEpochSeconds(1_750_000_100, 0)
    private val t2 = Instant.fromEpochSeconds(1_750_000_200, 0)

    private fun gold(
        karat: GoldKarat,
        side: MerchantSide,
        value: String,
        source: SourceTime,
        fetched: Instant,
    ): GoldQuote = GoldQuote(
        key = QuoteKey.Gold(karat, side),
        price = GoldPrice(Decimal.parseCanonical(value)),
        provenance = QuoteProvenance.live(QuoteProvider.ISAGHA, url, source, fetched),
    )

    private fun pair(karat: GoldKarat, sell: String, buy: String, source: SourceTime, fetched: Instant) =
        MerchantGoldQuotes(
            sell = gold(karat, MerchantSide.SELLS_TO_CUSTOMER, sell, source, fetched),
            buy = gold(karat, MerchantSide.BUYS_FROM_CUSTOMER, buy, source, fetched),
        )

    private fun cached(quote: PriceQuote) = RemoteQuoteRecord(quote, nextUpdateAt = null, attributionUrl = null)

    private fun fx(provider: QuoteProvider, rate: String, source: SourceTime, fetched: Instant) = FxQuote(
        rate = FxRate(Decimal.parseCanonical(rate)),
        provenance = QuoteProvenance.live(provider, url, source, fetched),
    )

    private fun fxFetch(quote: FxQuote, failures: List<FxAttemptFailure> = emptyList()) =
        FxFetch(quote, nextUpdateAt = null, attributionUrl = null, precedingFailures = failures)

    private fun goldFetch(
        quotes: Map<GoldKarat, MerchantGoldQuotes> = emptyMap(),
        rejected: Map<GoldKarat, DataError> = emptyMap(),
    ) = GoldFetch(quotes, rejected)

    @Test
    fun aFreshGoldPairIsAcceptedAndBothSidesAreWritten() {
        val plan = RefreshPlanner.plan(
            gold = Outcome.Success(
                goldFetch(
                    quotes = mapOf(GoldKarat.K21 to pair(GoldKarat.K21, "3500", "3400", SourceTime.Timestamp(t1), t1)),
                ),
            ),
            fx = Outcome.Failure(FxFetchFailure(emptyList())),
            cached = emptyList(),
        )
        assertIs<RefreshStepResult.Accepted>(plan.report.gold.getValue(GoldKarat.K21))
        assertEquals(2, plan.writes.size)
    }

    @Test
    fun anOlderSourceTimestampNeverReplacesTheCachedPair() {
        val cachedSell =
            cached(gold(GoldKarat.K21, MerchantSide.SELLS_TO_CUSTOMER, "3600", SourceTime.Timestamp(t2), t2))
        val cachedBuy =
            cached(gold(GoldKarat.K21, MerchantSide.BUYS_FROM_CUSTOMER, "3500", SourceTime.Timestamp(t2), t2))
        val plan = RefreshPlanner.plan(
            gold = Outcome.Success(
                goldFetch(
                    quotes = mapOf(GoldKarat.K21 to pair(GoldKarat.K21, "3500", "3400", SourceTime.Timestamp(t1), t1)),
                ),
            ),
            fx = Outcome.Failure(FxFetchFailure(emptyList())),
            cached = listOf(cachedSell, cachedBuy),
        )
        assertEquals(RefreshStepResult.Kept(RefreshRejection.OlderThanStored), plan.report.gold.getValue(GoldKarat.K21))
        assertTrue(plan.writes.isEmpty())
    }

    @Test
    fun anEqualSourceTimestampIsAcceptedSoTheFetchTimeAdvances() {
        val cachedSell =
            cached(gold(GoldKarat.K18, MerchantSide.SELLS_TO_CUSTOMER, "2000", SourceTime.Timestamp(t1), t0))
        val cachedBuy =
            cached(gold(GoldKarat.K18, MerchantSide.BUYS_FROM_CUSTOMER, "1950", SourceTime.Timestamp(t1), t0))
        val plan = RefreshPlanner.plan(
            gold = Outcome.Success(
                goldFetch(
                    quotes = mapOf(GoldKarat.K18 to pair(GoldKarat.K18, "2000", "1950", SourceTime.Timestamp(t1), t2)),
                ),
            ),
            fx = Outcome.Failure(FxFetchFailure(emptyList())),
            cached = listOf(cachedSell, cachedBuy),
        )
        assertIs<RefreshStepResult.Accepted>(plan.report.gold.getValue(GoldKarat.K18))
    }

    @Test
    fun anUnknownSourceTimeFallsBackToFetchOrdering() {
        // The cached quote was fetched later than the candidate, so the candidate must not replace it.
        val cachedSell = cached(gold(GoldKarat.K24, MerchantSide.SELLS_TO_CUSTOMER, "4000", SourceTime.Unknown, t2))
        val cachedBuy = cached(gold(GoldKarat.K24, MerchantSide.BUYS_FROM_CUSTOMER, "3900", SourceTime.Unknown, t2))
        val plan = RefreshPlanner.plan(
            gold = Outcome.Success(
                goldFetch(quotes = mapOf(GoldKarat.K24 to pair(GoldKarat.K24, "4010", "3910", SourceTime.Unknown, t1))),
            ),
            fx = Outcome.Failure(FxFetchFailure(emptyList())),
            cached = listOf(cachedSell, cachedBuy),
        )
        assertEquals(RefreshStepResult.Kept(RefreshRejection.OlderThanStored), plan.report.gold.getValue(GoldKarat.K24))
    }

    @Test
    fun dateOnlyValuesCompareAsDatesNotAsInventedInstants() {
        val older = LocalDate(2026, 10, 9)
        val newer = LocalDate(2026, 10, 10)
        val cachedFx = cached(fx(QuoteProvider.EXCHANGE_RATE_API, "50.1", SourceTime.DateOnly(newer), t0))
        val plan = RefreshPlanner.plan(
            gold = Outcome.Failure(DataError.Transport),
            fx = Outcome.Success(fxFetch(fx(QuoteProvider.EXCHANGE_RATE_API, "50.0", SourceTime.DateOnly(older), t2))),
            cached = listOf(cachedFx),
        )
        assertEquals(
            RefreshStepResult.Kept(RefreshRejection.OlderThanStored),
            plan.report.fx.getValue(QuoteProvider.EXCHANGE_RATE_API),
        )
        assertTrue(plan.writes.isEmpty())
    }

    @Test
    fun aDateOnlyCandidateAgainstATimestampUsesFetchOrdering() {
        val cachedFx = cached(fx(QuoteProvider.EXCHANGE_RATE_API, "50.1", SourceTime.Timestamp(t2), t0))
        val plan = RefreshPlanner.plan(
            gold = Outcome.Failure(DataError.Transport),
            fx = Outcome.Success(
                fxFetch(fx(QuoteProvider.EXCHANGE_RATE_API, "50.2", SourceTime.DateOnly(LocalDate(2026, 10, 1)), t1)),
            ),
            cached = listOf(cachedFx),
        )
        assertIs<RefreshStepResult.Accepted>(plan.report.fx.getValue(QuoteProvider.EXCHANGE_RATE_API))
    }

    @Test
    fun aRejectedKaratIsKeptWhileOthersAreAcceptedAndMissingOnesAreReported() {
        val plan = RefreshPlanner.plan(
            gold = Outcome.Success(
                goldFetch(
                    quotes = mapOf(GoldKarat.K24 to pair(GoldKarat.K24, "4000", "3900", SourceTime.Unknown, t1)),
                    rejected = mapOf(GoldKarat.K21 to DataError.RateLimited(retryAt = null)),
                ),
            ),
            fx = Outcome.Failure(FxFetchFailure(emptyList())),
            cached = emptyList(),
        )
        assertIs<RefreshStepResult.Accepted>(plan.report.gold.getValue(GoldKarat.K24))
        assertEquals(RefreshStepResult.Kept(RefreshRejection.RateLimited), plan.report.gold.getValue(GoldKarat.K21))
        assertEquals(RefreshStepResult.Kept(RefreshRejection.Missing), plan.report.gold.getValue(GoldKarat.K18))
        assertEquals(2, plan.writes.size)
    }

    @Test
    fun aWholeGoldFailureKeepsEveryKaratAndWritesNothing() {
        val plan = RefreshPlanner.plan(
            gold = Outcome.Failure(DataError.Transport),
            fx = Outcome.Failure(FxFetchFailure(emptyList())),
            cached = emptyList(),
        )
        GoldKarat.entries.forEach { karat ->
            assertEquals(RefreshStepResult.Kept(RefreshRejection.Unreachable), plan.report.gold.getValue(karat))
        }
        assertTrue(plan.writes.isEmpty())
    }

    @Test
    fun aFallbackFxProviderIsAcceptedAndThePrimaryFailureIsReportedWithoutErasingAnything() {
        val failure =
            FxAttemptFailure(QuoteProvider.EXCHANGE_RATE_API, endpoint = "primary", error = DataError.Http(503))
        val plan = RefreshPlanner.plan(
            gold = Outcome.Failure(DataError.Transport),
            fx = Outcome.Success(
                fxFetch(fx(QuoteProvider.FAWAZ_CURRENCY_API, "50.5", SourceTime.Unknown, t1), listOf(failure)),
            ),
            cached = emptyList(),
        )
        assertEquals(
            RefreshStepResult.Kept(RefreshRejection.Unreachable),
            plan.report.fx.getValue(QuoteProvider.EXCHANGE_RATE_API),
        )
        assertIs<RefreshStepResult.Accepted>(plan.report.fx.getValue(QuoteProvider.FAWAZ_CURRENCY_API))
        assertEquals(listOf(QuoteProvider.FAWAZ_CURRENCY_API), plan.writes.map { it.quote.provenance.provider })
    }

    @Test
    fun aTotalFxFailureLeavesTheCachedRatesAlone() {
        val failures = listOf(
            FxAttemptFailure(QuoteProvider.EXCHANGE_RATE_API, endpoint = "a", error = DataError.Timeout),
            FxAttemptFailure(
                QuoteProvider.FAWAZ_CURRENCY_API,
                endpoint = "b",
                error = DataError.InvalidPayload(com.mk.daftar.core.common.PayloadFailure.Malformed),
            ),
        )
        val plan = RefreshPlanner.plan(
            gold = Outcome.Failure(DataError.Transport),
            fx = Outcome.Failure(FxFetchFailure(failures)),
            cached = emptyList(),
        )
        assertEquals(
            RefreshStepResult.Kept(RefreshRejection.Unreachable),
            plan.report.fx.getValue(QuoteProvider.EXCHANGE_RATE_API),
        )
        assertEquals(
            RefreshStepResult.Kept(RefreshRejection.InvalidPayload),
            plan.report.fx.getValue(QuoteProvider.FAWAZ_CURRENCY_API),
        )
        assertTrue(plan.writes.isEmpty())
    }

    @Test
    fun aPairWithOneOlderSideIsKeptAsAWhole() {
        // The sell side is newer than the cache, but the buy side is not, so neither side may be written alone.
        val cachedSell =
            cached(gold(GoldKarat.K18, MerchantSide.SELLS_TO_CUSTOMER, "2000", SourceTime.Timestamp(t0), t0))
        val cachedBuy =
            cached(gold(GoldKarat.K18, MerchantSide.BUYS_FROM_CUSTOMER, "1950", SourceTime.Timestamp(t2), t2))
        val plan = RefreshPlanner.plan(
            gold = Outcome.Success(
                goldFetch(
                    quotes = mapOf(GoldKarat.K18 to pair(GoldKarat.K18, "2100", "1990", SourceTime.Timestamp(t1), t1)),
                ),
            ),
            fx = Outcome.Failure(FxFetchFailure(emptyList())),
            cached = listOf(cachedSell, cachedBuy),
        )
        assertEquals(RefreshStepResult.Kept(RefreshRejection.OlderThanStored), plan.report.gold.getValue(GoldKarat.K18))
        assertTrue(plan.writes.isEmpty())
    }

    @Test
    fun theFxDirectionIsNeverInferredFromMagnitude() {
        val key = QuoteKey.UsdEgp
        assertEquals(key, fx(QuoteProvider.FAWAZ_CURRENCY_API, "50", SourceTime.Unknown, t0).key)
    }
}
