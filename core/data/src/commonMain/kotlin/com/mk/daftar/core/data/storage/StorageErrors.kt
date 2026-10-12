package com.mk.daftar.core.data.storage

import androidx.sqlite.SQLiteException
import com.mk.daftar.core.database.driver.DatabaseDriverError
import com.mk.daftar.core.database.driver.DatabaseDriverException
import com.mk.daftar.core.database.mapper.CorruptRowException
import com.mk.daftar.core.domain.storage.StorageError

/**
 * Maps an engine failure to the domain's storage error. Messages are read only to classify the failure and are never
 * kept, returned or logged, since they can name tables, values or keys.
 *
 * [write] selects the default for an unrecognized SQLite failure: a rejected write is most likely a validation
 * trigger, while an unrecognized read failure is treated as an I/O problem.
 */
internal fun Throwable.toStorageError(write: Boolean = false): StorageError = when (this) {
    is CorruptRowException -> StorageError.CorruptData

    is DatabaseDriverException -> when (error) {
        DatabaseDriverError.KeyUnavailable -> StorageError.Locked

        DatabaseDriverError.Busy -> StorageError.Busy

        DatabaseDriverError.OpenFailed -> StorageError.Io

        DatabaseDriverError.KeyRejectedOrCorrupt,
        DatabaseDriverError.InvalidKeyLength,
        DatabaseDriverError.SqlCipherUnavailable,
        DatabaseDriverError.SetupFailed,
        -> StorageError.DatabaseUnavailable
    }

    is SQLiteException -> sqliteStorageError(message.orEmpty(), write)

    is IllegalArgumentException -> StorageError.InvalidInput

    is IllegalStateException -> illegalStateStorageError(message.orEmpty())

    else -> StorageError.Io
}

/** True for the storage failures that mean the vault cannot be used until it is repaired or reinstalled. */
internal fun StorageError.isUnavailableAtOpen(): Boolean = when (this) {
    StorageError.UnsupportedSchema, StorageError.CorruptData, StorageError.DatabaseUnavailable -> true
    else -> false
}

/** True when a SQLite failure is the RESTRICT foreign key refusing a delete. */
internal fun Throwable.isForeignKeyViolation(): Boolean =
    this is SQLiteException && isForeignKeyMessage(message.orEmpty())

/** Classifies a SQLite message. Exposed for tests, since the Android host stubs drop exception messages. */
internal fun isForeignKeyMessage(message: String): Boolean = "foreign key" in message.lowercase()

/** Classifies SQLite message text into a storage error. Exposed for tests; see [toStorageError]. */
internal fun sqliteStorageError(message: String, write: Boolean): StorageError {
    val text = message.lowercase()
    return when {
        "database is locked" in text || "sqlite_busy" in text || "(code 5" in text -> StorageError.Busy
        "disk is full" in text || "sqlite_full" in text || "database or disk is full" in text -> StorageError.DiskFull
        "unique constraint" in text || "primary key" in text -> StorageError.Conflict
        "foreign key" in text || "constraint" in text -> StorageError.InvalidInput
        "malformed" in text || "not a database" in text -> StorageError.CorruptData
        "disk i/o" in text || "i/o error" in text || "unable to open" in text -> StorageError.Io
        write -> StorageError.InvalidInput
        else -> StorageError.Io
    }
}

private fun illegalStateStorageError(message: String): StorageError {
    val text = message.lowercase()
    return if ("schema" in text || "identity" in text || "migration" in text) {
        StorageError.UnsupportedSchema
    } else {
        StorageError.DatabaseUnavailable
    }
}
