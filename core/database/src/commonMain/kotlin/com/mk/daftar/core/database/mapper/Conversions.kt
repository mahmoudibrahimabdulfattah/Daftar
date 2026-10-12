package com.mk.daftar.core.database.mapper

import com.mk.daftar.core.model.Decimal
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * A stored row that cannot be read as a valid model: a non-canonical decimal, an out-of-range timestamp, an unknown
 * enum name, or a field the model rejects. The message names the table and column only, never the stored value.
 */
class CorruptRowException(val table: String, val column: String) : RuntimeException("Corrupt row: $table.$column")

internal const val NANOS_PER_SECOND_MAX = 999_999_999

/** Runs a model construction, turning the model's own validation failures into [CorruptRowException]. */
internal inline fun <T> rowGuard(table: String, build: () -> T): T = try {
    build()
} catch (error: IllegalArgumentException) {
    throw CorruptRowException(table, "row")
}

internal fun corrupt(table: String, column: String): Nothing = throw CorruptRowException(table, column)

/** Decimal text is accepted only when it is exactly what [Decimal.toPlainString] writes. */
internal fun canonicalDecimal(table: String, column: String, text: String): Decimal {
    val parsed = Decimal.parseCanonicalOrNull(text) ?: corrupt(table, column)
    if (parsed.toPlainString() != text) corrupt(table, column)
    return parsed
}

internal fun instantOf(table: String, column: String, seconds: Long, nanos: Int): Instant {
    if (nanos < 0 || nanos > NANOS_PER_SECOND_MAX) corrupt(table, column)
    return try {
        Instant.fromEpochSeconds(seconds, nanos.toLong())
    } catch (error: RuntimeException) {
        corrupt(table, column)
    }
}

internal fun optionalInstantOf(table: String, column: String, seconds: Long?, nanos: Int?): Instant? {
    if ((seconds == null) != (nanos == null)) corrupt(table, column)
    return if (seconds == null || nanos == null) null else instantOf(table, column, seconds, nanos)
}

/** ISO `YYYY-MM-DD`, validated as a date and required to round-trip to the same text. */
internal fun localDateOf(table: String, column: String, text: String): LocalDate {
    val date = try {
        LocalDate.parse(text)
    } catch (error: RuntimeException) {
        corrupt(table, column)
    }
    if (date.toString() != text) corrupt(table, column)
    return date
}

internal inline fun <reified E : Enum<E>> enumOf(table: String, column: String, name: String): E =
    enumValues<E>().firstOrNull { it.name == name } ?: corrupt(table, column)

internal fun checkRevision(table: String, revision: Long): Long {
    if (revision < 1) corrupt(table, "revision")
    return revision
}

/** Trims a nullable note and maps blank to null, as the model does. Used on writes only. */
internal fun noteForStorage(note: String?): String? = note?.trim()?.ifEmpty { null }
