package com.mk.daftar.core.network

import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.PayloadFailure
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import com.mk.daftar.core.network.fixtures.TestFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class IsaghaPricesParserTest {

    private fun d(text: String) = Decimal.parseCanonical(text)

    private fun parse(html: String): Outcome<GoldFetch, DataError> = IsaghaPricesParser.parse(html, FETCHED_AT)

    private fun success(html: String): GoldFetch = assertIs<Outcome.Success<GoldFetch>>(parse(html)).data

    private fun assertInvalid(reason: PayloadFailure, outcome: Outcome<*, DataError>) {
        assertEquals(Outcome.Failure(DataError.InvalidPayload(reason)), outcome)
    }

    private fun GoldFetch.rejectedReason(karat: GoldKarat): DataError? = rejected[karat]

    private fun tile(
        unit: String = "21",
        badge: String = "${unit}K",
        sell: String = SELL.getValue(unit.toInt()),
        buy: String = BUY.getValue(unit.toInt()),
        attributesFirst: Boolean = false,
        sellFirst: Boolean = false,
    ): String {
        val attributes = if (attributesFirst) {
            "data-testid=\"prices-tile\" data-unit=\"$unit\""
        } else {
            "data-unit=\"$unit\" data-testid=\"prices-tile\""
        }
        val sellBlock = """
            <span class="prices-strip__price prices-strip__price--sell">
              <span class="prices-strip__caption">بيع</span>
              <span><span data-testid="price-figure">$sell</span><span> ج.م</span></span>
            </span>
        """.trimIndent()
        val buyBlock = """
            <span class="prices-strip__price prices-strip__price--buy">
              <span class="prices-strip__caption">شراء</span>
              <span><span data-testid="price-figure">$buy</span><span> ج.م</span></span>
            </span>
        """.trimIndent()
        val blocks = if (sellFirst) sellBlock + buyBlock else buyBlock + sellBlock
        return """<li $attributes><span>$badge</span><span>عيار $unit</span>$blocks</li>"""
    }

    private fun card(
        karat: Int = 21,
        unit: String = karat.toString(),
        sell: String = SELL.getValue(karat),
        buy: String = BUY.getValue(karat),
        heading: String = "عيار $karat",
    ): String = """
        <div class="price-card" data-testid="prices-card" data-unit="$unit">
          <div><span>$heading</span><span>iSagha.com</span></div>
          <div class="grid">
            <div>
              <div class="mb-1">بيع<ui-dropdown><div>السعر الذي يبيع به التاجر للعميل</div></ui-dropdown></div>
              <div><span data-testid="price-figure">$sell</span> ج.م</div>
            </div>
            <div>
              <div class="mb-1">الفجوة السعرية (بيع)</div>
              <div><span data-testid="price-figure">29.25</span></div>
            </div>
            <div>
              <div class="mb-1">شراء</div>
              <div><span data-testid="price-figure">$buy</span> ج.م</div>
            </div>
          </div>
        </div>
    """.trimIndent()

    private fun page(vararg markup: String, updated: String? = "2026-10-12T07:33:45+03:00"): String {
        val time = if (updated == null) "" else "<time datetime=\"$updated\" data-testid=\"prices-updated\">x</time>"
        return "<html><body>$time<ul>${markup.joinToString("\n")}</ul></body></html>"
    }

    private fun fullPage(vararg karats: Int) = page(
        *karats.map { tile(unit = it.toString(), sell = SELL[it]!!, buy = BUY[it]!!) }.toTypedArray(),
    )

    // Fixture (captured page) ----------------------------------------------------------------

    @Test
    fun capturedPageYieldsExactMerchantPricesForEverySupportedKarat() {
        val fetch = success(TestFixtures.isaghaPricesHtml)

        assertEquals(setOf(GoldKarat.K24, GoldKarat.K21, GoldKarat.K18), fetch.quotes.keys)
        assertTrue(fetch.rejected.isEmpty(), "unexpected rejections: ${fetch.rejected}")
        assertEquals(d("7068.5"), fetch.quotes.getValue(GoldKarat.K24).sell.price.egpPerGram)
        assertEquals(d("7022.75"), fetch.quotes.getValue(GoldKarat.K24).buy.price.egpPerGram)
        assertEquals(d("6185"), fetch.quotes.getValue(GoldKarat.K21).sell.price.egpPerGram)
        assertEquals(d("6145"), fetch.quotes.getValue(GoldKarat.K21).buy.price.egpPerGram)
        assertEquals(d("5301.5"), fetch.quotes.getValue(GoldKarat.K18).sell.price.egpPerGram)
        assertEquals(d("5267.25"), fetch.quotes.getValue(GoldKarat.K18).buy.price.egpPerGram)
    }

    @Test
    fun capturedPageUsesThePricesUpdatedTimestampAndNotTheChartTime() {
        val fetch = success(TestFixtures.isaghaPricesHtml)
        val provenance = fetch.quotes.getValue(GoldKarat.K24).sell.provenance

        assertEquals(SourceTime.Timestamp(Instant.parse("2026-10-12T04:33:45Z")), provenance.sourceTime)
        assertEquals(QuoteKind.LIVE, provenance.kind)
        assertEquals(QuoteProvider.ISAGHA, provenance.provider)
        assertEquals("https://market.isagha.com/prices", provenance.sourceUrl)
        assertEquals(FETCHED_AT, provenance.fetchedAt)
    }

    @Test
    fun capturedPageIgnoresOunceSilverPoundAndOtherUnits() {
        val fetch = success(TestFixtures.isaghaPricesHtml)

        assertEquals(3, fetch.quotes.size)
        assertEquals(setOf(GoldKarat.K24, GoldKarat.K21, GoldKarat.K18), fetch.quotes.keys)
    }

    @Test
    fun capturedPageKeepsSellAndBuyDirectionsExplicit() {
        val fetch = success(TestFixtures.isaghaPricesHtml)
        val merchant = fetch.quotes.getValue(GoldKarat.K24)

        assertEquals(QuoteKey.Gold(GoldKarat.K24, MerchantSide.SELLS_TO_CUSTOMER), merchant.sell.key)
        assertEquals(QuoteKey.Gold(GoldKarat.K24, MerchantSide.BUYS_FROM_CUSTOMER), merchant.buy.key)
    }

    @Test
    fun the24KPriceComesFromTheCardBecauseTheCapturedPageHasNoTile() {
        val tile24 = Regex("prices-tile\"[^>]*data-unit=\"24\"|data-unit=\"24\"[^>]*prices-tile\"")
        assertFalse(tile24.containsMatchIn(TestFixtures.isaghaPricesHtml))
        val fetch = success(TestFixtures.isaghaPricesHtml)
        assertEquals(d("7068.5"), fetch.quotes.getValue(GoldKarat.K24).sell.price.egpPerGram)
    }

    @Test
    fun tileOnlyPageReportsEveryAbsentKarat() {
        val tileOnly = page(tile(unit = "21", sell = "6185", buy = "6145"))
        val fetch = success(tileOnly)

        assertEquals(d("6185"), fetch.quotes.getValue(GoldKarat.K21).sell.price.egpPerGram)
        assertEquals(DataError.InvalidPayload(PayloadFailure.MissingField), fetch.rejectedReason(GoldKarat.K24))
    }

    // Synthetic markup -------------------------------------------------------------------------

    @Test
    fun tilesAndCardsAreReadRegardlessOfAttributeAndElementOrder() {
        val html = page(
            tile(unit = "21", attributesFirst = true, sellFirst = true),
            card(karat = 18, unit = "18", sell = "5301.5", buy = "5267.25"),
        )
        val fetch = success(html)

        assertEquals(d("6185"), fetch.quotes.getValue(GoldKarat.K21).sell.price.egpPerGram)
        assertEquals(d("6145"), fetch.quotes.getValue(GoldKarat.K21).buy.price.egpPerGram)
        assertEquals(d("5267.25"), fetch.quotes.getValue(GoldKarat.K18).buy.price.egpPerGram)
    }

    @Test
    fun entitiesGroupingAndArabicDigitsDecodeBeforeStrictParsing() {
        val html = page(
            tile(unit = "21", sell = "6&#44;185", buy = "٦١٤٥٫٠"),
        )
        val fetch = success(html)

        assertEquals(d("6185"), fetch.quotes.getValue(GoldKarat.K21).sell.price.egpPerGram)
        assertEquals(d("6145"), fetch.quotes.getValue(GoldKarat.K21).buy.price.egpPerGram)
    }

    @Test
    fun identicalDuplicateRepresentationsAgree() {
        val html = page(
            tile(unit = "21", sell = "6185", buy = "6145"),
            card(karat = 21, sell = "6185", buy = "6145"),
        )
        val fetch = success(html)

        assertEquals(d("6145"), fetch.quotes.getValue(GoldKarat.K21).buy.price.egpPerGram)
    }

    @Test
    fun conflictingDuplicatesInvalidateOnlyThatKarat() {
        val html = page(
            tile(unit = "21", sell = "6185", buy = "6145"),
            card(karat = 21, sell = "6190", buy = "6145"),
            tile(unit = "18", sell = "5301.5", buy = "5267.25"),
        )
        val fetch = success(html)

        assertEquals(DataError.InvalidPayload(PayloadFailure.InconsistentPrices), fetch.rejectedReason(GoldKarat.K21))
        assertTrue(GoldKarat.K21 !in fetch.quotes)
        assertEquals(d("5301.5"), fetch.quotes.getValue(GoldKarat.K18).sell.price.egpPerGram)
    }

    @Test
    fun missingKaratIsReportedWhileValidKaratsSurvive() {
        val fetch = success(fullPage(21, 18))

        assertEquals(DataError.InvalidPayload(PayloadFailure.MissingField), fetch.rejectedReason(GoldKarat.K24))
        assertEquals(setOf(GoldKarat.K21, GoldKarat.K18), fetch.quotes.keys)
    }

    @Test
    fun missingLabelsAreRejected() {
        val tileWithoutLabels = """
            <li data-unit="21" data-testid="prices-tile"><span>21K</span>
              <span class="prices-strip__price--sell"><span data-testid="price-figure">6185</span> ج.م</span>
              <span class="prices-strip__price--buy"><span data-testid="price-figure">6145</span> ج.م</span>
            </li>
        """.trimIndent()
        val fetch = success(page(tileWithoutLabels, card(karat = 18, unit = "18")))

        assertEquals(DataError.InvalidPayload(PayloadFailure.MissingField), fetch.rejectedReason(GoldKarat.K21))
        assertEquals(setOf(GoldKarat.K18), fetch.quotes.keys)
    }

    @Test
    fun cardWithoutABuyLabelIsRejected() {
        val cardWithoutBuy = card(
            karat = 21,
        ).replace("<div class=\"mb-1\">شراء</div>", "<div class=\"mb-1\">تغيير</div>")
        val fetch = success(page(cardWithoutBuy, card(karat = 18, unit = "18")))

        assertEquals(DataError.InvalidPayload(PayloadFailure.MissingField), fetch.rejectedReason(GoldKarat.K21))
    }

    @Test
    fun swappedBuyAndSellIsRejectedNotCorrected() {
        val fetch = success(page(tile(unit = "21", sell = "6145", buy = "6185"), card(karat = 18, unit = "18")))

        assertEquals(DataError.InvalidPayload(PayloadFailure.InconsistentPrices), fetch.rejectedReason(GoldKarat.K21))
        assertTrue(GoldKarat.K21 !in fetch.quotes)
    }

    @Test
    fun sellEqualToBuyIsAllowed() {
        val fetch = success(page(tile(unit = "21", sell = "6145", buy = "6145"), card(karat = 18, unit = "18")))

        assertEquals(d("6145"), fetch.quotes.getValue(GoldKarat.K21).sell.price.egpPerGram)
    }

    @Test
    fun zeroNegativeAndInvalidNumbersAreRejected() {
        val zero = success(page(tile(unit = "21", sell = "0", buy = "0"), card(karat = 18, unit = "18")))
        assertEquals(DataError.InvalidPayload(PayloadFailure.InvalidNumber), zero.rejectedReason(GoldKarat.K21))

        val negative = success(page(tile(unit = "21", sell = "-5", buy = "6145"), card(karat = 18, unit = "18")))
        assertEquals(DataError.InvalidPayload(PayloadFailure.InvalidNumber), negative.rejectedReason(GoldKarat.K21))

        val invalid = success(page(tile(unit = "21", sell = "abc", buy = "6145"), card(karat = 18, unit = "18")))
        assertEquals(DataError.InvalidPayload(PayloadFailure.InvalidNumber), invalid.rejectedReason(GoldKarat.K21))
    }

    @Test
    fun karatIdentityMustMatchTheBadge() {
        val wrongBadge = success(page(tile(unit = "21", badge = "18K"), card(karat = 18, unit = "18")))
        assertEquals(DataError.InvalidPayload(PayloadFailure.WrongUnit), wrongBadge.rejectedReason(GoldKarat.K21))

        val wrongHeading = success(page(card(karat = 21, heading = "عيار 22"), card(karat = 18, unit = "18")))
        assertEquals(DataError.InvalidPayload(PayloadFailure.WrongUnit), wrongHeading.rejectedReason(GoldKarat.K21))
    }

    @Test
    fun priceBlockWithoutEgpLabelIsRejected() {
        val withoutCurrency = tile(unit = "21").replace(" ج.م", "")
        val fetch = success(page(withoutCurrency, card(karat = 18, unit = "18")))

        assertEquals(DataError.InvalidPayload(PayloadFailure.WrongUnit), fetch.rejectedReason(GoldKarat.K21))
    }

    @Test
    fun blockWithTwoFiguresIsAmbiguous() {
        val twoFigures = tile(unit = "21").replace(
            "<span data-testid=\"price-figure\">6185</span>",
            "<span data-testid=\"price-figure\">6185</span><span data-testid=\"price-figure\">6185</span>",
        )
        val fetch = success(page(twoFigures, card(karat = 18, unit = "18")))

        assertEquals(DataError.InvalidPayload(PayloadFailure.AmbiguousField), fetch.rejectedReason(GoldKarat.K21))
    }

    @Test
    fun nestedTooltipTextInsideALabelIsNotPartOfTheLabel() {
        val fetch = success(page(card(karat = 21, sell = "6185", buy = "6145"), card(karat = 18, unit = "18")))

        assertEquals(d("6185"), fetch.quotes.getValue(GoldKarat.K21).sell.price.egpPerGram)
    }

    @Test
    fun challengeOrLoginPagesAreMalformedPayloads() {
        val challenge = "<html><body><h1>Checking your browser</h1><p>Please wait.</p></body></html>"
        assertInvalid(PayloadFailure.Malformed, parse(challenge))
        assertInvalid(PayloadFailure.Malformed, parse(""))
    }

    @Test
    fun pageWithOnlyUnsupportedUnitsHasNoValidKarats() {
        val ounceOnly = """
            <div data-testid="prices-card" data-unit="ounce"><span>أوقية</span></div>
        """.trimIndent()
        assertInvalid(PayloadFailure.MissingField, parse(page(ounceOnly)))
    }

    // Timestamps --------------------------------------------------------------------------------

    @Test
    fun malformedTimestampBecomesUnknownAndKeepsThePrices() {
        val fetch = success(page(tile(), card(karat = 18, unit = "18"), updated = "2026-13-45T99:00:00"))

        assertEquals(SourceTime.Unknown, fetch.quotes.getValue(GoldKarat.K21).sell.provenance.sourceTime)
        assertEquals(d("6185"), fetch.quotes.getValue(GoldKarat.K21).sell.price.egpPerGram)
    }

    @Test
    fun timestampMoreThanFiveMinutesAheadOfFetchIsRejected() {
        val future = "2026-10-12T08:00:00+03:00"
        val fetch = success(page(tile(), card(karat = 18, unit = "18"), updated = future))

        assertEquals(SourceTime.Unknown, fetch.quotes.getValue(GoldKarat.K21).sell.provenance.sourceTime)
    }

    @Test
    fun timestampWithinFiveMinutesIsAccepted() {
        val nearFuture = "2026-10-12T07:38:00+03:00"
        val fetch = success(page(tile(), card(karat = 18, unit = "18"), updated = nearFuture))

        assertEquals(
            SourceTime.Timestamp(Instant.parse("2026-10-12T04:38:00Z")),
            fetch.quotes.getValue(GoldKarat.K21).sell.provenance.sourceTime,
        )
    }

    @Test
    fun conflictingTimestampsBecomeUnknown() {
        val html = """
            <html><body>
              <time datetime="2026-10-12T07:33:45+03:00" data-testid="prices-updated">a</time>
              <time datetime="2026-10-12T07:30:00+03:00" data-testid="prices-updated">b</time>
              <ul>${tile()}${card(karat = 18, unit = "18")}</ul>
            </body></html>
        """.trimIndent()
        val fetch = success(html)

        assertEquals(SourceTime.Unknown, fetch.quotes.getValue(GoldKarat.K21).sell.provenance.sourceTime)
    }

    @Test
    fun missingTimestampBecomesUnknown() {
        val fetch = success(page(tile(), card(karat = 18, unit = "18"), updated = null))

        assertEquals(SourceTime.Unknown, fetch.quotes.getValue(GoldKarat.K21).sell.provenance.sourceTime)
        assertEquals(setOf(GoldKarat.K21, GoldKarat.K18), fetch.quotes.keys)
    }

    private companion object {
        val SELL = mapOf(24 to "7068.5", 21 to "6185", 18 to "5301.5")
        val BUY = mapOf(24 to "7022.75", 21 to "6145", 18 to "5267.25")
    }
}
