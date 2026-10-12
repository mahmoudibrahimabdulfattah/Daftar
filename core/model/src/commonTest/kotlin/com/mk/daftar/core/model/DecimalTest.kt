package com.mk.daftar.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecimalTest {

    private fun d(text: String) = Decimal.parseCanonical(text)

    @Test
    fun additionIsExact() {
        assertEquals("0.3", (d("0.1") + d("0.2")).toPlainString())
    }

    @Test
    fun multiplicationKeepsEveryDigit() {
        assertEquals("17441.935", (d("3.29") * d("5301.5")).toPlainString())
    }

    @Test
    fun subtractionKeepsSign() {
        assertEquals("-0.75", (d("0.25") - d("1")).toPlainString())
    }

    @Test
    fun numericEqualityIgnoresTrailingZerosAndHashesTheSame() {
        assertEquals(d("1"), d("1.0"))
        assertEquals(d("1.0"), d("1.00"))
        assertEquals(d("1").hashCode(), d("1.00").hashCode())
        assertEquals(0, d("2.50").compareTo(d("2.5")))
    }

    @Test
    fun zeroHasOneCanonicalForm() {
        assertEquals(Decimal.ZERO, d("-0"))
        assertEquals("0", d("0.000").toPlainString())
        assertEquals(d("0"), d("-0.0"))
    }

    @Test
    fun orderingUsesTheNumericValue() {
        assertTrue(d("10") > d("9.99"))
        assertTrue(d("-1") < d("0"))
        assertFalse(d("7068.5") < d("7068.50"))
    }

    @Test
    fun canonicalTextHasNoGroupingOrExponent() {
        assertEquals("1234567.5", d("1234567.50").toPlainString())
        assertEquals("0.00000001", d("0.00000001").toPlainString())
    }

    @Test
    fun fractionDigitsCountsCanonicalFraction() {
        assertEquals(0, d("1.000").fractionDigits)
        assertEquals(3, d("17441.935").fractionDigits)
    }

    @Test
    fun parseCanonicalAcceptsTheLimits() {
        val longestInteger = "9".repeat(Decimal.MAX_INTEGER_DIGITS)
        val longestFraction = "0." + "1".repeat(Decimal.MAX_FRACTION_DIGITS)
        assertEquals(longestInteger, d(longestInteger).toPlainString())
        assertEquals(longestFraction, d(longestFraction).toPlainString())
    }

    @Test
    fun parseCanonicalRejectsNonCanonicalText() {
        val rejected = listOf("", " 1", "1 ", "1,000", "1e3", "+1", ".5", "5.", "007", "1.2.3", "abc", "--1", "1_000")
        rejected.forEach { text ->
            assertNull(Decimal.parseCanonicalOrNull(text), "accepted $text")
            assertFailsWith<IllegalArgumentException>(text) { Decimal.parseCanonical(text) }
        }
    }

    @Test
    fun parseCanonicalRejectsExcessIntegerDigits() {
        assertNull(Decimal.parseCanonicalOrNull("1" + "0".repeat(Decimal.MAX_INTEGER_DIGITS)))
    }

    @Test
    fun parseCanonicalRejectsExcessFractionDigits() {
        assertNull(Decimal.parseCanonicalOrNull("0." + "1".repeat(Decimal.MAX_FRACTION_DIGITS + 1)))
    }

    @Test
    fun parseCanonicalRejectsOverlongText() {
        assertNull(Decimal.parseCanonicalOrNull("1".repeat(Decimal.MAX_INPUT_LENGTH + 1)))
    }

    @Test
    fun fromLongBuildsIntegers() {
        assertEquals(d("42"), Decimal.fromLong(42))
        assertEquals("-7", Decimal.fromLong(-7).toPlainString())
    }

    @Test
    fun roundingTiesFollowTheNamedMode() {
        assertEquals("2.35", d("2.345").roundToScale(2, DecimalRounding.HALF_UP).toPlainString())
        assertEquals("2.34", d("2.345").roundToScale(2, DecimalRounding.HALF_DOWN).toPlainString())
        assertEquals("2.34", d("2.345").roundToScale(2, DecimalRounding.HALF_EVEN).toPlainString())
        assertEquals("2.34", d("2.335").roundToScale(2, DecimalRounding.HALF_EVEN).toPlainString())
        assertEquals("2.34", d("2.349").roundToScale(2, DecimalRounding.DOWN).toPlainString())
        assertEquals("2.35", d("2.341").roundToScale(2, DecimalRounding.UP).toPlainString())
    }

    @Test
    fun halfUpRoundsNegativeTiesAwayFromZero() {
        assertEquals("-2.35", d("-2.345").roundToScale(2, DecimalRounding.HALF_UP).toPlainString())
    }

    @Test
    fun roundingDoesNotChangeTheStoredPrecisionUnlessAsked() {
        val exact = d("3.29") * d("5301.5")
        assertEquals("17441.935", exact.toPlainString())
        assertEquals("17441.94", exact.roundToScale(2, DecimalRounding.HALF_UP).toPlainString())
    }

    @Test
    fun roundingRejectsScalesOutsideTheSupportedRange() {
        assertFailsWith<IllegalArgumentException> { d("1").roundToScale(-1, DecimalRounding.HALF_UP) }
        assertFailsWith<IllegalArgumentException> {
            d("1").roundToScale(Decimal.MAX_FRACTION_DIGITS + 1, DecimalRounding.HALF_UP)
        }
    }
}
