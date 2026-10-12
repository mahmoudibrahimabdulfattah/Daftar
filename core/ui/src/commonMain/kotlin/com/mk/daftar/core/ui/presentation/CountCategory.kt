package com.mk.daftar.core.ui.presentation

/**
 * The Arabic count categories, as the plural table defines them: zero, one, two, few (3 to 10, modulo 100), many (11 to
 * 99, modulo 100) and other. English uses only zero, one and the plural, which the same categories cover.
 */
enum class CountCategory { ZERO, ONE, TWO, FEW, MANY, OTHER }

/** The category of an integer count. Counts are never negative. Exact 0, 1 and 2 come first; then the modulo rules. */
fun countCategoryOf(count: Int): CountCategory = when {
    count == 0 -> CountCategory.ZERO
    count == 1 -> CountCategory.ONE
    count == 2 -> CountCategory.TWO
    count % 100 in 3..10 -> CountCategory.FEW
    count % 100 in 11..99 -> CountCategory.MANY
    else -> CountCategory.OTHER
}
