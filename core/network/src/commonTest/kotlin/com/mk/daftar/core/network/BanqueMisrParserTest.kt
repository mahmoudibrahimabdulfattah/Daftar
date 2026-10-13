package com.mk.daftar.core.network

import com.mk.daftar.core.common.AppDispatchers
import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.PayloadFailure
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxBasis
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import com.mk.daftar.core.network.fixtures.TestFixtures
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

class BanqueMisrParserTest {

    private val endpoint = BanqueMisrDataSource.PAGE_URL
    private val dispatchers = AppDispatchers(default = Dispatchers.Unconfined)

    private fun parse(html: String) = BanqueMisrParser.parse(html, FETCHED_AT, endpoint)

    private fun parsed(html: String): FxFetch = assertIs<Outcome.Success<FxFetch>>(parse(html)).data

    private fun failure(html: String): DataError = assertIs<Outcome.Failure<DataError>>(parse(html)).error

    @Test
    fun theRecordedPageYieldsTheUsdTransferBuyAndItsPublicationTime() {
        val result = parsed(TestFixtures.banqueMisrFxHtml)

        val quote = assertIs<FxQuote>(result.quote)
        assertEquals(Decimal.parseCanonical("52.77"), quote.rate.egpPerUsd)
        assertEquals(FxBasis.BANK_TRANSFER_BUY, quote.basis)
        assertEquals(QuoteProvider.BANQUE_MISR, quote.provenance.provider)
        assertEquals(endpoint, quote.provenance.sourceUrl)
        // 13-10-2026 14:14:32 in Africa/Cairo, which is UTC+3 on that date.
        assertEquals(
            SourceTime.Timestamp(Instant.parse("2026-10-13T11:14:32Z")),
            quote.provenance.sourceTime,
        )
        assertEquals(FETCHED_AT, quote.provenance.fetchedAt)
        assertEquals(emptyList(), result.precedingFailures)
    }

    @Test
    fun specExampleCashAndTransferDifferAndUsdOneHundredIsEgpFiveThousandTwoHundredSeventySeven() {
        val quote = parsed(page(notes = "52.70" to "52.80", transfer = "52.77" to "52.87")).quote

        assertEquals(Decimal.parseCanonical("52.77"), quote.rate.egpPerUsd)
        assertEquals(Decimal.parseCanonical("5277.00"), quote.rate.egpPerUsd * Decimal.parseCanonical("100"))
    }

    @Test
    fun reorderedKnownHeadersMapByLabel() {
        val header = """
            <tr><td>Currency</td><td>Transfer Sell</td><td>Transfer Buy</td><td>Notes Sell</td><td>Notes Buy</td></tr>
        """.trimIndent()
        val row = "<tr><td>US Dollar</td><td>52.87</td><td>52.77</td><td>52.80</td><td>52.70</td></tr>"

        val quote = parsed(page(headerRows = header, bodyRows = row)).quote

        assertEquals(Decimal.parseCanonical("52.77"), quote.rate.egpPerUsd)
    }

    @Test
    fun arabicIndicDigitsAreReadAsExactDecimals() {
        val quote = parsed(page(transfer = "٥٢.٧٧" to "٥٢.٨٧")).quote

        assertEquals(Decimal.parseCanonical("52.77"), quote.rate.egpPerUsd)
    }

    @Test
    fun aMissingUsdRowIsRefused() {
        assertEquals(
            DataError.InvalidPayload(PayloadFailure.MissingField),
            failure(page(bodyRows = "<tr><td>EURO</td><td>58.85</td><td>59.19</td><td>58.85</td><td>59.19</td></tr>")),
        )
    }

    @Test
    fun aDuplicateUsdRowIsRefused() {
        val usd = usdRow("52.70", "52.80", "52.77", "52.87")
        assertEquals(
            DataError.InvalidPayload(PayloadFailure.AmbiguousField),
            failure(page(bodyRows = usd + usd)),
        )
    }

    @Test
    fun aUsdRowNamedByTheUsdCodeIsAccepted() {
        assertEquals(Decimal.parseCanonical("52.77"), parsed(page(usdLabel = "USD")).quote.rate.egpPerUsd)
    }

    @Test
    fun blankOrInvalidDecimalsAreRefused() {
        assertEquals(
            DataError.InvalidPayload(PayloadFailure.MissingField),
            failure(page(transfer = "" to "52.87")),
        )
        assertEquals(
            DataError.InvalidPayload(PayloadFailure.InvalidNumber),
            failure(page(transfer = "52,7a" to "52.87")),
        )
        assertEquals(
            DataError.InvalidPayload(PayloadFailure.InvalidNumber),
            failure(page(transfer = "0" to "52.87")),
        )
        assertEquals(
            DataError.InvalidPayload(PayloadFailure.InvalidNumber),
            failure(page(transfer = "-52.77" to "52.87")),
        )
        assertEquals(
            DataError.InvalidPayload(PayloadFailure.InvalidNumber),
            failure(page(transfer = "1000001" to "1000002")),
        )
    }

    @Test
    fun aBuyAboveItsSellInEitherPairIsRefused() {
        assertEquals(
            DataError.InvalidPayload(PayloadFailure.InconsistentPrices),
            failure(page(transfer = "52.90" to "52.87")),
        )
        assertEquals(
            DataError.InvalidPayload(PayloadFailure.InconsistentPrices),
            failure(page(notes = "52.90" to "52.80", transfer = "52.77" to "52.87")),
        )
    }

