package com.mk.daftar.core.data.backup

import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupPreferences
import com.mk.daftar.core.backup.BackupSnapshot
import com.mk.daftar.core.backup.BackupSnapshotSource
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.session.VaultHost
import com.mk.daftar.core.database.dao.BackupRows
import com.mk.daftar.core.database.mapper.toModel
import com.mk.daftar.core.database.mapper.toRecord
import com.mk.daftar.core.database.mapper.toStored
import com.mk.daftar.core.database.mapper.trackingStartYear
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.storage.StorageError
import kotlinx.coroutines.flow.first

/**
 * Reads one logical snapshot of the open vault. The eight tables come from a single transaction, and the preferences
 * are one committed read taken on their own, so the two parts are each consistent. Preferences never carry the
 * biometric flag into the snapshot.
 */
internal class VaultBackupSnapshotReader(
    private val host: VaultHost,
    private val preferences: UserPreferencesRepository,
) : BackupSnapshotSource {

    override suspend fun read(): Outcome<BackupSnapshot, BackupError> {
        val settings = when (val read = preferences.observe().first()) {
            is PreferencesRead.Failed -> return Outcome.Failure(BackupError.Storage(read.error))
            is PreferencesRead.Ready -> read.preferences
        }
        return when (
            val result = host.run { db, stillCurrent ->
                val rows = db.backupSnapshotDao().readAll()
                if (stillCurrent()) {
                    Outcome.Success(rows.toSnapshot(settings))
                } else {
                    Outcome.Failure(StorageError.SessionEnded)
                }
            }
        ) {
            is Outcome.Failure -> Outcome.Failure(BackupError.Storage(result.error))
            is Outcome.Success -> Outcome.Success(result.data)
        }
    }
}

/** Validated model values for every stored row. A row that fails validation fails the whole snapshot. */
internal fun BackupRows.toSnapshot(settings: UserPreferences): BackupSnapshot = BackupSnapshot(
    preferences = BackupPreferences(
        themeMode = settings.themeMode,
        language = settings.language,
        inactivityTimeout = settings.inactivityTimeout,
    ),
    goldHoldings = goldHoldings.map { it.toStored().value },
    banks = banks.map { it.toStored().value },
    bankAccounts = bankAccounts.map { it.toStored().value },
    cashBalances = cashBalances.map { it.toStored().value },
    zakatPayments = zakatPayments.map { it.toStored().value },
    personalNotes = personalNotes.map { it.toStored().value },
    remoteQuotes = remoteQuotes.map { it.toRecord() },
    manualQuotes = manualQuotes.map { it.toStored().value },
    zakatYears = zakatYears.map { it.toModel() },
    zakatTrackingStartYear = zakatTracking.trackingStartYear(),
)
