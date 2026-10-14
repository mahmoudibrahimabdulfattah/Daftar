package com.mk.daftar.feature.home

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.refresh.PriceRefreshController
import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.repository.BankAccountInput
import com.mk.daftar.core.domain.repository.BankDetail
import com.mk.daftar.core.domain.repository.BankInput
import com.mk.daftar.core.domain.repository.BankRepository
import com.mk.daftar.core.domain.repository.CashBalanceInput
import com.mk.daftar.core.domain.repository.CashRepository
import com.mk.daftar.core.domain.repository.FirstRunGuideRepository
import com.mk.daftar.core.domain.repository.GoldHoldingInput
import com.mk.daftar.core.domain.repository.GoldRepository
import com.mk.daftar.core.domain.repository.JournalRepository
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.domain.repository.NoteRepository
import com.mk.daftar.core.domain.repository.PersonalNoteInput
import com.mk.daftar.core.domain.repository.PriceRepository
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RefreshReport
import com.mk.daftar.core.domain.repository.ZakatPaymentInput
import com.mk.daftar.core.domain.repository.ZakatRepository
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.repository.ZakatYearRepository
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveZakatDayUseCase
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId
import com.mk.daftar.core.model.ZakatYear
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

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

internal class FakePrices : PriceRepository {
    val quotes = MutableStateFlow<VaultRead<QuoteSnapshot>>(VaultRead.Locked)
    override fun observeQuotes(): Flow<VaultRead<QuoteSnapshot>> = quotes
    override suspend fun refresh(): Outcome<RefreshReport, StorageError> = error("unused")
    override suspend fun setManual(input: ManualQuoteInput, expectedRevision: Long?): Outcome<Unit, StorageError> =
        error("unused")
    override suspend fun removeOverride(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> =
        error("unused")
    override suspend fun deleteManual(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> =
        error("unused")
}

/** Counts explicit refresh requests; the view model must only forward them, never fetch itself. */
internal class FakeRefresh : PriceRefreshController {
    val current = MutableStateFlow<PriceRefreshStatus>(PriceRefreshStatus.Idle)
    override val status: StateFlow<PriceRefreshStatus> = current.asStateFlow()
    var refreshCalls = 0
    override fun start() = Unit
    override fun refresh() {
        refreshCalls++
    }
}

/** The approved fixture as repository values of [generation]. */
internal fun FakePortfolio.load(generation: Long) {
    val input = readyInput()
    gold.data.value = VaultRead.Ready(generation, input.gold.map { Stored(it, 1) })
    banks.banks.value = VaultRead.Ready(
        generation,
        listOf("bank-misr", "alahli", "cib").map { id ->
            Stored(
                Bank.create(BankId(id), "Bank $id", FIXTURE_NOW, FIXTURE_NOW),
                1,
            )
        },
    )
    banks.accounts.value = VaultRead.Ready(generation, input.bankAccounts.map { Stored(it, 1) })
    cash.data.value = VaultRead.Ready(generation, input.cash.map { Stored(it, 1) })
    prices.quotes.value = VaultRead.Ready(generation, readySnapshot())
}

internal class FakePortfolio {
    val session = FakeSession()
    val gold = FakeGold()
    val banks = FakeBanks()
    val cash = FakeCash()
    val prices = FakePrices()
    val refresh = FakeRefresh()
}

internal fun readySnapshot(): QuoteSnapshot = QuoteSnapshot(
    remote = listOf(
        remoteGold(GoldKarat.K24, "7069"),
        remoteGold(GoldKarat.K21, "6185"),
        remoteGold(GoldKarat.K18, "5301"),
        remoteUsd("48.50"),
    ),
    manual = emptyList(),
)

/** A tracking read that never changes: the given state, or a locked or failed read. */
internal class FakeZakatTracking(private val read: VaultRead<ZakatTrackingState>) : ZakatYearRepository {
    override fun observeTracking(): Flow<VaultRead<ZakatTrackingState>> = flowOf(read)
    override suspend fun ensureTracking(today: LocalDate): Outcome<ZakatTrackingState, StorageError> = error("unused")
    override suspend fun setPaid(hijriYear: Int, paid: Boolean): Outcome<ZakatYear, StorageError> = error("unused")
    override suspend fun setDateAdjustment(hijriYear: Int, overrideDays: Int): Outcome<ZakatYear, StorageError> =
        error("unused")
    override suspend fun addYear(hijriYear: Int, today: LocalDate): Outcome<ZakatYear, StorageError> = error("unused")
}

/** The Cairo day the Home tests see: fixed, so due years are deterministic. */
internal fun fixedZakatDay(day: LocalDate): ObserveZakatDayUseCase = object : ObserveZakatDayUseCase(FixedClock) {
    override operator fun invoke(): Flow<LocalDate> = flowOf(day)
}

/** The first-run flag: [done] starts as given, and marking it done is counted. */
internal class FakeFirstRunGuide(initial: Boolean? = false) : FirstRunGuideRepository {
    val done = MutableStateFlow(initial)
    var marks = 0

    override fun observeDone(): Flow<Boolean?> = done

    override suspend fun markDone(): Outcome<Unit, StorageError> {
        marks++
        done.value = true
        return Outcome.Success(Unit)
    }

    override suspend fun reset(): Outcome<Unit, StorageError> {
        done.value = false
        return Outcome.Success(Unit)
    }
}

/** Journal records, empty unless a test fills them. Only the list reads are used by the guide. */
internal class FakeJournal : JournalRepository {
    val paymentRecords = MutableStateFlow<VaultRead<List<Stored<ZakatPayment>>>>(VaultRead.Ready(1, emptyList()))
    val noteRecords = MutableStateFlow<VaultRead<List<Stored<PersonalNote>>>>(VaultRead.Ready(1, emptyList()))

    override val zakat: ZakatRepository = object : ZakatRepository {
        override fun observeAll(): Flow<VaultRead<List<Stored<ZakatPayment>>>> = paymentRecords
        override fun observe(id: ZakatPaymentId): Flow<VaultRead<Stored<ZakatPayment>?>> = error("unused")
        override suspend fun create(input: ZakatPaymentInput): Outcome<Stored<ZakatPayment>, StorageError> =
            error("unused")
        override suspend fun update(
            id: ZakatPaymentId,
            expectedRevision: Long,
            input: ZakatPaymentInput,
        ): Outcome<Stored<ZakatPayment>, StorageError> = error("unused")
        override suspend fun delete(id: ZakatPaymentId, expectedRevision: Long): Outcome<Unit, StorageError> =
            error("unused")
    }

    override val notes: NoteRepository = object : NoteRepository {
        override fun observeAll(): Flow<VaultRead<List<Stored<PersonalNote>>>> = noteRecords
        override fun observe(id: NoteId): Flow<VaultRead<Stored<PersonalNote>?>> = error("unused")
        override suspend fun create(input: PersonalNoteInput): Outcome<Stored<PersonalNote>, StorageError> =
            error("unused")
        override suspend fun update(
            id: NoteId,
            expectedRevision: Long,
            input: PersonalNoteInput,
        ): Outcome<Stored<PersonalNote>, StorageError> = error("unused")
        override suspend fun delete(id: NoteId, expectedRevision: Long): Outcome<Unit, StorageError> = error("unused")
    }
}
