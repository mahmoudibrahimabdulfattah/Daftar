package com.mk.daftar.core.domain.testing

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.BankAccountInput
import com.mk.daftar.core.domain.repository.BankDetail
import com.mk.daftar.core.domain.repository.BankInput
import com.mk.daftar.core.domain.repository.BankRepository
import com.mk.daftar.core.domain.repository.CashBalanceInput
import com.mk.daftar.core.domain.repository.CashRepository
import com.mk.daftar.core.domain.repository.GoldHoldingInput
import com.mk.daftar.core.domain.repository.GoldRepository
import com.mk.daftar.core.domain.repository.InvestmentConversion
import com.mk.daftar.core.domain.repository.InvestmentConversionFailure
import com.mk.daftar.core.domain.repository.InvestmentInput
import com.mk.daftar.core.domain.repository.InvestmentRepository
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.domain.repository.PriceRepository
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RefreshReport
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.model.InvestmentAccountId
import com.mk.daftar.core.model.QuoteKey
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Unused members fail loudly, so a test never passes by accident on a path it did not mean to exercise. */
private fun unused(): Nothing = error("not used by this test")

internal class FakeSession(initial: VaultSessionState = VaultSessionState.Open(1)) : VaultSession {
    val current = MutableStateFlow(initial)

    override val state: StateFlow<VaultSessionState> = current

    override suspend fun lock() {
        current.value = VaultSessionState.Locked
    }
}

internal class FakeClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}

internal class FakeGoldRepository(
    val holdings: MutableStateFlow<VaultRead<List<Stored<GoldHolding>>>> = MutableStateFlow(VaultRead.Loading),
) : GoldRepository {
    val updates = mutableListOf<Triple<GoldHoldingId, Long, GoldHoldingInput>>()
    var updateResult: Outcome<Stored<GoldHolding>, StorageError> = Outcome.Failure(StorageError.Conflict)

    override fun observeAll(): Flow<VaultRead<List<Stored<GoldHolding>>>> = holdings

    override fun observe(id: GoldHoldingId): Flow<VaultRead<Stored<GoldHolding>?>> = unused()

    override suspend fun create(input: GoldHoldingInput): Outcome<Stored<GoldHolding>, StorageError> = unused()

    override suspend fun update(
        id: GoldHoldingId,
        expectedRevision: Long,
        input: GoldHoldingInput,
    ): Outcome<Stored<GoldHolding>, StorageError> {
        updates += Triple(id, expectedRevision, input)
        return updateResult
    }

    override suspend fun delete(id: GoldHoldingId, expectedRevision: Long): Outcome<Unit, StorageError> = unused()
}

internal class FakeBankRepository(
    val accounts: MutableStateFlow<VaultRead<List<Stored<BankAccount>>>> = MutableStateFlow(VaultRead.Loading),
) : BankRepository {
    var deleteResult: Outcome<Unit, StorageError> = Outcome.Failure(StorageError.BankHasAccounts)
    val deletes = mutableListOf<Pair<BankId, Long>>()

    override fun observeAccounts(): Flow<VaultRead<List<Stored<BankAccount>>>> = accounts

    override fun observeAll(): Flow<VaultRead<List<Stored<Bank>>>> = unused()

    override fun observe(id: BankId): Flow<VaultRead<Stored<Bank>?>> = unused()

    override fun observeDetail(id: BankId): Flow<VaultRead<BankDetail?>> = unused()

    override fun observeAccount(id: BankAccountId): Flow<VaultRead<Stored<BankAccount>?>> = unused()

    override suspend fun create(input: BankInput): Outcome<Stored<Bank>, StorageError> = unused()

    override suspend fun update(
        id: BankId,
        expectedRevision: Long,
        input: BankInput,
    ): Outcome<Stored<Bank>, StorageError> = unused()

    override suspend fun delete(id: BankId, expectedRevision: Long): Outcome<Unit, StorageError> {
        deletes += id to expectedRevision
        return deleteResult
    }

    override suspend fun createAccount(input: BankAccountInput): Outcome<Stored<BankAccount>, StorageError> = unused()

    override suspend fun updateAccount(
        id: BankAccountId,
        expectedRevision: Long,
        input: BankAccountInput,
    ): Outcome<Stored<BankAccount>, StorageError> = unused()

    override suspend fun deleteAccount(id: BankAccountId, expectedRevision: Long): Outcome<Unit, StorageError> =
        unused()
}

internal class FakeCashRepository(
    val balances: MutableStateFlow<VaultRead<List<Stored<CashBalance>>>> = MutableStateFlow(VaultRead.Loading),
) : CashRepository {
    override fun observeAll(): Flow<VaultRead<List<Stored<CashBalance>>>> = balances

    override fun observe(id: CashBalanceId): Flow<VaultRead<Stored<CashBalance>?>> = unused()

    override suspend fun create(input: CashBalanceInput): Outcome<Stored<CashBalance>, StorageError> = unused()

    override suspend fun update(
        id: CashBalanceId,
        expectedRevision: Long,
        input: CashBalanceInput,
    ): Outcome<Stored<CashBalance>, StorageError> = unused()

    override suspend fun delete(id: CashBalanceId, expectedRevision: Long): Outcome<Unit, StorageError> = unused()
}

internal class FakeInvestmentRepository(
    val investments: MutableStateFlow<VaultRead<List<Stored<InvestmentAccount>>>> =
        MutableStateFlow(VaultRead.Loading),
) : InvestmentRepository {
    override fun observeAll(): Flow<VaultRead<List<Stored<InvestmentAccount>>>> = investments

    override fun observe(id: InvestmentAccountId): Flow<VaultRead<Stored<InvestmentAccount>?>> = unused()

    override suspend fun create(input: InvestmentInput): Outcome<Stored<InvestmentAccount>, StorageError> = unused()

    override suspend fun update(
        id: InvestmentAccountId,
        expectedRevision: Long,
        input: InvestmentInput,
    ): Outcome<Stored<InvestmentAccount>, StorageError> = unused()

    override suspend fun delete(id: InvestmentAccountId, expectedRevision: Long): Outcome<Unit, StorageError> = unused()

    override suspend fun convertCashToInvestment(
        sourceId: CashBalanceId,
        expectedRevision: Long,
        draftId: InvestmentAccountId,
        providerId: InstitutionId?,
        valueAsOf: Instant,
    ): Outcome<InvestmentConversion, InvestmentConversionFailure> = unused()
}

internal class FakePriceRepository(
    val quotes: MutableStateFlow<VaultRead<QuoteSnapshot>> = MutableStateFlow(VaultRead.Loading),
) : PriceRepository {
    var refreshResult: Outcome<RefreshReport, StorageError> = Outcome.Failure(StorageError.Io)
    val manualRevisions = mutableListOf<Long?>()
    var setManualResult: Outcome<Unit, StorageError> = Outcome.Success(Unit)
    val removed = mutableListOf<Pair<QuoteKey, Long>>()
    val deleted = mutableListOf<Pair<QuoteKey, Long>>()

    override fun observeQuotes(): Flow<VaultRead<QuoteSnapshot>> = quotes

    override suspend fun refresh(): Outcome<RefreshReport, StorageError> = refreshResult

    override suspend fun setManual(input: ManualQuoteInput, expectedRevision: Long?): Outcome<Unit, StorageError> {
        manualRevisions += expectedRevision
        return setManualResult
    }

    override suspend fun removeOverride(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> {
        removed += key to expectedRevision
        return Outcome.Success(Unit)
    }

    override suspend fun deleteManual(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> {
        deleted += key to expectedRevision
        return Outcome.Failure(StorageError.Conflict)
    }
}
