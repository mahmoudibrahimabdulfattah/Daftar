package com.mk.daftar.core.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Something the user owns and that counts towards their wealth. Values are derived, never stored. */
sealed interface Asset

/** A record that sits in the notebook's journal rather than in the wealth totals. */
sealed interface JournalEntry

/** Gold the user holds, in one karat and form. Its value comes from valuation, not from this record. */
data class GoldHolding(
    val id: GoldHoldingId,
    val karat: GoldKarat,
    val form: GoldForm,
    val weight: GoldWeight,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) : Asset {
    init {
        requireOrdered(createdAt, updatedAt)
        note?.let { requireTrimmed(it, "note") }
        note?.let { require(codePointCount(it) <= MAX_NOTE_CODE_POINTS) { "note too long" } }
    }

    companion object {
        fun create(
            id: GoldHoldingId,
            karat: GoldKarat,
            form: GoldForm,
            weight: GoldWeight,
            note: String?,
            createdAt: Instant,
            updatedAt: Instant,
        ): GoldHolding = GoldHolding(id, karat, form, weight, normalizeNote(note), createdAt, updatedAt)
    }
}

/**
 * A bank the user keeps accounts with. A container, not an asset.
 *
 * [institutionId] links the bank to a catalog institution, or is null for a custom or not-yet-identified bank.
 * [identityReviewed] records that the user made the identity choice explicitly, so automatic matching never
 * revisits it.
 * The display [name] is always the user's own and never changes with the identity.
 */
data class Bank(
    val id: BankId,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val institutionId: InstitutionId? = null,
    val identityReviewed: Boolean = false,
) {
    init {
        requireOrdered(createdAt, updatedAt)
        requireTrimmed(name, "name")
        require(name.isNotEmpty() && codePointCount(name) <= MAX_NAME_CODE_POINTS) { "invalid bank name" }
    }

    companion object {
        fun create(
            id: BankId,
            name: String,
            createdAt: Instant,
            updatedAt: Instant,
            institutionId: InstitutionId? = null,
            identityReviewed: Boolean = false,
        ): Bank = Bank(id, requireName(name, "name"), createdAt, updatedAt, institutionId, identityReviewed)
    }
}

/** A bank account in its original currency. The balance is nonnegative with at most two decimals. */
data class BankAccount(
    val id: BankAccountId,
    val bankId: BankId,
    val name: String,
    val balance: Money,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) : Asset {
    init {
        requireOrdered(createdAt, updatedAt)
        requireTrimmed(name, "name")
        require(name.isNotEmpty() && codePointCount(name) <= MAX_NAME_CODE_POINTS) { "invalid account name" }
        requireUserMoney(balance, "balance")
        note?.let { requireTrimmed(it, "note") }
    }

    companion object {
        fun create(
            id: BankAccountId,
            bankId: BankId,
            name: String,
            balance: Money,
            note: String?,
            createdAt: Instant,
            updatedAt: Instant,
        ): BankAccount = BankAccount(
            id = id,
            bankId = bankId,
            name = requireName(name, "name"),
            balance = balance,
            note = normalizeNote(note),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

enum class CashKind { CASH, WALLET }

/** Cash on hand or in a mobile wallet. The balance is nonnegative with at most two decimals. */
data class CashBalance(
    val id: CashBalanceId,
    val name: String,
    val kind: CashKind,
    val balance: Money,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) : Asset {
    init {
        requireOrdered(createdAt, updatedAt)
        requireTrimmed(name, "name")
        require(name.isNotEmpty() && codePointCount(name) <= MAX_NAME_CODE_POINTS) { "invalid cash name" }
        requireUserMoney(balance, "balance")
        note?.let { requireTrimmed(it, "note") }
    }

    companion object {
        fun create(
            id: CashBalanceId,
            name: String,
            kind: CashKind,
            balance: Money,
            note: String?,
            createdAt: Instant,
            updatedAt: Instant,
        ): CashBalance = CashBalance(
            id = id,
            name = requireName(name, "name"),
            kind = kind,
            balance = balance,
            note = normalizeNote(note),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

/**
 * A zakat payment made in Egyptian pounds. Notes and payments are never assets. [forHijriYear] links the payment to a
 * declared year; null means unassigned. A payment never changes the year's declaration.
 */
data class ZakatPayment(
    val id: ZakatPaymentId,
    val paidOn: LocalDate,
    val amount: Money,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val forHijriYear: Int? = null,
) : JournalEntry {
    init {
        requireOrdered(createdAt, updatedAt)
        require(amount.currency == Currency.EGP) { "zakat payments are recorded in EGP" }
        require(amount.amount > Decimal.ZERO) { "zakat payment must be positive" }
        require(amount.amount.fractionDigits <= USER_FRACTION_DIGITS) { "zakat payment allows at most two decimals" }
        note?.let { requireTrimmed(it, "note") }
        forHijriYear?.let {
            require(it in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR) { "linked Hijri year is outside the supported range" }
        }
    }

    companion object {
        fun create(
            id: ZakatPaymentId,
            paidOn: LocalDate,
            amount: Money,
            note: String?,
            createdAt: Instant,
            updatedAt: Instant,
            forHijriYear: Int? = null,
        ): ZakatPayment = ZakatPayment(id, paidOn, amount, normalizeNote(note), createdAt, updatedAt, forHijriYear)
    }
}

/** A free-form personal note with a title and body. */
data class PersonalNote(
    val id: NoteId,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) : JournalEntry {
    init {
        requireOrdered(createdAt, updatedAt)
        requireTrimmed(title, "title")
        require(title.isNotEmpty() && codePointCount(title) <= MAX_NAME_CODE_POINTS) { "invalid note title" }
        require(body.isNotBlank()) { "note body must not be blank" }
        require(codePointCount(body) <= MAX_NOTE_BODY_CODE_POINTS) { "note body exceeds $MAX_NOTE_BODY_CODE_POINTS" }
    }

    companion object {
        fun create(id: NoteId, title: String, body: String, createdAt: Instant, updatedAt: Instant): PersonalNote {
            require(body.isNotBlank()) { "note body must not be blank" }
            return PersonalNote(id, requireName(title, "title"), body, createdAt, updatedAt)
        }
    }
}

private fun requireUserMoney(money: Money, field: String) {
    require(money.amount >= Decimal.ZERO) { "$field must not be negative" }
    require(money.amount.fractionDigits <= USER_FRACTION_DIGITS) { "$field allows at most two decimals" }
}
