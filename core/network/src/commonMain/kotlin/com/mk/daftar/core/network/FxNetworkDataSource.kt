package com.mk.daftar.core.network

import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.QuoteProvider
import kotlin.time.Instant

/** Remote USD to EGP rate. Failures list every attempted provider endpoint in order. */
interface FxNetworkDataSource {
    suspend fun fetch(): Outcome<FxFetch, FxFetchFailure>
}

/**
 * The first valid response. [precedingFailures] lists earlier attempts that failed before it.
 * [attributionUrl] is the attribution a provider requires for display, if any.
 */
data class FxFetch(
    val quote: FxQuote,
    val nextUpdateAt: Instant?,
    val attributionUrl: String?,
    val precedingFailures: List<FxAttemptFailure>,
)

data class FxAttemptFailure(val provider: QuoteProvider, val endpoint: String, val error: DataError)

/** Every attempt failed, in the order they were made. */
data class FxFetchFailure(val attempts: List<FxAttemptFailure>)
