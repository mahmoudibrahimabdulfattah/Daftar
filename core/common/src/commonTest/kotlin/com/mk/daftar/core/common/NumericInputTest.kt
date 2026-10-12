package com.mk.daftar.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class NumericInputTest {

    private fun user(text: String) = NumericInput.normalize(text, NumericInputPolicy.USER_ENTRY)
    private fun markup(text: String) = NumericInput.normalize(text, NumericInputPolicy.REMOTE_MARKUP)
    private fun json(text: String) = NumericInput.normalize(text, NumericInputPolicy.REMOTE_JSON)

    private fun assertOk(expected: String, actual: Outcome<String, NumericInputFailure>) {
        assertEquals(Outcome.Success(expected), actual)
    }

    private fun assertFails(expected: NumericInputFailure, actual: Outcome<String, NumericInputFailure>) {
        assertEquals(Outcome.Failure(expected), actual)
    }

    @Test
    fun asciiDigitsPassThrough() {
        assertOk("6185", user("6185"))
        assertOk("7068.5", markup("7068.5"))
        assertOk("0.5", user("0.5"))
        assertOk("0.5", user(".5"))
    }

    @Test
    fun arabicIndicAndPersianDigitsBecomeAscii() {
        assertOk("7068.5", markup("٧٠٦٨.٥"))
        assertOk("6185", markup("۶۱۸۵"))
    }

    @Test
    fun arabicDecimalSeparatorIsAccepted() {
        assertOk("7068.5", markup("٧٠٦٨٫٥"))
        assertOk("7068.5", markup("7068٫5"))
    }

    @Test
    fun validGroupingIsRemoved() {
        assertOk("7068.5", markup("7,068.5"))
        assertOk("1234567", markup("1,234,567"))
        assertOk("1234.5", markup("١٬٢٣٤٫٥"))
        assertOk("12", markup("12"))
        assertOk("123", markup("123"))
    }

    @Test
    fun malformedGroupingIsRejected() {
        assertFails(NumericInputFailure.Grouping, markup("1,23"))
        assertFails(NumericInputFailure.Grouping, markup("12,34,567"))
        assertFails(NumericInputFailure.Grouping, markup("1234,567"))
        assertFails(NumericInputFailure.Grouping, markup(",123"))
        assertFails(NumericInputFailure.Grouping, markup("1,234,"))
    }

    @Test
    fun mixedSeparatorsAreRejected() {
        assertFails(NumericInputFailure.MixedSeparators, markup("1.5٫2"))
        assertFails(NumericInputFailure.MixedSeparators, markup("1,234٬567,8"))
    }

    @Test
    fun europeanStyleGroupingAfterTheDecimalPointIsRejected() {
        assertFails(NumericInputFailure.Malformed, markup("1.234,5"))
    }

    @Test
    fun signsAreRejectedWhereProhibited() {
        assertFails(NumericInputFailure.Sign, user("-5"))
        assertFails(NumericInputFailure.Sign, user("+5"))
        assertFails(NumericInputFailure.Sign, markup("−5"))
        assertFails(NumericInputFailure.Sign, json("-1.5"))
    }

    @Test
    fun signsInsideTheNumberAreMalformed() {
        assertFails(NumericInputFailure.Malformed, markup("1-2"))
    }

    @Test
    fun exponentTextIsRejectedForUserEntry() {
        assertFails(NumericInputFailure.Exponent, user("1e3"))
        assertFails(NumericInputFailure.Exponent, markup("2E2"))
    }

    @Test
    fun jsonExponentTokensConvertExactly() {
        assertOk("53", json("5.3e1"))
        assertOk("50.123", json("5.0123E1"))
        assertOk("0.001", json("1E-3"))
        assertOk("1200", json("1.2e3"))
        assertOk("0.0000001", json("1e-7"))
    }

    @Test
    fun jsonRejectsGroupingAndAbsurdExponents() {
        assertFails(NumericInputFailure.Malformed, json("1,000"))
        assertFails(NumericInputFailure.IntegerDigits, json("1e999"))
        assertFails(NumericInputFailure.Malformed, json("1e"))
        assertFails(NumericInputFailure.Malformed, json("e5"))
    }

    @Test
    fun userEntryAllowsTwoFractionalDigitsOnly() {
        assertOk("1.25", user("1.25"))
        assertOk("1.1", user("1.10"))
        assertFails(NumericInputFailure.FractionDigits, user("1.001"))
    }

    @Test
    fun remoteValuesAllowTwelveFractionalDigits() {
        assertOk("0.123456789012", markup("0.123456789012"))
        assertFails(NumericInputFailure.FractionDigits, markup("0.1234567890123"))
        assertOk("50.123456789012", json("50.123456789012"))
    }

    @Test
    fun excessPrecisionIsRejectedNotTruncated() {
        assertFails(NumericInputFailure.FractionDigits, json("50.1234567890123"))
        assertFails(NumericInputFailure.FractionDigits, user("3.141"))
    }

    @Test
    fun integerDigitsAreLimitedToEighteen() {
        assertOk("999999999999999999", markup("999999999999999999"))
        assertFails(NumericInputFailure.IntegerDigits, markup("1" + "0".repeat(18)))
        assertFails(NumericInputFailure.IntegerDigits, markup("1,000,000,000,000,000,000,000"))
    }

    @Test
    fun inputOverSixtyFourCharactersIsRejected() {
        assertFails(NumericInputFailure.TooLong, markup("1".repeat(65)))
    }

    @Test
    fun emptyAndBlankInputIsRejected() {
        assertFails(NumericInputFailure.Empty, user(""))
        assertFails(NumericInputFailure.Empty, user("   "))
        assertFails(NumericInputFailure.Malformed, user("."))
        assertFails(NumericInputFailure.Malformed, user("5."))
    }

    @Test
    fun surroundingWhitespaceAndDirectionMarksAreIgnored() {
        assertOk("7068.5", markup("‎ 7068.5 ‏"))
        assertOk("6185", markup(" 6185 "))
    }

    @Test
    fun innerWhitespaceIsMalformed() {
        assertFails(NumericInputFailure.Malformed, markup("70 68"))
    }

    @Test
    fun zeroNormalizesWithoutSign() {
        assertFails(NumericInputFailure.Sign, json("-0.0"))
        assertOk("0", markup("0.000"))
    }

    @Test
    fun letterDigitsAndSymbolsAreMalformed() {
        assertFails(NumericInputFailure.Malformed, user("12a"))
        assertFails(NumericInputFailure.Malformed, markup("ج.م"))
        assertFails(NumericInputFailure.Malformed, markup("1$"))
    }

    @Test
    fun normalizeDigitsMapsArabicIndicAndPersianDigitsAndNothingElse() {
        assertEquals("2026-03-20", NumericInput.normalizeDigits("٢٠٢٦-٠٣-٢٠"))
        // Only digits change: the Arabic decimal separator stays for the caller to interpret.
        assertEquals("1234٫5", NumericInput.normalizeDigits("۱۲۳۴٫۵"))
        assertEquals("abc-", NumericInput.normalizeDigits("abc-"))
    }
}
