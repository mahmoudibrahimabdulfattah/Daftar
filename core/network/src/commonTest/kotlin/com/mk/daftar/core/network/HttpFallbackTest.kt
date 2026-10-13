package com.mk.daftar.core.network

import com.mk.daftar.core.common.AppDispatchers
import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.PayloadFailure
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.network.fixtures.TestFixtures
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException

class HttpFallbackTest {

    private val clock = FixedClock(FETCHED_AT)
    private val dispatchers = AppDispatchers(default = Dispatchers.Unconfined)
    private val exchangeEndpoint = "https://open.er-api.com/v6/latest/USD"
    private val cdnEndpoint = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json"
    private val mirrorEndpoint = "https://latest.currency-api.pages.dev/v1/currencies/usd.json"

    private val exchangeBody = """
        {"result":"success","time_last_update_unix":1791776030,"base_code":"USD","rates":{"USD":1,"EGP":50.25}}
    """.trimIndent()
    private val fawazBody = """{"date":"2026-10-12","usd":{"egp":50.5}}"""

    @Test
    fun primarySuccessMakesASingleRequest() = runBlocking<Unit> {
        val log = RequestLog()
        val client = clientFor(mapOf(exchangeEndpoint to ok(exchangeBody)), log)

        val result = assertIs<Outcome.Success<FxFetch>>(fallback(client).fetch()).data

        assertEquals(listOf(exchangeEndpoint), log.urls)
        assertEquals(QuoteProvider.EXCHANGE_RATE_API, result.quote.provenance.provider)
        assertEquals(emptyList(), result.precedingFailures)
    }

    @Test
    fun serverErrorFallsBackToTheCdnAndKeepsTheFailure() = runBlocking<Unit> {
        val log = RequestLog()
        val client = clientFor(
            mapOf(exchangeEndpoint to status(HttpStatusCode.InternalServerError), cdnEndpoint to ok(fawazBody)),
            log,
        )

        val result = assertIs<Outcome.Success<FxFetch>>(fallback(client).fetch()).data

        assertEquals(listOf(exchangeEndpoint, cdnEndpoint), log.urls)
        assertEquals(Decimal.parseCanonical("50.5"), result.quote.rate.egpPerUsd)
        assertEquals(
            listOf(FxAttemptFailure(QuoteProvider.EXCHANGE_RATE_API, exchangeEndpoint, DataError.Http(500))),
            result.precedingFailures,
        )
    }

    @Test
    fun invalidPrimaryValueFallsBackInsteadOfBlending() = runBlocking<Unit> {
        val euroBase = exchangeBody.replace("\"base_code\":\"USD\"", "\"base_code\":\"EUR\"")
        val client = clientFor(mapOf(exchangeEndpoint to ok(euroBase), cdnEndpoint to ok(fawazBody)), RequestLog())

        val result = assertIs<Outcome.Success<FxFetch>>(fallback(client).fetch()).data

        assertEquals(QuoteProvider.FAWAZ_CURRENCY_API, result.quote.provenance.provider)
        assertEquals(DataError.InvalidPayload(PayloadFailure.WrongUnit), result.precedingFailures.single().error)
    }

    @Test
    fun mirrorIsTriedOnceAfterTheCdnFails() = runBlocking<Unit> {
        val log = RequestLog()
        val client = clientFor(
            mapOf(
                exchangeEndpoint to status(HttpStatusCode.NotFound),
                cdnEndpoint to status(HttpStatusCode.InternalServerError),
                mirrorEndpoint to ok(fawazBody),
            ),
            log,
        )

        val result = assertIs<Outcome.Success<FxFetch>>(fallback(client).fetch()).data

        assertEquals(listOf(exchangeEndpoint, cdnEndpoint, mirrorEndpoint), log.urls)
        assertEquals(
            listOf(
                FxAttemptFailure(QuoteProvider.EXCHANGE_RATE_API, exchangeEndpoint, DataError.Http(404)),
                FxAttemptFailure(QuoteProvider.FAWAZ_CURRENCY_API, cdnEndpoint, DataError.Http(500)),
            ),
            result.precedingFailures,
        )
    }

