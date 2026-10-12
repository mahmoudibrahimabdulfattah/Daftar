package com.mk.daftar.core.common

/** Why numeric text could not be normalized. Inputs are rejected, never truncated or rounded. */
enum class NumericInputFailure {
    Empty,
    TooLong,
    Malformed,
    MixedSeparators,
    Grouping,
    Sign,
    Exponent,
    IntegerDigits,
    FractionDigits,
}

/** Which notations a numeric source may use, and how much precision it may carry. */
data class NumericInputPolicy(
    val allowGrouping: Boolean,
    val allowSign: Boolean,
    val allowExponent: Boolean,
    val maxFractionDigits: Int,
    val maxIntegerDigits: Int = 18,
    val maxLength: Int = 64,
) {
    companion object {
        /** Money and weight typed by the user: grouped digits allowed, no sign or exponent, two decimals. */
        val USER_ENTRY = NumericInputPolicy(
            allowGrouping = true,
            allowSign = false,
            allowExponent = false,
            maxFractionDigits = 2,
        )

        /** Prices read from visible markup: grouped digits allowed, plain notation, twelve decimals. */
        val REMOTE_MARKUP = NumericInputPolicy(
            allowGrouping = true,
            allowSign = false,
            allowExponent = false,
            maxFractionDigits = 12,
        )

        /** JSON number tokens: plain or exponent notation, no grouping, twelve decimals. */
        val REMOTE_JSON = NumericInputPolicy(
            allowGrouping = false,
            allowSign = false,
            allowExponent = true,
            maxFractionDigits = 12,
        )
    }
}

/**
 * Turns numeric text from users or providers into canonical ASCII decimal text (`1234.5`).
 *
 * Accepts ASCII, Arabic-Indic and Persian digits, and the Arabic decimal (U+066B) and group (U+066C) separators
 * alongside `.` and `,`. Returns canonical text without grouping, exponent, or trailing fractional zeros.
 * Pure string work only; the result still needs exact decimal parsing in `core:model`.
 */
object NumericInput {

    fun normalize(raw: String, policy: NumericInputPolicy): Outcome<String, NumericInputFailure> {
        val text = raw.filterNot { it in INVISIBLE }.trim()
        if (text.isEmpty()) return Outcome.Failure(NumericInputFailure.Empty)
        if (text.length > policy.maxLength) return Outcome.Failure(NumericInputFailure.TooLong)

        val mapped = text.map { digitOrSelf(it) }.joinToString("")
        val signChar = mapped.firstOrNull()?.takeIf { it == '+' || it == '-' || it == '−' }
        if (signChar != null && !policy.allowSign) return Outcome.Failure(NumericInputFailure.Sign)
        val negative = signChar == '-' || signChar == '−'
        val unsigned = if (signChar != null) mapped.substring(1) else mapped

        val exponentIndex = unsigned.indexOfFirst { it == 'e' || it == 'E' }
        val mantissa = if (exponentIndex >= 0) unsigned.substring(0, exponentIndex) else unsigned
        val exponentPart = if (exponentIndex >= 0) unsigned.substring(exponentIndex + 1) else null
        if (exponentPart != null && !policy.allowExponent) return Outcome.Failure(NumericInputFailure.Exponent)

        val exponent = exponentPart?.let { parseExponent(it) ?: return Outcome.Failure(NumericInputFailure.Malformed) }
            ?: 0

        val parts = when (val split = splitDecimal(mantissa, policy.allowGrouping)) {
            is Outcome.Failure -> return Outcome.Failure(split.error)
            is Outcome.Success -> split.data
        }
        val (integerDigits, fractionDigits) = shift(parts.first, parts.second, exponent)

        val integer = integerDigits.trimStart('0')
        if (integer.length > policy.maxIntegerDigits) return Outcome.Failure(NumericInputFailure.IntegerDigits)
        val fraction = fractionDigits.trimEnd('0')
        if (fraction.length > policy.maxFractionDigits) return Outcome.Failure(NumericInputFailure.FractionDigits)

        val body = when {
            integer.isEmpty() && fraction.isEmpty() -> "0"
            fraction.isEmpty() -> integer
            integer.isEmpty() -> "0.$fraction"
            else -> "$integer.$fraction"
        }
        val isZero = body == "0"
        return Outcome.Success(if (negative && !isZero) "-$body" else body)
    }

