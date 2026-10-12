package com.mk.daftar.core.ui.presentation

/** The quantity forms that Arabic and English count nouns use. English uses [ONE] and [OTHER] only. */
enum class QuantityForm { ONE, TWO, FEW, OTHER }

/** The form for [count]: 1, 2, 3–10 (modulo 100), then everything else. Counts are never negative. */
fun quantityFormOf(count: Int): QuantityForm = when {
    count == 1 -> QuantityForm.ONE
    count == 2 -> QuantityForm.TWO
    count % 100 in 3..10 -> QuantityForm.FEW
    else -> QuantityForm.OTHER
}
