package com.mk.daftar.core.database

import com.mk.daftar.core.database.entity.BankAccountEntity
import com.mk.daftar.core.database.entity.BankEntity
import com.mk.daftar.core.database.entity.CashBalanceEntity
import com.mk.daftar.core.database.entity.GoldHoldingEntity
import com.mk.daftar.core.database.entity.PersonalNoteEntity
import com.mk.daftar.core.database.entity.ZakatPaymentEntity
import com.mk.daftar.core.database.mapper.CorruptRowException
import com.mk.daftar.core.database.mapper.toEntity
import com.mk.daftar.core.database.mapper.toStored
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class RecordMappersTest {

    private val created = Instant.fromEpochSeconds(1_700_000_000, 111_111_111)
    private val updated = Instant.fromEpochSeconds(1_700_000_500, 999_999_999)

    @Test
    fun goldHoldingRoundTripsWithExactWeightAndRevision() {
        val holding = GoldHolding(
            id = GoldHoldingId("gold-1"),
            karat = GoldKarat.K21,
            form = GoldForm.JEWELRY,
            weight = GoldWeight(Decimal.parseCanonical("12.5")),
            note = "ring",
            createdAt = created,
            updatedAt = updated,
        )
        val entity = holding.toEntity(revision = 3)
        assertEquals("12.5", entity.weightGrams)
        assertEquals("JEWELRY", entity.form)
        val stored = entity.toStored()
        assertEquals(holding, stored.value)
        assertEquals(3L, stored.revision)
    }

    @Test
    fun bankAndAccountRoundTrip() {
        val bank = Bank(BankId("bank-1"), "Bank A", created, updated)
        assertEquals(bank, bank.toEntity(1).toStored().value)
        val account = BankAccount(
            id = BankAccountId("acc-1"),
            bankId = BankId("bank-1"),
            name = "Savings",
            balance = Money(Decimal.parseCanonical("1000.25"), Currency.EGP),
            note = null,
            createdAt = created,
            updatedAt = updated,
        )
        val entity = account.toEntity(2)
        assertEquals("1000.25", entity.balance)
        assertEquals("EGP", entity.currency)
        assertEquals(account, entity.toStored().value)
    }

    @Test
    fun cashZakatAndNoteRoundTrip() {
        val cash = CashBalance(
            CashBalanceId("cash-1"),
            "Wallet",
            CashKind.WALLET,
            Money(Decimal.parseCanonical("0.5"), Currency.USD),
            null,
            created,
            updated,
        )
        assertEquals(cash, cash.toEntity(1).toStored().value)

        val zakat = ZakatPayment(
            id = ZakatPaymentId("z-1"),
            paidOn = LocalDate(2026, 3, 1),
            amount = Money(Decimal.parseCanonical("250"), Currency.EGP),
            note = "Ramadan",
            createdAt = created,
            updatedAt = updated,
        )
        val zakatEntity = zakat.toEntity(1)
        assertEquals("2026-03-01", zakatEntity.paidOn)
        assertEquals(zakat, zakatEntity.toStored().value)

        val note = PersonalNote(NoteId("n-1"), "Title", "Body text", created, updated)
        assertEquals(note, note.toEntity(1).toStored().value)
    }

    @Test
    fun corruptGoldRowsAreRejectedNotRepaired() {
        val valid = GoldHolding(
            GoldHoldingId("g"),
            GoldKarat.K24,
            GoldForm.BAR,
            GoldWeight(Decimal.ONE),
            null,
            created,
            updated,
        ).toEntity(1)
        assertFailsWith<CorruptRowException> { valid.copy(karat = "K22").toStored() }
        assertFailsWith<CorruptRowException> { valid.copy(form = "COIN").toStored() }
        assertFailsWith<CorruptRowException> { valid.copy(weightGrams = "1.50").toStored() }
        assertFailsWith<CorruptRowException> { valid.copy(weightGrams = "0").toStored() }
        assertFailsWith<CorruptRowException> { valid.copy(updatedNanos = 1_000_000_000).toStored() }
        assertFailsWith<CorruptRowException> { valid.copy(revision = 0).toStored() }
        assertFailsWith<CorruptRowException> { valid.copy(id = " ").toStored() }
        assertFailsWith<CorruptRowException> { valid.copy(note = " untrimmed ").toStored() }
    }

    @Test
    fun updatedBeforeCreatedIsCorrupt() {
        val valid = BankEntity("b", 10, 0, 9, 0, 1, "Bank")
        assertFailsWith<CorruptRowException> { valid.toStored() }
    }

    @Test
    fun corruptAccountAndCashRowsAreRejected() {
        val account = BankAccountEntity(
            id = "a", createdSeconds = 1, createdNanos = 0, updatedSeconds = 1, updatedNanos = 0, revision = 1,
            bankId = "b", name = "Acc", balance = "-5", currency = "EGP", note = null,
        )
        assertFailsWith<CorruptRowException> { account.toStored() }
        assertFailsWith<CorruptRowException> { account.copy(balance = "5", currency = "EUR").toStored() }

        val cash = CashBalanceEntity(
            id = "c", createdSeconds = 1, createdNanos = 0, updatedSeconds = 1, updatedNanos = 0, revision = 1,
            name = "Cash", kind = "BANK", balance = "1", currency = "EGP", note = null,
        )
        assertFailsWith<CorruptRowException> { cash.toStored() }
    }

    @Test
    fun corruptZakatAndNoteRowsAreRejected() {
        val zakat = ZakatPaymentEntity(
            id = "z", createdSeconds = 1, createdNanos = 0, updatedSeconds = 1, updatedNanos = 0, revision = 1,
            paidOn = "2026-03-01", amount = "10", currency = "USD", note = null,
        )
        assertFailsWith<CorruptRowException> { zakat.toStored() }
        assertFailsWith<CorruptRowException> { zakat.copy(currency = "EGP", paidOn = "2026-3-1").toStored() }
        assertFailsWith<CorruptRowException> { zakat.copy(currency = "EGP", amount = "0").toStored() }

        val note = PersonalNoteEntity(
            id = "n",
            createdSeconds = 1,
            createdNanos = 0,
            updatedSeconds = 1,
            updatedNanos = 0,
            revision = 1,
            title = " padded",
            body = "text",
        )
        assertFailsWith<CorruptRowException> { note.toStored() }
        assertFailsWith<CorruptRowException> { note.copy(title = "ok", body = "   ").toStored() }
    }
}
