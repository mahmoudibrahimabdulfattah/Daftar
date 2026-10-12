package com.mk.daftar.core.network

import com.mk.daftar.core.common.AppDispatchers
import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlinx.coroutines.withContext

/** Primary USD to EGP source: open.er-api.com, free and attribution-required. */
internal class ExchangeRateApiDataSource(
    private val client: HttpClient,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    val endpoint: String = ENDPOINT,
) {
    suspend fun fetch(): Outcome<FxFetch, DataError> {
        val body = when (val result = client.fetchBoundedText(endpoint, JSON_ACCEPT, JSON_MAX_BYTES, clock)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }
        val fetchedAt = clock.now()
        return withContext(dispatchers.default) { FxParsers.exchangeRate(body, fetchedAt, endpoint) }
    }

    internal companion object {
        const val ENDPOINT = "https://open.er-api.com/v6/latest/USD"
    }
}

internal const val JSON_ACCEPT = "application/json"
