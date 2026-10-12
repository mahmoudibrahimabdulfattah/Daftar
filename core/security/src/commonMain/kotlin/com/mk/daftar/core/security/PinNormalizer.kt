package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

/** Turns PIN text typed in any supported numeral script into an owned ASCII digit buffer. */
object PinNormalizer {
    const val MIN_DIGITS = 6
    const val MAX_DIGITS = 16

    /**
     * Maps ASCII, Arabic-Indic (U+0660 to U+0669) and Persian (U+06F0 to U+06F9) digits to ASCII and keeps leading
     * zeros. Any other character, or a length outside 6 to 16 digits, is rejected. The caller's text is not wiped,
     * because the platform cannot zero a String. The returned buffer is owned by the caller.
     */
    fun normalize(text: CharSequence): Outcome<SecretBytes, SecurityError> {
        if (text.length > MAX_DIGITS) return Outcome.Failure(SecurityError.InvalidPinFormat)
        val out = ByteArray(text.length)
        for (index in 0 until text.length) {
            val value = digitValue(text[index])
            if (value < 0) {
                out.fill(0)
                return Outcome.Failure(SecurityError.InvalidPinFormat)
            }
            out[index] = ('0'.code + value).toByte()
        }
        if (!isValidDigits(out)) {
            out.fill(0)
            return Outcome.Failure(SecurityError.InvalidPinFormat)
        }
        return Outcome.Success(SecretBytes.owned(out))
    }

    /** True when [pin] holds only ASCII digits and its length is within bounds. */
    internal fun isValidDigits(pin: ByteArray): Boolean {
        if (pin.size !in MIN_DIGITS..MAX_DIGITS) return false
        return pin.all { it.toInt() in ASCII_ZERO..ASCII_NINE }
    }

    private fun digitValue(ch: Char): Int = when (ch) {
        in '0'..'9' -> ch - '0'
        in '٠'..'٩' -> ch - '٠'
        in '۰'..'۹' -> ch - '۰'
        else -> -1
    }

    private const val ASCII_ZERO = 0x30
    private const val ASCII_NINE = 0x39
}
