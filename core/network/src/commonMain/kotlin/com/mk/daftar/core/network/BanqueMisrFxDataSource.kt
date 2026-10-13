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
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.FxRate
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Banque Misr's published exchange-rate table. This is the first source in the FX chain and the only one whose buy side
 * is a bank's transfer buying rate. Its request has a 10 second limit; the whole FX chain has its own budget.
 */
internal class BanqueMisrDataSource(
    private val client: HttpClient,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    val endpoint: String = PAGE_URL,
) {
    suspend fun fetch(): Outcome<FxFetch, DataError> {
        val html = when (
            val result = withTimeoutOrNull(REQUEST_LIMIT) {
                client.fetchBoundedText(endpoint, HTML_ACCEPT, HTML_MAX_BYTES, clock, mediaType = HTML_MEDIA_TYPE)
            }
        ) {
            null -> return Outcome.Failure(DataError.Timeout)
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }
        val fetchedAt = clock.now()
        return withContext(dispatchers.default) { BanqueMisrParser.parse(html, fetchedAt, endpoint) }
    }

    internal companion object {
        const val PAGE_URL = "https://www.banquemisr.com/Home/CAPITAL%20MARKETS/Exchange%20Rates%20and%20Currencies"
        const val HTML_ACCEPT = "text/html"
        const val HTML_MEDIA_TYPE = "text/html"

        /** Per-request limit for this source. */
        val REQUEST_LIMIT = 10.seconds
    }
}

/**
 * Pure parser for the Banque Misr table. It reads the table structurally: header cells are mapped by their labels, with
 * spans expanded, so reordered known headers are accepted and unknown headers fail closed. The one USD row contributes
 * its Transfer Buy value, which is the only value this source ever returns. The page's own timestamp is read as
 * Africa/Cairo time, and an absent or unreadable timestamp becomes [SourceTime.Unknown] ("checked" semantics).
 */
internal object BanqueMisrParser {

    private val MAX_RATE = Decimal.parseCanonical("1000000")
    private const val CAIRO_ZONE = "Africa/Cairo"
    private val PUBLISHED = Regex("""(\d{2})-(\d{2})-(\d{4})\s+(\d{2}):(\d{2}):(\d{2})""")
    private val USD_LABELS = setOf("us dollar", "usd", "u.s. dollar")

    fun parse(html: String, fetchedAt: Instant, endpoint: String): Outcome<FxFetch, DataError> {
        if (isRejectedPage(html)) return invalid(PayloadFailure.RejectedPage)
        val document = Ksoup.parse(html)
        val tables = document.select("table").filter { it.isExchangeTable() }
        if (tables.isEmpty()) return invalid(PayloadFailure.MissingField)
        if (tables.size > 1) return invalid(PayloadFailure.AmbiguousField)
        val table = tables.single()

        val headerGrid = grid(table.select("thead tr").toList()) ?: return invalid(PayloadFailure.AmbiguousField)
        val layout = when (val result = layoutOf(headerGrid)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }
        val body = grid(table.select("tbody tr").toList()) ?: return invalid(PayloadFailure.AmbiguousField)
        val usdRows = body.rows.indices.filter { row ->
            body.text(row, layout.currency).lowercase() in USD_LABELS
        }
        if (usdRows.isEmpty()) return invalid(PayloadFailure.MissingField)
        if (usdRows.size > 1) return invalid(PayloadFailure.AmbiguousField)
        val row = usdRows.single()

        val notesBuy = when (val result = rateAt(body, row, layout.notesBuy)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }
        val notesSell = when (val result = rateAt(body, row, layout.notesSell)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }
        val buy = when (val result = rateAt(body, row, layout.transferBuy)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }
        val sell = when (val result = rateAt(body, row, layout.transferSell)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }
        // A buy above its sell means the columns were misread or the table is wrong; both pairs must hold.
        if (buy > sell || notesBuy > notesSell) return invalid(PayloadFailure.InconsistentPrices)

        val sourceTime = publicationTime(document)
        val provenance = QuoteProvenance.live(QuoteProvider.BANQUE_MISR, endpoint, sourceTime, fetchedAt)
        return Outcome.Success(
            FxFetch(
                quote = FxQuote(FxRate(buy), provenance),
                nextUpdateAt = null,
                attributionUrl = null,
                precedingFailures = emptyList(),
            ),
        )
    }

    /** WAF blocks and login pages carry no rate table; they are named so they are never read as data. */
    private fun isRejectedPage(html: String): Boolean {
        val lower = html.lowercase()
        return "request rejected" in lower ||
            "the requested url was rejected" in lower ||
            "type=\"password\"" in lower
    }

    /** The table whose header carries both the Notes and the Transfer groups. */
    private fun Element.isExchangeTable(): Boolean {
        val labels = select("thead td, thead th").map { normalizedLabel(it.text()) }
        fun group(name: String) = labels.any { it == name || it.startsWith("$name ") }
        return group("notes") && group("transfer")
    }

