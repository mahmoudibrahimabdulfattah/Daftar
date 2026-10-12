package com.mk.daftar.feature.journal.testing

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.NoteRepository
import com.mk.daftar.core.domain.repository.PersonalNoteInput
import com.mk.daftar.core.domain.repository.ZakatPaymentInput
import com.mk.daftar.core.domain.repository.ZakatRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/** The instant every fixture is created at, and the day the tests treat as today in UTC. */
internal val NOW: Instant = Instant.parse("2026-10-12T09:40:00Z")
internal val TODAY: LocalDate = LocalDate(2026, 10, 12)

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

internal fun egp(text: String): Money = Money(Decimal.parseCanonical(text), Currency.EGP)

internal fun payment(id: String, paidOn: LocalDate, amount: String, note: String? = null): ZakatPayment =
    ZakatPayment.create(ZakatPaymentId(id), paidOn, egp(amount), note, NOW, NOW)

/** A note whose creation and last update are the same instant, unless the test says otherwise. */
internal fun note(id: String, title: String, body: String, updatedAt: Instant = NOW): PersonalNote =
    PersonalNote.create(NoteId(id), title, body, updatedAt, updatedAt)

/**
 * An in-memory zakat repository with the contract the data layer keeps: revisions start at 1 and grow on every edit,
 * an update or delete must name the revision it read, and observations are ordered by paid date, newest first.
 * Failures and gates are injected per test.
 */
internal class FakeZakatRepository(initial: List<ZakatPayment> = emptyList()) : ZakatRepository {
    private val records = MutableStateFlow(initial.map { Stored(it, 1) })
    private val locked = MutableStateFlow(false)
    private var counter = 0

    val createdInputs = mutableListOf<ZakatPaymentInput>()
    val updateRevisions = mutableListOf<Long>()
    val deleteRevisions = mutableListOf<Long>()
    var writeFailure: StorageError? = null
    var createGate: CompletableDeferred<Unit>? = null

    override fun observeAll(): Flow<VaultRead<List<Stored<ZakatPayment>>>> =
        combine(records, locked) { list, isLocked ->
            if (isLocked) {
                VaultRead.Locked
            } else {
                VaultRead.Ready(1, list.sortedWith(compareByDescending<Stored<ZakatPayment>> { it.value.paidOn }))
            }
        }

    override fun observe(id: ZakatPaymentId): Flow<VaultRead<Stored<ZakatPayment>?>> =
        combine(records, locked) { list, isLocked ->
            if (isLocked) VaultRead.Locked else VaultRead.Ready(1, list.firstOrNull { it.value.id == id })
        }

    override suspend fun create(input: ZakatPaymentInput): Outcome<Stored<ZakatPayment>, StorageError> {
        createdInputs += input
        createGate?.await()
        writeFailure?.let { return Outcome.Failure(it) }
        counter++
        val stored = Stored(payment("z$counter", input.paidOn, input.amount.amount.toPlainString(), input.note), 1)
        records.update { it + stored }
        return Outcome.Success(stored)
    }

    override suspend fun update(
        id: ZakatPaymentId,
        expectedRevision: Long,
        input: ZakatPaymentInput,
    ): Outcome<Stored<ZakatPayment>, StorageError> {
        updateRevisions += expectedRevision
        writeFailure?.let { return Outcome.Failure(it) }
        val current = records.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        val updated = Stored(
            payment(id.value, input.paidOn, input.amount.amount.toPlainString(), input.note),
            current.revision + 1,
        )
        records.update { list -> list.map { if (it === current) updated else it } }
        return Outcome.Success(updated)
    }

    override suspend fun delete(id: ZakatPaymentId, expectedRevision: Long): Outcome<Unit, StorageError> {
        deleteRevisions += expectedRevision
        writeFailure?.let { return Outcome.Failure(it) }
        val current = records.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        records.update { list -> list.filterNot { it === current } }
        return Outcome.Success(Unit)
    }

    /** Another writer changes the record: the revision moves on and the observers see the new value. */
    fun externalUpdate(id: String, paidOn: LocalDate, amount: String, note: String? = null) {
        records.update { list ->
            list.map { stored ->
                if (stored.value.id.value == id) {
                    Stored(payment(id, paidOn, amount, note), stored.revision + 1)
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

/** An in-memory note repository with the same revision and ordering contract as the zakat fake. */
internal class FakeNoteRepository(initial: List<PersonalNote> = emptyList()) : NoteRepository {
    private val records = MutableStateFlow(initial.map { Stored(it, 1) })
    private val locked = MutableStateFlow(false)
    private var counter = 0

    val createdInputs = mutableListOf<PersonalNoteInput>()
    val updateRevisions = mutableListOf<Long>()
    val deleteRevisions = mutableListOf<Long>()
    var writeFailure: StorageError? = null
    var createGate: CompletableDeferred<Unit>? = null

    override fun observeAll(): Flow<VaultRead<List<Stored<PersonalNote>>>> =
        combine(records, locked) { list, isLocked ->
            if (isLocked) {
                VaultRead.Locked
            } else {
                VaultRead.Ready(1, list.sortedByDescending { it.value.updatedAt })
            }
        }

    override fun observe(id: NoteId): Flow<VaultRead<Stored<PersonalNote>?>> =
        combine(records, locked) { list, isLocked ->
            if (isLocked) VaultRead.Locked else VaultRead.Ready(1, list.firstOrNull { it.value.id == id })
        }

    override suspend fun create(input: PersonalNoteInput): Outcome<Stored<PersonalNote>, StorageError> {
        createdInputs += input
        createGate?.await()
        writeFailure?.let { return Outcome.Failure(it) }
        counter++
        val stored = Stored(note("n$counter", input.title, input.body), 1)
        records.update { it + stored }
        return Outcome.Success(stored)
    }

    override suspend fun update(
        id: NoteId,
        expectedRevision: Long,
        input: PersonalNoteInput,
    ): Outcome<Stored<PersonalNote>, StorageError> {
        updateRevisions += expectedRevision
        writeFailure?.let { return Outcome.Failure(it) }
        val current = records.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        val updated = Stored(note(id.value, input.title, input.body, updatedAt = NOW), current.revision + 1)
        records.update { list -> list.map { if (it === current) updated else it } }
        return Outcome.Success(updated)
    }

    override suspend fun delete(id: NoteId, expectedRevision: Long): Outcome<Unit, StorageError> {
        deleteRevisions += expectedRevision
        writeFailure?.let { return Outcome.Failure(it) }
        val current = records.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        records.update { list -> list.filterNot { it === current } }
        return Outcome.Success(Unit)
    }

    fun externalUpdate(id: String, title: String, body: String) {
        records.update { list ->
            list.map { stored ->
                if (stored.value.id.value == id) Stored(note(id, title, body), stored.revision + 1) else stored
            }
        }
    }

    fun externalDelete(id: String) {
        records.update { list -> list.filterNot { it.value.id.value == id } }
    }

    fun setLocked(value: Boolean) {
        locked.value = value
    }
}
