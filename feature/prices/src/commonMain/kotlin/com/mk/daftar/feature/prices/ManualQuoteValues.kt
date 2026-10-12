package com.mk.daftar.feature.prices

import com.mk.daftar.core.domain.repository.isManuallyEntered
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.USER_FRACTION_DIGITS

/** The quotes a user may enter by hand, in display order: sell prices by karat, then USD to EGP. */
val ENTERABLE_QUOTE_KEYS: List<QuoteKey> = listOf(
    QuoteKey.Gold(GoldKarat.K24, MerchantSide.SELLS_TO_CUSTOMER),
    QuoteKey.Gold(GoldKarat.K21, MerchantSide.SELLS_TO_CUSTOMER),
    QuoteKey.Gold(GoldKarat.K18, MerchantSide.SELLS_TO_CUSTOMER),
    QuoteKey.UsdEgp,
)

/** The route token of an enterable quote, or null for a key a user never enters (buy side). */
fun quoteKeyToken(key: QuoteKey): String? = when (key) {
    is QuoteKey.Gold -> if (key.isManuallyEntered()) "gold-${key.karat.carats}-sell" else null
    QuoteKey.UsdEgp -> "usd-egp"
}

/** The enterable quote a route token names. An unknown token is null, and is never read as another quote. */
fun quoteKeyOfToken(token: String): QuoteKey? = ENTERABLE_QUOTE_KEYS.firstOrNull { quoteKeyToken(it) == token }

/** Why a typed manual value cannot be used. [NOT_POSITIVE] covers zero and negative values alike. */
enum class ManualValueProblem { EMPTY, NOT_A_NUMBER, GROUPING, NOT_POSITIVE, TOO_PRECISE, TOO_LARGE, NOTE_TOO_LONG }

/** The result of reading a typed manual value. Parsing never goes through a floating-point type. */
sealed interface ManualValue {
    data class Valid(val value: Decimal) : ManualValue

    data class Invalid(val problem: ManualValueProblem) : ManualValue
}

private const val ARABIC_DECIMAL_SEPARATOR = '٫'
private const val ARABIC_THOUSANDS_SEPARATOR = '٬'

/** Directional marks that keyboards and pasted text carry; they are ignored, never counted as input. */
private val DIRECTIONAL_MARKS = setOf(
    '؜',
    '‎',
    '‏',
    '‪',
    '‫',
    '‬',
    '‭',
    '‮',
    '⁦',
    '⁧',
    '⁨',
    '⁩',
)

/** The longest note a manual quote carries, in UTF-16 units; well above what a person types. */
const val MAX_MANUAL_NOTE_LENGTH: Int = 500

/**
 * Reads a typed price. ASCII, Arabic-Indic and Persian digits are accepted; the Arabic decimal separator is a dot. Any
 * other separator, a space, or an exponent is rejected as ambiguous rather than guessed. The value must have at most
 * [USER_FRACTION_DIGITS] decimal places and be greater than zero.
 */
fun parseManualValue(raw: String): ManualValue {
    val text = raw.filterNot { it in DIRECTIONAL_MARKS }.trim()
    if (text.isEmpty()) return ManualValue.Invalid(ManualValueProblem.EMPTY)
    val body = StringBuilder()
    var dots = 0
    var negative = false
    for (char in text) {
        val digit = digitOf(char)
        when {
            digit != null -> body.append(digit)

            char == '.' || char == ARABIC_DECIMAL_SEPARATOR -> {
                dots++
                body.append('.')
            }

            char == ',' || char == ARABIC_THOUSANDS_SEPARATOR || char.isWhitespace() ->
                return ManualValue.Invalid(ManualValueProblem.GROUPING)

            char == '-' && body.isEmpty() && !negative -> negative = true

            else -> return ManualValue.Invalid(ManualValueProblem.NOT_A_NUMBER)
        }
    }
    if (dots > 1) return ManualValue.Invalid(ManualValueProblem.GROUPING)
    val digits = body.toString()
    if (digits.none { it in '0'..'9' }) return ManualValue.Invalid(ManualValueProblem.NOT_A_NUMBER)
    if (negative) return ManualValue.Invalid(ManualValueProblem.NOT_POSITIVE)
    val integer = digits.substringBefore('.').trimStart('0').ifEmpty { "0" }
    val fraction = if ('.' in digits) digits.substringAfter('.') else ""
    if (fraction.trimEnd('0').length > USER_FRACTION_DIGITS) return ManualValue.Invalid(ManualValueProblem.TOO_PRECISE)
    val canonical = if (fraction.isEmpty()) integer else "$integer.$fraction"
    val value = Decimal.parseCanonicalOrNull(canonical) ?: return ManualValue.Invalid(ManualValueProblem.TOO_LARGE)
    if (value <= Decimal.ZERO) return ManualValue.Invalid(ManualValueProblem.NOT_POSITIVE)
    return ManualValue.Valid(value)
}

/** The note as stored: trimmed, or null when blank. Length is checked separately with [isManualNoteTooLong]. */
fun normalizedManualNote(raw: String): String? = raw.trim().ifEmpty { null }

/** True when [raw] is too long to store as a note. */
fun isManualNoteTooLong(raw: String): Boolean = raw.trim().length > MAX_MANUAL_NOTE_LENGTH

private fun digitOf(char: Char): Char? = when (char) {
    in '0'..'9' -> char
    in '٠'..'٩' -> '0' + (char - '٠')
    in '۰'..'۹' -> '0' + (char - '۰')
    else -> null
}
