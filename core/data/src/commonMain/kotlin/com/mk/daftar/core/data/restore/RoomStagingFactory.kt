package com.mk.daftar.core.data.restore

import com.mk.daftar.core.backup.BackupSnapshot
import com.mk.daftar.core.backup.RestoreFailure
import com.mk.daftar.core.backup.canonicalForm
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.backup.toSnapshot
import com.mk.daftar.core.data.records.RecordRules
import com.mk.daftar.core.data.session.DaftarDatabaseBuilderProvider
import com.mk.daftar.core.data.session.openDaftarDatabase
import com.mk.daftar.core.database.DaftarDatabase
import com.mk.daftar.core.database.dao.RestoreRows
import com.mk.daftar.core.database.mapper.startYearRow
import com.mk.daftar.core.database.mapper.toEntity
import com.mk.daftar.core.database.mapper.toManualEntity
import com.mk.daftar.core.database.mapper.toRemoteEntity
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.security.KeyLease
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.VaultGeneration
import com.mk.daftar.core.security.VaultGenerations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Builds a staged generation with Room. The new generation's key is created by its own security instance, the rows are
 * written and read back in one transaction, and the PIN is proven on a second, fresh instance. The security instance
 * that created the key is locked before this returns, so no key outlives the build.
 */
internal class RoomStagingFactory(
    private val generations: VaultGenerations,
    private val databases: DaftarDatabaseBuilderProvider,
    private val ioDispatcher: CoroutineDispatcher,
) : StagingFactory {

    override suspend fun build(
        generation: VaultGeneration,
        snapshot: BackupSnapshot,
        pin: SecretBytes,
    ): Outcome<Unit, RestoreFailure> {
        val security = generations.securityFor(generation)
        try {
            val lease: KeyLease = when (val created = security.create(pin)) {
                is Outcome.Failure -> return Outcome.Failure(RestoreFailure.Staging)
                is Outcome.Success -> created.data
            }
            try {
                writeAndVerify(generation, lease, snapshot).let { result ->
                    if (result is Outcome.Failure) return result
                }
            } finally {
                lease.close()
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            return Outcome.Failure(RestoreFailure.Staging)
        } finally {
            security.lock()
        }
        // The envelope must open on its own, from a fresh instance, with the PIN the user just chose.
        val proof = generations.securityFor(generation)
        try {
            return when (val opened = proof.unlock(pin)) {
                is Outcome.Failure -> Outcome.Failure(RestoreFailure.Verification)

                is Outcome.Success -> {
                    opened.data.close()
                    Outcome.Success(Unit)
                }
            }
        } finally {
            proof.lock()
        }
    }

    private suspend fun writeAndVerify(
        generation: VaultGeneration,
        lease: KeyLease,
        snapshot: BackupSnapshot,
    ): Outcome<Unit, RestoreFailure> {
        val database: DaftarDatabase = withContext(ioDispatcher) {
            openDaftarDatabase(databases.builder(generation), lease)
        }
        try {
            withContext(ioDispatcher) { database.restoreWriteDao().writeAll(snapshot.toRestoreRows()) }
            val readBack = withContext(ioDispatcher) {
                database.backupSnapshotDao().readAll().toSnapshot(snapshot.preferences.asSettings())
            }
            return if (readBack.canonicalForm() == snapshot.canonicalForm()) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(RestoreFailure.Verification)
            }
        } finally {
            withContext(NonCancellable + ioDispatcher) { database.close() }
        }
    }
}

/** The imported settings as the settings type the snapshot reader reads back, with biometrics off. */
private fun com.mk.daftar.core.backup.BackupPreferences.asSettings(): UserPreferences = UserPreferences(
    themeMode = themeMode,
    language = language,
    inactivityTimeout = inactivityTimeout,
    biometricEnabled = false,
)

/** The rows of a snapshot, in the order the foreign keys need. Every row starts at the first revision. */
internal fun BackupSnapshot.toRestoreRows(): RestoreRows = RestoreRows(
    banks = banks.map { it.toEntity(RecordRules.FIRST_REVISION) },
    bankAccounts = bankAccounts.map { it.toEntity(RecordRules.FIRST_REVISION) },
    goldHoldings = goldHoldings.map { it.toEntity(RecordRules.FIRST_REVISION) },
    cashBalances = cashBalances.map { it.toEntity(RecordRules.FIRST_REVISION) },
    zakatPayments = zakatPayments.map { it.toEntity(RecordRules.FIRST_REVISION) },
    personalNotes = personalNotes.map { it.toEntity(RecordRules.FIRST_REVISION) },
    remoteQuotes = remoteQuotes.map { it.quote.toRemoteEntity(it.nextUpdateAt, it.attributionUrl) },
    manualQuotes = manualQuotes.map { it.toManualEntity(RecordRules.FIRST_REVISION) },
    zakatYears = zakatYears.map { it.toEntity() },
    zakatTracking = zakatTrackingStartYear?.let { startYearRow(it) },
)
