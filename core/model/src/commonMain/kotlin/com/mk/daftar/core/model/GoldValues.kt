package com.mk.daftar.core.model

enum class GoldKarat(val carats: Int) {
    K24(24),
    K21(21),
    K18(18),
}

enum class GoldForm { BAR, JEWELRY }

/** Gold mass in grams. Strictly positive; user entries carry at most two fractional digits. */
data class GoldWeight(val grams: Decimal) {
    init {
        require(grams > Decimal.ZERO) { "gold weight must be positive" }
        require(grams.fractionDigits <= USER_FRACTION_DIGITS) { "gold weight allows at most two decimals" }
    }
}

/** Egyptian pounds per gram of gold at one karat. Strictly positive. */
data class GoldPrice(val egpPerGram: Decimal) {
    init {
        require(egpPerGram > Decimal.ZERO) { "gold price must be positive" }
    }
}

/** Egyptian pounds per US dollar. Strictly positive. */
data class FxRate(val egpPerUsd: Decimal) {
    init {
        require(egpPerUsd > Decimal.ZERO) { "exchange rate must be positive" }
    }
}

/** User-entered money and weight precision: two fractional digits. */
const val USER_FRACTION_DIGITS = 2
