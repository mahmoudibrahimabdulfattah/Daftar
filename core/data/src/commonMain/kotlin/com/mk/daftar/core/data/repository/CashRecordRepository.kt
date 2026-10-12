package com.mk.daftar.core.data.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.records.RecordRules
import com.mk.daftar.core.data.session.VaultHost
import com.mk.daftar.core.data.session.missingOrConflict
import com.mk.daftar.core.database.mapper.toEntity
import com.mk.daftar.core.database.mapper.toStored
import com.mk.daftar.core.domain.repository.CashBalanceInput
import com.mk.daftar.core.domain.repository.CashRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class CashRecordRepository(private val host: VaultHost) : CashRepository {
    override fun observeAll(): Flow<VaultRead<List<Stored<CashBalance>>>> =
        host.observe { db -> db.cashBalanceDao().observeAll().map { rows -> rows.map { it.toStored() } } }

    override fun observe(id: CashBalanceId): Flow<VaultRead<Stored<CashBalance>?>> =
        host.observe { db -> db.cashBalanceDao().observe(id.value).map { row -> row?.toStored() } }

    override suspend fun create(input: CashBalanceInput): Outcome<Stored<CashBalance>, StorageError> =
        host.write { db ->
            val now = host.now()
            val cash = CashBalance.create(
                id = CashBalanceId(host.ids.nextId()),
                name = input.name,
                kind = input.kind,
                balance = input.balance,
                note = input.note,
                createdAt = now,
                updatedAt = now,
            )
            db.cashBalanceDao().insert(cash.toEntity(RecordRules.FIRST_REVISION))
            Outcome.Success(Stored(cash, RecordRules.FIRST_REVISION))
        }

    override suspend fun update(
        id: CashBalanceId,
        expectedRevision: Long,
        input: CashBalanceInput,
    ): Outcome<Stored<CashBalance>, StorageError> = host.write { db ->
        val dao = db.cashBalanceDao()
        val previous = dao.get(id.value)?.toStored() ?: return@write Outcome.Failure(StorageError.NotFound)
        if (!RecordRules.matches(
                previous.revision,
                expectedRevision,
            )
        ) {
            return@write Outcome.Failure(StorageError.Conflict)
        }
        val updated = CashBalance.create(
            id = id,
            name = input.name,
            kind = input.kind,
            balance = input.balance,
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

    override suspend fun delete(id: CashBalanceId, expectedRevision: Long): Outcome<Unit, StorageError> =
        host.write { db ->
            val dao = db.cashBalanceDao()
            if (dao.deleteIfRevision(id.value, expectedRevision) == 1) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(missingOrConflict(exists = dao.get(id.value) != null))
            }
        }
}
