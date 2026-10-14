package com.mk.daftar.core.ui.presentation

import com.mk.daftar.core.model.Decimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FiguresTest {

    private fun d(text: String): Decimal = Decimal.parseCanonical(text)

    @Test
    fun moneyGroupsThousandsAndAlwaysShowsTwoPlaces() {
        assertEquals("1,076,212.74", d("1076212.74").moneyText())
        assertEquals("388,500.00", d("388500").moneyText())
        assertEquals("9,900.00", d("9900").moneyText())
        assertEquals("0.00", Decimal.ZERO.moneyText())
        assertEquals("48.50", d("48.5").moneyText())
        assertEquals("999.00", d("999").moneyText())
    }

    @Test
    fun moneyRoundsOnceHalfUpAtPresentation() {
        assertEquals("1.01", d("1.005").moneyText())
        assertEquals("1.00", d("1.004").moneyText())
        assertEquals("2,000.00", d("1999.999").moneyText())
    }

    @Test
    fun fixtureSplitTotalsSixtyThreeThirtySixAndOne() {
        val gold = d("677812.74")
        val banks = d("388500.00")
        val cash = d("9900.00")
        assertEquals(listOf(63, 36, 1), allocationPercents(listOf(gold, banks, cash)))
    }

    @Test
    fun percentagesAlwaysTotalOneHundredWhenTheTotalIsPositive() {
        val samples = listOf(
            listOf("1", "1", "1"),
            listOf("0.01", "0", "0.02"),
            listOf("3", "3", "3"),
            listOf("7", "0", "0"),
            listOf("123.45", "67.89", "0.66"),
        )
        for (sample in samples) {
            val percents = allocationPercents(sample.map(::d))
            assertEquals(100, percents.sum(), "sample $sample gave $percents")
            assertTrue(percents.all { it >= 0 })
        }
    }

    @Test
    fun equalPartsSplitWithTiesGoingToTheEarlierPart() {
        assertEquals(listOf(34, 33, 33), allocationPercents(listOf("1", "1", "1").map(::d)))
    }

    @Test
    fun zeroTotalGivesZeroPercentages() {
        assertEquals(listOf(0, 0), allocationPercents(listOf(Decimal.ZERO, Decimal.ZERO)))
        assertEquals(emptyList(), allocationPercents(emptyList()))
    }

    @Test
    fun negativePartsAreRejected() {
        assertFailsWith<IllegalArgumentException> { allocationPercents(listOf(d("-1"), d("2"))) }
    }

    @Test
    fun visibilityStartsVisibleTogglesAndKeepsTheChoiceOnLock() {
        val visibility = ValuesVisibility()
        assertTrue(visibility.visible.value)
        visibility.toggle()
        assertFalse(visibility.visible.value)
        visibility.toggle()
        assertTrue(visibility.visible.value)
        visibility.toggle()
        visibility.reset()
        assertFalse(visibility.visible.value)
    }
}
