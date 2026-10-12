package com.mk.daftar.core.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode

/**
 * Exact base-10 number with unlimited precision for money, weights and prices.
 *
 * Equality and hashing follow the numeric value, so `1`, `1.0` and `1.00` are the same [Decimal].
 * There is no `Float`/`Double` constructor or conversion; values enter through [parseCanonical] or [fromLong].
 */
class Decimal private constructor(private val value: BigDecimal) : Comparable<Decimal> {

    /** Canonical text: ASCII digits, a dot, no grouping, no exponent, no trailing fractional zeros. */
    private val canonical: String = canonicalPlainString(value.toPlainString())

    /** Count of digits after the decimal point in canonical form. */
    val fractionDigits: Int
        get() = canonical.substringAfter('.', "").length

    operator fun plus(other: Decimal): Decimal = Decimal(value + other.value)

    operator fun minus(other: Decimal): Decimal = Decimal(value - other.value)

    operator fun times(other: Decimal): Decimal = Decimal(value * other.value)

    /** Rounds to [scale] fractional digits. Arithmetic never rounds implicitly; call this only at display or storage boundaries. */
    fun roundToScale(scale: Int, mode: DecimalRounding): Decimal {
        require(scale in 0..MAX_FRACTION_DIGITS) { "scale must be within 0..$MAX_FRACTION_DIGITS, was $scale" }
        return Decimal(value.roundToDigitPositionAfterDecimalPoint(scale.toLong(), mode.toBigNum()))
    }

    fun toPlainString(): String = canonical

    override fun compareTo(other: Decimal): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean = other is Decimal && canonical == other.canonical

    override fun hashCode(): Int = canonical.hashCode()

    override fun toString(): String = canonical

    companion object {
        /** Longest accepted external numeric text, counted in characters. */
        const val MAX_INPUT_LENGTH = 64

        /** Largest accepted integer digit count for external numeric input. */
        const val MAX_INTEGER_DIGITS = 18

        /** Largest accepted fractional digit count for external numeric input. */
        const val MAX_FRACTION_DIGITS = 12

        private val CANONICAL = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")

        val ZERO: Decimal = Decimal(BigDecimal.ZERO)

        val ONE: Decimal = Decimal(BigDecimal.ONE)

        fun fromLong(value: Long): Decimal = Decimal(BigDecimal.fromLong(value))

        /**
         * Parses canonical decimal text such as `7068.5` or `-3`. Rejects grouping, exponents, signs other than a
         * single leading minus, and inputs beyond [MAX_INPUT_LENGTH], [MAX_INTEGER_DIGITS] or [MAX_FRACTION_DIGITS].
         */
        fun parseCanonical(text: String): Decimal {
            val reason = canonicalFailure(text)
            require(reason == null) { "not canonical decimal text: $reason" }
            return Decimal(BigDecimal.parseString(text))
        }

        /** Like [parseCanonical], returning null instead of throwing. */
        fun parseCanonicalOrNull(text: String): Decimal? =
            if (canonicalFailure(text) == null) Decimal(BigDecimal.parseString(text)) else null

        private fun canonicalFailure(text: String): String? {
            if (text.length > MAX_INPUT_LENGTH) return "longer than $MAX_INPUT_LENGTH characters"
            if (!CANONICAL.matches(text)) return "not an ASCII decimal"
            val canonicalText = canonicalPlainString(text)
            val integerDigits = canonicalText.removePrefix("-").substringBefore('.').length
            if (integerDigits > MAX_INTEGER_DIGITS) return "more than $MAX_INTEGER_DIGITS integer digits"
            val fractionDigits = canonicalText.substringAfter('.', "").length
            if (fractionDigits > MAX_FRACTION_DIGITS) return "more than $MAX_FRACTION_DIGITS fractional digits"
            return null
        }

        private fun canonicalPlainString(plain: String): String {
            val negative = plain.startsWith('-')
            val unsigned = plain.removePrefix("-")
            val integerPart = unsigned.substringBefore('.').trimStart('0').ifEmpty { "0" }
            val fractionPart = unsigned.substringAfter('.', "").trimEnd('0')
            if (integerPart == "0" && fractionPart.isEmpty()) return "0"
            val body = if (fractionPart.isEmpty()) integerPart else "$integerPart.$fractionPart"
            return if (negative) "-$body" else body
        }
    }
}

/** Rounding modes for the explicit [Decimal.roundToScale] boundary. Ties go away from zero for [HALF_UP]. */
enum class DecimalRounding {
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    DOWN,
    UP,
}

private fun DecimalRounding.toBigNum(): RoundingMode = when (this) {
    DecimalRounding.HALF_UP -> RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
    DecimalRounding.HALF_DOWN -> RoundingMode.ROUND_HALF_TOWARDS_ZERO
    DecimalRounding.HALF_EVEN -> RoundingMode.ROUND_HALF_TO_EVEN
    DecimalRounding.DOWN -> RoundingMode.TOWARDS_ZERO
    DecimalRounding.UP -> RoundingMode.AWAY_FROM_ZERO
}
