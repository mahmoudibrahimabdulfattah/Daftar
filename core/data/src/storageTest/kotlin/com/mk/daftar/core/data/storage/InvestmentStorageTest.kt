package com.mk.daftar.core.data.storage

import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.repository.InvestmentRecordRepository
import com.mk.daftar.core.domain.repository.CashBalanceInput
import com.mk.daftar.core.domain.repository.InvestmentConversion
import com.mk.daftar.core.domain.repository.InvestmentConversionFailure
import com.mk.daftar.core.domain.repository.InvestmentInput
import com.mk.daftar.core.domain.repository.InvestmentRepository
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccountId
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.network.FxFetchFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Broker accounts through the production repository, over real Room and SQLCipher. Converting a cash entry moves it in
 * one transaction: the amount, name and note arrive exactly, the cash row is gone, a retry never converts twice, and a
 * refusal leaves the source as it was.
 */
class InvestmentStorageTest {

    private val thndr = InstitutionId("eg.thndr")

    private fun withVault(block: suspend (StorageFixture, InvestmentRepository) -> Unit) = runTest {
        val location = newTestLocation()
        val fixture = StorageFixture(
            location,
            FakeGold(Outcome.Failure(DataError.Transport)),
            FakeFx(Outcome.Failure(FxFetchFailure(emptyList()))),
        )
        try {
            block(fixture, InvestmentRecordRepository(fixture.host))
        } finally {
            fixture.engine.lock()
            location.remove()
        }
    }

    private suspend fun StorageFixture.open() = engine.unlock { Outcome.Success(key(1)) }

    private fun egp(amount: String) = Money(Decimal.parseCanonical(amount), Currency.EGP)

    private fun usd(amount: String) = Money(Decimal.parseCanonical(amount), Currency.USD)

    @Test
    fun anAccountIsStoredWithItsExactAmount() = withVault { f, investments ->
        f.open()
        val asOf = Clock.System.now()
        val stored = investments.create(
            InvestmentInput(thndr, "Thndr", Decimal.parseCanonical("50000.25"), "EGX", asOf),
        ).requireSuccess()

        val all = assertIs<VaultRead.Ready<*>>(investments.observeAll().first()).value as List<*>
        assertEquals(1, all.size)
        assertEquals("50000.25", stored.value.amount.amount.toPlainString())
        assertEquals(thndr, stored.value.providerId)
    }

    @Test
    fun convertingACashEntryMovesItWholeAndRemovesTheSource() = withVault { f, investments ->
        f.open()
        val cash = f.cash.create(CashBalanceInput("Thundr wallet", CashKind.WALLET, egp("7500.00"), "broker cash"))
            .requireSuccess()
        val draft = InvestmentAccountId("draft-1")

        val converted = investments.convertCashToInvestment(
            cash.value.id,
            cash.revision,
            draft,
            thndr,
            Clock.System.now(),
        ).requireSuccess()

        val investment = assertIs<InvestmentConversion.Converted>(converted).investment.value
        assertEquals("Thundr wallet", investment.name)
        assertEquals("broker cash", investment.note)
        assertEquals(egp("7500.00").amount, investment.amount.amount)
        assertEquals(cash.value.id, investment.convertedFromCashId)
        val remaining = assertIs<VaultRead.Ready<*>>(f.cash.observeAll().first()).value as List<*>
        assertEquals(0, remaining.size, "the cash entry leaves Cash & wallets, so nothing is counted twice")
    }

    @Test
    fun aRetriedConversionReportsAlreadyConvertedAndWritesNothing() = withVault { f, investments ->
        f.open()
        val cash = f.cash.create(CashBalanceInput("Wallet", CashKind.WALLET, egp("100.00"), null)).requireSuccess()
        val draft = InvestmentAccountId("draft-2")
        investments.convertCashToInvestment(cash.value.id, cash.revision, draft, thndr, Clock.System.now())
            .requireSuccess()

        val retry = investments.convertCashToInvestment(cash.value.id, cash.revision, draft, thndr, Clock.System.now())

        assertIs<InvestmentConversion.AlreadyConverted>(retry.requireSuccess())
        val all = assertIs<VaultRead.Ready<*>>(investments.observeAll().first()).value as List<*>
        assertEquals(1, all.size)
    }

    @Test
    fun aDollarEntryIsRefusedAndKept() = withVault { f, investments ->
        f.open()
        val cash = f.cash.create(CashBalanceInput("USD", CashKind.CASH, usd("300.00"), null)).requireSuccess()

        val outcome = investments.convertCashToInvestment(
            cash.value.id,
            cash.revision,
            InvestmentAccountId("draft-3"),
            thndr,
            Clock.System.now(),
        )

        assertEquals(
            InvestmentConversionFailure.UnsupportedCurrency,
            assertIs<Outcome.Failure<InvestmentConversionFailure>>(outcome).error,
        )
        val remaining = assertIs<VaultRead.Ready<*>>(f.cash.observeAll().first()).value as List<*>
        assertEquals(1, remaining.size)
    }

    @Test
    fun aStaleRevisionIsRefusedAndTheSourceIsUntouched() = withVault { f, investments ->
        f.open()
        val cash = f.cash.create(CashBalanceInput("Wallet", CashKind.WALLET, egp("10.00"), null)).requireSuccess()
        f.cash.update(cash.value.id, cash.revision, CashBalanceInput("Wallet", CashKind.WALLET, egp("20.00"), null))
            .requireSuccess()

        val outcome = investments.convertCashToInvestment(
            cash.value.id,
            cash.revision,
            InvestmentAccountId("draft-4"),
            thndr,
            Clock.System.now(),
        )

        assertEquals(
            InvestmentConversionFailure.SourceChanged,
            assertIs<Outcome.Failure<InvestmentConversionFailure>>(outcome).error,
        )
        val all = assertIs<VaultRead.Ready<*>>(investments.observeAll().first()).value as List<*>
        assertEquals(0, all.size)
    }
}
