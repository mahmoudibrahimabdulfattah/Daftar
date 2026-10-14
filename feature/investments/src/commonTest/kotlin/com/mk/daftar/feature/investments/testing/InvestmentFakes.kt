@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.investments.testing

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.CashBalanceInput
import com.mk.daftar.core.domain.repository.CashRepository
import com.mk.daftar.core.domain.repository.InvestmentConversion
import com.mk.daftar.core.domain.repository.InvestmentConversionFailure
import com.mk.daftar.core.domain.repository.InvestmentInput
import com.mk.daftar.core.domain.repository.InvestmentRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.model.InvestmentAccountId
import com.mk.daftar.core.model.Money
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestCoroutineScheduler

/** The fixed "now" of every investment test. Tests move the clock explicitly when they need another moment. */
internal val NOW: Instant = Instant.parse("2026-10-14T12:00:00Z")

/**
 * A clock for tests. Without a [scheduler] it stands still at [instant]; with one it moves with the virtual time of the
 * test, as the real clock moves while a screen waits on a value's threshold.
 */
internal class TestClock(var instant: Instant = NOW, private val scheduler: TestCoroutineScheduler? = null) : Clock {
    override fun now(): Instant = scheduler?.let { instant + it.currentTime.milliseconds } ?: instant
}

internal fun egp(text: String): Money = Money(Decimal.parseCanonical(text), Currency.EGP)

internal fun usd(text: String): Money = Money(Decimal.parseCanonical(text), Currency.USD)

internal fun account(
    id: String,
    amount: String,
    valueAsOf: Instant = NOW,
    providerId: String? = null,
    name: String = "Account $id",
    note: String? = null,
    createdAt: Instant = NOW,
): InvestmentAccount = InvestmentAccount.create(
    id = InvestmentAccountId(id),
    providerId = providerId?.let { InstitutionId(it) },
    name = name,
    amount = egp(amount),
    note = note,
    valueAsOf = valueAsOf,
    convertedFromCashId = null,
    createdAt = createdAt,
    updatedAt = createdAt,
)

internal fun cashEntry(
    id: String,
    amount: String,
    currency: Currency = Currency.EGP,
    name: String = "Entry $id",
    note: String? = null,
): CashBalance = CashBalance.create(
    id = CashBalanceId(id),
    name = name,
    kind = CashKind.CASH,
    balance = Money(Decimal.parseCanonical(amount), currency),
    note = note,
    createdAt = NOW,
    updatedAt = NOW,
)

/**
 * An in-memory investment repository with the data layer's contract: revisions start at 1 and grow on every edit, an
 * update or delete names the revision it read, and a missing record is NotFound. Failures are injected per test.
 */
internal class FakeInvestmentRepository(initial: List<InvestmentAccount> = emptyList()) : InvestmentRepository {
    private val records = MutableStateFlow(initial.map { Stored(it, 1) })
    private val locked = MutableStateFlow(false)
    private var counter = 0

    val createdInputs = mutableListOf<InvestmentInput>()
    val updatedInputs = mutableListOf<InvestmentInput>()
    val updateRevisions = mutableListOf<Long>()
    val deleteRevisions = mutableListOf<Long>()
    val conversionCalls = mutableListOf<Triple<CashBalanceId, InvestmentAccountId, Instant>>()
    var writeFailure: StorageError? = null
    var conversionFailure: InvestmentConversionFailure? = null

    /** The stored accounts, for assertions. */
    val current: List<Stored<InvestmentAccount>> get() = records.value

    override fun observeAll(): Flow<VaultRead<List<Stored<InvestmentAccount>>>> =
        combine(records, locked) { list, isLocked -> if (isLocked) VaultRead.Locked else VaultRead.Ready(1, list) }

    override fun observe(id: InvestmentAccountId): Flow<VaultRead<Stored<InvestmentAccount>?>> =
        combine(records, locked) { list, isLocked ->
            if (isLocked) VaultRead.Locked else VaultRead.Ready(1, list.firstOrNull { it.value.id == id })
        }

    override suspend fun create(input: InvestmentInput): Outcome<Stored<InvestmentAccount>, StorageError> {
        createdInputs += input
        writeFailure?.let { return Outcome.Failure(it) }
        counter++
        val stored = Stored(
            InvestmentAccount.create(
                id = InvestmentAccountId("i$counter"),
                providerId = input.providerId,
                name = input.name,
                amount = Money(input.amount, Currency.EGP),
                note = input.note,
                valueAsOf = input.valueAsOf,
                convertedFromCashId = null,
                createdAt = NOW,
                updatedAt = NOW,
            ),
            1,
        )
        records.update { it + stored }
        return Outcome.Success(stored)
    }

