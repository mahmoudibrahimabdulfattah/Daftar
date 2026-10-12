package com.mk.daftar.core.domain.storage

/**
 * What an observation saw. [Locked] and [Loading] are never mistaken for an empty list: a locked or opening vault
 * exposes no data at all. [Ready] carries the session [generation] that produced the value.
 */
sealed interface VaultRead<out T> {
    /** No session is open. No data is exposed. */
    data object Locked : VaultRead<Nothing>

    /** A session is opening, or its first read has not completed. */
    data object Loading : VaultRead<Nothing>

    data class Ready<out T>(val generation: Long, val value: T) : VaultRead<T>

    data class Failed(val error: StorageError) : VaultRead<Nothing>
}

/** A record value with its storage revision. Revisions start at 1 and increase on every edit. */
data class Stored<out T>(val value: T, val revision: Long) {
    init {
        require(revision >= 1) { "revision starts at 1" }
    }
}

/**
 * Why a storage operation failed. Messages never carry record values, keys, SQL or engine text; the data layer
 * maps every engine failure to one of these.
 */
enum class StorageError {
    /** No session is open. */
    Locked,

    /** The session ended while the operation was in flight. The result is unknown until the vault is reopened. */
    SessionEnded,
    NotFound,

    /** The expected revision no longer matches the stored record. Reload before retrying. */
    Conflict,

    /** A bank cannot be deleted while accounts still reference it. */
    BankHasAccounts,
    InvalidInput,

    /** A stored row failed validation. The row is never omitted or replaced with a default. */
    CorruptData,
    Busy,
    DiskFull,
    Io,
    DatabaseUnavailable,
    UnsupportedSchema,
}
