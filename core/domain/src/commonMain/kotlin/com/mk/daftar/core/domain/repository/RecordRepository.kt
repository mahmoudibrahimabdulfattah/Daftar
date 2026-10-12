package com.mk.daftar.core.domain.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import kotlinx.coroutines.flow.Flow

/**
 * The shape shared by every record type. Inputs carry no ID, timestamps or revision: the data layer assigns the ID and
 * the creation time, keeps the creation time on edit, and owns the revision. [expectedRevision] guards every change.
 */
interface RecordRepository<Id, Record, Input> {
    /** Records in the default order of their type. Emits [VaultRead.Locked] while no session is open. */
    fun observeAll(): Flow<VaultRead<List<Stored<Record>>>>

    /** One record, or `Ready(null)` when it does not exist. */
    fun observe(id: Id): Flow<VaultRead<Stored<Record>?>>

    suspend fun create(input: Input): Outcome<Stored<Record>, StorageError>

    suspend fun update(id: Id, expectedRevision: Long, input: Input): Outcome<Stored<Record>, StorageError>

    suspend fun delete(id: Id, expectedRevision: Long): Outcome<Unit, StorageError>
}
