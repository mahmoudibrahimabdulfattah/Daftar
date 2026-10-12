package com.mk.daftar.feature.gold.testing

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
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.domain.valuation.selectQuotes
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.FxRate
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldPrice
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.GoldWeight
import com.mk.daftar.core.model.MerchantSide
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

/** The instant every fixture is created at. Quotes fetched at this instant are fresh. */
internal val NOW: Instant = Instant.parse("2026-10-12T09:40:00Z")

internal fun dec(text: String): Decimal = Decimal.parseCanonical(text)

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

internal fun holding(
    id: String,
    karat: GoldKarat,
    grams: String,
    form: GoldForm = GoldForm.BAR,
    note: String? = null,
): GoldHolding = GoldHolding.create(
    id = GoldHoldingId(id),
    karat = karat,
    form = form,
    weight = GoldWeight(dec(grams)),
    note = note,
    createdAt = NOW,
    updatedAt = NOW,
)

internal fun sellKey(karat: GoldKarat): QuoteKey.Gold = QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER)

/** A remote merchant sell quote fetched at [fetchedAt], as the refresh would have cached it. */
internal fun remoteGold(karat: GoldKarat, price: String, fetchedAt: Instant = NOW): RemoteQuoteRecord {
    val provenance = QuoteProvenance.live(
        provider = QuoteProvider.ISAGHA,
        sourceUrl = "https://market.isagha.com/prices",
        sourceTime = SourceTime.Unknown,
        fetchedAt = fetchedAt,
    )
    return RemoteQuoteRecord(GoldQuote(sellKey(karat), GoldPrice(dec(price)), provenance), null, null)
}

internal fun remoteUsd(rate: String, fetchedAt: Instant = NOW): RemoteQuoteRecord {
    val provenance = QuoteProvenance.live(
        provider = QuoteProvider.EXCHANGE_RATE_API,
        sourceUrl = "https://open.er-api.com/v6/latest/USD",
        sourceTime = SourceTime.Unknown,
        fetchedAt = fetchedAt,
    )
    return RemoteQuoteRecord(FxQuote(FxRate(dec(rate)), provenance), nextUpdateAt = null, attributionUrl = null)
}

/** A pinned manual merchant sell quote, recorded at [recordedAt]. */
internal fun pinnedManualGold(karat: GoldKarat, price: String, recordedAt: Instant = NOW): Stored<PriceQuote> {
    val provenance = QuoteProvenance(
        provider = QuoteProvider.USER,
        sourceUrl = null,
        sourceTime = SourceTime.Unknown,
        fetchedAt = null,
        recordedAt = recordedAt,
        kind = QuoteKind.MANUAL,
        manualPinned = true,
        note = null,
    )
    return Stored(GoldQuote(sellKey(karat), GoldPrice(dec(price)), provenance), revision = 1)
}

internal fun selectedOf(
    remote: List<RemoteQuoteRecord>,
    manual: List<Stored<PriceQuote>> = emptyList(),
): SelectedQuotes = selectQuotes(QuoteSnapshot(remote = remote, manual = manual), NOW)

/** The approved fixture's quotes: 21K at 6,185, 18K at 5,301, 24K at 7,069 and USD at 48.50. */
internal fun readyQuotes(): SelectedQuotes = selectedOf(
    listOf(
        remoteGold(GoldKarat.K24, "7069"),
        remoteGold(GoldKarat.K21, "6185"),
        remoteGold(GoldKarat.K18, "5301"),
        remoteUsd("48.50"),
    ),
)

/** A session the test opens and locks directly. Opening it at a generation makes its reads ready. */
internal class FakeSession : VaultSession {
    private val mutable = MutableStateFlow<VaultSessionState>(VaultSessionState.Locked)
    override val state: StateFlow<VaultSessionState> = mutable.asStateFlow()

    fun open(generation: Long = 1) {
        mutable.value = VaultSessionState.Open(generation)
    }

    override suspend fun lock() {
        mutable.value = VaultSessionState.Locked
    }
}

/**
 * An in-memory gold repository with the data layer's contract: revisions start at 1 and grow on every edit, an update or
 * delete names the revision it read, and observations follow the repository order, which is the insertion order here.
 * Failures and gates are injected per test.
 */
