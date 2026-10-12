package com.mk.daftar.core.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.mk.daftar.core.common.AppDispatchers
import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.NumericInput
import com.mk.daftar.core.common.NumericInputPolicy
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.PayloadFailure
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldPrice
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.withContext

/** iSagha merchant prices, read from the public prices page. */
internal class IsaghaGoldDataSource(
    private val client: HttpClient,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
) : GoldNetworkDataSource {

    override suspend fun fetch(): Outcome<GoldFetch, DataError> {
        val html = when (val result = client.fetchBoundedText(PAGE_URL, HTML_ACCEPT, HTML_MAX_BYTES, clock)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }
        val fetchedAt = clock.now()
        return withContext(dispatchers.default) { IsaghaPricesParser.parse(html, fetchedAt) }
    }

    internal companion object {
        const val PAGE_URL = "https://market.isagha.com/prices"
        const val HTML_ACCEPT = "text/html"
    }
}

/**
 * Pure parser for the iSagha prices page. Reads the tile strip first and the mobile card as a fallback, per karat.
 * Karats without a unique, consistent, valid sell/buy pair are rejected; the rest of the page still counts.
 */
internal object IsaghaPricesParser {

    private const val SELL_LABEL = "بيع"
    private const val BUY_LABEL = "شراء"
    private const val CURRENCY_LABEL = "ج.م"

