package com.mk.daftar.core.database

import com.mk.daftar.core.database.mapper.CorruptRowException
import com.mk.daftar.core.database.mapper.canonicalDecimal
import com.mk.daftar.core.database.mapper.instantOf
import com.mk.daftar.core.database.mapper.localDateOf
import com.mk.daftar.core.database.mapper.optionalInstantOf
import com.mk.daftar.core.model.Decimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class ConversionTest {

    @Test
    fun canonicalDecimalTextRoundTripsExactly() {
        listOf("0", "0.5", "7068.5", "123456789.12", "999999999999999999", "0.000000000001", "3000").forEach { text ->
            assertEquals(text, canonicalDecimal("t", "c", text).toPlainString())
        }
    }

    @Test
    fun noncanonicalDecimalTextIsRejected() {
        listOf("1.50", "-0", "01", ".5", "5.", "1e3", "+1", " 1", "", "1,000", "0.00").forEach { text ->
            assertFailsWith<CorruptRowException>("accepted $text") { canonicalDecimal("t", "c", text) }
        }
    }

    @Test
    fun decimalEqualityIsNumericButStorageTextIsCanonical() {
        val stored = Decimal.parseCanonical("12.5")
        assertEquals("12.5", stored.toPlainString())
        assertEquals(stored, Decimal.parseCanonical("12.5"))
    }

    @Test
    fun instantsRoundTripSecondsAndNanosLosslessly() {
        val samples = listOf(
            Instant.fromEpochSeconds(0, 0),
            Instant.fromEpochSeconds(1_700_000_000, 123_456_789),
            Instant.fromEpochSeconds(-1, 999_999_999),
            Instant.fromEpochSeconds(-62_135_596_800, 1),
        )
        samples.forEach { instant ->
            val rebuilt = instantOf("t", "c", instant.epochSeconds, instant.nanosecondsOfSecond)
            assertEquals(instant, rebuilt)
        }
    }

    @Test
    fun outOfRangeNanosAreRejected() {
        assertFailsWith<CorruptRowException> { instantOf("t", "c", 1, -1) }
        assertFailsWith<CorruptRowException> { instantOf("t", "c", 1, 1_000_000_000) }
    }

    @Test
    fun nullableInstantNeedsBothColumnsOrNeither() {
        assertEquals(null, optionalInstantOf("t", "c", null, null))
        assertEquals(Instant.fromEpochSeconds(5, 6), optionalInstantOf("t", "c", 5, 6))
        assertFailsWith<CorruptRowException> { optionalInstantOf("t", "c", 5, null) }
        assertFailsWith<CorruptRowException> { optionalInstantOf("t", "c", null, 6) }
    }

    @Test
    fun isoDatesRoundTripAndImpossibleDatesAreRejected() {
        assertEquals(LocalDate(2024, 2, 29), localDateOf("t", "c", "2024-02-29"))
        listOf("2024-1-5", "2024-02-30", "2024/02/01", "20240201", "").forEach { text ->
            assertFailsWith<CorruptRowException>("accepted $text") { localDateOf("t", "c", text) }
        }
    }
}
