package com.mk.daftar.core.data.repository

import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.RefreshRejection
import com.mk.daftar.core.domain.repository.RefreshReport
import com.mk.daftar.core.domain.repository.RefreshStepResult
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import com.mk.daftar.core.network.FxAttemptFailure
import com.mk.daftar.core.network.FxFetch
import com.mk.daftar.core.network.FxFetchFailure
import com.mk.daftar.core.network.GoldFetch
import kotlin.time.Instant

/** One remote quote the refresh will commit, with the attribution and next-update time its provider supplied. */
internal class RemoteWrite(val quote: PriceQuote, val nextUpdateAt: Instant?, val attributionUrl: String?)

/** What a refresh reports, and exactly which quotes it commits. Nothing else is written. */
internal class RefreshPlan(val report: RefreshReport, val writes: List<RemoteWrite>)

/**
 * Decides a refresh without touching storage. Fetched values are accepted only when they do not replace a newer cached
 * quote. Comparable timestamps or dates decide the ordering; when either side lacks a comparable source time, the
 * fetch time decides. A gold karat is accepted or kept as a pair, so a buy and sell price never split. Each FX provider
 * is reported independently, so a fallback never erases the primary's last successful quote.
 */
internal object RefreshPlanner {

    fun plan(
        gold: Outcome<GoldFetch, DataError>,
        fx: Outcome<FxFetch, FxFetchFailure>,
        cached: List<RemoteQuoteRecord>,
    ): RefreshPlan {
        val stored = cached.associateBy { keyOf(it.quote) to it.quote.provenance.provider }
        val writes = mutableListOf<RemoteWrite>()
        val goldResults = LinkedHashMap<GoldKarat, RefreshStepResult>()
        val fxResults = LinkedHashMap<QuoteProvider, RefreshStepResult>()

        when (gold) {
            is Outcome.Failure -> GoldKarat.entries.forEach { goldResults[it] = kept(gold.error) }

            is Outcome.Success -> GoldKarat.entries.forEach { karat ->
                val pair = gold.data.quotes[karat]
                val rejected = gold.data.rejected[karat]
                goldResults[karat] = when {
                    pair != null -> {
                        val pairQuotes = listOf<PriceQuote>(pair.sell, pair.buy)
                        val blocked = pairQuotes.any { !replaces(it, stored) }
                        if (blocked) {
                            RefreshStepResult.Kept(RefreshRejection.OlderThanStored)
                        } else {
                            pairQuotes.forEach { writes += RemoteWrite(it, null, null) }
                            RefreshStepResult.Accepted(pairQuotes)
                        }
                    }

                    rejected != null -> kept(rejected)

                    else -> RefreshStepResult.Kept(RefreshRejection.Missing)
                }
            }
        }

        when (fx) {
            is Outcome.Failure -> fx.error.attempts.forEach { fxResults[it.provider] = keptAttempt(it) }

            is Outcome.Success -> {
                fx.data.precedingFailures.forEach { fxResults[it.provider] = keptAttempt(it) }
                val quote: FxQuote = fx.data.quote
                fxResults[quote.provenance.provider] = if (replaces(quote, stored)) {
                    writes += RemoteWrite(quote, fx.data.nextUpdateAt, fx.data.attributionUrl)
                    RefreshStepResult.Accepted(listOf(quote))
                } else {
                    RefreshStepResult.Kept(RefreshRejection.OlderThanStored)
                }
            }
        }
        return RefreshPlan(RefreshReport(gold = goldResults, fx = fxResults), writes)
    }

    /** True when [candidate] is not older than the cached quote for the same key and provider. */
    private fun replaces(
        candidate: PriceQuote,
        stored: Map<Pair<QuoteKey, QuoteProvider>, RemoteQuoteRecord>,
    ): Boolean {
        val previous = stored[keyOf(candidate) to candidate.provenance.provider]?.quote?.provenance ?: return true
        return isNotOlder(candidate.provenance, previous)
    }

    private fun isNotOlder(candidate: QuoteProvenance, previous: QuoteProvenance): Boolean {
        val newSource = candidate.sourceTime
        val oldSource = previous.sourceTime
        return when {
            newSource is SourceTime.Timestamp && oldSource is SourceTime.Timestamp -> newSource.value >= oldSource.value
            newSource is SourceTime.DateOnly && oldSource is SourceTime.DateOnly -> newSource.value >= oldSource.value
            else -> fetchedAt(candidate) >= fetchedAt(previous)
        }
    }

    private fun fetchedAt(provenance: QuoteProvenance): Instant =
        checkNotNull(provenance.fetchedAt) { "remote quotes always carry a fetch time" }

    private fun keyOf(quote: PriceQuote): QuoteKey = when (quote) {
        is GoldQuote -> quote.key
        is FxQuote -> quote.key
    }

    private fun kept(error: DataError): RefreshStepResult.Kept = RefreshStepResult.Kept(rejection(error))

    private fun keptAttempt(attempt: FxAttemptFailure): RefreshStepResult.Kept = kept(attempt.error)

    private fun rejection(error: DataError): RefreshRejection = when (error) {
        DataError.Transport, DataError.Timeout, DataError.Unknown -> RefreshRejection.Unreachable
        is DataError.Http -> RefreshRejection.Unreachable
        is DataError.RateLimited -> RefreshRejection.RateLimited
        is DataError.InvalidPayload -> RefreshRejection.InvalidPayload
    }
}
