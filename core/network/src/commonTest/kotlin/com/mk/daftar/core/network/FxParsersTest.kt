package com.mk.daftar.core.network

import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.PayloadFailure
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class FxParsersTest {

    private val endpoint = "https://open.er-api.com/v6/latest/USD"
    private val fawazEndpoint = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json"
    private val fetchedAt: Instant = Instant.fromEpochSeconds(1_790_995_000)
    private val lastUpdate = 1_790_990_000L
    private val nextUpdate = 1_791_076_400L

    private fun d(text: String) = Decimal.parseCanonical(text)

    private fun exchange(body: String) = FxParsers.exchangeRate(body, fetchedAt, endpoint)

    private fun exchangeSuccess(body: String = valid()): FxFetch =
        assertIs<Outcome.Success<FxFetch>>(exchange(body)).data

    private fun assertInvalid(reason: PayloadFailure, outcome: Outcome<*, DataError>) {
        assertEquals(Outcome.Failure(DataError.InvalidPayload(reason)), outcome)
    }

    private fun valid(
        egp: String = "50.123456789012",
        usd: String? = "1",
        base: String = "USD",
        result: String = "success",
        last: Long = lastUpdate,
        next: Long? = nextUpdate,
    ): String {
        val usdField = if (usd == null) "" else "\"USD\": $usd, "
        val nextField = if (next == null) "" else "\"time_next_update_unix\": $next, "
        return """
            {
              "result": "$result",
              "provider": "https://www.exchangerate-api.com",
              "documentation": "https://www.exchangerate-api.com/docs",
              "terms_of_use": "https://www.exchangerate-api.com/terms",
              "time_last_update_unix": $last,
              "time_last_update_utc": "Sat, 31 Oct 2026 00:00:00 +0000",
              $nextField
              "time_eol_unix": 0,
              "base_code": "$base",
              "rates": { $usdField "EGP": $egp, "EUR": 0.9 }
            }
        """.trimIndent()
    }

    @Test
    fun validResponseYieldsExactRateAndAuthoritativeTimestamp() {
        val fetch = exchangeSuccess()

        assertEquals(d("50.123456789012"), fetch.quote.rate.egpPerUsd)
        assertEquals(QuoteProvider.EXCHANGE_RATE_API, fetch.quote.provenance.provider)
        assertEquals(SourceTime.Timestamp(Instant.fromEpochSeconds(lastUpdate)), fetch.quote.provenance.sourceTime)
        assertEquals(Instant.fromEpochSeconds(nextUpdate), fetch.nextUpdateAt)
        assertEquals(fetchedAt, fetch.quote.provenance.fetchedAt)
        assertEquals(QuoteKind.LIVE, fetch.quote.provenance.kind)
        assertEquals(endpoint, fetch.quote.provenance.sourceUrl)
        assertEquals("https://www.exchangerate-api.com", fetch.attributionUrl)
        assertEquals(emptyList(), fetch.precedingFailures)
    }

    @Test
    fun highPrecisionRatesStayExact() {
        assertEquals(d("50.123456789012"), exchangeSuccess(valid(egp = "50.123456789012")).quote.rate.egpPerUsd)
    }

    @Test
    fun jsonExponentTokensConvertToTheExactDecimal() {
        assertEquals(d("50.123456789012"), exchangeSuccess(valid(egp = "5.0123456789012e1")).quote.rate.egpPerUsd)
        assertEquals(d("50.5"), exchangeSuccess(valid(egp = "5.05E1")).quote.rate.egpPerUsd)
    }

    @Test
    fun ratesWithMoreThanTwelveFractionalDigitsAreRejectedNotTruncated() {
        assertInvalid(PayloadFailure.InvalidNumber, exchange(valid(egp = "50.1234567890123")))
    }

    @Test
    fun rateTokensAsStringsAreRejected() {
        assertInvalid(PayloadFailure.InvalidNumber, exchange(valid(egp = "\"50.1\"")))
    }

    @Test
    fun nonPositiveRatesAreRejected() {
        assertInvalid(PayloadFailure.InvalidNumber, exchange(valid(egp = "0")))
        assertInvalid(PayloadFailure.InvalidNumber, exchange(valid(egp = "-50")))
    }

    @Test
    fun wrongBaseCurrencyIsRejected() {
        assertInvalid(PayloadFailure.WrongUnit, exchange(valid(base = "EUR")))
    }

    @Test
    fun providerErrorResultIsRejected() {
        assertInvalid(PayloadFailure.Malformed, exchange(valid(result = "error")))
    }

    @Test
    fun missingEgpRateIsRejected() {
        val body = valid().replace("\"EGP\": 50.123456789012, ", "").replace(", \"EGP\": 50.123456789012", "")
        assertInvalid(PayloadFailure.MissingField, exchange(body))
    }

    @Test
    fun usdRateMustBeOneWhenPresent() {
        assertEquals(d("50.123456789012"), exchangeSuccess(valid(usd = "1.0")).quote.rate.egpPerUsd)
        assertInvalid(PayloadFailure.InvalidNumber, exchange(valid(usd = "0.99")))
    }

    @Test
    fun timestampMoreThanFiveMinutesAheadIsRejected() {
        val ahead = fetchedAt.epochSeconds + 6 * 60
        assertInvalid(PayloadFailure.InvalidTimestamp, exchange(valid(last = ahead)))
    }

    @Test
    fun timestampExactlyFiveMinutesAheadIsAccepted() {
        val ahead = fetchedAt.epochSeconds + 5 * 60
        assertEquals(
            Instant.fromEpochSeconds(ahead),
            exchangeSuccess(valid(last = ahead)).quote.provenance.sourceTime.let {
                assertIs<SourceTime.Timestamp>(it).value
            },
        )
    }

    @Test
    fun nextUpdateBeforeLastUpdateIsRejected() {
        assertInvalid(PayloadFailure.InvalidTimestamp, exchange(valid(next = lastUpdate - 1)))
    }

    @Test
    fun nextUpdateIsOptional() {
        assertNull(exchangeSuccess(valid(next = null)).nextUpdateAt)
    }

    @Test
    fun timestampAsStringIsRejected() {
        val body = valid().replace(
            "\"time_last_update_unix\": $lastUpdate",
            "\"time_last_update_unix\": \"$lastUpdate\"",
        )
        assertInvalid(PayloadFailure.InvalidTimestamp, exchange(body))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val body = valid().replace("\"base_code\"", "\"surprise\": {\"nested\": [1, 2]}, \"base_code\"")
        assertEquals(d("50.123456789012"), exchangeSuccess(body).quote.rate.egpPerUsd)
    }

    @Test
    fun malformedJsonAndNonObjectsAreMalformed() {
        assertInvalid(PayloadFailure.Malformed, exchange("{"))
        assertInvalid(PayloadFailure.Malformed, exchange("[1, 2]"))
        assertInvalid(PayloadFailure.Malformed, exchange("<html>not json</html>"))
        assertInvalid(PayloadFailure.Malformed, exchange(""))
    }

    @Test
    fun byteOrderMarkBeforeJsonIsTolerated() {
        assertEquals(d("50.123456789012"), exchangeSuccess("" + valid()).quote.rate.egpPerUsd)
    }

    private fun fawaz(body: String) = FxParsers.fawazCurrency(body, FETCHED_AT, fawazEndpoint)

    private fun fawazBody(date: String = "2026-10-12", egp: String = "50.25") =
        """{"date": "$date", "usd": {"egp": $egp, "eur": 0.9}}"""

    @Test
    fun fawazResponseKeepsItsDateOnlyAndNeverInventsAnInstant() {
        val fetch = assertIs<Outcome.Success<FxFetch>>(fawaz(fawazBody())).data

        assertEquals(d("50.25"), fetch.quote.rate.egpPerUsd)
        assertEquals(SourceTime.DateOnly(LocalDate(2026, 10, 12)), fetch.quote.provenance.sourceTime)
        assertEquals(QuoteProvider.FAWAZ_CURRENCY_API, fetch.quote.provenance.provider)
        assertEquals(fawazEndpoint, fetch.quote.provenance.sourceUrl)
        assertEquals(FETCHED_AT, fetch.quote.provenance.fetchedAt)
        assertNull(fetch.nextUpdateAt)
        assertNull(fetch.attributionUrl)
    }

    @Test
    fun fawazDateOneDayAheadOfUtcIsAccepted() {
        // The fetch is 2026-10-12 UTC, so 2026-10-13 is the Egyptian day ahead of UTC and still legitimate.
        assertIs<Outcome.Success<FxFetch>>(fawaz(fawazBody(date = "2026-10-13")))
    }

    @Test
    fun fawazDateFarAheadIsRejected() {
        assertInvalid(PayloadFailure.InvalidTimestamp, fawaz(fawazBody(date = "2026-10-15")))
    }

    @Test
    fun fawazInvalidDatesAreRejected() {
        assertInvalid(PayloadFailure.InvalidTimestamp, fawaz(fawazBody(date = "2026-02-30")))
        assertInvalid(PayloadFailure.InvalidTimestamp, fawaz(fawazBody(date = "12-10-2026")))
        assertInvalid(PayloadFailure.MissingField, fawaz("""{"usd": {"egp": 50.25}}"""))
    }

    @Test
    fun fawazMissingOrInvalidEgpIsRejected() {
        assertInvalid(PayloadFailure.MissingField, fawaz("""{"date": "2026-10-12", "usd": {"eur": 0.9}}"""))
        assertInvalid(PayloadFailure.MissingField, fawaz("""{"date": "2026-10-12"}"""))
        assertInvalid(PayloadFailure.InvalidNumber, fawaz(fawazBody(egp = "\"50.25\"")))
        assertInvalid(PayloadFailure.InvalidNumber, fawaz(fawazBody(egp = "-1")))
    }

    @Test
    fun fawazExactHighPrecisionRates() {
        val fetch = assertIs<Outcome.Success<FxFetch>>(fawaz(fawazBody(egp = "5.0123456789012e1"))).data
        assertEquals(d("50.123456789012"), fetch.quote.rate.egpPerUsd)
    }
}
