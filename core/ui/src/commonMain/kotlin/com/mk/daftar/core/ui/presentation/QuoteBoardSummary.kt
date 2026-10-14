package com.mk.daftar.core.ui.presentation

import com.mk.daftar.core.domain.valuation.QuoteFreshness
import com.mk.daftar.core.domain.valuation.QuoteOrigin
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.model.QuoteKind
import kotlin.time.Instant

/** The state of one displayed price, judged the same way on Home and on the prices screen. */
enum class QuoteStatus { LIVE, CACHED, MANUAL, STALE, CLOCK_UNCERTAIN }

/** Freshness first, then whether the value was entered by hand, then live versus saved. */
fun SelectedQuote.quoteStatus(): QuoteStatus = when {
    freshness == QuoteFreshness.CLOCK_UNCERTAIN -> QuoteStatus.CLOCK_UNCERTAIN
    freshness == QuoteFreshness.STALE -> QuoteStatus.STALE
    origin != QuoteOrigin.REMOTE -> QuoteStatus.MANUAL
    provenance.kind == QuoteKind.LIVE -> QuoteStatus.LIVE
    else -> QuoteStatus.CACHED
}

/** The small text under a price whose state differs from the rest of the board. */
enum class QuoteMarker { LIVE, CACHED, MANUAL, STALE, CLOCK_UNCERTAIN, MANUAL_STALE, MANUAL_CLOCK_UNCERTAIN }

/**
 * One freshness statement for a whole board of prices, instead of a tag on every cell. [norm] is the most common state
 * among the remote prices (or MANUAL when every price was entered by hand); prices that differ get a [QuoteMarker].
 * [oldest] is the oldest reference time among the shown prices, never the newest, because prices from different
 * sources are not one synchronized update.
 */
data class QuoteBoardSummary(
    val norm: QuoteStatus?,
    val oldest: Instant?,
    val someDiffer: Boolean,
    val someMissing: Boolean,
    val markers: List<QuoteMarker?>,
) {
    /** Whether nothing at all is available. */
    val empty: Boolean
        get() = norm == null
}

/** Summarizes [quotes] in display order; a null entry is a missing price. */
fun quoteBoardSummaryOf(quotes: List<SelectedQuote?>): QuoteBoardSummary {
    val available = quotes.filterNotNull()
    if (available.isEmpty()) {
        return QuoteBoardSummary(
            null,
            null,
            someDiffer = false,
            someMissing = quotes.isNotEmpty(),
            markers = quotes.map {
                null
            },
        )
    }
    val remote = available.filter { it.origin == QuoteOrigin.REMOTE }
    val norm = if (remote.isEmpty()) {
        // Every price is manual: the summary says so, and only freshness exceptions are marked.
        val freshnesses = available.map { it.quoteStatus() }
        mostCommon(freshnesses) ?: QuoteStatus.MANUAL
    } else {
        mostCommon(remote.map { it.quoteStatus() })
    }
    val allManual = remote.isEmpty()
    val markers = quotes.map { quote -> quote?.let { markerOf(it, norm, allManual) } }
    return QuoteBoardSummary(
        norm = norm,
        oldest = available.minOf { it.referenceAt },
        someDiffer = markers.any { it != null },
        someMissing = quotes.any { it == null },
        markers = markers,
    )
}

private fun markerOf(quote: SelectedQuote, norm: QuoteStatus?, allManual: Boolean): QuoteMarker? {
    val status = quote.quoteStatus()
    val manual = quote.origin != QuoteOrigin.REMOTE
    if (manual && !allManual) {
        return when (quote.freshness) {
            QuoteFreshness.STALE -> QuoteMarker.MANUAL_STALE
            QuoteFreshness.CLOCK_UNCERTAIN -> QuoteMarker.MANUAL_CLOCK_UNCERTAIN
            else -> QuoteMarker.MANUAL
        }
    }
    if (status == norm) return null
    return when (status) {
        QuoteStatus.LIVE -> QuoteMarker.LIVE
        QuoteStatus.CACHED -> QuoteMarker.CACHED
        QuoteStatus.MANUAL -> QuoteMarker.MANUAL
        QuoteStatus.STALE -> QuoteMarker.STALE
        QuoteStatus.CLOCK_UNCERTAIN -> QuoteMarker.CLOCK_UNCERTAIN
    }
}

/** The most frequent status; ties go to the more cautious one (time uncertain, old, saved, then updated). */
private fun mostCommon(statuses: List<QuoteStatus>): QuoteStatus? {
    val caution = listOf(
        QuoteStatus.CLOCK_UNCERTAIN,
        QuoteStatus.STALE,
        QuoteStatus.CACHED,
        QuoteStatus.MANUAL,
        QuoteStatus.LIVE,
    )
    val counts = statuses.groupingBy { it }.eachCount()
    val top = counts.values.maxOrNull() ?: return null
    return caution.first { counts[it] == top }
}
