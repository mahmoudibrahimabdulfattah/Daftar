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
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.security.BiometricCapability
import com.mk.daftar.core.security.BiometricError
import com.mk.daftar.core.security.BiometricPrompter
import com.mk.daftar.core.security.BiometricSlotState
import com.mk.daftar.core.security.KeyLease
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.VaultSecurity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Builds a fresh Room builder for one database file. Each open takes its own builder. */
fun interface DaftarDatabaseBuilderProvider {
    fun builder(): RoomDatabase.Builder<DaftarDatabase>
}

/** Why an unlock or create did not open the vault. */
sealed interface VaultOpenFailure {
    data class Security(val error: SecurityError) : VaultOpenFailure

    /** A biometric unlock was refused. The PIN path is unaffected. */
    data class Biometric(val error: BiometricError) : VaultOpenFailure

    /**
     * The vault was created, but biometrics could not be enrolled. Nothing is opened: the PIN vault stays usable and
     * its envelope is not changed by the failed enrollment.
     */
    data class Enrollment(val error: BiometricError) : VaultOpenFailure

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

    /** Runs one authentication: [attempt] unlocks or creates the vault and returns its lease, or the reason it did not. */
    internal suspend fun openWith(
        attempt: suspend () -> Outcome<KeyLease, VaultOpenFailure>,
    ): Outcome<Unit, VaultOpenFailure> = when (val result = engine.unlock(attempt)) {
        is EngineOpen.Opened -> Outcome.Success(Unit)
        EngineOpen.AlreadyOpen -> Outcome.Failure(VaultOpenFailure.AlreadyOpen)
        is EngineOpen.AttemptFailed -> Outcome.Failure(result.error)
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
 *
 * Biometric steps prove the session through the same lease. A biometric unlock opens the vault only after the DEK
 * unwraps; enrollment happens before the session is published, so Home cannot appear before it completes.
 */
class VaultAuthenticator internal constructor(
    private val security: VaultSecurity,
    private val session: DaftarVaultSession,
    private val preferences: UserPreferencesRepository,
) {
    suspend fun create(pin: SecretBytes): Outcome<Unit, VaultOpenFailure> =
        session.openWith { security.create(pin).toOpenFailure() }

    suspend fun unlock(pin: SecretBytes): Outcome<Unit, VaultOpenFailure> =
        session.openWith { security.unlock(pin).toOpenFailure() }

    /**
     * Creates the PIN vault, enrolls the same DEK for biometrics, then publishes the session. If enrollment fails, or
     * its preference cannot be stored, the vault stays closed with its PIN envelope intact, and the caller shows the PIN
     * path. The enrollment is rolled back in that case.
     */
    suspend fun createWithBiometrics(pin: SecretBytes, prompter: BiometricPrompter): Outcome<Unit, VaultOpenFailure> =
        session.openWith {
            val lease = when (val created = security.create(pin)) {
                is Outcome.Failure -> return@openWith Outcome.Failure(VaultOpenFailure.Security(created.error))
                is Outcome.Success -> created.data
            }
            when (val enrolled = security.enrollBiometrics(lease, prompter)) {
                is Outcome.Failure -> {
                    security.lock()
                    Outcome.Failure(VaultOpenFailure.Enrollment(enrolled.error))
                }

                is Outcome.Success -> when (preferences.setBiometricEnabled(true)) {
                    is Outcome.Failure -> {
                        security.disableBiometrics()
                        security.lock()
                        Outcome.Failure(VaultOpenFailure.Enrollment(BiometricError.StorageFailure))
                    }

                    is Outcome.Success -> Outcome.Success(lease)
                }
            }
        }

    /** Opens the vault with the biometric slot. The preference is corrected when the slot is no longer usable. */
    suspend fun unlockWithBiometrics(prompter: BiometricPrompter): Outcome<Unit, VaultOpenFailure> = session.openWith {
        when (val unlocked = security.unlockWithBiometrics(prompter)) {
            is Outcome.Success -> Outcome.Success(unlocked.data)

            is Outcome.Failure -> {
                if (unlocked.error.invalidatesPreference()) preferences.setBiometricEnabled(false)
                Outcome.Failure(VaultOpenFailure.Biometric(unlocked.error))
            }
        }
    }

    /** Whether the platform can authenticate with a strong biometric now. Used for the onboarding offer. */
    suspend fun biometricCapability(): BiometricCapability = security.biometricCapability()

    /**
     * Effective biometric availability. The preference is intent: biometrics count as ready only when the preference
     * is on, a valid slot exists, and the platform can authenticate. A missing or invalid slot turns the preference off;
     * an orphan slot is removed while the preference is off. A temporarily unavailable platform keeps both.
     */
    suspend fun biometricReady(): Boolean {
        val enabled = preferences.observe().first().let { read ->
            (read as? PreferencesRead.Ready)?.preferences?.biometricEnabled ?: false
        }
        val state = security.biometricSlotState().let { result ->
            when (result) {
                is Outcome.Failure -> return false
                is Outcome.Success -> result.data
            }
        }
        when (state) {
            BiometricSlotState.Absent, BiometricSlotState.Invalid -> {
                if (state == BiometricSlotState.Invalid) security.disableBiometrics()
                if (enabled) preferences.setBiometricEnabled(false)
                return false
            }

            BiometricSlotState.Valid -> {
                if (!enabled) {
                    security.disableBiometrics()
                    return false
                }
            }
        }
        return security.biometricCapability() == BiometricCapability.Available
    }

    /** Turns biometrics off. The slot and its key are destroyed; the PIN is unaffected. */
    suspend fun disableBiometrics(): Outcome<Unit, BiometricError> {
        when (val removed = security.disableBiometrics()) {
            is Outcome.Failure -> return removed
            is Outcome.Success -> Unit
        }
        return when (preferences.setBiometricEnabled(false)) {
            is Outcome.Failure -> Outcome.Failure(BiometricError.StorageFailure)
            is Outcome.Success -> Outcome.Success(Unit)
        }
    }

    /**
     * Re-enrolls biometrics for the vault after the current PIN is verified. The preference turns on only after the new
     * slot is committed; if the preference cannot be stored, the new enrollment is rolled back.
     */
    suspend fun reenrollBiometrics(pin: SecretBytes, prompter: BiometricPrompter): Outcome<Unit, BiometricError> {
        when (val enrolled = security.reenrollBiometrics(pin, prompter)) {
            is Outcome.Failure -> return enrolled
            is Outcome.Success -> Unit
        }
        return when (preferences.setBiometricEnabled(true)) {
            is Outcome.Failure -> {
                security.disableBiometrics()
                Outcome.Failure(BiometricError.StorageFailure)
            }

            is Outcome.Success -> Outcome.Success(Unit)
        }
    }
}

/** Slot-level failures after which the stored preference must not claim biometrics are available. */
private fun BiometricError.invalidatesPreference(): Boolean = when (this) {
    BiometricError.EnrollmentChanged, BiometricError.CorruptSlot, BiometricError.NotEnrolled -> true
    else -> false
}

private fun Outcome<KeyLease, SecurityError>.toOpenFailure(): Outcome<KeyLease, VaultOpenFailure> = when (this) {
    is Outcome.Success -> this
    is Outcome.Failure -> Outcome.Failure(VaultOpenFailure.Security(error))
}
