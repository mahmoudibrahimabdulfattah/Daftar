package com.mk.daftar.core.database.driver

/**
 * Why a keyed database connection could not be opened. Messages carry no key material, file contents or SQL text.
 */
sealed interface DatabaseDriverError {
    /** The key lease was already closed (vault locked) before the connection was opened. */
    data object KeyUnavailable : DatabaseDriverError

    /** The key is not the 32 bytes the raw-key encoding requires. */
    data object InvalidKeyLength : DatabaseDriverError

    /** The file could not be opened at all. */
    data object OpenFailed : DatabaseDriverError

    /** `PRAGMA cipher_version` returned nothing: the library is not SQLCipher. */
    data object SqlCipherUnavailable : DatabaseDriverError

    /**
     * Another connection held the file (or its recovery lock) longer than the busy timeout. The file is intact and
     * the operation can be retried; this is not a key failure.
     */
    data object Busy : DatabaseDriverError

    /** `sqlite_master` could not be read: wrong key, a file that is not this database, or tampered pages. */
    data object KeyRejectedOrCorrupt : DatabaseDriverError

    /** A required connection setting (for example `temp_store`) could not be applied. */
    data object SetupFailed : DatabaseDriverError
}

/**
 * Thrown by the driver when a connection cannot be opened. The connection has already been closed, and the file
 * has not been deleted, recreated or rekeyed.
 */
class DatabaseDriverException internal constructor(val error: DatabaseDriverError, cause: Throwable? = null) :
    RuntimeException("Database connection failed: $error", cause) {
    override fun toString(): String = "DatabaseDriverException($error)"
}
