package com.mk.daftar.core.model

/**
 * The limits that journal and editor input must meet, exposed so screens can validate before a record is built. The
 * record constructors enforce the same rules and stay authoritative: nothing here creates IDs or timestamps.
 */
object EntryLimits {
    /** Longest name or title, in Unicode code points. */
    const val NAME_CODE_POINTS: Int = MAX_NAME_CODE_POINTS

    /** Longest optional note, in Unicode code points, after trimming. */
    const val NOTE_CODE_POINTS: Int = MAX_NOTE_CODE_POINTS

    /** Longest personal-note body, in Unicode code points. */
    const val NOTE_BODY_CODE_POINTS: Int = MAX_NOTE_BODY_CODE_POINTS

    /** Most fractional digits a payment amount may carry. */
    const val PAYMENT_FRACTION_DIGITS: Int = USER_FRACTION_DIGITS

    /** Unicode code points in [text]. A surrogate pair counts once. */
    fun codePoints(text: String): Int = codePointCount(text)

    /** A name or title is valid when it is nonblank after trimming and within [NAME_CODE_POINTS]. */
    fun isValidName(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isNotEmpty() && codePointCount(trimmed) <= NAME_CODE_POINTS
    }

    /** An optional note is valid within [NOTE_CODE_POINTS] after trimming. Blank is valid and means no note. */
    fun isValidNote(text: String): Boolean = codePointCount(text.trim()) <= NOTE_CODE_POINTS

    /** A personal-note body is valid when it has text and fits [NOTE_BODY_CODE_POINTS]. Its line breaks are kept. */
    fun isValidBody(text: String): Boolean = text.isNotBlank() && codePointCount(text) <= NOTE_BODY_CODE_POINTS

    /** A zakat payment is strictly positive with at most [PAYMENT_FRACTION_DIGITS] fractional digits. */
    fun isValidPayment(amount: Decimal): Boolean =
        amount > Decimal.ZERO && amount.fractionDigits <= PAYMENT_FRACTION_DIGITS

    /** A recorded balance is zero or positive with at most [PAYMENT_FRACTION_DIGITS] fractional digits. */
    fun isValidBalance(amount: Decimal): Boolean =
        amount >= Decimal.ZERO && amount.fractionDigits <= PAYMENT_FRACTION_DIGITS
}
