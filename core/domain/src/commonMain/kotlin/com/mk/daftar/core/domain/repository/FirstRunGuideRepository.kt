package com.mk.daftar.core.domain.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.storage.StorageError
import kotlinx.coroutines.flow.Flow

/**
 * Whether the first-run guide was dismissed or completed for the vault on this device. A flag only: it holds no
 * identity, no record and no analytics. A new vault starts with the flag cleared.
 */
interface FirstRunGuideRepository {
    /** The stored flag. Null while the preferences cannot be read, so the guide is never shown on a guess. */
    fun observeDone(): Flow<Boolean?>

    /** Marks the guide done. Idempotent. */
    suspend fun markDone(): Outcome<Unit, StorageError>

    /** Clears the flag for a new vault. */
    suspend fun reset(): Outcome<Unit, StorageError>
}