    @Test
    fun noRequestIsMadeAfterASuccessfulSecondarySource() = runBlocking<Unit> {
        val log = RequestLog()
        val client = clientFor(
            mapOf(
                exchangeEndpoint to status(HttpStatusCode.NotFound),
                cdnEndpoint to ok(fawazBody),
                mirrorEndpoint to ok(fawazBody),
            ),
            log,
        )

        fallback(client).fetch()

        assertEquals(listOf(exchangeEndpoint, cdnEndpoint), log.urls)
    }

    @Test
    fun allProvidersFailingReportsEveryAttemptInOrder() = runBlocking<Unit> {
        val log = RequestLog()
        val client = clientFor(
            mapOf(
                exchangeEndpoint to status(HttpStatusCode.TooManyRequests, mapOf(HttpHeaders.RetryAfter to "60")),
                cdnEndpoint to { throw HttpRequestTimeoutException(cdnEndpoint, 20_000) },
                mirrorEndpoint to { throw IOException("connection reset") },
            ),
            log,
        )

        val failure = assertIs<Outcome.Failure<FxFetchFailure>>(fallback(client).fetch()).error

        assertEquals(listOf(exchangeEndpoint, cdnEndpoint, mirrorEndpoint), log.urls)
        assertEquals(
            listOf(
                FxAttemptFailure(
                    QuoteProvider.EXCHANGE_RATE_API,
                    exchangeEndpoint,
                    DataError.RateLimited(FETCHED_AT + 60.seconds),
                ),
                FxAttemptFailure(QuoteProvider.FAWAZ_CURRENCY_API, cdnEndpoint, DataError.Timeout),
                FxAttemptFailure(QuoteProvider.FAWAZ_CURRENCY_API, mirrorEndpoint, DataError.Transport),
            ),
            failure.attempts,
        )
    }

    @Test
    fun malformedBodiesAreReportedAsInvalidPayloads() = runBlocking<Unit> {
        val client = clientFor(
            mapOf(
                exchangeEndpoint to ok("{"),
                cdnEndpoint to ok("<html>challenge</html>"),
                mirrorEndpoint to ok(fawazBody),
            ),
            RequestLog(),
        )

        val result = assertIs<Outcome.Success<FxFetch>>(fallback(client).fetch()).data

        assertEquals(
            listOf(
                DataError.InvalidPayload(PayloadFailure.Malformed),
                DataError.InvalidPayload(PayloadFailure.Malformed),
            ),
            result.precedingFailures.map { it.error },
        )
    }

    @Test
    fun oversizedJsonIsRefusedWhileReading() = runBlocking<Unit> {
        val oversized = "{\"result\":\"success\"" + " ".repeat(JSON_MAX_BYTES) + "}"
        val client = clientFor(mapOf(exchangeEndpoint to ok(oversized)), RequestLog())

        assertEquals(
            Outcome.Failure(DataError.InvalidPayload(PayloadFailure.TooLarge)),
            ExchangeRateApiDataSource(client, clock, dispatchers).fetch(),
        )
    }

    @Test
    fun declaredContentLengthOverTheLimitIsRefusedBeforeReading() = runBlocking<Unit> {
        val client = clientFor(
            mapOf(
                exchangeEndpoint to ok(
                    exchangeBody,
                    extraHeaders = mapOf(HttpHeaders.ContentLength to (JSON_MAX_BYTES + 1).toString()),
                ),
            ),
            RequestLog(),
        )

        assertEquals(
            Outcome.Failure(DataError.InvalidPayload(PayloadFailure.TooLarge)),
            ExchangeRateApiDataSource(client, clock, dispatchers).fetch(),
        )
    }

    @Test
    fun bodyExactlyAtTheHtmlLimitIsAccepted() = runBlocking<Unit> {
        val body = "a".repeat(HTML_MAX_BYTES)
        val client = clientFor(mapOf(exchangeEndpoint to ok(body, "text/html")), RequestLog())

        val text = client.fetchBoundedText(exchangeEndpoint, "text/html", HTML_MAX_BYTES, clock)

        assertEquals(body.length, assertIs<Outcome.Success<String>>(text).data.length)
    }

