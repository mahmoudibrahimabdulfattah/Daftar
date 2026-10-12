package com.mk.daftar.feature.banks

import com.mk.daftar.core.common.NumericInput
import com.mk.daftar.core.common.NumericInputPolicy
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.EntryLimits

/** A bank name is nonblank after trimming and within the model's limit. */
enum class BankNameError { Required, TooLong }

/** The name the model will store, or the reason it cannot. Repository factories still have the final word. */
internal fun bankNameError(raw: String): BankNameError? = when {
    raw.trim().isEmpty() -> BankNameError.Required
    !EntryLimits.isValidName(raw) -> BankNameError.TooLong
    else -> null
}

enum class AccountNameError { Required, TooLong }

internal fun accountNameError(raw: String): AccountNameError? = when {
    raw.trim().isEmpty() -> AccountNameError.Required
    !EntryLimits.isValidName(raw) -> AccountNameError.TooLong
    else -> null
}

enum class AccountBalanceError { Required, Invalid }

/**
 * The balance a draft describes: Arabic-Indic and Persian digits accepted, grouping allowed, signs and exponents rejected,
 * at most two decimals. Null while the text is blank or not a valid balance. Nothing is rounded or truncated.
 */
internal fun parseBalance(text: String): Decimal? {
    val canonical = (NumericInput.normalize(text, NumericInputPolicy.USER_ENTRY) as? Outcome.Success)?.data
        ?: return null
    val amount = Decimal.parseCanonicalOrNull(canonical) ?: return null
    return amount.takeIf { EntryLimits.isValidBalance(it) }
}

internal fun balanceError(text: String): AccountBalanceError? = when {
    text.isBlank() -> AccountBalanceError.Required
    parseBalance(text) == null -> AccountBalanceError.Invalid
    else -> null
}

/** A balance as the draft's normalized text, so two drafts with the same meaning compare equal. */
internal fun normalizedBalanceText(text: String): String = parseBalance(text)?.toPlainString() ?: text.trim()

/** An optional note: blank is no note; longer than the model's limit is an error. */
internal fun noteTooLong(text: String): Boolean = !EntryLimits.isValidNote(text)

internal fun normalizedNote(text: String): String? = text.trim().ifEmpty { null }
