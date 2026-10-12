package com.mk.daftar.feature.prices

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.refresh.PriceRefreshController
import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.repository.BankAccountInput
import com.mk.daftar.core.domain.repository.BankDetail
import com.mk.daftar.core.domain.repository.BankInput
import com.mk.daftar.core.domain.repository.BankRepository
import com.mk.daftar.core.domain.repository.CashBalanceInput
import com.mk.daftar.core.domain.repository.CashRepository
import com.mk.daftar.core.domain.repository.GoldHoldingInput
import com.mk.daftar.core.domain.repository.GoldRepository
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
import com.mk.daftar.core.model.QuoteKey
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FakeSession : VaultSession {
    private val mutable = MutableStateFlow<VaultSessionState>(VaultSessionState.Locked)
    override val state: StateFlow<VaultSessionState> = mutable.asStateFlow()

    fun open(generation: Long) {
        mutable.value = VaultSessionState.Open(generation)
    }

    fun set(next: VaultSessionState) {
        mutable.value = next
    }

    override suspend fun lock() {
        mutable.value = VaultSessionState.Locked
    }
}

internal object FixedClock : Clock {
    override fun now(): Instant = FIXTURE_NOW
}

internal class FakeGold : GoldRepository {
    val data = MutableStateFlow<VaultRead<List<Stored<GoldHolding>>>>(VaultRead.Locked)
    override fun observeAll(): Flow<VaultRead<List<Stored<GoldHolding>>>> = data
    override fun observe(id: GoldHoldingId): Flow<VaultRead<Stored<GoldHolding>?>> = error("unused")
    override suspend fun create(input: GoldHoldingInput): Outcome<Stored<GoldHolding>, StorageError> = error("unused")
    override suspend fun update(
        id: GoldHoldingId,
        expectedRevision: Long,
        input: GoldHoldingInput,
    ): Outcome<Stored<GoldHolding>, StorageError> = error("unused")
    override suspend fun delete(id: GoldHoldingId, expectedRevision: Long): Outcome<Unit, StorageError> =
        error("unused")
}

internal class FakeBanks : BankRepository {
    val banks = MutableStateFlow<VaultRead<List<Stored<Bank>>>>(VaultRead.Locked)
    val accounts = MutableStateFlow<VaultRead<List<Stored<BankAccount>>>>(VaultRead.Locked)
    override fun observeAll(): Flow<VaultRead<List<Stored<Bank>>>> = banks
    override fun observeAccounts(): Flow<VaultRead<List<Stored<BankAccount>>>> = accounts
    override fun observe(id: BankId): Flow<VaultRead<Stored<Bank>?>> = error("unused")
    override fun observeDetail(id: BankId): Flow<VaultRead<BankDetail?>> = error("unused")
    override fun observeAccount(id: BankAccountId): Flow<VaultRead<Stored<BankAccount>?>> = error("unused")
    override suspend fun create(input: BankInput): Outcome<Stored<Bank>, StorageError> = error("unused")
    override suspend fun update(
        id: BankId,
        expectedRevision: Long,
        input: BankInput,
    ): Outcome<Stored<Bank>, StorageError> = error("unused")
    override suspend fun delete(id: BankId, expectedRevision: Long): Outcome<Unit, StorageError> = error("unused")
    override suspend fun createAccount(input: BankAccountInput): Outcome<Stored<BankAccount>, StorageError> =
        error("unused")
    override suspend fun updateAccount(
        id: BankAccountId,
        expectedRevision: Long,
        input: BankAccountInput,
    ): Outcome<Stored<BankAccount>, StorageError> = error("unused")
    override suspend fun deleteAccount(id: BankAccountId, expectedRevision: Long): Outcome<Unit, StorageError> =
        error("unused")
}