    @Test
    fun bodyOneByteOverTheHtmlLimitIsRefused() = runBlocking<Unit> {
        val body = "a".repeat(HTML_MAX_BYTES + 1)
        val client = clientFor(mapOf(exchangeEndpoint to ok(body, "text/html")), RequestLog())

        assertEquals(
            Outcome.Failure(DataError.InvalidPayload(PayloadFailure.TooLarge)),
            client.fetchBoundedText(exchangeEndpoint, "text/html", HTML_MAX_BYTES, clock),
        )
    }

    @Test
    fun httpStatusesMapToTypedErrors() = runBlocking<Unit> {
        listOf(HttpStatusCode.NotFound, HttpStatusCode.InternalServerError, HttpStatusCode.ServiceUnavailable)
            .forEach { code ->
                val client = clientFor(mapOf(exchangeEndpoint to status(code)), RequestLog())
                assertEquals(
                    Outcome.Failure(DataError.Http(code.value)),
                    client.fetchBoundedText(exchangeEndpoint, "application/json", 10, clock),
                )
            }
    }

    @Test
    fun rateLimitedKeepsRetryAfterSecondsAndDropsDates() = runBlocking<Unit> {
        val withSeconds = clientFor(
            mapOf(exchangeEndpoint to status(HttpStatusCode.TooManyRequests, mapOf(HttpHeaders.RetryAfter to "120"))),
            RequestLog(),
        )
        assertEquals(
            Outcome.Failure(DataError.RateLimited(FETCHED_AT + 120.seconds)),
            withSeconds.fetchBoundedText(exchangeEndpoint, "application/json", 10, clock),
        )

        val withDate = clientFor(
            mapOf(
                exchangeEndpoint to status(
                    HttpStatusCode.TooManyRequests,
                    mapOf(HttpHeaders.RetryAfter to "Sat, 31 Oct 2026 00:00:00 GMT"),
                ),
            ),
            RequestLog(),
        )
        assertEquals(
            Outcome.Failure(DataError.RateLimited(null)),
            withDate.fetchBoundedText(exchangeEndpoint, "application/json", 10, clock),
        )
    }

    @Test
    fun timeoutAndTransportFailuresAreDistinct() = runBlocking<Unit> {
        val timeout = clientFor(
            mapOf(exchangeEndpoint to { throw HttpRequestTimeoutException(exchangeEndpoint, 20_000) }),
            RequestLog(),
        )
        assertEquals(
            Outcome.Failure(DataError.Timeout),
            timeout.fetchBoundedText(exchangeEndpoint, "application/json", 10, clock),
        )

        val transport = clientFor(mapOf(exchangeEndpoint to { throw IOException("no route") }), RequestLog())
        assertEquals(
            Outcome.Failure(DataError.Transport),
            transport.fetchBoundedText(exchangeEndpoint, "application/json", 10, clock),
        )
    }

    @Test
    fun cancellationPropagatesInsteadOfBecomingAnError() = runBlocking<Unit> {
        assertFailsWith<CancellationException> {
            networkCall<Unit>(clock) { throw CancellationException("stopped") }
        }
    }

    @Test
    fun cancellationStopsTheFallbackChainBeforeTheNextRequest() = runBlocking<Unit> {
        val log = RequestLog()
        val client = clientFor(
            mapOf(
                exchangeEndpoint to { throw CancellationException("stopped") },
                cdnEndpoint to ok(fawazBody),
            ),
            log,
        )

        assertFailsWith<CancellationException> { fallback(client).fetch() }
        assertEquals(listOf(exchangeEndpoint), log.urls)
    }

    @Test
    fun exchangeRateRequestsNeverRetry() = runBlocking<Unit> {
        val log = RequestLog()
        val client = clientFor(mapOf(exchangeEndpoint to status(HttpStatusCode.InternalServerError)), log)

        ExchangeRateApiDataSource(client, clock, dispatchers).fetch()

        assertEquals(1, log.urls.size)
    }

