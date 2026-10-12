package com.mk.daftar.feature.cash.testing

import com.mk.daftar.core.common.Outcome
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
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
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
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.FxRate
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** The instant every fixture is created at. */
internal val NOW: Instant = Instant.parse("2026-10-12T09:40:00Z")

internal class FixedClock(private val instant: Instant = NOW) : Clock {
    override fun now(): Instant = instant
}

internal fun egp(text: String): Money = Money(Decimal.parseCanonical(text), Currency.EGP)

internal fun usd(text: String): Money = Money(Decimal.parseCanonical(text), Currency.USD)

internal fun balance(
    id: String,
    name: String,
    kind: CashKind = CashKind.CASH,
    money: Money = egp("0"),
    note: String? = null,
): CashBalance = CashBalance.create(CashBalanceId(id), name, kind, money, note, NOW, NOW)

/** A remote USD rate fetched now, as a refresh would have cached it. */
internal fun remoteUsd(rate: String): RemoteQuoteRecord {
    val provenance = QuoteProvenance.live(
        provider = QuoteProvider.EXCHANGE_RATE_API,
        sourceUrl = "https://open.er-api.com/v6/latest/USD",
        sourceTime = SourceTime.Unknown,
        fetchedAt = NOW,
    )
    return RemoteQuoteRecord(
        FxQuote(FxRate(Decimal.parseCanonical(rate)), provenance),
        nextUpdateAt = null,
        attributionUrl = "https://www.exchangerate-api.com/docs/free",
    )
}

/** A USD rate the user pinned by hand, so its valuation carries a manual warning. */
internal fun pinnedManualUsd(rate: String): Stored<PriceQuote> {
    val provenance = QuoteProvenance(
        provider = QuoteProvider.USER,
        sourceUrl = null,
        sourceTime = SourceTime.Unknown,
        fetchedAt = null,
        recordedAt = NOW,
        kind = QuoteKind.MANUAL,
        manualPinned = true,
        note = null,
    )
    return Stored(FxQuote(FxRate(Decimal.parseCanonical(rate)), provenance), revision = 1)
}

internal fun quoteSnapshot(
    vararg remote: RemoteQuoteRecord,
    manual: List<Stored<PriceQuote>> = emptyList(),
): QuoteSnapshot = QuoteSnapshot(remote = remote.toList(), manual = manual)

/** A session the test drives directly. Locking it returns to Locked, as the real session does. */
internal class FakeSession : VaultSession {
    private val mutable = MutableStateFlow<VaultSessionState>(VaultSessionState.Locked)
    override val state: StateFlow<VaultSessionState> = mutable.asStateFlow()

    fun set(next: VaultSessionState) {
        mutable.value = next
    }

    override suspend fun lock() {
        mutable.value = VaultSessionState.Locked
    }
}

/** Gold and banks hold nothing in these tests, so the portfolio's other categories are empty and complete. */
internal class EmptyGold : GoldRepository {
    val data = MutableStateFlow<VaultRead<List<Stored<GoldHolding>>>>(VaultRead.Ready(1, emptyList()))
    override fun observeAll(): Flow<VaultRead<List<Stored<GoldHolding>>>> = data
    override fun observe(id: GoldHoldingId): Flow<VaultRead<Stored<GoldHolding>?>> = unused()
    override suspend fun create(input: GoldHoldingInput): Outcome<Stored<GoldHolding>, StorageError> = unused()
    override suspend fun update(
        id: GoldHoldingId,
        expectedRevision: Long,
        input: GoldHoldingInput,
    ): Outcome<Stored<GoldHolding>, StorageError> = unused()
    override suspend fun delete(id: GoldHoldingId, expectedRevision: Long): Outcome<Unit, StorageError> = unused()
}

internal class EmptyBanks : BankRepository {
    private val accounts = MutableStateFlow<VaultRead<List<Stored<BankAccount>>>>(VaultRead.Ready(1, emptyList()))
    override fun observeAll(): Flow<VaultRead<List<Stored<Bank>>>> = unused()
    override fun observeAccounts(): Flow<VaultRead<List<Stored<BankAccount>>>> = accounts
    override fun observe(id: BankId): Flow<VaultRead<Stored<Bank>?>> = unused()
    override fun observeDetail(id: BankId): Flow<VaultRead<BankDetail?>> = unused()
    override fun observeAccount(id: BankAccountId): Flow<VaultRead<Stored<BankAccount>?>> = unused()
    override suspend fun create(input: BankInput): Outcome<Stored<Bank>, StorageError> = unused()
    override suspend fun update(
        id: BankId,
        expectedRevision: Long,
        input: BankInput,
    ): Outcome<Stored<Bank>, StorageError> = unused()
    override suspend fun delete(id: BankId, expectedRevision: Long): Outcome<Unit, StorageError> = unused()
    override suspend fun createAccount(input: BankAccountInput): Outcome<Stored<BankAccount>, StorageError> = unused()
    override suspend fun updateAccount(
        id: BankAccountId,
        expectedRevision: Long,
        input: BankAccountInput,
    ): Outcome<Stored<BankAccount>, StorageError> = unused()
    override suspend fun deleteAccount(id: BankAccountId, expectedRevision: Long): Outcome<Unit, StorageError> =
        unused()
}

