package com.mk.daftar.core.network

import com.mk.daftar.core.common.AppDispatchers
import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.QuoteProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/** The whole production chain, against recorded-shape responses. Each test pins one rule of the selection order. */
class FxChainTest {

    private val dispatchers = AppDispatchers(default = Dispatchers.Unconfined)
    private val clock = FixedClock(FETCHED_AT)
    private val banque = BanqueMisrDataSource.PAGE_URL
    private val exchange = ExchangeRateApiDataSource.ENDPOINT
    private val cdn = FawazFxDataSource.CDN_ENDPOINT
    private val mirror = FawazFxDataSource.MIRROR_ENDPOINT

    /** Unix seconds one hour before the fetch: a fresh open.er-api time. */
    private val exchangeFresh = 1791776030L
    private val exchangeTwoDaysOld = 1791540000L - 86_400L

    private val fawazBody = """{"date":"2026-10-12","usd":{"egp":50.5}}"""

    private fun exchangeBody(unix: Long) =
        """{"result":"success","time_last_update_unix":$unix,"base_code":"USD","rates":{"USD":1,"EGP":50.25}}"""

    /** A Banque Misr page whose publication time is [cairoLocal], written as the page writes it (dd-MM-yyyy HH:mm:ss). */
    private fun bankPage(cairoLocal: String, transfer: String = "52.77") = """
        <html><head><title>Exchange rate and currencies</title></head><body>
        <div class="generic-details-title"><p>$cairoLocal</p></div>
        <div class="table-responsive"><table>
        <thead>
        <tr class="first-row"><td rowspan="2">Currency</td><td colspan="2">Notes</td><td colspan="2">Transfer</td></tr>
        <tr class="second-row"><td>Buy</td><td>Sell</td><td>Buy</td><td>Sell</td></tr>
        </thead>
        <tbody><tr><td><img class="img-flag" src="/usd.ashx" /><br />US Dollar</td>
        <td>52.70</td><td>52.80</td><td>$transfer</td><td>52.87</td></tr></tbody>
        </table></div></body></html>
    """.trimIndent()

    private class Route(val status: HttpStatusCode, val body: String, val contentType: String, val delayFirst: Long = 0)

    private fun ok(body: String, contentType: String = "application/json") = Route(HttpStatusCode.OK, body, contentType)

    private fun failing(code: HttpStatusCode = HttpStatusCode.InternalServerError) = Route(code, "", "application/json")

    private fun clientFor(routes: Map<String, Route>, log: RequestLog): HttpClient = daftarHttpClient(
        MockEngine { request ->
            log.record(request)
            val route = routes[request.url.toString()] ?: error("unrouted ${request.url}")
            if (route.delayFirst > 0) delay(route.delayFirst.seconds)
            respond(route.body, route.status, headers { append(HttpHeaders.ContentType, route.contentType) })
        },
    )

    private fun chain(client: HttpClient): FxNetworkDataSource = marketFxChain(client, clock, dispatchers)

    @Test
    fun aFreshBankQuoteWinsAndNoOtherSourceIsAsked() = runBlocking<Unit> {
        val log = RequestLog()
        val client = clientFor(mapOf(banque to ok(bankPage("12-10-2026 06:00:00"), "text/html")), log)

        val result = assertIs<Outcome.Success<FxFetch>>(chain(client).fetch()).data

        assertEquals(listOf(banque), log.urls)
        assertEquals(QuoteProvider.BANQUE_MISR, result.quote.provenance.provider)
        assertEquals(Decimal.parseCanonical("52.77"), result.quote.rate.egpPerUsd)
        assertEquals(emptyList(), result.precedingFailures)
        assertEquals(null, log.urls.single().indexOf('?').takeIf { it >= 0 })
    }

    @Test
    fun aStaleBankQuoteIsNotAFailureAndTheFreshPrimaryWins() = runBlocking<Unit> {
        val log = RequestLog()
        val client = clientFor(
            mapOf(
                banque to ok(bankPage("10-10-2026 12:00:00"), "text/html"),
                exchange to ok(exchangeBody(exchangeFresh)),
            ),
            log,
        )

        val result = assertIs<Outcome.Success<FxFetch>>(chain(client).fetch()).data

        assertEquals(listOf(banque, exchange), log.urls)
        assertEquals(QuoteProvider.EXCHANGE_RATE_API, result.quote.provenance.provider)
        assertEquals(emptyList(), result.precedingFailures)
    }

    @Test
    fun aBankHttpErrorFallsThroughAndIsReportedInOrder() = runBlocking<Unit> {
        val client = clientFor(
            mapOf(banque to failing(), exchange to ok(exchangeBody(exchangeFresh))),
            RequestLog(),
        )

        val result = assertIs<Outcome.Success<FxFetch>>(chain(client).fetch()).data

        assertEquals(QuoteProvider.EXCHANGE_RATE_API, result.quote.provenance.provider)
        assertEquals(
            listOf(FxAttemptFailure(QuoteProvider.BANQUE_MISR, banque, DataError.Http(500))),
            result.precedingFailures,
        )
    }

    @Test
    fun aFutureDatedBankQuoteIsNeverFreshAndTheChainContinues() = runBlocking<Unit> {
        val client = clientFor(
            mapOf(
                banque to ok(bankPage("12-10-2026 09:00:00"), "text/html"),
                exchange to ok(exchangeBody(exchangeFresh)),
            ),
            RequestLog(),
        )

        val result = assertIs<Outcome.Success<FxFetch>>(chain(client).fetch()).data

        assertEquals(QuoteProvider.EXCHANGE_RATE_API, result.quote.provenance.provider)
    }

