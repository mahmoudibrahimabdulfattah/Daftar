package com.mk.daftar.core.database.migration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrozenBankIdentitiesTest {

    @Test
    fun everyFrozenNameMatchesItsOwnInstitutionExactly() {
        assertEquals("eg.nbe", FrozenBankIdentities.uniqueMatch("National Bank of Egypt"))
        assertEquals("eg.nbe", FrozenBankIdentities.uniqueMatch("البنك الأهلي المصري"))
        assertEquals("eg.banque-misr", FrozenBankIdentities.uniqueMatch("Banque Misr"))
        assertEquals("eg.banque-misr", FrozenBankIdentities.uniqueMatch("BM"))
        assertEquals("eg.cib", FrozenBankIdentities.uniqueMatch("CIB"))
        assertEquals("eg.qnb", FrozenBankIdentities.uniqueMatch("QNB Egypt"))
        assertEquals("eg.banque-du-caire", FrozenBankIdentities.uniqueMatch("Banque du Caire"))
        assertEquals("eg.aaib", FrozenBankIdentities.uniqueMatch("AAIB"))
        assertEquals("eg.hsbc", FrozenBankIdentities.uniqueMatch("HSBC Egypt"))
        assertEquals("eg.alexbank", FrozenBankIdentities.uniqueMatch("Alex Bank"))
        assertEquals("eg.faisal", FrozenBankIdentities.uniqueMatch("Faisal"))
        assertEquals("eg.adib", FrozenBankIdentities.uniqueMatch("ADIB"))
        assertEquals("eg.emirates-nbd", FrozenBankIdentities.uniqueMatch("ENBD"))
        assertEquals("eg.credit-agricole", FrozenBankIdentities.uniqueMatch("Crédit Agricole Egypt"))
        assertEquals("eg.bank-abc", FrozenBankIdentities.uniqueMatch("Arab Banking Corporation"))
        assertEquals("eg.egebank", FrozenBankIdentities.uniqueMatch("EGBANK"))
    }

    @Test
    fun theSharedArabicAlefLamAlahliIsAmbiguousAndStaysUnmatched() {
        assertNull(FrozenBankIdentities.uniqueMatch("الأهلي"))
    }

    @Test
    fun matchingIgnoresCaseDiacriticsTatweelAndHarmlessWhitespace() {
        assertEquals("eg.cib", FrozenBankIdentities.uniqueMatch("  cib  "))
        assertEquals("eg.nbe", FrozenBankIdentities.uniqueMatch("national   bank of EGYPT"))
        assertEquals("eg.nbe", FrozenBankIdentities.uniqueMatch("الْبَنْكُ الأَهْلِيُّ الـمِصْرِيُّ"))
    }

    @Test
    fun substringsAndFuzzyNamesAreNeverMatched() {
        assertNull(FrozenBankIdentities.uniqueMatch("National"))
        assertNull(FrozenBankIdentities.uniqueMatch("Bank of Egypt"))
        assertNull(FrozenBankIdentities.uniqueMatch("My CIB account"))
        assertNull(FrozenBankIdentities.uniqueMatch("Misr"))
        assertNull(FrozenBankIdentities.uniqueMatch("Egypt"))
        assertNull(FrozenBankIdentities.uniqueMatch("بنك"))
    }

    @Test
    fun customNamesStayUnmatched() {
        assertNull(FrozenBankIdentities.uniqueMatch("My savings bank"))
        assertNull(FrozenBankIdentities.uniqueMatch(""))
    }
}