    private fun publicationTime(document: Document): SourceTime {
        val text = document.select(".generic-details-title p").firstOrNull()?.text() ?: return SourceTime.Unknown
        val match = PUBLISHED.find(text) ?: return SourceTime.Unknown
        val (day, month, year, hour, minute, second) = match.destructured
        val monthNumber = month.toInt()
        if (monthNumber !in 1..Month.entries.size) return SourceTime.Unknown
        return try {
            val local = LocalDateTime(
                year = year.toInt(),
                month = Month.entries[monthNumber - 1],
                day = day.toInt(),
                hour = hour.toInt(),
                minute = minute.toInt(),
                second = second.toInt(),
            )
            SourceTime.Timestamp(local.toInstant(TimeZone.of(CAIRO_ZONE)))
        } catch (failure: IllegalArgumentException) {
            SourceTime.Unknown
        }
    }

    /** Column indexes of the four expected cells and the currency label. */
    private class Layout(
        val currency: Int,
        val notesBuy: Int,
        val notesSell: Int,
        val transferBuy: Int,
        val transferSell: Int,
    )

    /** Maps every header column by its labels. Any column that is not a known label fails closed. */
    private fun layoutOf(header: Grid): Outcome<Layout, DataError> {
        val keyed = mutableMapOf<String, MutableList<Int>>()
        for (column in 0 until header.columns) {
            val labels = header.rows.indices.map { header.text(it, column) }
                .map { normalizedLabel(it) }
                .filter { it.isNotEmpty() }
                .distinct()
            // A group label over a sub-label ("Notes", "Buy") and one composite label ("Notes Buy") are the same.
            val key = when (labels.joinToString(" ")) {
                "currency" -> "currency"
                "notes buy" -> "notesBuy"
                "notes sell" -> "notesSell"
                "transfer buy" -> "transferBuy"
                "transfer sell" -> "transferSell"
                else -> return invalid(PayloadFailure.Malformed)
            }
            keyed.getOrPut(key) { mutableListOf() } += column
        }
        val required = listOf("currency", "notesBuy", "notesSell", "transferBuy", "transferSell")
        if (required.any { keyed[it] == null }) return invalid(PayloadFailure.MissingField)
        if (required.any { keyed.getValue(it).size != 1 }) return invalid(PayloadFailure.AmbiguousField)
        return Outcome.Success(
            Layout(
                currency = keyed.getValue("currency").single(),
                notesBuy = keyed.getValue("notesBuy").single(),
                notesSell = keyed.getValue("notesSell").single(),
                transferBuy = keyed.getValue("transferBuy").single(),
                transferSell = keyed.getValue("transferSell").single(),
            ),
        )
    }

    /** One rate cell, read as a positive exact decimal within [MAX_RATE]. Blank and invalid cells are refused. */
    private fun rateAt(body: Grid, row: Int, column: Int): Outcome<Decimal, DataError> {
        val text = body.text(row, column)
        if (text.isBlank()) return invalid(PayloadFailure.MissingField)
        val canonical = when (val result = NumericInput.normalize(text, NumericInputPolicy.REMOTE_MARKUP)) {
            is Outcome.Failure -> return invalid(PayloadFailure.InvalidNumber)
            is Outcome.Success -> result.data
        }
        val value = Decimal.parseCanonicalOrNull(canonical) ?: return invalid(PayloadFailure.InvalidNumber)
        if (value <= Decimal.ZERO || value > MAX_RATE) return invalid(PayloadFailure.InvalidNumber)
        return Outcome.Success(value)
    }

    private fun normalizedLabel(text: String): String = text.trim().replace(Regex("\\s+"), " ").lowercase()

    /** A table grid with row and column spans expanded: every cell position holds its text. */
    private class Grid(val rows: List<Map<Int, String>>, val columns: Int) {
        fun text(row: Int, column: Int): String = rows[row][column].orEmpty().trim().replace(Regex("\\s+"), " ")
    }

    /** Expands spans. Null when two cells claim the same position, which is an ambiguous table. */
    private fun grid(trs: List<Element>): Grid? {
        val rows = MutableList(trs.size) { mutableMapOf<Int, String>() }
        trs.forEachIndexed { r, tr ->
            var column = 0
            for (cell in tr.children()) {
                val tag = cell.tagName().lowercase()
                if (tag != "td" && tag != "th") continue
                while (rows[r].containsKey(column)) column++
                val colSpan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                val rowSpan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                val text = cell.text()
                for (dr in 0 until rowSpan) {
                    val target = r + dr
                    if (target >= rows.size) break
                    for (dc in 0 until colSpan) {
                        if (rows[target].containsKey(column + dc)) return null
                        rows[target][column + dc] = text
                    }
                }
                column += colSpan
            }
        }
        val columns = rows.maxOfOrNull { row -> row.keys.maxOrNull()?.plus(1) ?: 0 } ?: 0
        return Grid(rows, columns)
    }
}
