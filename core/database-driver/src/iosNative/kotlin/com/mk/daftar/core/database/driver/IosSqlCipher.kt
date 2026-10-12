@file:OptIn(ExperimentalForeignApi::class)

package com.mk.daftar.core.database.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import sqlcipher.SQLITE_DONE
import sqlcipher.SQLITE_NULL
import sqlcipher.SQLITE_OK
import sqlcipher.SQLITE_OPEN_CREATE
import sqlcipher.SQLITE_OPEN_FULLMUTEX
import sqlcipher.SQLITE_OPEN_READWRITE
import sqlcipher.SQLITE_ROW
import sqlcipher.sqlite3_bind_blob
import sqlcipher.sqlite3_bind_double
import sqlcipher.sqlite3_bind_int64
import sqlcipher.sqlite3_bind_null
import sqlcipher.sqlite3_bind_text
import sqlcipher.sqlite3_bind_zeroblob
import sqlcipher.sqlite3_clear_bindings
import sqlcipher.sqlite3_close_v2
import sqlcipher.sqlite3_column_blob
import sqlcipher.sqlite3_column_bytes
import sqlcipher.sqlite3_column_count
import sqlcipher.sqlite3_column_double
import sqlcipher.sqlite3_column_int64
import sqlcipher.sqlite3_column_name
import sqlcipher.sqlite3_column_text
import sqlcipher.sqlite3_column_type
import sqlcipher.sqlite3_errmsg
import sqlcipher.sqlite3_finalize
import sqlcipher.sqlite3_get_autocommit
import sqlcipher.sqlite3_key
import sqlcipher.sqlite3_open_v2
import sqlcipher.sqlite3_prepare_v2
import sqlcipher.sqlite3_reset
import sqlcipher.sqlite3_step

/** SQLITE_TRANSIENT: SQLite copies bound memory before the call returns. The C macro is not exported to Kotlin. */
private val SQLITE_TRANSIENT = (-1L).toCPointer<CFunction<(COpaquePointer?) -> Unit>>()

/**
 * Opens the file with the SQLCipher symbols linked into the app, keys it through `sqlite3_key`, and sets the busy
 * timeout. sqlite3_open_v2 does not read the file, and the key call comes before any statement can read it. The key
 * is passed as the mutable `x'<hex>'` buffer with its explicit length, never as a SQL string.
 */
internal actual fun openKeyedConnection(fileName: String, rawKeyLiteral: ByteArray): SQLiteConnection {
    val connection = openHandle(fileName)
    try {
        connection.applyRawKey(rawKeyLiteral)
        connection.executeOnce(BUSY_TIMEOUT_PRAGMA_SQL)
    } catch (error: Exception) {
        connection.close()
        throw error
    }
    return connection
}

private const val BUSY_TIMEOUT_PRAGMA_SQL = "PRAGMA busy_timeout = 5000"

private fun SQLiteConnection.executeOnce(sql: String) {
    val statement = prepare(sql)
    try {
        statement.step()
    } finally {
        statement.close()
    }
}

private fun openHandle(fileName: String): NativeSqlCipherConnection = memScoped {
    val handle = alloc<CPointerVar<sqlite3>>()
    val rc = sqlite3_open_v2(
        fileName,
        handle.ptr,
        SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX,
        null,
    )
    val db = handle.value
    if (rc != SQLITE_OK || db == null) {
        if (db != null) sqlite3_close_v2(db)
        throw DatabaseDriverException(DatabaseDriverError.OpenFailed)
    }
    NativeSqlCipherConnection(db)
}

/** A single-threaded SQLite connection over SQLCipher's C API. Room serializes use of each connection. */
internal class NativeSqlCipherConnection(private val db: CPointer<sqlite3>) : SQLiteConnection {
    private val statements = mutableListOf<NativeSqlCipherStatement>()
    private var closed = false

    override fun prepare(sql: String): SQLiteStatement {
        check(!closed) { "connection is closed" }
        return memScoped {
            val handle = alloc<CPointerVar<sqlite3_stmt>>()
            val rc = sqlite3_prepare_v2(db, sql, -1, handle.ptr, null)
            if (rc != SQLITE_OK) throw sqliteFailure(rc, db)
            NativeSqlCipherStatement(db, handle.value).also { statements += it }
        }
    }

    override fun inTransaction(): Boolean = sqlite3_get_autocommit(db) == 0

