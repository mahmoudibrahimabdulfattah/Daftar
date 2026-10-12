package com.mk.daftar.feature.banks

import com.mk.daftar.core.model.Decimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BankValidationTest {

    @Test
    fun namesAreTrimmedNonblankAndAtMostOneHundredTwentyCodePoints() {
        assertEquals(BankNameError.Required, bankNameError(""))
        assertEquals(BankNameError.Required, bankNameError("   "))
        assertNull(bankNameError("بنك مصر"))
        assertNull(bankNameError("a".repeat(120)))
        assertEquals(BankNameError.TooLong, bankNameError("a".repeat(121)))
    }

    @Test
    fun balancesAcceptArabicIndicDigitsAndGroupingWithAtMostTwoDecimals() {
        assertEquals("3000", parseBalance("٣٬٠٠٠")!!.toPlainString())
        assertEquals("1234.56", parseBalance("1,234.56")!!.toPlainString())
        assertEquals("0", parseBalance("0")!!.toPlainString())
        assertEquals("0.5", parseBalance("٠٫٥")!!.toPlainString())
    }

    @Test
    fun balancesRejectSignsExponentsMalformedTextAndExtraPrecisionWithoutRounding() {
        listOf("-1", "+1", "1e3", "1.234", "1..2", "1,23", "", "١.٢٣٤", "abc").forEach { text ->
            assertNull(parseBalance(text), "rejects '$text'")
        }
    }

    @Test
    fun blankBalanceIsRequiredAndMalformedIsInvalid() {
        assertEquals(AccountBalanceError.Required, balanceError("  "))
        assertEquals(AccountBalanceError.Invalid, balanceError("1.234"))
        assertNull(balanceError("0"))
    }

    @Test
    fun aDraftBalanceNormalizesToItsCanonicalMeaning() {
        assertEquals("3000.5", normalizedBalanceText("٣٬٠٠٠٫٥"))
        assertEquals(Decimal.parseCanonical("3000.5").toPlainString(), normalizedBalanceText("3,000.50"))
    }

    @Test
    fun blankNotesAreNoNoteAndNotesOverTheLimitAreRejected() {
        assertNull(normalizedNote("   "))
        assertEquals("ملاحظة", normalizedNote("  ملاحظة  "))
        assertEquals(true, noteTooLong("a".repeat(4_001)))
        assertEquals(false, noteTooLong("a".repeat(4_000)))
    }
}
