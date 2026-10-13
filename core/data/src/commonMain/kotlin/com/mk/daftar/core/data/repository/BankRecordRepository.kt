package com.mk.daftar.core.data.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.records.RecordRules
import com.mk.daftar.core.data.session.VaultHost
import com.mk.daftar.core.data.session.missingOrConflict
import com.mk.daftar.core.data.storage.isForeignKeyViolation
import com.mk.daftar.core.data.storage.toStorageError
import com.mk.daftar.core.database.mapper.toBankDetail
import com.mk.daftar.core.database.mapper.toEntity
import com.mk.daftar.core.database.mapper.toStored
import com.mk.daftar.core.domain.repository.BankAccountInput
import com.mk.daftar.core.domain.repository.BankDetail
import com.mk.daftar.core.domain.repository.BankInput
import com.mk.daftar.core.domain.repository.BankRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class BankRecordRepository(private val host: VaultHost) : BankRepository {
    override fun observeAll(): Flow<VaultRead<List<Stored<Bank>>>> =
        host.observe { db -> db.bankDao().observeAll().map { rows -> rows.map { it.toStored() } } }

    override fun observe(id: BankId): Flow<VaultRead<Stored<Bank>?>> =
        host.observe { db -> db.bankDao().observe(id.value).map { row -> row?.toStored() } }

    /** The bank and its accounts from one statement, so the two cannot disagree. */
    override fun observeDetail(id: BankId): Flow<VaultRead<BankDetail?>> =
        host.observe { db -> db.bankDao().observeDetail(id.value).map { rows -> rows.toBankDetail() } }

    override fun observeAccounts(): Flow<VaultRead<List<Stored<BankAccount>>>> =
        host.observe { db -> db.bankAccountDao().observeAll().map { rows -> rows.map { it.toStored() } } }

    override fun observeAccount(id: BankAccountId): Flow<VaultRead<Stored<BankAccount>?>> =
        host.observe { db -> db.bankAccountDao().observe(id.value).map { row -> row?.toStored() } }

    override suspend fun create(input: BankInput): Outcome<Stored<Bank>, StorageError> = host.write { db ->
        val now = host.now()
        val identity = input.identity
        val bank = Bank.create(
            id = BankId(host.ids.nextId()),
            name = input.name,
            createdAt = now,
            updatedAt = now,
            institutionId = identity?.institutionId,
            identityReviewed = identity != null,
        )
        db.bankDao().insert(bank.toEntity(RecordRules.FIRST_REVISION))
        Outcome.Success(Stored(bank, RecordRules.FIRST_REVISION))
    }

    override suspend fun update(
        id: BankId,
        expectedRevision: Long,
        input: BankInput,
    ): Outcome<Stored<Bank>, StorageError> = host.write { db ->
        val dao = db.bankDao()
        val previous = dao.get(id.value)?.toStored() ?: return@write Outcome.Failure(StorageError.NotFound)
        if (!RecordRules.matches(
                previous.revision,
                expectedRevision,
            )
        ) {
            return@write Outcome.Failure(StorageError.Conflict)
        }
        // A name edit keeps the stored identity; only an explicit identity choice changes it.
        val identity = input.identity
        val updated = Bank.create(
            id = id,
            name = input.name,
            createdAt = previous.value.createdAt,
            updatedAt = RecordRules.editedAt(host.now(), previous.value.updatedAt),
            institutionId = if (identity != null) identity.institutionId else previous.value.institutionId,
            identityReviewed = if (identity != null) true else previous.value.identityReviewed,
        )
        val next = RecordRules.nextRevision(expectedRevision)
        if (dao.updateIfRevision(updated.toEntity(next), expectedRevision) == 1) {
            Outcome.Success(Stored(updated, next))
        } else {
            Outcome.Failure(missingOrConflict(exists = dao.get(id.value) != null))
        }
    }

    /**
     * The RESTRICT foreign key is the final authority: a bank that still has accounts refuses the delete, even if a
     * concurrent account insert raced with this check.
     */
    override suspend fun delete(id: BankId, expectedRevision: Long): Outcome<Unit, StorageError> =
        host.write(mapFailure = { error ->
            if (error.isForeignKeyViolation()) StorageError.BankHasAccounts else error.toStorageError(write = true)
        }) { db ->
            val dao = db.bankDao()
            if (dao.deleteIfRevision(id.value, expectedRevision) == 1) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(missingOrConflict(exists = dao.get(id.value) != null))
            }
        }

    override suspend fun createAccount(input: BankAccountInput): Outcome<Stored<BankAccount>, StorageError> =
        host.write { db ->
            // An account may only belong to a bank that exists now. The foreign key would also refuse it.
            if (db.bankDao().get(input.bankId.value) == null) return@write Outcome.Failure(StorageError.NotFound)
            val now = host.now()
            val account = BankAccount.create(
                id = BankAccountId(host.ids.nextId()),
                bankId = input.bankId,
                name = input.name,
                balance = input.balance,
                note = input.note,
                createdAt = now,
                updatedAt = now,
            )
            db.bankAccountDao().insert(account.toEntity(RecordRules.FIRST_REVISION))
            Outcome.Success(Stored(account, RecordRules.FIRST_REVISION))
        }

    override suspend fun updateAccount(
        id: BankAccountId,
        expectedRevision: Long,
        input: BankAccountInput,
    ): Outcome<Stored<BankAccount>, StorageError> = host.write { db ->
        val dao = db.bankAccountDao()
        val previous = dao.get(id.value)?.toStored() ?: return@write Outcome.Failure(StorageError.NotFound)
        if (!RecordRules.matches(
                previous.revision,
                expectedRevision,
            )
        ) {
            return@write Outcome.Failure(StorageError.Conflict)
        }
        if (db.bankDao().get(input.bankId.value) == null) return@write Outcome.Failure(StorageError.NotFound)
        val updated = BankAccount.create(
            id = id,
            bankId = input.bankId,
            name = input.name,
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

    override suspend fun deleteAccount(id: BankAccountId, expectedRevision: Long): Outcome<Unit, StorageError> =
        host.write { db ->
            val dao = db.bankAccountDao()
            if (dao.deleteIfRevision(id.value, expectedRevision) == 1) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(missingOrConflict(exists = dao.get(id.value) != null))
            }
        }
}