    @Test
    fun goldRequestsUseOnlyTheHtmlAcceptHeaderAndNoQueryData() = runBlocking<Unit> {
        val log = RequestLog()
        val client =
            clientFor(mapOf(IsaghaGoldDataSource.PAGE_URL to ok(TestFixtures.isaghaPricesHtml, "text/html")), log)

        val result = IsaghaGoldDataSource(client, clock, dispatchers).fetch()

        assertIs<Outcome.Success<GoldFetch>>(result)
        val request = log.requests.single()
        assertEquals("https", request.url.protocol.name)
        assertEquals("market.isagha.com", request.url.host)
        assertEquals("/prices", request.url.encodedPath)
        assertTrue(request.url.parameters.isEmpty())
        assertEquals("text/html", request.headers[HttpHeaders.Accept])
    }

    @Test
    fun goldChallengePageFromTheNetworkIsAMalformedPayload() = runBlocking<Unit> {
        val client = clientFor(
            mapOf(IsaghaGoldDataSource.PAGE_URL to ok("<html><body>Verify you are human</body></html>", "text/html")),
            RequestLog(),
        )

        assertEquals(
            Outcome.Failure(DataError.InvalidPayload(PayloadFailure.Malformed)),
            IsaghaGoldDataSource(client, clock, dispatchers).fetch(),
        )
    }

    @Test
    fun goldHttpFailuresKeepTheirTypedError() = runBlocking<Unit> {
        val client = clientFor(mapOf(IsaghaGoldDataSource.PAGE_URL to status(HttpStatusCode.NotFound)), RequestLog())

        assertEquals(
            Outcome.Failure(DataError.Http(404)),
            IsaghaGoldDataSource(client, clock, dispatchers).fetch(),
        )
    }

    @Test
    fun fallbackUsesTheInjectedClockForRetryTimes() = runBlocking<Unit> {
        val later = Instant.parse("2026-10-12T05:00:00Z")
        val client = clientFor(
            mapOf(exchangeEndpoint to status(HttpStatusCode.TooManyRequests, mapOf(HttpHeaders.RetryAfter to "5"))),
            RequestLog(),
        )

        assertEquals(
            Outcome.Failure(DataError.RateLimited(later + 5.seconds)),
            ExchangeRateApiDataSource(client, FixedClock(later), dispatchers).fetch(),
        )
    }

    /** The open.er-api and fawazahmed0 part of the production chain, without the Banque Misr first step. */
    private fun fallback(client: HttpClient): FallbackFxDataSource {
        val exchange = ExchangeRateApiDataSource(client, clock, dispatchers)
        val fawaz = FawazFxDataSource(client, clock, dispatchers)
        return FallbackFxDataSource(
            steps = listOf(FxStep(QuoteProvider.EXCHANGE_RATE_API, exchange.endpoint) { exchange.fetch() }) +
                FawazFxDataSource.ENDPOINTS.map { endpoint ->
                    FxStep(QuoteProvider.FAWAZ_CURRENCY_API, endpoint) { fawaz.fetchEndpoint(endpoint) }
                },
            clock = clock,
        )
    }

    /**
     * Builds a client whose responses come from [routes], keyed by exact URL. A route either returns a response or
     * throws. Every request is recorded in [log] before it is answered.
     */
    private fun clientFor(routes: Map<String, Route>, log: RequestLog): HttpClient = daftarHttpClient(
        MockEngine { request ->
            log.record(request)
            val spec = routes[request.url.toString()]?.invoke() ?: error("unrouted ${request.url}")
            respond(spec.body, spec.status, headers { spec.headers.forEach { (name, value) -> append(name, value) } })
        },
    )

    private fun ok(
        body: String,
        contentType: String = "application/json",
        extraHeaders: Map<String, String> = emptyMap(),
    ): Route = {
        ResponseSpec(
            status = HttpStatusCode.OK,
            body = body,
            headers = mapOf(HttpHeaders.ContentType to contentType) + extraHeaders,
        )
    }

    private fun status(code: HttpStatusCode, headers: Map<String, String> = emptyMap()): Route = {
        ResponseSpec(status = code, body = "", headers = mapOf(HttpHeaders.ContentType to "application/json") + headers)
    }
}

/** A canned response for the mock engine. */
internal data class ResponseSpec(val status: HttpStatusCode, val body: String, val headers: Map<String, String>)

/** A route answers with a response or throws an exception. */
internal typealias Route = () -> ResponseSpec
