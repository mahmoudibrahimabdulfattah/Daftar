package com.mk.daftar.core.network

import com.mk.daftar.core.common.AppDispatchers
import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.withTimeoutOrNull

/** One attempt of the FX chain. [fetch] never throws for an expected failure. */
internal class FxStep(
    val provider: QuoteProvider,
    val endpoint: String,
    val fetch: suspend () -> Outcome<FxFetch, DataError>,
)

/**
 * Sequential USD to EGP policy. Steps run in order and the first fresh quote wins. A quote is fresh when the time it
 * describes is not in the future and is at most 24 hours old; a stale or future-dated success is kept only as a
 * candidate.
 * When no step is fresh, the newest candidate by its effective time wins, with ties going to the earlier step. Failed
 * attempts are reported in order. The whole chain has one budget; a step that is still running when it expires is
 * reported as a timeout.
 */
internal class FallbackFxDataSource(
    private val steps: List<FxStep>,
    private val clock: Clock,
    private val budget: Duration = CHAIN_BUDGET,
) : FxNetworkDataSource {

    override suspend fun fetch(): Outcome<FxFetch, FxFetchFailure> {
        val failures = mutableListOf<FxAttemptFailure>()
        val candidates = mutableListOf<Candidate>()
        var winner: FxFetch? = null
        var running: FxStep? = null
        val finished = withTimeoutOrNull(budget) {
            for ((index, step) in steps.withIndex()) {
                running = step
                when (val result = step.fetch()) {
                    is Outcome.Failure -> failures += FxAttemptFailure(step.provider, step.endpoint, result.error)

                    is Outcome.Success -> {
                        val fetch = result.data
                        if (isFresh(fetch.quote.provenance, clock.now())) {
                            winner = fetch
                            break
                        }
                        candidates += Candidate(fetch, index)
                    }
                }
            }
            running = null
            true
        } == true
        if (!finished) running?.let { failures += FxAttemptFailure(it.provider, it.endpoint, DataError.Timeout) }

        winner?.let { return Outcome.Success(it.copy(precedingFailures = failures.toList())) }
        val newest = candidates.minWithOrNull(NEWEST_FIRST)
            ?: return Outcome.Failure(FxFetchFailure(failures.toList()))
        return Outcome.Success(newest.fetch.copy(precedingFailures = failures.toList()))
    }

    private class Candidate(val fetch: FxFetch, val stepIndex: Int)

    internal companion object {
        /** The whole chain, from the first request to the last. */
        val CHAIN_BUDGET: Duration = 30.seconds

        /** A quote older than this is stale. Exactly this age is still fresh. */
        val FRESH_WINDOW: Duration = 24.hours

        private val NEWEST_FIRST: Comparator<Candidate> = Comparator { a, b ->
            val byTime = effectiveTime(b.fetch.quote.provenance).compareTo(effectiveTime(a.fetch.quote.provenance))
            if (byTime != 0) byTime else a.stepIndex.compareTo(b.stepIndex)
        }

        /** The instant a quote is judged by: its source time when it has one, otherwise its fetch time. */
        fun effectiveTime(provenance: QuoteProvenance): Instant {
            val source = provenance.sourceTime
            return if (source is SourceTime.Timestamp) source.value else checkNotNull(provenance.fetchedAt)
        }

        fun isFresh(provenance: QuoteProvenance, now: Instant): Boolean {
            val effective = effectiveTime(provenance)
            return effective <= now && now - effective <= FRESH_WINDOW
        }
    }
}

/**
 * The production USD to EGP chain: Banque Misr transfer buy, open.er-api.com, then the two fawazahmed0 endpoints. The
 * request for each step carries no account data.
 */
internal fun marketFxChain(client: HttpClient, clock: Clock, dispatchers: AppDispatchers): FxNetworkDataSource {
    val banque = BanqueMisrDataSource(client, clock, dispatchers)
    val exchange = ExchangeRateApiDataSource(client, clock, dispatchers)
    val fawaz = FawazFxDataSource(client, clock, dispatchers)
    val steps = listOf(
        FxStep(QuoteProvider.BANQUE_MISR, banque.endpoint) { banque.fetch() },
        FxStep(QuoteProvider.EXCHANGE_RATE_API, exchange.endpoint) { exchange.fetch() },
    ) + FawazFxDataSource.ENDPOINTS.map { endpoint ->
        FxStep(QuoteProvider.FAWAZ_CURRENCY_API, endpoint) { fawaz.fetchEndpoint(endpoint) }
    }
    return FallbackFxDataSource(steps, clock)
}
