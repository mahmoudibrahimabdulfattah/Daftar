package com.mk.daftar.core.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * One broker account the user tracks with a single manually maintained EGP value: the total account value the user
 * chooses to track, never a list of holdings, prices or a profit figure.
 *
 * [providerId] is a catalog broker ID, or null for Other. Unknown well-formed IDs are kept. [valueAsOf] is the
 * effective time of [amount], stored separately from the record's [updatedAt]. [convertedFromCashId] is the source
 * cash entry when this account was created by a user-confirmed conversion; it is never reused for another record.
 */
data class InvestmentAccount(
    val id: InvestmentAccountId,
    val providerId: InstitutionId?,
    val name: String,
    val amount: Money,
    val note: String?,
    val valueAsOf: Instant,
    val convertedFromCashId: CashBalanceId?,
    val createdAt: Instant,
    val updatedAt: Instant,
) : Asset {
    init {
        requireOrdered(createdAt, updatedAt)
        requireTrimmed(name, "name")
        require(name.isNotEmpty() && codePointCount(name) <= MAX_NAME_CODE_POINTS) { "invalid investment name" }
        require(amount.currency == Currency.EGP) { "investment values are recorded in EGP" }
        require(isValidAmount(amount.amount)) { "investment value is outside the allowed range" }
        note?.let { requireTrimmed(it, "note") }
        note?.let { require(codePointCount(it) <= MAX_NOTE_CODE_POINTS) { "note too long" } }
        convertedFromCashId?.let { require(it.value.isNotBlank()) { "converted source id must not be blank" } }
    }

    companion object {
        /** The largest value one account can hold: 999,999,999,999.99 EGP. */
        val MAX_AMOUNT: Decimal = Decimal.parseCanonical("999999999999.99")

        /** The earliest effective date the app accepts for a newly entered value: 1 January 1900, Cairo time. */
        val EARLIEST_VALUE_AS_OF: Instant =
            LocalDateTime(1900, 1, 1, 0, 0).toInstant(TimeZone.of("Africa/Cairo"))

        /** A value is in range when it is zero or positive, at most [MAX_AMOUNT], and has at most two decimals. */
        fun isValidAmount(amount: Decimal): Boolean =
            amount >= Decimal.ZERO && amount <= MAX_AMOUNT && amount.fractionDigits <= USER_FRACTION_DIGITS

        fun create(
            id: InvestmentAccountId,
            providerId: InstitutionId?,
            name: String,
            amount: Money,
            note: String?,
            valueAsOf: Instant,
            convertedFromCashId: CashBalanceId?,
            createdAt: Instant,
            updatedAt: Instant,
        ): InvestmentAccount = InvestmentAccount(
            id = id,
            providerId = providerId,
            name = requireName(name, "name"),
            amount = amount,
            note = normalizeNote(note),
            valueAsOf = valueAsOf,
            convertedFromCashId = convertedFromCashId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
