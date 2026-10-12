package com.mk.daftar.core.network

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.model.QuoteProvider

/**
 * Sequential FX policy: open.er-api.com first, then the fawazahmed0 endpoints. The first valid response is
 * returned unchanged. No averaging or cross-provider blending. Every failed attempt is reported in order.
 */
internal class FallbackFxDataSource(
    private val primary: ExchangeRateApiDataSource,
    private val secondary: FawazFxDataSource,
) : FxNetworkDataSource {

    override suspend fun fetch(): Outcome<FxFetch, FxFetchFailure> {
        val primaryError = when (val result = primary.fetch()) {
            is Outcome.Success -> return Outcome.Success(result.data)
            is Outcome.Failure -> result.error
        }
        val primaryFailure = FxAttemptFailure(QuoteProvider.EXCHANGE_RATE_API, primary.endpoint, primaryError)
        return when (val result = secondary.fetch()) {
            is Outcome.Success -> Outcome.Success(
                result.data.copy(precedingFailures = listOf(primaryFailure) + result.data.precedingFailures),
            )

            is Outcome.Failure -> Outcome.Failure(FxFetchFailure(listOf(primaryFailure) + result.error.attempts))
        }
    }
}
