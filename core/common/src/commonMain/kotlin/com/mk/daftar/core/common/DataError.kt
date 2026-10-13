package com.mk.daftar.core.common

import kotlin.time.Instant

/** Why a remote data operation failed. Never carries raw response bodies or personal data. */
sealed interface DataError {
    /** Connection, DNS, TLS or other I/O failure before a response arrived. */
    data object Transport : DataError

    data object Timeout : DataError

    data class Http(val status: Int) : DataError

    /** The provider asked us to back off; [retryAt] is set only when the response said when. */
    data class RateLimited(val retryAt: Instant?) : DataError

    data class InvalidPayload(val reason: PayloadFailure) : DataError

    data object Unknown : DataError
}

/** Why a successfully received payload was rejected. */
enum class PayloadFailure {
    Malformed,
    MissingField,
    AmbiguousField,
    InvalidNumber,
    InvalidTimestamp,
    WrongUnit,
    InconsistentPrices,
    TooLarge,
    WrongContentType,
    RejectedPage,
}
