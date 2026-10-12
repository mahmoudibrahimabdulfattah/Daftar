package com.mk.daftar.core.network

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
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pure parsers for the FX payloads. They take response text and the injected fetch time, and make no network or
 * clock calls. Numbers are read from their exact JSON tokens, never through Double.
 */
internal object FxParsers {

    private val json = Json

    /** open.er-api.com: the authoritative instant is `time_last_update_unix`. */
    fun exchangeRate(body: String, fetchedAt: Instant, endpoint: String): Outcome<FxFetch, DataError> {
        val root = when (val parsed = parseObject(body)) {
            is Outcome.Failure -> return Outcome.Failure(parsed.error)
            is Outcome.Success -> parsed.data
        }
        if (string(root, "result") != "success") return invalid(PayloadFailure.Malformed)
        if (string(root, "base_code") != "USD") return invalid(PayloadFailure.WrongUnit)

        val rates = obj(root, "rates") ?: return invalid(PayloadFailure.MissingField)
        val egp = when (val result = rate(rates["EGP"])) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }
        val usdToken = rates["USD"]
        if (usdToken != null) {
            val usd = when (val result = rate(usdToken)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.data
            }
            if (usd != Decimal.ONE) return invalid(PayloadFailure.InvalidNumber)
        }

        val lastUpdate = when (
            val result = unixSeconds(
                root["time_last_update_unix"],
                notAfter =
                    fetchedAt + MAX_FUTURE_SKEW,
            )
        ) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }
        val nextUpdate = if (root["time_next_update_unix"] == null) {
            null
        } else {
            when (val result = unixSeconds(root["time_next_update_unix"], notAfter = null)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.data
            }
        }
        if (nextUpdate != null && nextUpdate < lastUpdate) return invalid(PayloadFailure.InvalidTimestamp)

        val provenance = QuoteProvenance.live(
            provider = QuoteProvider.EXCHANGE_RATE_API,
            sourceUrl = endpoint,
            sourceTime = SourceTime.Timestamp(lastUpdate),
            fetchedAt = fetchedAt,
        )
        return Outcome.Success(
            FxFetch(
                quote = FxQuote(FxRate(egp), provenance),
                nextUpdateAt = nextUpdate,
                attributionUrl = EXCHANGE_RATE_ATTRIBUTION,
                precedingFailures = emptyList(),
            ),
        )
    }

    /** fawazahmed0 currency API: the date is date-only provenance and never becomes an exact instant. */
    fun fawazCurrency(body: String, fetchedAt: Instant, endpoint: String): Outcome<FxFetch, DataError> {
        val root = when (val parsed = parseObject(body)) {
            is Outcome.Failure -> return Outcome.Failure(parsed.error)
            is Outcome.Success -> parsed.data
        }
        val dateText = string(root, "date") ?: return invalid(PayloadFailure.MissingField)
        val date = parseDate(dateText) ?: return invalid(PayloadFailure.InvalidTimestamp)
        val today = fetchedAt.toLocalDateTime(TimeZone.UTC).date
        // Egypt is ahead of UTC, so a date one day past UTC's date is still legitimate.
        if (date > today.plusOneDay()) return invalid(PayloadFailure.InvalidTimestamp)

        val usd = obj(root, "usd") ?: return invalid(PayloadFailure.MissingField)
        val egp = when (val result = rate(usd["egp"])) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }
        val provenance = QuoteProvenance.live(
            provider = QuoteProvider.FAWAZ_CURRENCY_API,
            sourceUrl = endpoint,
            sourceTime = SourceTime.DateOnly(date),
            fetchedAt = fetchedAt,
        )
        return Outcome.Success(
            FxFetch(
                quote = FxQuote(FxRate(egp), provenance),
                nextUpdateAt = null,
                attributionUrl = null,
                precedingFailures = emptyList(),
            ),
        )
    }

    private fun parseObject(body: String): Outcome<JsonObject, DataError> {
        val text = body.removePrefix(BYTE_ORDER_MARK)
        val element = try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return invalid(PayloadFailure.Malformed)
        } catch (_: IllegalArgumentException) {
            return invalid(PayloadFailure.Malformed)
        }
        return if (element is JsonObject) Outcome.Success(element) else invalid(PayloadFailure.Malformed)
    }

    private fun string(obj: JsonObject, key: String): String? {
        val primitive = obj[key] as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.content else null
    }

    private fun obj(obj: JsonObject, key: String): JsonObject? = obj[key] as? JsonObject

    /** A positive exact decimal from a JSON number token. Strings are rejected. */
    private fun rate(element: JsonElement?): Outcome<Decimal, DataError> {
        if (element == null) return invalid(PayloadFailure.MissingField)
        val primitive = element as? JsonPrimitive ?: return invalid(PayloadFailure.InvalidNumber)
        if (primitive.isString) return invalid(PayloadFailure.InvalidNumber)
        val canonical = when (val result = NumericInput.normalize(primitive.content, NumericInputPolicy.REMOTE_JSON)) {
            is Outcome.Failure -> return invalid(PayloadFailure.InvalidNumber)
            is Outcome.Success -> result.data
        }
        val value = Decimal.parseCanonicalOrNull(canonical) ?: return invalid(PayloadFailure.InvalidNumber)
        if (value <= Decimal.ZERO) return invalid(PayloadFailure.InvalidNumber)
        return Outcome.Success(value)
    }

    /** A whole-second unix timestamp token, not after [notAfter] when one is given. */
    private fun unixSeconds(element: JsonElement?, notAfter: Instant?): Outcome<Instant, DataError> {
        if (element == null) return invalid(PayloadFailure.MissingField)
        val primitive = element as? JsonPrimitive ?: return invalid(PayloadFailure.InvalidTimestamp)
        if (primitive.isString) return invalid(PayloadFailure.InvalidTimestamp)
        val seconds = primitive.content.toLongOrNull() ?: return invalid(PayloadFailure.InvalidTimestamp)
        val instant = try {
            Instant.fromEpochSeconds(seconds)
        } catch (_: IllegalArgumentException) {
            return invalid(PayloadFailure.InvalidTimestamp)
        }
        if (notAfter != null && instant > notAfter) return invalid(PayloadFailure.InvalidTimestamp)
        return Outcome.Success(instant)
    }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun LocalDate.plusOneDay(): LocalDate = LocalDate.fromEpochDays(toEpochDays() + 1)

    private const val BYTE_ORDER_MARK = ""
    const val EXCHANGE_RATE_ATTRIBUTION = "https://www.exchangerate-api.com"
}

internal fun <T> invalid(reason: PayloadFailure): Outcome<T, DataError> =
    Outcome.Failure(DataError.InvalidPayload(reason))