/** Quotes are set by the test. Refresh is never reached from the cash feature. */
internal class FakePrices(initial: QuoteSnapshot) : PriceRepository {
    val quotes = MutableStateFlow<VaultRead<QuoteSnapshot>>(VaultRead.Ready(1, initial))
    override fun observeQuotes(): Flow<VaultRead<QuoteSnapshot>> = quotes
    override suspend fun refresh(): Outcome<RefreshReport, StorageError> = unused()
    override suspend fun setManual(input: ManualQuoteInput, expectedRevision: Long?): Outcome<Unit, StorageError> =
        unused()
    override suspend fun removeOverride(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> = unused()
    override suspend fun deleteManual(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> = unused()
}

/**
 * An in-memory cash repository with the contract the data layer keeps: revisions start at 1 and grow on every edit, an
 * update or delete must name the revision it read, and a missing record is NotFound. Failures, gates and locks are
 * injected per test.
 */
internal class FakeCashRepository(initial: List<CashBalance> = emptyList()) : CashRepository {
    private val records = MutableStateFlow(initial.map { Stored(it, 1) })
    private val locked = MutableStateFlow(false)
    private var counter = 0

    val createdInputs = mutableListOf<CashBalanceInput>()
    val updatedInputs = mutableListOf<CashBalanceInput>()
    val updateRevisions = mutableListOf<Long>()
    val deleteRevisions = mutableListOf<Long>()
    var writeFailure: StorageError? = null
    var createGate: CompletableDeferred<Unit>? = null

    override fun observeAll(): Flow<VaultRead<List<Stored<CashBalance>>>> =
        combine(records, locked) { list, isLocked -> if (isLocked) VaultRead.Locked else VaultRead.Ready(1, list) }

    override fun observe(id: CashBalanceId): Flow<VaultRead<Stored<CashBalance>?>> =
        combine(records, locked) { list, isLocked ->
            if (isLocked) VaultRead.Locked else VaultRead.Ready(1, list.firstOrNull { it.value.id == id })
        }

    override suspend fun create(input: CashBalanceInput): Outcome<Stored<CashBalance>, StorageError> {
        createdInputs += input
        createGate?.await()
        writeFailure?.let { return Outcome.Failure(it) }
        counter++
        val stored = Stored(
            CashBalance.create(CashBalanceId("c$counter"), input.name, input.kind, input.balance, input.note, NOW, NOW),
            1,
        )
        records.update { it + stored }
        return Outcome.Success(stored)
    }

    override suspend fun update(
        id: CashBalanceId,
        expectedRevision: Long,
        input: CashBalanceInput,
    ): Outcome<Stored<CashBalance>, StorageError> {
        updateRevisions += expectedRevision
        updatedInputs += input
        writeFailure?.let { return Outcome.Failure(it) }
        val current = records.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        val updated = Stored(
            CashBalance.create(id, input.name, input.kind, input.balance, input.note, NOW, NOW),
            current.revision + 1,
        )
        records.update { list -> list.map { if (it === current) updated else it } }
        return Outcome.Success(updated)
    }

    override suspend fun delete(id: CashBalanceId, expectedRevision: Long): Outcome<Unit, StorageError> {
        deleteRevisions += expectedRevision
        writeFailure?.let { return Outcome.Failure(it) }
        val current = records.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        records.update { list -> list.filterNot { it === current } }
        return Outcome.Success(Unit)
    }

    /** Another writer changes the record: the revision moves on and the observers see the new value. */
    fun externalUpdate(id: String, name: String, money: Money, note: String? = null) {
        records.update { list ->
            list.map { stored ->
                if (stored.value.id.value == id) {
                    val kind = stored.value.kind
                    Stored(
                        CashBalance.create(CashBalanceId(id), name, kind, money, note, NOW, NOW),
                        stored.revision + 1,
                    )
                } else {
                    stored
                }
            }
        }
    }

    fun externalDelete(id: String) {
        records.update { list -> list.filterNot { it.value.id.value == id } }
    }

    fun setLocked(value: Boolean) {
        locked.value = value
    }

    fun revisionOf(id: String): Long? = records.value.firstOrNull { it.value.id.value == id }?.revision
}

private fun unused(): Nothing = error("not used by the cash tests")
