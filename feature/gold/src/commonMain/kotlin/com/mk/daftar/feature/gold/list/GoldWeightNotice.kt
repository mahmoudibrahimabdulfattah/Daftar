package com.mk.daftar.feature.gold.list

import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldKarat

/**
 * What the total-weight line says. The total is gross weight across the holdings. One karat states that karat; mixed
 * karats say the total is not pure gold, so no single fine-gold figure is ever implied.
 */
sealed interface GoldWeightNotice {
    /** The values are hidden, so the weight is not read out. */
    data object Hidden : GoldWeightNotice

    /** Every holding is the same karat. */
    data class Single(val totalWeight: Decimal, val karat: GoldKarat) : GoldWeightNotice

    /** The holdings span more than one karat. */
    data class Mixed(val totalWeight: Decimal) : GoldWeightNotice
}

/**
 * The notice for the list. It is null when there is no holding, since a weight of nothing in no karat needs no line.
 * [karats] holds the karat of each holding, in any order.
 */
fun goldWeightNoticeOf(hidden: Boolean, karats: List<GoldKarat>, totalWeight: Decimal): GoldWeightNotice? {
    if (hidden) return GoldWeightNotice.Hidden
    val distinct = karats.distinct()
    return when (distinct.size) {
        0 -> null
        1 -> GoldWeightNotice.Single(totalWeight, distinct.single())
        else -> GoldWeightNotice.Mixed(totalWeight)
    }
}
