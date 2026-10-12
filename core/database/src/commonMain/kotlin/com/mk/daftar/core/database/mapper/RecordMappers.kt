package com.mk.daftar.core.database.mapper

import com.mk.daftar.core.database.dao.BankDetailRow
import com.mk.daftar.core.database.entity.BankAccountEntity
import com.mk.daftar.core.database.entity.BankEntity
import com.mk.daftar.core.database.entity.CashBalanceEntity
import com.mk.daftar.core.database.entity.GoldHoldingEntity
import com.mk.daftar.core.database.entity.PersonalNoteEntity
import com.mk.daftar.core.database.entity.ZakatPaymentEntity
import com.mk.daftar.core.domain.repository.BankDetail
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldWeight
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId

/*
 * Entity <-> model mappers for the six record tables. Reads validate every column and reject a row rather than
 * repairing it. Writes take the model, which already passed its factories, and the revision the caller owns.
 */

private const val GOLD = "gold_holdings"
private const val BANKS = "banks"
private const val ACCOUNTS = "bank_accounts"
private const val CASH = "cash_balances"
private const val ZAKAT = "zakat_payments"
private const val NOTES = "personal_notes"

fun GoldHoldingEntity.toStored(): Stored<GoldHolding> {
    val holding = rowGuard(GOLD) {
        GoldHolding(
            id = GoldHoldingId(id),
            karat = enumOf<GoldKarat>(GOLD, "karat", karat),
            form = enumOf<GoldForm>(GOLD, "form", form),
            weight = GoldWeight(canonicalDecimal(GOLD, "weight_grams", weightGrams)),
            note = note,
            createdAt = instantOf(GOLD, "created", createdSeconds, createdNanos),
            updatedAt = instantOf(GOLD, "updated", updatedSeconds, updatedNanos),
        )
    }
    return Stored(holding, checkRevision(GOLD, revision))
}

fun GoldHolding.toEntity(revision: Long): GoldHoldingEntity = GoldHoldingEntity(
    id = id.value,
    createdSeconds = createdAt.epochSeconds,
    createdNanos = createdAt.nanosecondsOfSecond,
    updatedSeconds = updatedAt.epochSeconds,
    updatedNanos = updatedAt.nanosecondsOfSecond,
    revision = revision,
    karat = karat.name,
    form = form.name,
    weightGrams = weight.grams.toPlainString(),
    note = note,
)

fun BankEntity.toStored(): Stored<Bank> {
    val bank = rowGuard(BANKS) {
        Bank(
            id = BankId(id),
            name = name,
            createdAt = instantOf(BANKS, "created", createdSeconds, createdNanos),
            updatedAt = instantOf(BANKS, "updated", updatedSeconds, updatedNanos),
        )
    }
    return Stored(bank, checkRevision(BANKS, revision))
}

fun Bank.toEntity(revision: Long): BankEntity = BankEntity(
    id = id.value,
    createdSeconds = createdAt.epochSeconds,
    createdNanos = createdAt.nanosecondsOfSecond,
    updatedSeconds = updatedAt.epochSeconds,
    updatedNanos = updatedAt.nanosecondsOfSecond,
    revision = revision,
    name = name,
)

fun BankAccountEntity.toStored(): Stored<BankAccount> {
    val account = rowGuard(ACCOUNTS) {
        BankAccount(
            id = BankAccountId(id),
            bankId = BankId(bankId),
            name = name,
            balance = moneyOf(ACCOUNTS, balance, currency),
            note = note,
            createdAt = instantOf(ACCOUNTS, "created", createdSeconds, createdNanos),
            updatedAt = instantOf(ACCOUNTS, "updated", updatedSeconds, updatedNanos),
        )
    }
    return Stored(account, checkRevision(ACCOUNTS, revision))
}

