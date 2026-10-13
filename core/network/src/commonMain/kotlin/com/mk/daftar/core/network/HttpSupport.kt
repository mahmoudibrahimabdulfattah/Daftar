package com.mk.daftar.core.network

import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.PayloadFailure
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readBuffer
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal const val REQUEST_TIMEOUT_MS = 20_000L
internal const val CONNECT_TIMEOUT_MS = 10_000L
internal const val HTML_MAX_BYTES = 1_048_576
internal const val JSON_MAX_BYTES = 262_144

/** Largest accepted lead time of a provider timestamp over the injected fetch time. */
internal val MAX_FUTURE_SKEW: Duration = 5.minutes

private const val MAX_RETRY_AFTER_SECONDS = 366L * 24 * 60 * 60

/**
 * The shared HTTP client. Without an [engine] the platform engine (OkHttp on Android, Darwin on iOS) is used;
 * tests pass a mock engine. No retries, polling, cookies or disk cache.
 */
internal fun daftarHttpClient(engine: HttpClientEngine? = null): HttpClient =
    if (engine == null) HttpClient { configure() } else HttpClient(engine) { configure() }

private fun HttpClientConfig<*>.configure() {
    expectSuccess = true
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        socketTimeoutMillis = CONNECT_TIMEOUT_MS
    }
}

/**
 * GETs [url] and returns the body as UTF-8 text, reading at most [maxBytes]. The limit is enforced while reading,
 * and a declared Content-Length over the limit is refused before any body is read. When [mediaType] is given, the
 * response must declare that media type, without parameters, or it is refused as a wrong content type.
 */
internal suspend fun HttpClient.fetchBoundedText(
    url: String,
    accept: String,
    maxBytes: Int,
    clock: Clock,
    mediaType: String? = null,
): Outcome<String, DataError> = networkCall(clock) {
    prepareGet(url) {
        header(HttpHeaders.Accept, accept)
    }.execute { response ->
        val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val declaredType = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()?.lowercase()
        when {
            declared != null && declared > maxBytes -> Outcome.Failure(
                DataError.InvalidPayload(PayloadFailure.TooLarge),
            )

            mediaType != null && declaredType != mediaType ->
                Outcome.Failure(DataError.InvalidPayload(PayloadFailure.WrongContentType))

            else -> readBoundedText(response, maxBytes)
        }
    }
}

/** Runs one network step, mapping expected failures to [DataError] and always rethrowing cancellation. */
internal suspend fun <T> networkCall(clock: Clock, block: suspend () -> Outcome<T, DataError>): Outcome<T, DataError> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(e.toDataError(clock))
    }

internal fun Throwable.toDataError(clock: Clock): DataError = when (this) {
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> DataError.Timeout

    is ResponseException -> {
        val status = response.status.value
        if (status == TOO_MANY_REQUESTS) {
            DataError.RateLimited(retryAfter(response, clock))
        } else {
            DataError.Http(status)
        }
    }

    is SerializationException -> DataError.InvalidPayload(PayloadFailure.Malformed)

    is IOException -> DataError.Transport

    else -> DataError.Unknown
}

private const val TOO_MANY_REQUESTS = 429

/** Only the seconds form of Retry-After is read; an HTTP date yields no retry time. */
private fun retryAfter(response: HttpResponse, clock: Clock): Instant? {
    val seconds = response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull() ?: return null
    if (seconds !in 0..MAX_RETRY_AFTER_SECONDS) return null
    return clock.now() + seconds.seconds
}

private suspend fun readBoundedText(response: HttpResponse, maxBytes: Int): Outcome<String, DataError> =
    readBoundedText(response.bodyAsChannel(), maxBytes)

private suspend fun readBoundedText(channel: ByteReadChannel, maxBytes: Int): Outcome<String, DataError> {
    // Reads at most one byte past the limit, so an oversized body is detected without buffering all of it.
    val bytes = channel.readBuffer(maxBytes.toLong() + 1).readByteArray()
    if (bytes.size > maxBytes) return Outcome.Failure(DataError.InvalidPayload(PayloadFailure.TooLarge))
    return Outcome.Success(bytes.decodeToString())
}
