package com.mk.daftar.core.data.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.datastore.DaftarPreferencesDataSource
import com.mk.daftar.core.domain.repository.FirstRunGuideRepository
import com.mk.daftar.core.domain.storage.StorageError
import kotlinx.coroutines.flow.Flow

/** The first-run flag over the non-sensitive preferences store. Readable while the vault is locked. */
internal class FirstRunGuideRecordRepository(private val source: DaftarPreferencesDataSource) :
    FirstRunGuideRepository {
    override fun observeDone(): Flow<Boolean?> = source.observeFirstRunGuideDone()

    override suspend fun markDone(): Outcome<Unit, StorageError> = source.setFirstRunGuideDone(true)

    override suspend fun reset(): Outcome<Unit, StorageError> = source.setFirstRunGuideDone(false)
}
