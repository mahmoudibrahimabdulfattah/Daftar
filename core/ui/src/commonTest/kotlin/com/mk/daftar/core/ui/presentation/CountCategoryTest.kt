package com.mk.daftar.core.ui.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class CountCategoryTest {

    @Test
    fun zeroOneAndTwoAreExact() {
        assertEquals(CountCategory.ZERO, countCategoryOf(0))
        assertEquals(CountCategory.ONE, countCategoryOf(1))
        assertEquals(CountCategory.TWO, countCategoryOf(2))
    }

    @Test
    fun threeToTenAreFew() {
        assertEquals(CountCategory.FEW, countCategoryOf(3))
        assertEquals(CountCategory.FEW, countCategoryOf(10))
        assertEquals(CountCategory.FEW, countCategoryOf(103))
    }

    @Test
    fun elevenToNinetyNineAreMany() {
        assertEquals(CountCategory.MANY, countCategoryOf(11))
        assertEquals(CountCategory.MANY, countCategoryOf(99))
        assertEquals(CountCategory.MANY, countCategoryOf(111))
    }

    @Test
    fun hundredsAndOtherwiseAreOther() {
        assertEquals(CountCategory.OTHER, countCategoryOf(100))
        assertEquals(CountCategory.OTHER, countCategoryOf(101))
        assertEquals(CountCategory.OTHER, countCategoryOf(102))
        assertEquals(CountCategory.OTHER, countCategoryOf(1000))
    }

    @Test
    fun theTableBoundariesMatchTheDecision() {
        val expected = mapOf(
            0 to CountCategory.ZERO,
            1 to CountCategory.ONE,
            2 to CountCategory.TWO,
            3 to CountCategory.FEW,
            10 to CountCategory.FEW,
            11 to CountCategory.MANY,
            99 to CountCategory.MANY,
            100 to CountCategory.OTHER,
            101 to CountCategory.OTHER,
            103 to CountCategory.FEW,
        )
        for ((count, category) in expected) {
            assertEquals(category, countCategoryOf(count), "for $count")
        }
    }
}
