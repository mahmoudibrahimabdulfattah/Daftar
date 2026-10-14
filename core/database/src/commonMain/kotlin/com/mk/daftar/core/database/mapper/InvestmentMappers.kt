package com.mk.daftar.core.database.mapper

import com.mk.daftar.core.database.entity.CashBalanceEntity
import com.mk.daftar.core.database.entity.InvestmentAccountEntity
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.model.InvestmentAccountId
import com.mk.daftar.core.model.Money
import kotlin.time.Instant

private const val INVESTMENTS = "investment_accounts"

/** The stored investment, validated by the model, with its revision. A row that fails validation is never repaired. */
fun InvestmentAccountEntity.toStored(): Stored<InvestmentAccount> {
    val account = rowGuard(INVESTMENTS) {
        InvestmentAccount(
            id = InvestmentAccountId(id),
            providerId = providerId?.let { InstitutionId.parseOrNull(it) ?: corrupt(INVESTMENTS, "provider_id") },
            name = name,
            amount = Money(canonicalDecimal(INVESTMENTS, "amount", amount), Currency.EGP),
            note = note,
            valueAsOf = instantOf(INVESTMENTS, "value_as_of", valueAsOfSeconds, valueAsOfNanos),
            convertedFromCashId = convertedFromCashId?.let { CashBalanceId(it) },
            createdAt = instantOf(INVESTMENTS, "created", createdSeconds, createdNanos),
            updatedAt = instantOf(INVESTMENTS, "updated", updatedSeconds, updatedNanos),
        )
    }
    return Stored(account, checkRevision(INVESTMENTS, revision))
}

fun InvestmentAccount.toEntity(revision: Long): InvestmentAccountEntity = InvestmentAccountEntity(
    id = id.value,
    createdSeconds = createdAt.epochSeconds,
    createdNanos = createdAt.nanosecondsOfSecond,
    updatedSeconds = updatedAt.epochSeconds,
    updatedNanos = updatedAt.nanosecondsOfSecond,
    revision = revision,
    providerId = providerId?.value,
    name = name,
    amount = amount.amount.toPlainString(),
    valueAsOfSeconds = valueAsOf.epochSeconds,
    valueAsOfNanos = valueAsOf.nanosecondsOfSecond,
    note = note,
    convertedFromCashId = convertedFromCashId?.value,
)

/** The conversion request as the transaction receives it. Times are supplied by the caller, never read here. */
data class CashConversionRequest(
    val sourceId: String,
    val expectedRevision: Long,
    val draftId: String,
    val providerId: InstitutionId?,
    val valueAsOf: Instant,
    val now: Instant,
)

/** The outcome of building the investment row from a source cash row, before anything is written. */
sealed interface ConvertedRow {
    data class Built(val row: InvestmentAccountEntity) : ConvertedRow

    data object UnsupportedCurrency : ConvertedRow

    data object AmountOutOfRange : ConvertedRow
}

/**
 * Builds the investment that a cash entry becomes. The name, amount and note are copied exactly, the source currency
 * must be EGP, and the copied amount must fit the investment bounds. Nothing is rounded or clamped.
 */
fun CashBalanceEntity.toConvertedInvestment(request: CashConversionRequest): ConvertedRow {
    val cash = toStored().value
    if (cash.balance.currency != Currency.EGP) return ConvertedRow.UnsupportedCurrency
    if (!InvestmentAccount.isValidAmount(cash.balance.amount)) return ConvertedRow.AmountOutOfRange
    val account = InvestmentAccount.create(
        id = InvestmentAccountId(request.draftId),
        providerId = request.providerId,
        name = cash.name,
        amount = Money(cash.balance.amount, Currency.EGP),
        note = cash.note,
        valueAsOf = request.valueAsOf,
        convertedFromCashId = cash.id,
        createdAt = request.now,
        updatedAt = request.now,
    )
    return ConvertedRow.Built(account.toEntity(revision = 1L))
}
