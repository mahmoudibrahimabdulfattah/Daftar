package com.mk.daftar.core.network

import com.mk.daftar.core.common.AppDispatchers
import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.model.QuoteProvider
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.withContext

/**
 * Fallback USD to EGP source: fawazahmed0 currency API, tried on its CDN and then its documented mirror.
 * The first valid response wins; earlier failed attempts are reported in [FxFetch.precedingFailures].
 */
internal class FawazFxDataSource(
    private val client: HttpClient,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    private val endpoints: List<String> = ENDPOINTS,
) : FxNetworkDataSource {

    override suspend fun fetch(): Outcome<FxFetch, FxFetchFailure> {
        val failures = mutableListOf<FxAttemptFailure>()
        for (endpoint in endpoints) {
            when (val result = fetchOne(endpoint)) {
                is Outcome.Success -> return Outcome.Success(
                    result.data.copy(precedingFailures = failures.toList()),
                )

                is Outcome.Failure ->
                    failures +=
                        FxAttemptFailure(QuoteProvider.FAWAZ_CURRENCY_API, endpoint, result.error)
            }
        }
        return Outcome.Failure(FxFetchFailure(failures.toList()))
    }

    private suspend fun fetchOne(endpoint: String): Outcome<FxFetch, DataError> {
        val body = when (val result = client.fetchBoundedText(endpoint, JSON_ACCEPT, JSON_MAX_BYTES, clock)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }
        val fetchedAt: Instant = clock.now()
        return withContext(dispatchers.default) { FxParsers.fawazCurrency(body, fetchedAt, endpoint) }
    }

    internal companion object {
        const val CDN_ENDPOINT = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json"
        const val MIRROR_ENDPOINT = "https://latest.currency-api.pages.dev/v1/currencies/usd.json"
        val ENDPOINTS = listOf(CDN_ENDPOINT, MIRROR_ENDPOINT)
    }
}