    @Test
    fun whenNothingIsFreshTheNewestCandidateByEffectiveTimeWins() = runBlocking<Unit> {
        // Bank publication 09-10-2026 13:00 Cairo is 10:00 UTC; open.er-api is one hour earlier; both are stale and the
        // fawazahmed0 endpoints fail, so the bank's newer effective time must win.
        val client = clientFor(
            mapOf(
                banque to ok(bankPage("09-10-2026 13:00:00"), "text/html"),
                exchange to ok(exchangeBody(1791540000L - 3_600L)),
                cdn to failing(),
                mirror to failing(),
            ),
            RequestLog(),
        )

        val result = assertIs<Outcome.Success<FxFetch>>(chain(client).fetch()).data

        assertEquals(QuoteProvider.BANQUE_MISR, result.quote.provenance.provider)
        assertEquals(
            listOf(
                FxAttemptFailure(QuoteProvider.FAWAZ_CURRENCY_API, cdn, DataError.Http(500)),
                FxAttemptFailure(QuoteProvider.FAWAZ_CURRENCY_API, mirror, DataError.Http(500)),
            ),
            result.precedingFailures,
        )
    }

    @Test
    fun anEqualEffectiveTimeGoesToTheEarlierProviderInTheChain() = runBlocking<Unit> {
        val client = clientFor(
            mapOf(
                banque to ok(bankPage("09-10-2026 13:00:00"), "text/html"),
                exchange to ok(exchangeBody(1791540000L)),
                cdn to failing(),
                mirror to failing(),
            ),
            RequestLog(),
        )

        val result = assertIs<Outcome.Success<FxFetch>>(chain(client).fetch()).data

        assertEquals(QuoteProvider.BANQUE_MISR, result.quote.provenance.provider)
    }

    @Test
    fun aStaleOpenExchangeTimeIsAFallbackCandidateNotAFreshQuote() = runBlocking<Unit> {
        val client = clientFor(
            mapOf(
                banque to failing(HttpStatusCode.NotFound),
                exchange to ok(exchangeBody(exchangeTwoDaysOld)),
                cdn to ok(fawazBody),
            ),
            RequestLog(),
        )

        val result = assertIs<Outcome.Success<FxFetch>>(chain(client).fetch()).data

        assertEquals(QuoteProvider.FAWAZ_CURRENCY_API, result.quote.provenance.provider)
    }

    @Test
    fun everyAttemptFailingReportsTheWholeChainInOrder() = runBlocking<Unit> {
        val log = RequestLog()
        val client = clientFor(
            mapOf(
                banque to failing(HttpStatusCode.NotFound),
                exchange to failing(HttpStatusCode.TooManyRequests),
                cdn to failing(),
                mirror to failing(),
            ),
            log,
        )

        val failure = assertIs<Outcome.Failure<FxFetchFailure>>(chain(client).fetch()).error

        assertEquals(listOf(banque, exchange, cdn, mirror), log.urls)
        assertEquals(
            listOf(
                QuoteProvider.BANQUE_MISR,
                QuoteProvider.EXCHANGE_RATE_API,
                QuoteProvider.FAWAZ_CURRENCY_API,
                QuoteProvider.FAWAZ_CURRENCY_API,
            ),
            failure.attempts.map { it.provider },
        )
    }

    @Test
    fun theChainBudgetReportsTheStepThatWasStillRunningAsATimeout() = runTest {
        val slow = FxStep(QuoteProvider.BANQUE_MISR, banque) {
            delay(40.seconds)
            Outcome.Failure(DataError.Unknown)
        }
        val fast = FxStep(QuoteProvider.EXCHANGE_RATE_API, exchange) {
            Outcome.Failure(DataError.Transport)
        }

        val failure = assertIs<Outcome.Failure<FxFetchFailure>>(
            FallbackFxDataSource(listOf(slow, fast), clock, budget = 30.seconds).fetch(),
        ).error

        assertEquals(
            listOf(FxAttemptFailure(QuoteProvider.BANQUE_MISR, banque, DataError.Timeout)),
            failure.attempts,
        )
    }

    @Test
    fun theChainBudgetIsThirtySecondsAndTheBankRequestTenSeconds() {
        assertEquals(30.seconds, FallbackFxDataSource.CHAIN_BUDGET)
        assertEquals(10.seconds, BanqueMisrDataSource.REQUEST_LIMIT)
    }

    @Test
    fun exactlyTwentyFourHoursOldIsFreshAndOneMillisecondMoreIsStale() {
        val fetched = FETCHED_AT
        fun provenance(effective: Instant) = com.mk.daftar.core.model.QuoteProvenance.live(
            QuoteProvider.BANQUE_MISR,
            banque,
            com.mk.daftar.core.model.SourceTime.Timestamp(effective),
            fetched,
        )
        val now = fetched
        assertEquals(true, FallbackFxDataSource.isFresh(provenance(now - FallbackFxDataSource.FRESH_WINDOW), now))
        assertEquals(
            false,
            FallbackFxDataSource.isFresh(
                provenance(now - FallbackFxDataSource.FRESH_WINDOW - kotlin.time.Duration.parse("1ms")),
                now,
            ),
        )
    }
}
