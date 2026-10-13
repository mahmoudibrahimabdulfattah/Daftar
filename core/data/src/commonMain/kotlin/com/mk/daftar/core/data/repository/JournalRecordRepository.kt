package com.mk.daftar.core.data.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.records.RecordRules
import com.mk.daftar.core.data.session.VaultHost
import com.mk.daftar.core.data.session.missingOrConflict
import com.mk.daftar.core.database.mapper.toEntity
import com.mk.daftar.core.database.mapper.toStored
import com.mk.daftar.core.domain.repository.JournalRepository
import com.mk.daftar.core.domain.repository.NoteRepository
import com.mk.daftar.core.domain.repository.PersonalNoteInput
import com.mk.daftar.core.domain.repository.ZakatPaymentInput
import com.mk.daftar.core.domain.repository.ZakatRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class JournalRecordRepository(private val host: VaultHost) : JournalRepository {
    override val zakat: ZakatRepository = ZakatRecordRepository(host)
    override val notes: NoteRepository = NoteRecordRepository(host)
}

private class ZakatRecordRepository(private val host: VaultHost) : ZakatRepository {
    override fun observeAll(): Flow<VaultRead<List<Stored<ZakatPayment>>>> =
        host.observe { db -> db.zakatPaymentDao().observeAll().map { rows -> rows.map { it.toStored() } } }

    override fun observe(id: ZakatPaymentId): Flow<VaultRead<Stored<ZakatPayment>?>> =
        host.observe { db -> db.zakatPaymentDao().observe(id.value).map { row -> row?.toStored() } }

    override suspend fun create(input: ZakatPaymentInput): Outcome<Stored<ZakatPayment>, StorageError> =
        host.write { db ->
            val now = host.now()
            val payment = ZakatPayment.create(
                id = ZakatPaymentId(host.ids.nextId()),
                paidOn = input.paidOn,
                amount = input.amount,
                note = input.note,
                createdAt = now,
                updatedAt = now,
                forHijriYear = input.forHijriYear,
            )
            db.zakatPaymentDao().insert(payment.toEntity(RecordRules.FIRST_REVISION))
            Outcome.Success(Stored(payment, RecordRules.FIRST_REVISION))
        }

    override suspend fun update(
        id: ZakatPaymentId,
        expectedRevision: Long,
        input: ZakatPaymentInput,
    ): Outcome<Stored<ZakatPayment>, StorageError> = host.write { db ->
        val dao = db.zakatPaymentDao()
        val previous = dao.get(id.value)?.toStored() ?: return@write Outcome.Failure(StorageError.NotFound)
        if (!RecordRules.matches(
                previous.revision,
                expectedRevision,
            )
        ) {
            return@write Outcome.Failure(StorageError.Conflict)
        }
        val updated = ZakatPayment.create(
            id = id,
            paidOn = input.paidOn,
            amount = input.amount,
            note = input.note,
            createdAt = previous.value.createdAt,
            updatedAt = RecordRules.editedAt(host.now(), previous.value.updatedAt),
            forHijriYear = input.forHijriYear,
        )
        val next = RecordRules.nextRevision(expectedRevision)
        if (dao.updateIfRevision(updated.toEntity(next), expectedRevision) == 1) {
            Outcome.Success(Stored(updated, next))
        } else {
            Outcome.Failure(missingOrConflict(exists = dao.get(id.value) != null))
        }
    }

    override suspend fun delete(id: ZakatPaymentId, expectedRevision: Long): Outcome<Unit, StorageError> =
        host.write { db ->
            val dao = db.zakatPaymentDao()
            if (dao.deleteIfRevision(id.value, expectedRevision) == 1) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(missingOrConflict(exists = dao.get(id.value) != null))
            }
        }
}

private class NoteRecordRepository(private val host: VaultHost) : NoteRepository {
    override fun observeAll(): Flow<VaultRead<List<Stored<PersonalNote>>>> =
        host.observe { db -> db.personalNoteDao().observeAll().map { rows -> rows.map { it.toStored() } } }

    override fun observe(id: NoteId): Flow<VaultRead<Stored<PersonalNote>?>> =
        host.observe { db -> db.personalNoteDao().observe(id.value).map { row -> row?.toStored() } }

    override suspend fun create(input: PersonalNoteInput): Outcome<Stored<PersonalNote>, StorageError> =
        host.write { db ->
            val now = host.now()
            val note = PersonalNote.create(
                id = NoteId(host.ids.nextId()),
                title = input.title,
                body = input.body,
                createdAt = now,
                updatedAt = now,
            )
            db.personalNoteDao().insert(note.toEntity(RecordRules.FIRST_REVISION))
            Outcome.Success(Stored(note, RecordRules.FIRST_REVISION))
        }

    override suspend fun update(
        id: NoteId,
        expectedRevision: Long,
        input: PersonalNoteInput,
    ): Outcome<Stored<PersonalNote>, StorageError> = host.write { db ->
        val dao = db.personalNoteDao()
        val previous = dao.get(id.value)?.toStored() ?: return@write Outcome.Failure(StorageError.NotFound)
        if (!RecordRules.matches(
                previous.revision,
                expectedRevision,
            )
        ) {
            return@write Outcome.Failure(StorageError.Conflict)
        }
        val updated = PersonalNote.create(
            id = id,
            title = input.title,
            body = input.body,
            createdAt = previous.value.createdAt,
            updatedAt = RecordRules.editedAt(host.now(), previous.value.updatedAt),
        )
        val next = RecordRules.nextRevision(expectedRevision)
        if (dao.updateIfRevision(updated.toEntity(next), expectedRevision) == 1) {
            Outcome.Success(Stored(updated, next))
        } else {
            Outcome.Failure(missingOrConflict(exists = dao.get(id.value) != null))
        }
    }

    override suspend fun delete(id: NoteId, expectedRevision: Long): Outcome<Unit, StorageError> = host.write { db ->
        val dao = db.personalNoteDao()
        if (dao.deleteIfRevision(id.value, expectedRevision) == 1) {
            Outcome.Success(Unit)
        } else {
            Outcome.Failure(missingOrConflict(exists = dao.get(id.value) != null))
        }
    }
}
