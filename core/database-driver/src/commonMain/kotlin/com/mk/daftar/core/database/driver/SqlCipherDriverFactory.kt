package com.mk.daftar.core.database.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import com.mk.daftar.core.security.KeyLease

/**
 * Bounded access to the 32-byte database key. The bytes are valid only inside [useKey]; implementations must not
 * keep or return them.
 */
interface DatabaseKeyAccess {
    /** Runs [block] with the key. Throws [DatabaseDriverException] with [DatabaseDriverError.KeyUnavailable] if closed. */
    fun <R> useKey(block: (ByteArray) -> R): R
}

/** Adapts a vault [KeyLease] to the bounded accessor. The lease stays owned by the vault session. */
fun KeyLease.databaseKeyAccess(): DatabaseKeyAccess = object : DatabaseKeyAccess {
    override fun <R> useKey(block: (ByteArray) -> R): R {
        if (isClosed) throw DatabaseDriverException(DatabaseDriverError.KeyUnavailable)
        return useDatabaseKey(block)
    }
}

/**
 * The only public way to get a SQLite driver for the private database. Every connection it opens is keyed with
 * SQLCipher's raw-key encoding, verified as SQLCipher, and only then handed to Room. The result never falls back
 * to an unencrypted engine.
 */
fun createSqlCipherDriver(keys: DatabaseKeyAccess): SQLiteDriver = SqlCipherDriver(keys, ::openKeyedConnection)

/** Convenience overload for the vault lease. Equivalent to `createSqlCipherDriver(lease.databaseKeyAccess())`. */
fun createSqlCipherDriver(lease: KeyLease): SQLiteDriver = createSqlCipherDriver(lease.databaseKeyAccess())

/**
 * Opens [fileName] over SQLCipher with the raw key [rawKeyLiteral] (`x'<64 hex>'`), applied before any statement can
 * read the file. The platform also sets a busy timeout, so concurrent first opens wait instead of failing. The caller
 * wipes [rawKeyLiteral] after this returns, so implementations must copy it if they keep it. Throws on any failure,
 * with the connection already closed.
 */
internal expect fun openKeyedConnection(fileName: String, rawKeyLiteral: ByteArray): SQLiteConnection