internal class FakeGoldRepository(initial: List<GoldHolding> = emptyList()) : GoldRepository {
    private val records = MutableStateFlow(initial.map { Stored(it, 1) })
    private val locked = MutableStateFlow(false)
    private var counter = 0

    val createdInputs = mutableListOf<GoldHoldingInput>()
    val updatedInputs = mutableListOf<GoldHoldingInput>()
    val updateRevisions = mutableListOf<Long>()
    val deleteRevisions = mutableListOf<Long>()
    var writeFailure: StorageError? = null
    var createGate: CompletableDeferred<Unit>? = null

    override fun observeAll(): Flow<VaultRead<List<Stored<GoldHolding>>>> = combine(records, locked) { list, isLocked ->
        if (isLocked) VaultRead.Locked else VaultRead.Ready(1, list)
    }

    override fun observe(id: GoldHoldingId): Flow<VaultRead<Stored<GoldHolding>?>> =
        combine(records, locked) { list, isLocked ->
            if (isLocked) VaultRead.Locked else VaultRead.Ready(1, list.firstOrNull { it.value.id == id })
        }

    override suspend fun create(input: GoldHoldingInput): Outcome<Stored<GoldHolding>, StorageError> {
        createdInputs += input
        createGate?.await()
        writeFailure?.let { return Outcome.Failure(it) }
        counter++
        val stored = Stored(
            GoldHolding.create(
                GoldHoldingId("h$counter"),
                input.karat,
                input.form,
                input.weight,
                input.note,
                NOW,
                NOW,
            ),
            1,
        )
        records.update { it + stored }
        return Outcome.Success(stored)
    }

    override suspend fun update(
        id: GoldHoldingId,
        expectedRevision: Long,
        input: GoldHoldingInput,
    ): Outcome<Stored<GoldHolding>, StorageError> {
        updateRevisions += expectedRevision
        updatedInputs += input
        writeFailure?.let { return Outcome.Failure(it) }
        val current = records.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        val updated = Stored(
            GoldHolding.create(id, input.karat, input.form, input.weight, input.note, current.value.createdAt, NOW),
            current.revision + 1,
        )
        records.update { list -> list.map { if (it === current) updated else it } }
        return Outcome.Success(updated)
    }

    override suspend fun delete(id: GoldHoldingId, expectedRevision: Long): Outcome<Unit, StorageError> {
        deleteRevisions += expectedRevision
        writeFailure?.let { return Outcome.Failure(it) }
        val current = records.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        records.update { list -> list.filterNot { it === current } }
        return Outcome.Success(Unit)
    }

    /** Another writer changes the holding: the revision moves on and observers see the new value. */
    fun externalUpdate(id: String, karat: GoldKarat, grams: String, note: String? = null) {
        records.update { list ->
            list.map { stored ->
                if (stored.value.id.value == id) {
                    Stored(holding(id, karat, grams, note = note), stored.revision + 1)
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

/** Bank repository for the portfolio: no accounts, so only gold and quotes drive the valuation under test. */
internal class FakeBankRepository : BankRepository {
    override fun observeAll(): Flow<VaultRead<List<Stored<Bank>>>> = flowReady(emptyList())
    override fun observeAccounts(): Flow<VaultRead<List<Stored<BankAccount>>>> = flowReady(emptyList())
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

internal class FakeCashRepository : CashRepository {
    override fun observeAll(): Flow<VaultRead<List<Stored<CashBalance>>>> = flowReady(emptyList())
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

/** Quotes the test sets directly: the snapshot is what the valuation and the editor's preview read. */
internal class FakePriceRepository(initial: QuoteSnapshot = QuoteSnapshot(emptyList(), emptyList())) : PriceRepository {
    val snapshot = MutableStateFlow<VaultRead<QuoteSnapshot>>(VaultRead.Ready(1, initial))
    override fun observeQuotes(): Flow<VaultRead<QuoteSnapshot>> = snapshot
    override suspend fun refresh(): Outcome<RefreshReport, StorageError> = error("unused")
    override suspend fun setManual(input: ManualQuoteInput, expectedRevision: Long?): Outcome<Unit, StorageError> =
        error("unused")
    override suspend fun removeOverride(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> =
        error("unused")
    override suspend fun deleteManual(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> =
        error("unused")
}

private fun <T> flowReady(value: T): Flow<VaultRead<T>> = MutableStateFlow(VaultRead.Ready(1, value))
