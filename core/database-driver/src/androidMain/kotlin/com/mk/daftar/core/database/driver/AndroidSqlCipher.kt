package com.mk.daftar.core.database.driver

import androidx.sqlite.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.driver.SQLCipherConnection

private const val ANDROID_BUSY_TIMEOUT_PRAGMA = "PRAGMA busy_timeout = 5000"

/** Loads the SQLCipher native library once, before the first database opens. */
private object SqlCipherNativeLibrary {
    init {
        System.loadLibrary("sqlcipher")
    }

    fun ensureLoaded() = Unit
}

/**
 * Opens the file through Zetetic's engine with the raw key as the password. Zetetic hands that password to
 * SQLCipher's `sqlite3_key`, which reads the `x'<hex>'` form as a raw key, before its own configuration reads
 * (`PRAGMA journal_mode` and the `sqlite_schema` check). Zetetic's own `PRAGMA key` path through a pre-open hook was
 * tried and failed on those configuration reads, so this is the sequence Zetetic's open is built around.
 *
 * The busy timeout is set from `postKey`, after the key and before any read. [SQLCipherConnection] then provides the
 * statement semantics over this one keyed database. Zetetic's own driver is not used because it takes a passphrase
 * at construction, which would bypass this module's keying and verification.
 */
internal actual fun openKeyedConnection(fileName: String, rawKeyLiteral: ByteArray): SQLiteConnection {
    SqlCipherNativeLibrary.ensureLoaded()
    val hook = object : SQLiteDatabaseHook {
        override fun preKey(connection: net.zetetic.database.sqlcipher.SQLiteConnection) = Unit

        override fun postKey(connection: net.zetetic.database.sqlcipher.SQLiteConnection) {
            connection.executeRaw(ANDROID_BUSY_TIMEOUT_PRAGMA, null, null)
        }
    }
    val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
    // Zetetic keeps the password array for later pooled connections, so it receives a copy the caller can wipe.
    val database = SQLiteDatabase.openDatabase(
        fileName,
        rawKeyLiteral.copyOf(),
        null as SQLiteDatabase.CursorFactory?,
        flags,
        hook,
    )
    return SQLCipherConnection(database)
}

/** SQLCipher reports its version; a plain SQLite build has no such pragma. */
internal actual val CIPHER_ENGINE_QUERY: String = "PRAGMA cipher_version"