    /** Splits the unsigned mantissa into integer and fraction digits, validating separators and grouping. */
    private fun splitDecimal(text: String, allowGrouping: Boolean): Outcome<Pair<String, String>, NumericInputFailure> {
        if (text.isEmpty()) return Outcome.Failure(NumericInputFailure.Malformed)
        val hasDotDecimal = '.' in text
        val hasArabicDecimal = '٫' in text
        if (hasDotDecimal && hasArabicDecimal) return Outcome.Failure(NumericInputFailure.MixedSeparators)
        val hasComma = ',' in text
        val hasArabicGroup = '٬' in text
        if (hasComma && hasArabicGroup) return Outcome.Failure(NumericInputFailure.MixedSeparators)
        if ((hasComma || hasArabicGroup) && !allowGrouping) return Outcome.Failure(NumericInputFailure.Malformed)

        val decimalMarkers = text.indices.filter { text[it] == '.' || text[it] == '٫' }
        if (decimalMarkers.size > 1) return Outcome.Failure(NumericInputFailure.Malformed)
        val decimalIndex = decimalMarkers.firstOrNull() ?: -1
        val integerText = if (decimalIndex >= 0) text.substring(0, decimalIndex) else text
        val fractionText = if (decimalIndex >= 0) text.substring(decimalIndex + 1) else ""

        if (!fractionText.all { it in '0'..'9' }) return Outcome.Failure(NumericInputFailure.Malformed)
        if (decimalIndex >= 0 && fractionText.isEmpty()) return Outcome.Failure(NumericInputFailure.Malformed)
        if (integerText.isEmpty() && fractionText.isEmpty()) return Outcome.Failure(NumericInputFailure.Malformed)

        val groupChar = when {
            hasComma -> ','
            hasArabicGroup -> '٬'
            else -> null
        }
        val integerDigits = if (groupChar == null) {
            integerText
        } else {
            val groups = integerText.split(groupChar)
            val first = groups.first()
            val validGroups = first.length in 1..3 && groups.drop(1).all { it.length == 3 }
            if (!validGroups || groups.any { it.isEmpty() }) return Outcome.Failure(NumericInputFailure.Grouping)
            groups.joinToString("")
        }
        if (!integerDigits.all { it in '0'..'9' }) return Outcome.Failure(NumericInputFailure.Malformed)
        return Outcome.Success(integerDigits to fractionText)
    }

    /** Moves the decimal point of `integer.fraction` by [exponent] places, exactly. */
    private fun shift(integer: String, fraction: String, exponent: Int): Pair<String, String> {
        if (exponent == 0) return integer to fraction
        val digits = integer + fraction
        val point = integer.length + exponent
        return when {
            point <= 0 -> "" to "0".repeat(-point) + digits
            point >= digits.length -> digits + "0".repeat(point - digits.length) to ""
            else -> digits.substring(0, point) to digits.substring(point)
        }
    }

    private fun parseExponent(text: String): Int? {
        val match = EXPONENT.matchEntire(text) ?: return null
        return match.value.toInt()
    }

    /** Replaces Arabic-Indic and Persian digits with ASCII digits. Every other character is left as it is. */
    fun normalizeDigits(raw: String): String = raw.map { digitOrSelf(it) }.joinToString("")

    private fun digitOrSelf(ch: Char): Char = when (ch) {
        in '٠'..'٩' -> '0' + (ch - '٠')
        in '۰'..'۹' -> '0' + (ch - '۰')
        else -> ch
    }

    private val EXPONENT = Regex("[+-]?[0-9]{1,3}")

    private val INVISIBLE = setOf('‎', '‏', '؜', '﻿', '⁦', '⁧', '⁨', '⁩')
}