    /** Keys the database before any read. The caller wipes [rawKeyLiteral] after this returns. */
    fun applyRawKey(rawKeyLiteral: ByteArray) {
        check(!closed) { "connection is closed" }
        val rc = rawKeyLiteral.usePinned { pinned -> sqlite3_key(db, pinned.addressOf(0), rawKeyLiteral.size) }
        if (rc != SQLITE_OK) throw sqliteFailure(rc, db)
    }

    override fun close() {
        if (closed) return
        closed = true
        statements.forEach { it.finalizeStatement() }
        statements.clear()
        sqlite3_close_v2(db)
    }
}

/** One prepared statement. [stmt] is null only when the SQL held no executable statement, such as a comment. */
internal class NativeSqlCipherStatement(private val db: CPointer<sqlite3>, private var stmt: CPointer<sqlite3_stmt>?) :
    SQLiteStatement {
    private var closed = false

    override fun bindBlob(index: Int, value: ByteArray) {
        val s = active()
        val rc = if (value.isEmpty()) {
            sqlite3_bind_zeroblob(s, index, 0)
        } else {
            value.usePinned { sqlite3_bind_blob(s, index, it.addressOf(0), value.size, SQLITE_TRANSIENT) }
        }
        requireOk(rc)
    }

    override fun bindDouble(index: Int, value: Double) {
        requireOk(sqlite3_bind_double(active(), index, value))
    }

    override fun bindLong(index: Int, value: Long) {
        requireOk(sqlite3_bind_int64(active(), index, value))
    }

    override fun bindText(index: Int, value: String) {
        val s = active()
        val bytes = value.encodeToByteArray()
        val rc = if (bytes.isEmpty()) {
            sqlite3_bind_text(s, index, "", 0, SQLITE_TRANSIENT)
        } else {
            bytes.usePinned { sqlite3_bind_text(s, index, it.addressOf(0), bytes.size, SQLITE_TRANSIENT) }
        }
        requireOk(rc)
    }

    override fun bindNull(index: Int) {
        requireOk(sqlite3_bind_null(active(), index))
    }

    override fun getBlob(index: Int): ByteArray {
        val s = active()
        val length = sqlite3_column_bytes(s, index)
        val pointer = sqlite3_column_blob(s, index) ?: return ByteArray(0)
        return pointer.reinterpret<ByteVar>().readBytes(length)
    }

    override fun getDouble(index: Int): Double = sqlite3_column_double(active(), index)

    override fun getLong(index: Int): Long = sqlite3_column_int64(active(), index)

    override fun getText(index: Int): String {
        val s = active()
        // The text pointer must be read before the byte count, as SQLite requires for text conversion.
        val pointer = sqlite3_column_text(s, index) ?: return ""
        val length = sqlite3_column_bytes(s, index)
        return pointer.reinterpret<ByteVar>().readBytes(length).decodeToString()
    }

    override fun isNull(index: Int): Boolean = sqlite3_column_type(active(), index) == SQLITE_NULL

    override fun getColumnCount(): Int = stmt?.let { sqlite3_column_count(it) } ?: 0

    override fun getColumnName(index: Int): String = sqlite3_column_name(active(), index)?.toKString() ?: ""

    override fun getColumnType(index: Int): Int = sqlite3_column_type(active(), index)

    override fun step(): Boolean {
        val s = stmt ?: return false
        return when (val rc = sqlite3_step(s)) {
            SQLITE_ROW -> true
            SQLITE_DONE -> false
            else -> throw sqliteFailure(rc, db)
        }
    }

    override fun reset() {
        stmt?.let { sqlite3_reset(it) }
    }

    override fun clearBindings() {
        stmt?.let { sqlite3_clear_bindings(it) }
    }

    override fun close() {
        if (closed) return
        finalizeStatement()
    }

    internal fun finalizeStatement() {
        if (closed) return
        closed = true
        stmt?.let { sqlite3_finalize(it) }
        stmt = null
    }

    private fun active(): CPointer<sqlite3_stmt> {
        check(!closed) { "statement is closed" }
        return checkNotNull(stmt) { "statement has no executable SQL" }
    }

    private fun requireOk(rc: Int) {
        if (rc != SQLITE_OK) throw sqliteFailure(rc, db)
    }
}

private fun sqliteFailure(rc: Int, db: CPointer<sqlite3>): SQLiteException {
    // The engine message can name a table or column but never carries key material or row values.
    val message = sqlite3_errmsg(db)?.toKString() ?: "SQLite error"
    return SQLiteException("SQLite error $rc: $message")
}