    fun parse(html: String, fetchedAt: Instant): Outcome<GoldFetch, DataError> {
        val document = Ksoup.parse(html)
        if (document.select(PRICE_MARKUP).isEmpty()) {
            return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.Malformed))
        }
        val sourceTime = parseSourceTime(document, fetchedAt)
        val quotes = linkedMapOf<GoldKarat, MerchantGoldQuotes>()
        val rejected = linkedMapOf<GoldKarat, DataError>()
        for (karat in GoldKarat.entries) {
            when (val result = parseKarat(document, karat)) {
                is Outcome.Success -> quotes[karat] = quotesFor(karat, result.data, sourceTime, fetchedAt)
                is Outcome.Failure -> rejected[karat] = result.error
            }
        }
        if (quotes.isEmpty()) {
            return Outcome.Failure(
                rejected.values.firstOrNull() ?: DataError.InvalidPayload(PayloadFailure.MissingField),
            )
        }
        return Outcome.Success(GoldFetch(quotes, rejected))
    }

    private fun parseSourceTime(document: Document, fetchedAt: Instant): SourceTime {
        val values = document.select("time[data-testid=prices-updated][datetime]")
            .map { it.attr("datetime").trim() }
            .distinct()
        if (values.size != 1) return SourceTime.Unknown
        val instant = parseInstantOrNull(values.single()) ?: return SourceTime.Unknown
        if (instant > fetchedAt + MAX_FUTURE_SKEW) return SourceTime.Unknown
        return SourceTime.Timestamp(instant)
    }

    private fun parseInstantOrNull(text: String): Instant? = try {
        Instant.parse(text)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun parseKarat(document: Document, karat: GoldKarat): Outcome<MerchantPair, DataError> {
        val tiles = document.select("[data-testid=prices-tile][data-unit=\"${karat.carats}\"]")
        val cards = document.select("[data-testid=prices-card][data-unit=\"${karat.carats}\"]")
        val representations = tiles.map { parseTile(it, karat) } + cards.map { parseCard(it, karat) }
        if (representations.isEmpty()) return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.MissingField))
        val parsed = mutableListOf<MerchantPair>()
        for (representation in representations) {
            when (representation) {
                is Outcome.Failure -> return representation
                is Outcome.Success -> parsed += representation.data
            }
        }
        if (parsed.distinct().size !=
            1
        ) {
            return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.InconsistentPrices))
        }
        return Outcome.Success(parsed.first())
    }

    private fun parseTile(tile: Element, karat: GoldKarat): Outcome<MerchantPair, DataError> {
        val badge = "${karat.carats}K"
        if (tile.select("span").none { it.text().trim() == badge }) {
            return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.WrongUnit))
        }
        val sell = tile.blockFor(SELL_SIDE_CLASS, SELL_LABEL)
        val buy = tile.blockFor(BUY_SIDE_CLASS, BUY_LABEL)
        return pairOf(sell, buy)
    }

    private fun parseCard(card: Element, karat: GoldKarat): Outcome<MerchantPair, DataError> {
        val heading = "عيار ${karat.carats}"
        if (card.select("span").none { it.ownText().trim() == heading }) {
            return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.WrongUnit))
        }
        val sell = card.labelledBlock(SELL_LABEL)
        val buy = card.labelledBlock(BUY_LABEL)
        return pairOf(sell, buy)
    }

    private fun pairOf(
        sell: Outcome<Decimal, DataError>,
        buy: Outcome<Decimal, DataError>,
    ): Outcome<MerchantPair, DataError> {
        val sellValue = when (sell) {
            is Outcome.Failure -> return sell
            is Outcome.Success -> sell.data
        }
        val buyValue = when (buy) {
            is Outcome.Failure -> return buy
            is Outcome.Success -> buy.data
        }
        if (sellValue < buyValue) return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.InconsistentPrices))
        return Outcome.Success(MerchantPair(sellValue, buyValue))
    }

    /** The one price block of a tile's side, identified by its class and caption. */
    private fun Element.blockFor(sideClass: String, caption: String): Outcome<Decimal, DataError> {
        val blocks = select(sideClass)
        if (blocks.size != 1) return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.AmbiguousField))
        val block = blocks.single()
        val labels = block.select(".prices-strip__caption").filter { it.ownText().trim() == caption }
        if (labels.size != 1) return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.MissingField))
        return block.priceFigure()
    }

    /**
     * The direct price block whose label's own text is [caption]. Tooltip text nested in the label is excluded
     * because only the label's own text is compared.
     */
    private fun Element.labelledBlock(caption: String): Outcome<Decimal, DataError> {
        val blocks = select("div")
            .filter { it.ownText().trim() == caption }
            .mapNotNull { it.parent() }
            .distinct()
        if (blocks.size != 1) {
            val reason = if (blocks.isEmpty()) PayloadFailure.MissingField else PayloadFailure.AmbiguousField
            return Outcome.Failure(DataError.InvalidPayload(reason))
        }
        return blocks.single().priceFigure()
    }

    /** Exactly one price figure and the EGP label in a price block, read as a positive exact decimal. */
    private fun Element.priceFigure(): Outcome<Decimal, DataError> {
        if (!text().contains(CURRENCY_LABEL)) return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.WrongUnit))
        val figures = select(PRICE_FIGURE)
        if (figures.size != 1) {
            val reason = if (figures.isEmpty()) PayloadFailure.MissingField else PayloadFailure.AmbiguousField
            return Outcome.Failure(DataError.InvalidPayload(reason))
        }
        val normalized = when (
            val result = NumericInput.normalize(
                figures.single().text(),
                NumericInputPolicy.REMOTE_MARKUP,
            )
        ) {
            is Outcome.Failure -> return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.InvalidNumber))
            is Outcome.Success -> result.data
        }
        val value = Decimal.parseCanonicalOrNull(normalized)
            ?: return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.InvalidNumber))
        if (value <= Decimal.ZERO) return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.InvalidNumber))
        return Outcome.Success(value)
    }

    private fun quotesFor(
        karat: GoldKarat,
        pair: MerchantPair,
        sourceTime: SourceTime,
        fetchedAt: Instant,
    ): MerchantGoldQuotes {
        val provenance = QuoteProvenance.live(QuoteProvider.ISAGHA, PAGE_URL, sourceTime, fetchedAt)
        return MerchantGoldQuotes(
            sell = GoldQuote(QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER), GoldPrice(pair.sell), provenance),
            buy = GoldQuote(QuoteKey.Gold(karat, MerchantSide.BUYS_FROM_CUSTOMER), GoldPrice(pair.buy), provenance),
        )
    }

    private data class MerchantPair(val sell: Decimal, val buy: Decimal)

    private const val PAGE_URL = IsaghaGoldDataSource.PAGE_URL
    private const val PRICE_FIGURE = "[data-testid=price-figure]"
    private const val PRICE_MARKUP = "[data-testid=prices-tile], [data-testid=prices-card]"
    private const val SELL_SIDE_CLASS = ".prices-strip__price--sell"
    private const val BUY_SIDE_CLASS = ".prices-strip__price--buy"
}
