package com.mk.daftar.feature.journal

import com.mk.daftar.core.common.NumericInput
import com.mk.daftar.core.model.Decimal
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** A calendar date as `yyyy-mm-dd`, the form the date field shows and accepts. */
internal fun LocalDate.isoText(): String = "${year.padded(4)}-${(month.ordinal + 1).padded(2)}-${day.padded(2)}"

private fun Int.padded(width: Int): String = toString().padStart(width, '0')

private val ISO_DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

/**
 * Parses a typed `yyyy-mm-dd` date. Arabic-Indic and Persian digits are normalized first. The text is never read as a
 * decimal number. Null when the text is not a real calendar date, so a leap day only counts in a leap year.
 */
internal fun parseIsoDate(raw: String): LocalDate? {
    val text = NumericInput.normalizeDigits(raw.trim())
    if (!ISO_DATE.matches(text)) return null
    return runCatching { LocalDate.parse(text) }.getOrNull()
}

/** The local calendar date of [instant] in [zone]. */
internal fun Instant.localDate(zone: TimeZone): LocalDate = toLocalDateTime(zone).date
