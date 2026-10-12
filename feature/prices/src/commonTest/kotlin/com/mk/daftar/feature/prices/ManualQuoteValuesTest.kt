package com.mk.daftar.feature.prices

import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManualQuoteValuesTest {

    private fun valid(text: String) = (parseManualValue(text) as ManualValue.Valid).value

    private fun problem(text: String) = (parseManualValue(text) as ManualValue.Invalid).problem

    @Test
    fun asciiArabicIndicAndPersianDigitsReadTheSameValue() {
        assertEquals(dec("6185.5"), valid("6185.5"))
        assertEquals(dec("6185.5"), valid("٦١٨٥٫٥"))
        assertEquals(dec("6185.5"), valid("۶۱۸۵٫۵"))
        assertEquals(dec("6185.50"), valid("٦١٨٥.٥٠"))
    }

    @Test
    fun theArabicDecimalSeparatorIsADot() {
        assertEquals(dec("48.5"), valid("48٫5"))
        assertEquals(dec("0.5"), valid(".5"))
        assertEquals(dec("7"), valid("7."))
    }

    @Test
    fun directionalMarksAndOuterSpacesAreIgnored() {
        assertEquals(dec("6185"), valid("⁦ 6185 ⁩"))
        assertEquals(dec("6185"), valid("‏6185"))
    }

    @Test
    fun aGroupingSeparatorIsRejectedAsAmbiguousRatherThanGuessed() {
        assertEquals(ManualValueProblem.GROUPING, problem("6,185"))
        assertEquals(ManualValueProblem.GROUPING, problem("6٬185"))
        assertEquals(ManualValueProblem.GROUPING, problem("6 185"))
        assertEquals(ManualValueProblem.GROUPING, problem("6 185"))
        assertEquals(ManualValueProblem.GROUPING, problem("1.234.567"))
        assertEquals(ManualValueProblem.GROUPING, problem("6,5"))
    }

    @Test
    fun exponentsSignsAndLettersAreNotNumbers() {
        assertEquals(ManualValueProblem.NOT_A_NUMBER, problem("1e3"))
        assertEquals(ManualValueProblem.NOT_A_NUMBER, problem("1E3"))
        assertEquals(ManualValueProblem.NOT_A_NUMBER, problem("+5"))
        assertEquals(ManualValueProblem.NOT_A_NUMBER, problem("abc"))
        assertEquals(ManualValueProblem.NOT_A_NUMBER, problem("."))
        assertEquals(ManualValueProblem.NOT_A_NUMBER, problem("-"))
    }

    @Test
    fun emptyTextIsEmptyNotZero() {
        assertEquals(ManualValueProblem.EMPTY, problem(""))
        assertEquals(ManualValueProblem.EMPTY, problem("   "))
    }

    @Test
    fun zeroAndNegativeValuesAreNotPositive() {
        assertEquals(ManualValueProblem.NOT_POSITIVE, problem("0"))
        assertEquals(ManualValueProblem.NOT_POSITIVE, problem("0.00"))
        assertEquals(ManualValueProblem.NOT_POSITIVE, problem("-5"))
        assertEquals(ManualValueProblem.NOT_POSITIVE, problem("-0"))
    }

    @Test
    fun morePlacesThanTheSharedRuleAllowsAreRejected() {
        assertEquals(ManualValueProblem.TOO_PRECISE, problem("6185.555"))
        assertEquals(ManualValueProblem.TOO_PRECISE, problem("0.001"))
        assertEquals(dec("6185.5"), valid("6185.500"))
    }

    @Test
    fun valuesBeyondTheDecimalRangeAreTooLarge() {
        assertEquals(ManualValueProblem.TOO_LARGE, problem("1234567890123456789012"))
    }

    @Test
    fun aNoteIsTrimmedAndBlankMeansNone() {
        assertNull(normalizedManualNote("   "))
        assertEquals("Source: goldsmith", normalizedManualNote("  Source: goldsmith "))
        assertEquals(false, isManualNoteTooLong("x".repeat(MAX_MANUAL_NOTE_LENGTH)))
        assertEquals(true, isManualNoteTooLong("x".repeat(MAX_MANUAL_NOTE_LENGTH + 1)))
    }

    @Test
    fun onlyEnterableQuotesHaveTokensAndTheyRoundTrip() {
        assertEquals("gold-21-sell", quoteKeyToken(sellKey(GoldKarat.K21)))
        assertEquals("usd-egp", quoteKeyToken(QuoteKey.UsdEgp))
        assertNull(quoteKeyToken(buyKey(GoldKarat.K21)))
        ENTERABLE_QUOTE_KEYS.forEach { key ->
            assertEquals(key, quoteKeyOfToken(quoteKeyToken(key)!!))
        }
        assertNull(quoteKeyOfToken("gold-99-sell"))
        assertNull(quoteKeyOfToken("gold-24"))
        assertNull(quoteKeyOfToken(""))
    }

    @Test
    fun parsedValuesAreExactDecimals() {
        val value = valid("0.1")
        assertEquals(Decimal.parseCanonical("0.1"), value)
    }
}
