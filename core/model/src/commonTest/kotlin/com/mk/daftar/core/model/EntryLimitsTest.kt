package com.mk.daftar.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntryLimitsTest {

    @Test
    fun namesAreTrimmedNonblankAndCountedInCodePoints() {
        assertTrue(EntryLimits.isValidName("  Loan  "))
        assertFalse(EntryLimits.isValidName(" \t "))
        assertTrue(EntryLimits.isValidName("😀".repeat(120)))
        assertFalse(EntryLimits.isValidName("😀".repeat(121)))
        assertEquals(2, EntryLimits.codePoints("😀😁"))
    }

    @Test
    fun notesAreOptionalWithinTheirLimit() {
        assertTrue(EntryLimits.isValidNote(""))
        assertTrue(EntryLimits.isValidNote("a".repeat(4_000)))
        assertFalse(EntryLimits.isValidNote("a".repeat(4_001)))
    }

    @Test
    fun bodiesNeedTextAndKeepTheirLimit() {
        assertFalse(EntryLimits.isValidBody("\n  \n"))
        assertTrue(EntryLimits.isValidBody("سطر\n\nسطر"))
        assertTrue(EntryLimits.isValidBody("a".repeat(10_000)))
        assertFalse(EntryLimits.isValidBody("a".repeat(10_001)))
    }

    @Test
    fun paymentsArePositiveWithAtMostTwoDecimals() {
        assertFalse(EntryLimits.isValidPayment(Decimal.ZERO))
        assertFalse(EntryLimits.isValidPayment(Decimal.parseCanonical("-1")))
        assertFalse(EntryLimits.isValidPayment(Decimal.parseCanonical("1.234")))
        assertTrue(EntryLimits.isValidPayment(Decimal.parseCanonical("0.01")))
        assertTrue(EntryLimits.isValidPayment(Decimal.parseCanonical("10000.5")))
    }
}
