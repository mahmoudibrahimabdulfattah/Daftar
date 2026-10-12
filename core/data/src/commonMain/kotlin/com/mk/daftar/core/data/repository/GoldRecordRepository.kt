package com.mk.daftar.core.data.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.records.RecordRules
import com.mk.daftar.core.data.session.VaultHost
import com.mk.daftar.core.data.session.missingOrConflict
import com.mk.daftar.core.database.mapper.toEntity
import com.mk.daftar.core.database.mapper.toStored
import com.mk.daftar.core.domain.repository.GoldHoldingInput
import com.mk.daftar.core.domain.repository.GoldRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class GoldRecordRepository(private val host: VaultHost) : GoldRepository {
    override fun observeAll(): Flow<VaultRead<List<Stored<GoldHolding>>>> =
        host.observe { db -> db.goldHoldingDao().observeAll().map { rows -> rows.map { it.toStored() } } }

    override fun observe(id: GoldHoldingId): Flow<VaultRead<Stored<GoldHolding>?>> =
        host.observe { db -> db.goldHoldingDao().observe(id.value).map { row -> row?.toStored() } }

    override suspend fun create(input: GoldHoldingInput): Outcome<Stored<GoldHolding>, StorageError> =
        host.write { db ->
            val now = host.now()
            val holding = GoldHolding.create(
                id = GoldHoldingId(host.ids.nextId()),
                karat = input.karat,
                form = input.form,
                weight = input.weight,
                note = input.note,
                createdAt = now,
                updatedAt = now,
            )
            db.goldHoldingDao().insert(holding.toEntity(RecordRules.FIRST_REVISION))
            Outcome.Success(Stored(holding, RecordRules.FIRST_REVISION))
        }

    override suspend fun update(
        id: GoldHoldingId,
        expectedRevision: Long,
        input: GoldHoldingInput,
    ): Outcome<Stored<GoldHolding>, StorageError> = host.write { db ->
        val dao = db.goldHoldingDao()
        val previous = dao.get(id.value)?.toStored() ?: return@write Outcome.Failure(StorageError.NotFound)
        if (!RecordRules.matches(
                previous.revision,
                expectedRevision,
            )
        ) {
            return@write Outcome.Failure(StorageError.Conflict)
        }
        val updated = GoldHolding.create(
            id = id,
            karat = input.karat,
            form = input.form,
            weight = input.weight,
            note = input.note,
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

    override suspend fun delete(id: GoldHoldingId, expectedRevision: Long): Outcome<Unit, StorageError> =
        host.write { db ->
            val dao = db.goldHoldingDao()
            if (dao.deleteIfRevision(id.value, expectedRevision) == 1) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(missingOrConflict(exists = dao.get(id.value) != null))
            }
        }
}