    override suspend fun update(
        id: InvestmentAccountId,
        expectedRevision: Long,
        input: InvestmentInput,
    ): Outcome<Stored<InvestmentAccount>, StorageError> {
        updateRevisions += expectedRevision
        updatedInputs += input
        writeFailure?.let { return Outcome.Failure(it) }
        val current = records.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        val updated = Stored(
            InvestmentAccount.create(
                id = id,
                providerId = input.providerId,
                name = input.name,
                amount = Money(input.amount, Currency.EGP),
                note = input.note,
                valueAsOf = input.valueAsOf,
                convertedFromCashId = current.value.convertedFromCashId,
                createdAt = current.value.createdAt,
                updatedAt = NOW,
            ),
            current.revision + 1,
        )
        records.update { list -> list.map { if (it === current) updated else it } }
        return Outcome.Success(updated)
    }

    override suspend fun delete(id: InvestmentAccountId, expectedRevision: Long): Outcome<Unit, StorageError> {
        deleteRevisions += expectedRevision
        writeFailure?.let { return Outcome.Failure(it) }
        val current = records.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        records.update { list -> list.filterNot { it === current } }
        return Outcome.Success(Unit)
    }

    override suspend fun convertCashToInvestment(
        sourceId: CashBalanceId,
        expectedRevision: Long,
        draftId: InvestmentAccountId,
        providerId: InstitutionId?,
        valueAsOf: Instant,
    ): Outcome<InvestmentConversion, InvestmentConversionFailure> {
        conversionCalls += Triple(sourceId, draftId, valueAsOf)
        conversionFailure?.let { return Outcome.Failure(it) }
        val stored = Stored(
            InvestmentAccount.create(
                id = draftId,
                providerId = providerId,
                name = "Entry ${sourceId.value}",
                amount = egp("100"),
                note = null,
                valueAsOf = valueAsOf,
                convertedFromCashId = sourceId,
                createdAt = NOW,
                updatedAt = NOW,
            ),
            1,
        )
        records.update { it + stored }
        return Outcome.Success(InvestmentConversion.Converted(stored))
    }

    fun setLocked(value: Boolean) {
        locked.value = value
    }
}

/** An in-memory cash repository for the conversion flow. Only reads and the revision contract matter here. */
internal class FakeCashRepository(initial: List<CashBalance> = emptyList()) : CashRepository {
    private val records = MutableStateFlow(initial.map { Stored(it, 1) })
    private val locked = MutableStateFlow(false)

    override fun observeAll(): Flow<VaultRead<List<Stored<CashBalance>>>> =
        combine(records, locked) { list, isLocked -> if (isLocked) VaultRead.Locked else VaultRead.Ready(1, list) }

    override fun observe(id: CashBalanceId): Flow<VaultRead<Stored<CashBalance>?>> =
        combine(records, locked) { list, isLocked ->
            if (isLocked) VaultRead.Locked else VaultRead.Ready(1, list.firstOrNull { it.value.id == id })
        }

    override suspend fun create(input: CashBalanceInput): Outcome<Stored<CashBalance>, StorageError> =
        Outcome.Failure(StorageError.InvalidInput)

    override suspend fun update(
        id: CashBalanceId,
        expectedRevision: Long,
        input: CashBalanceInput,
    ): Outcome<Stored<CashBalance>, StorageError> = Outcome.Failure(StorageError.InvalidInput)

    override suspend fun delete(id: CashBalanceId, expectedRevision: Long): Outcome<Unit, StorageError> =
        Outcome.Failure(StorageError.InvalidInput)

    /** The entry changes under the user's review: its revision moves on. */
    fun externalUpdate(id: String, amount: String) {
        records.update { list ->
            list.map { stored ->
                if (stored.value.id.value == id) {
                    Stored(cashEntry(id, amount), stored.revision + 1)
                } else {
                    stored
                }
            }
        }
    }

    /** The entry disappears, as it does after a conversion or a deletion elsewhere. */
    fun externalRemove(id: String) {
        records.update { list -> list.filterNot { it.value.id.value == id } }
    }

    fun seed(entries: List<CashBalance>) {
        records.value = entries.map { Stored(it, 1) }
    }

    fun setLocked(value: Boolean) {
        locked.value = value
    }
}
