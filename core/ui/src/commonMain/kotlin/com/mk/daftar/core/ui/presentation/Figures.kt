package com.mk.daftar.core.ui.presentation

import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.DecimalRounding

/**
 * Presentation of an exact amount: two places, HALF_UP, thousands grouped with commas and ASCII digits. Values are
 * rounded once, here; callers never format a sum of formatted values.
 */
fun Decimal.moneyText(): String {
    val rounded = roundToScale(2, DecimalRounding.HALF_UP).toPlainString()
    val negative = rounded.startsWith("-")
    val digits = rounded.removePrefix("-")
    val whole = digits.substringBefore('.')
    val fraction = digits.substringAfter('.', "").padEnd(2, '0')
    val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
    return (if (negative) "-" else "") + grouped + "." + fraction
}

/**
 * Whole-number percentages of [parts] of their exact sum, using the largest-remainder method so the results total
 * exactly 100. Parts must not be negative. Every step is exact: a part's whole percent is the largest k with
 * k·total ≤ 100·part, and remainders are compared as exact amounts, which share the total as denominator. Returns
 * zeros when the total is zero.
 */
fun allocationPercents(parts: List<Decimal>): List<Int> {
    require(parts.all { it >= Decimal.ZERO }) { "allocation parts must not be negative" }
    val total = parts.fold(Decimal.ZERO) { sum, part -> sum + part }
    if (total == Decimal.ZERO) return parts.map { 0 }
    val hundred = Decimal.fromLong(100)
    val scaled = parts.map { it * hundred }
    val floors = scaled.map { amount ->
        var whole = 0
        while (whole < 100 && Decimal.fromLong((whole + 1).toLong()) * total <= amount) whole++
        whole
    }
    val remainders = scaled.mapIndexed { index, amount -> amount - Decimal.fromLong(floors[index].toLong()) * total }
    val leftover = 100 - floors.sum()
    val receivers = remainders.indices
        .sortedWith { a, b ->
            val byRemainder = remainders[b].compareTo(remainders[a])
            if (byRemainder != 0) byRemainder else a.compareTo(b)
        }
        .take(leftover)
        .toSet()
    return floors.mapIndexed { index, whole -> if (index in receivers) whole + 1 else whole }
}