fun BankAccount.toEntity(revision: Long): BankAccountEntity = BankAccountEntity(
    id = id.value,
    createdSeconds = createdAt.epochSeconds,
    createdNanos = createdAt.nanosecondsOfSecond,
    updatedSeconds = updatedAt.epochSeconds,
    updatedNanos = updatedAt.nanosecondsOfSecond,
    revision = revision,
    bankId = bankId.value,
    name = name,
    balance = balance.amount.toPlainString(),
    currency = balance.currency.name,
    note = note,
)

fun CashBalanceEntity.toStored(): Stored<CashBalance> {
    val cash = rowGuard(CASH) {
        CashBalance(
            id = CashBalanceId(id),
            name = name,
            kind = enumOf<CashKind>(CASH, "kind", kind),
            balance = moneyOf(CASH, balance, currency),
            note = note,
            createdAt = instantOf(CASH, "created", createdSeconds, createdNanos),
            updatedAt = instantOf(CASH, "updated", updatedSeconds, updatedNanos),
        )
    }
    return Stored(cash, checkRevision(CASH, revision))
}

fun CashBalance.toEntity(revision: Long): CashBalanceEntity = CashBalanceEntity(
    id = id.value,
    createdSeconds = createdAt.epochSeconds,
    createdNanos = createdAt.nanosecondsOfSecond,
    updatedSeconds = updatedAt.epochSeconds,
    updatedNanos = updatedAt.nanosecondsOfSecond,
    revision = revision,
    name = name,
    kind = kind.name,
    balance = balance.amount.toPlainString(),
    currency = balance.currency.name,
    note = note,
)

fun ZakatPaymentEntity.toStored(): Stored<ZakatPayment> {
    val payment = rowGuard(ZAKAT) {
        ZakatPayment(
            id = ZakatPaymentId(id),
            paidOn = localDateOf(ZAKAT, "paid_on", paidOn),
            amount = moneyOf(ZAKAT, amount, currency),
            note = note,
            createdAt = instantOf(ZAKAT, "created", createdSeconds, createdNanos),
            updatedAt = instantOf(ZAKAT, "updated", updatedSeconds, updatedNanos),
        )
    }
    return Stored(payment, checkRevision(ZAKAT, revision))
}

fun ZakatPayment.toEntity(revision: Long): ZakatPaymentEntity = ZakatPaymentEntity(
    id = id.value,
    createdSeconds = createdAt.epochSeconds,
    createdNanos = createdAt.nanosecondsOfSecond,
    updatedSeconds = updatedAt.epochSeconds,
    updatedNanos = updatedAt.nanosecondsOfSecond,
    revision = revision,
    paidOn = paidOn.toString(),
    amount = amount.amount.toPlainString(),
    currency = amount.currency.name,
    note = note,
)

fun PersonalNoteEntity.toStored(): Stored<PersonalNote> {
    val note = rowGuard(NOTES) {
        PersonalNote(
            id = NoteId(id),
            title = title,
            body = body,
            createdAt = instantOf(NOTES, "created", createdSeconds, createdNanos),
            updatedAt = instantOf(NOTES, "updated", updatedSeconds, updatedNanos),
        )
    }
    return Stored(note, checkRevision(NOTES, revision))
}

fun PersonalNote.toEntity(revision: Long): PersonalNoteEntity = PersonalNoteEntity(
    id = id.value,
    createdSeconds = createdAt.epochSeconds,
    createdNanos = createdAt.nanosecondsOfSecond,
    updatedSeconds = updatedAt.epochSeconds,
    updatedNanos = updatedAt.nanosecondsOfSecond,
    revision = revision,
    title = title,
    body = body,
)

/** The bank and its accounts from one observed statement, or null when the bank does not exist. */
fun List<BankDetailRow>.toBankDetail(): BankDetail? {
    val first = firstOrNull() ?: return null
    return BankDetail(
        bank = first.bank.toStored(),
        accounts = mapNotNull { row -> row.account?.toStored() },
    )
}

private fun moneyOf(table: String, amount: String, currency: String): Money = Money(
    amount = canonicalDecimal(table, "amount", amount),
    currency = enumOf<Currency>(table, "currency", currency),
)
