package com.mk.daftar.core.data.session

import androidx.room.RoomDatabase
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.ids.IdGenerator
import com.mk.daftar.core.data.storage.isUnavailableAtOpen
import com.mk.daftar.core.data.storage.toStorageError
import com.mk.daftar.core.database.DaftarDatabase
import com.mk.daftar.core.database.buildDaftarDatabase
import com.mk.daftar.core.database.dao.ManualQuoteDao
import com.mk.daftar.core.database.driver.createSqlCipherDriver
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.security.KeyLease
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.VaultSecurity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Builds a fresh Room builder for one database file. Each open takes its own builder. */
fun interface DaftarDatabaseBuilderProvider {
    fun builder(): RoomDatabase.Builder<DaftarDatabase>
}

/** Why an unlock or create did not open the vault. */
sealed interface VaultOpenFailure {
    data class Security(val error: SecurityError) : VaultOpenFailure

    data class Storage(val error: com.mk.daftar.core.domain.storage.StorageError) : VaultOpenFailure

    /** A session is already open. */
    data object AlreadyOpen : VaultOpenFailure

    /** A lock arrived while the attempt was in flight. The late result was discarded. */
    data object Superseded : VaultOpenFailure
}

/**
 * The one owner of the vault's database and key. Each unlocked generation owns exactly one key lease and one Room
 * instance; repositories borrow the open database for the duration of a guarded operation and never keep it.
 *
 * Unlocking goes through [VaultAuthenticator], which keeps PIN buffers out of the domain layer.
 */
class DaftarVaultSession internal constructor(
    private val security: VaultSecurity,
    private val databases: DaftarDatabaseBuilderProvider,
    clock: Clock,
    ids: IdGenerator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    sessionDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : VaultSession {

    private val engine = VaultSessionEngine<KeyLease, DaftarDatabase>(
        openDatabase = ::openDatabase,
        closeDatabase = ::closeDatabase,
        releaseLease = { lease -> lease.close() },
        revokeAuthentication = { security.lock() },
        isUnavailable = { error -> error.toStorageError().isUnavailableAtOpen() },
        sessionDispatcher = sessionDispatcher,
    )

    internal val host: VaultHost = VaultHost(engine, clock, ids)

    override val state: StateFlow<VaultSessionState> = engine.state

    override suspend fun lock() {
        engine.lock()
    }

    /** Runs one authentication: [attempt] unlocks or creates the vault and returns its lease. */
    internal suspend fun openWith(
        attempt: suspend () -> Outcome<KeyLease, SecurityError>,
    ): Outcome<Unit, VaultOpenFailure> = when (val result = engine.unlock(attempt)) {
        is EngineOpen.Opened -> Outcome.Success(Unit)
        EngineOpen.AlreadyOpen -> Outcome.Failure(VaultOpenFailure.AlreadyOpen)
        is EngineOpen.AttemptFailed -> Outcome.Failure(VaultOpenFailure.Security(result.error))
        EngineOpen.Superseded -> Outcome.Failure(VaultOpenFailure.Superseded)
        is EngineOpen.OpenFailed -> Outcome.Failure(VaultOpenFailure.Storage(result.cause.toStorageError()))
    }

    /**
     * Builds Room over the lease's raw key, then forces an actual open. The read runs Room's identity check and the
     * schema callback, so a bad file fails here rather than in a later repository call.
     */
    private suspend fun openDatabase(lease: KeyLease): DaftarDatabase = withContext(ioDispatcher) {
        val database = buildDaftarDatabase(databases.builder(), createSqlCipherDriver(lease))
        try {
            database.manualQuoteDao().get(FORCE_OPEN_KEY)
            database
        } catch (error: Throwable) {
            withContext(NonCancellable) { database.close() }
            throw error
        }
    }

    private suspend fun closeDatabase(database: DaftarDatabase) {
        withContext(ioDispatcher) { database.close() }
    }

    private companion object {
        /** A key that always exists in the schema, so forcing the open reads a real table. */
        val FORCE_OPEN_KEY: String = "USD_EGP"
    }
}

/**
 * Composition-facing unlock and create. Owns no database state of its own: it hands the PIN to [VaultSecurity] and the
 * resulting lease to [DaftarVaultSession]. The caller owns the PIN buffers and wipes them after the call.
 */
class VaultAuthenticator internal constructor(
    private val security: VaultSecurity,
    private val session: DaftarVaultSession,
) {
    suspend fun create(pin: com.mk.daftar.core.security.SecretBytes): Outcome<Unit, VaultOpenFailure> =
        session.openWith { security.create(pin) }

    suspend fun unlock(pin: com.mk.daftar.core.security.SecretBytes): Outcome<Unit, VaultOpenFailure> =
        session.openWith { security.unlock(pin) }
}