internal class FakeCash : CashRepository {
    val data = MutableStateFlow<VaultRead<List<Stored<CashBalance>>>>(VaultRead.Locked)
    override fun observeAll(): Flow<VaultRead<List<Stored<CashBalance>>>> = data
    override fun observe(id: CashBalanceId): Flow<VaultRead<Stored<CashBalance>?>> = error("unused")
    override suspend fun create(input: CashBalanceInput): Outcome<Stored<CashBalance>, StorageError> = error("unused")
    override suspend fun update(
        id: CashBalanceId,
        expectedRevision: Long,
        input: CashBalanceInput,
    ): Outcome<Stored<CashBalance>, StorageError> = error("unused")
    override suspend fun delete(id: CashBalanceId, expectedRevision: Long): Outcome<Unit, StorageError> =
        error("unused")
}

/**
 * The price repository as a test drives it: the snapshot is set directly, every write is recorded with the revision it
 * was given, and each write's outcome can be chosen. A write can be held open to test cancellation.
 */
internal class FakePrices : PriceRepository {
    val quotes = MutableStateFlow<VaultRead<QuoteSnapshot>>(VaultRead.Locked)
    var refreshResult: Outcome<RefreshReport, StorageError> = Outcome.Success(RefreshReport(emptyMap(), emptyMap()))
    var setResult: Outcome<Unit, StorageError> = Outcome.Success(Unit)
    var removeResult: Outcome<Unit, StorageError> = Outcome.Success(Unit)
    var deleteResult: Outcome<Unit, StorageError> = Outcome.Success(Unit)

    /** When set, the next write waits on it before answering. */
    var holdWrites: CompletableDeferred<Unit>? = null

    val setCalls = mutableListOf<Pair<ManualQuoteInput, Long?>>()
    val removeCalls = mutableListOf<Pair<QuoteKey, Long>>()
    val deleteCalls = mutableListOf<Pair<QuoteKey, Long>>()
    var refreshCalls = 0

    override fun observeQuotes(): Flow<VaultRead<QuoteSnapshot>> = quotes

    override suspend fun refresh(): Outcome<RefreshReport, StorageError> {
        refreshCalls++
        return refreshResult
    }

    override suspend fun setManual(input: ManualQuoteInput, expectedRevision: Long?): Outcome<Unit, StorageError> {
        setCalls += input to expectedRevision
        holdWrites?.await()
        return setResult
    }

    override suspend fun removeOverride(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> {
        removeCalls += key to expectedRevision
        holdWrites?.await()
        return removeResult
    }

    override suspend fun deleteManual(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> {
        deleteCalls += key to expectedRevision
        holdWrites?.await()
        return deleteResult
    }
}

/** Records explicit refresh requests; the screens must only forward them. */
internal class FakeRefresh : PriceRefreshController {
    val current = MutableStateFlow<PriceRefreshStatus>(PriceRefreshStatus.Idle)
    override val status: StateFlow<PriceRefreshStatus> = current.asStateFlow()
    var refreshCalls = 0
    override fun start() = Unit
    override fun refresh() {
        refreshCalls++
    }
}

/** Every repository and flow the prices feature observes, wired to one session. */
internal class PricesRig {
    val session = FakeSession()
    val gold = FakeGold()
    val banks = FakeBanks()
    val cash = FakeCash()
    val prices = FakePrices()
    val refresh = FakeRefresh()

    /** Opens a generation and loads the repositories with empty holdings, so only the snapshot decides the board. */
    fun openEmpty(generation: Long = 1L) {
        session.open(generation)
        gold.data.value = VaultRead.Ready(generation, emptyList())
        banks.banks.value = VaultRead.Ready(generation, emptyList())
        banks.accounts.value = VaultRead.Ready(generation, emptyList())
        cash.data.value = VaultRead.Ready(generation, emptyList())
    }

    /** Publishes [snapshot] as the prices of [generation]. */
    fun publish(snapshot: QuoteSnapshot, generation: Long = 1L) {
        prices.quotes.value = VaultRead.Ready(generation, snapshot)
    }
}