    @Test
    fun aMissingTransferCellNeverBorrowsTheCashRate() {
        val row = "<tr><td>US Dollar</td><td>52.70</td><td>52.80</td><td></td><td>52.87</td></tr>"
        assertEquals(DataError.InvalidPayload(PayloadFailure.MissingField), failure(page(bodyRows = row)))
    }

    @Test
    fun anUnknownHeaderFailsClosed() {
        val header = "<tr><td>Currency</td><td>Notes Buy</td><td>Notes Sell</td>" +
            "<td>Cheque</td><td>Transfer Sell</td></tr>"
        assertEquals(DataError.InvalidPayload(PayloadFailure.Malformed), failure(page(headerRows = header)))
    }

    @Test
    fun aMissingExpectedHeaderFailsClosed() {
        val header = "<tr><td>Currency</td><td>Notes</td><td>Transfer</td></tr>"
        assertTrue(failure(page(headerRows = header)) is DataError.InvalidPayload)
    }

    @Test
    fun wafAndLoginPagesAreRejectedAsPages() {
        val waf = "<html><body><h1>Request Rejected</h1><p>The requested URL was rejected.</p></body></html>"
        val login = "<html><body><form><input type=\"password\" name=\"p\"/></form></body></html>"

        assertEquals(DataError.InvalidPayload(PayloadFailure.RejectedPage), failure(waf))
        assertEquals(DataError.InvalidPayload(PayloadFailure.RejectedPage), failure(login))
    }

    @Test
    fun aPageWithoutPublicationTimeIsCheckedNotPublished() {
        val quote = parsed(page(title = "")).quote

        assertEquals(SourceTime.Unknown, quote.provenance.sourceTime)
        assertEquals(FETCHED_AT, quote.provenance.fetchedAt)
    }

    @Test
    fun anUnreadablePublicationTimeIsTreatedAsAbsent() {
        val quote = parsed(page(title = "<div class=\"generic-details-title\"><p>13-13-2026 99:99:99</p></div>")).quote

        assertEquals(SourceTime.Unknown, quote.provenance.sourceTime)
    }

    @Test
    fun aWrongContentTypeIsRefusedByTheRequest() = runBlocking<Unit> {
        val engine = MockEngine {
            respond(
                content = TestFixtures.banqueMisrFxHtml,
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "application/json") },
            )
        }
        val client = daftarHttpClient(engine)

        assertEquals(
            Outcome.Failure(DataError.InvalidPayload(PayloadFailure.WrongContentType)),
            BanqueMisrDataSource(client, FixedClock(FETCHED_AT), dispatchers).fetch(),
        )
    }

    @Test
    fun anOversizedBodyIsRefused() = runBlocking<Unit> {
        val oversized = "a".repeat(HTML_MAX_BYTES + 1)
        val engine = MockEngine {
            respond(
                content = oversized,
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "text/html") },
            )
        }

        assertEquals(
            Outcome.Failure(DataError.InvalidPayload(PayloadFailure.TooLarge)),
            BanqueMisrDataSource(daftarHttpClient(engine), FixedClock(FETCHED_AT), dispatchers).fetch(),
        )
    }

    @Test
    fun aSlowPageIsCutOffAtTheTenSecondRequestLimit() = runTest {
        val engine = MockEngine {
            delay(11.seconds)
            respond(
                TestFixtures.banqueMisrFxHtml,
                HttpStatusCode.OK,
                headers { append(HttpHeaders.ContentType, "text/html") },
            )
        }

        assertEquals(
            Outcome.Failure(DataError.Timeout),
            BanqueMisrDataSource(daftarHttpClient(engine), FixedClock(FETCHED_AT), dispatchers).fetch(),
        )
    }

    private fun usdRow(notesBuy: String, notesSell: String, transferBuy: String, transferSell: String): String =
        "<tr><td><img class=\"img-flag\" src=\"/-/media/flags/usd.ashx\" /><br />US Dollar</td>" +
            "<td>$notesBuy</td><td>$notesSell</td><td>$transferBuy</td><td>$transferSell</td></tr>"

    /** A page shaped like the recorded one, with the parts a test varies. */
    private fun page(
        headerRows: String = DEFAULT_HEADER,
        bodyRows: String? = null,
        usdLabel: String = "US Dollar",
        notes: Pair<String, String> = "52.70" to "52.80",
        transfer: Pair<String, String> = "52.77" to "52.87",
        title: String = "<div class=\"generic-details-title\"><p>13-10-2026 14:14:32</p></div>",
    ): String {
        val row = bodyRows ?: "<tr><td><img class=\"img-flag\" src=\"/x.ashx\" /><br />$usdLabel</td>" +
            "<td>${notes.first}</td><td>${notes.second}</td><td>${transfer.first}</td><td>${transfer.second}</td></tr>"
        return "<html><head><title>Exchange rate and currencies</title></head><body>$title" +
            "<div class=\"table-responsive\"><table><thead>$headerRows</thead><tbody>$row</tbody></table></div>" +
            "</body></html>"
    }

    private companion object {
        const val DEFAULT_HEADER = "<tr class=\"first-row\"><td rowspan=\"2\">Currency</td>" +
            "<td colspan=\"2\">Notes</td>" +
            "<td colspan=\"2\">Transfer</td></tr>" +
            "<tr class=\"second-row\"><td>Buy</td><td>Sell</td><td>Buy</td><td>Sell</td></tr>"
    }
}
