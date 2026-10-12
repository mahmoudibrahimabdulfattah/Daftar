package com.mk.daftar.core.data.session

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.ids.IdGenerator
import com.mk.daftar.core.data.storage.toStorageError
import com.mk.daftar.core.database.DaftarDatabase
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * What repositories use to reach the vault. Each call borrows the open database for one guarded operation; nothing
 * here keeps a DAO, a database or a lease after the call returns.
 */
internal class VaultHost(
    private val engine: VaultSessionEngine<*, DaftarDatabase>,
    private val clock: Clock,
    val ids: IdGenerator,
) {
    fun now(): Instant = clock.now()

    fun <T> observe(read: (DaftarDatabase) -> Flow<T>): Flow<VaultRead<T>> = engine.observe(read)

    /** A read or a multi-step operation. [stillCurrent] reports whether the session that started it is still open. */
    suspend fun <T> run(
        mapFailure: (Throwable) -> StorageError = { it.toStorageError(write = false) },
        block: suspend (DaftarDatabase, () -> Boolean) -> Outcome<T, StorageError>,
    ): Outcome<T, StorageError> = when (val op = engine.run(block)) {
        is EngineOp.Done -> op.value
        EngineOp.Locked -> Outcome.Failure(StorageError.Locked)
        EngineOp.Ended -> Outcome.Failure(StorageError.SessionEnded)
        is EngineOp.Failed -> Outcome.Failure(mapFailure(op.cause))
    }

    /**
     * A mutation. It is refused once the session has ended, and a failure maps with write defaults. A success means
     * the transaction committed before this returned; a session that ends first reports [StorageError.SessionEnded].
     */
    suspend fun <T> write(
        mapFailure: (Throwable) -> StorageError = { it.toStorageError(write = true) },
        block: suspend (DaftarDatabase) -> Outcome<T, StorageError>,
    ): Outcome<T, StorageError> = run(mapFailure) { database, stillCurrent ->
        if (stillCurrent()) block(database) else Outcome.Failure(StorageError.SessionEnded)
    }
}

/** The error for a missing or stale row: missing is [StorageError.NotFound], otherwise a revision [StorageError.Conflict]. */
internal fun missingOrConflict(exists: Boolean): StorageError =
    if (exists) StorageError.Conflict else StorageError.NotFound
