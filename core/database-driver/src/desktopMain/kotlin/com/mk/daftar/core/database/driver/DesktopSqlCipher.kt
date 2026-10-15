package com.mk.daftar.core.database.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

/**
 * The SQLite3 Multiple Ciphers C API, bound through JNA from the library bundled with the app. Only the calls the
 * connection needs are declared. Strings cross as UTF-8.
 */
@Suppress("FunctionName", "ktlint:standard:function-naming")
internal interface Sqlite3mc : Library {
    fun sqlite3_open_v2(filename: String, db: PointerByReference, flags: Int, vfs: String?): Int

    fun sqlite3_close_v2(db: Pointer): Int

    fun sqlite3_key(db: Pointer, key: Pointer, length: Int): Int

    fun sqlite3mc_cipher_index(name: String): Int

    fun sqlite3mc_config(db: Pointer, parameter: String, value: Int): Int

    fun sqlite3mc_config_cipher(db: Pointer, cipher: String, parameter: String, value: Int): Int

    fun sqlite3_errmsg(db: Pointer): String?

    fun sqlite3_get_autocommit(db: Pointer): Int

    fun sqlite3_prepare_v2(db: Pointer, sql: ByteArray, length: Int, statement: PointerByReference, tail: Pointer?): Int

    fun sqlite3_step(statement: Pointer): Int

    fun sqlite3_reset(statement: Pointer): Int

    fun sqlite3_clear_bindings(statement: Pointer): Int

    fun sqlite3_finalize(statement: Pointer): Int

    fun sqlite3_bind_blob(statement: Pointer, index: Int, value: ByteArray, length: Int, destructor: Pointer): Int

    fun sqlite3_bind_zeroblob(statement: Pointer, index: Int, length: Int): Int

    fun sqlite3_bind_double(statement: Pointer, index: Int, value: Double): Int

    fun sqlite3_bind_int64(statement: Pointer, index: Int, value: Long): Int

    fun sqlite3_bind_text(statement: Pointer, index: Int, value: ByteArray, length: Int, destructor: Pointer): Int

    fun sqlite3_bind_null(statement: Pointer, index: Int): Int

    fun sqlite3_column_count(statement: Pointer): Int

    fun sqlite3_column_name(statement: Pointer, index: Int): String?

    fun sqlite3_column_type(statement: Pointer, index: Int): Int

    fun sqlite3_column_blob(statement: Pointer, index: Int): Pointer?

    fun sqlite3_column_text(statement: Pointer, index: Int): Pointer?

    fun sqlite3_column_bytes(statement: Pointer, index: Int): Int

    fun sqlite3_column_double(statement: Pointer, index: Int): Double

    fun sqlite3_column_int64(statement: Pointer, index: Int): Long

    companion object {
        /** Loaded once, from the app's own bundled copy: never a system SQLite that might not encrypt. */
        val native: Sqlite3mc by lazy {
            Native.load(
                "sqlite3mc",
                Sqlite3mc::class.java,
                mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"),
            )
        }
    }
}

private const val SQLITE_OK = 0
private const val SQLITE_ROW = 100
private const val SQLITE_DONE = 101
private const val SQLITE_NULL = 5
private const val SQLITE_OPEN_READWRITE = 0x00000002
private const val SQLITE_OPEN_CREATE = 0x00000004
private const val SQLITE_OPEN_FULLMUTEX = 0x00010000

/** SQLITE_TRANSIENT: SQLite copies bound memory before the call returns. */
private val SQLITE_TRANSIENT: Pointer = Pointer.createConstant(-1L)

/** SQLCipher v4 page format: the same scheme, KDF and HMAC defaults SQLCipher 4 uses on the phones. */
private const val SQLCIPHER_LEGACY_VERSION = 4

private const val BUSY_TIMEOUT_PRAGMA_SQL = "PRAGMA busy_timeout = 5000"

/** The active cipher scheme. SQLite3 Multiple Ciphers answers `sqlcipher`; a plain SQLite has no such pragma. */
internal actual val CIPHER_ENGINE_QUERY: String = "PRAGMA cipher"

/**
 * Opens the file with the bundled SQLite3 Multiple Ciphers, selects its SQLCipher v4 scheme, keys it through
 * `sqlite3_key`, and sets the busy timeout. sqlite3_open_v2 does not read the file, and the key call comes before any
 * statement can read it. The key is passed as native memory holding the `x'<hex>'` literal with its explicit length,
 * never as a SQL string, and that memory is zeroed as soon as the call returns.
 */
internal actual fun openKeyedConnection(fileName: String, rawKeyLiteral: ByteArray): SQLiteConnection {
    val connection = openHandle(fileName)
    try {
        connection.selectSqlCipherScheme()
        connection.applyRawKey(rawKeyLiteral)
        connection.executeOnce(BUSY_TIMEOUT_PRAGMA_SQL)
    } catch (error: Exception) {
        connection.close()
        throw error
    }
    return connection
}

private fun SQLiteConnection.executeOnce(sql: String) {
    val statement = prepare(sql)
    try {
        statement.step()
    } finally {
        statement.close()
    }
}

private fun openHandle(fileName: String): DesktopSqlCipherConnection {
    val api = Sqlite3mc.native
    val handle = PointerByReference()
    val rc = api.sqlite3_open_v2(
        fileName,
        handle,
        SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX,
        null,
    )
    val db = handle.value
    if (rc != SQLITE_OK || db == null) {
        if (db != null) api.sqlite3_close_v2(db)
        throw DatabaseDriverException(DatabaseDriverError.OpenFailed)
    }
    return DesktopSqlCipherConnection(api, db)
}

/** A single-threaded SQLite connection over the native API. Room serializes use of each connection. */
internal class DesktopSqlCipherConnection(private val api: Sqlite3mc, private val db: Pointer) : SQLiteConnection {
    private val statements = mutableListOf<DesktopSqlCipherStatement>()
    private var closed = false

    override fun prepare(sql: String): SQLiteStatement {
        check(!closed) { "connection is closed" }
        val bytes = sql.encodeToByteArray()
        val handle = PointerByReference()
        val rc = api.sqlite3_prepare_v2(db, bytes, bytes.size, handle, null)
        if (rc != SQLITE_OK) throw sqliteFailure(api, rc, db)
        return DesktopSqlCipherStatement(api, db, handle.value).also { statements += it }
    }

    override fun inTransaction(): Boolean = api.sqlite3_get_autocommit(db) == 0

    /** Selects the SQLCipher v4 scheme for this connection, before the key is applied. */
    fun selectSqlCipherScheme() {
        val index = api.sqlite3mc_cipher_index("sqlcipher")
        if (index <= 0 || api.sqlite3mc_config(db, "cipher", index) != index) {
            throw DatabaseDriverException(DatabaseDriverError.SqlCipherUnavailable)
        }
        if (api.sqlite3mc_config_cipher(db, "sqlcipher", "legacy", SQLCIPHER_LEGACY_VERSION) !=
            SQLCIPHER_LEGACY_VERSION
        ) {
            throw DatabaseDriverException(DatabaseDriverError.SqlCipherUnavailable)
        }
    }

    /** Keys the database before any read. The native copy is zeroed here; the caller wipes [rawKeyLiteral]. */
    fun applyRawKey(rawKeyLiteral: ByteArray) {
        check(!closed) { "connection is closed" }
        val memory = Memory(rawKeyLiteral.size.toLong())
        try {
            memory.write(0, rawKeyLiteral, 0, rawKeyLiteral.size)
            val rc = api.sqlite3_key(db, memory, rawKeyLiteral.size)
            if (rc != SQLITE_OK) throw sqliteFailure(api, rc, db)
        } finally {
            memory.clear()
            memory.close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        statements.forEach { it.finalizeStatement() }
        statements.clear()
        api.sqlite3_close_v2(db)
    }
}

/** One prepared statement. [stmt] is null only when the SQL held no executable statement, such as a comment. */
internal class DesktopSqlCipherStatement(
    private val api: Sqlite3mc,
    private val db: Pointer,
    private var stmt: Pointer?,
) : SQLiteStatement {
    private var closed = false

    override fun bindBlob(index: Int, value: ByteArray) {
        val s = active()
        val rc = if (value.isEmpty()) {
            api.sqlite3_bind_zeroblob(s, index, 0)
        } else {
            api.sqlite3_bind_blob(s, index, value, value.size, SQLITE_TRANSIENT)
        }
        requireOk(rc)
    }

    override fun bindDouble(index: Int, value: Double) {
        requireOk(api.sqlite3_bind_double(active(), index, value))
    }

    override fun bindLong(index: Int, value: Long) {
        requireOk(api.sqlite3_bind_int64(active(), index, value))
    }

    override fun bindText(index: Int, value: String) {
        val bytes = value.encodeToByteArray()
        requireOk(api.sqlite3_bind_text(active(), index, bytes, bytes.size, SQLITE_TRANSIENT))
    }

    override fun bindNull(index: Int) {
        requireOk(api.sqlite3_bind_null(active(), index))
    }

    override fun getBlob(index: Int): ByteArray {
        val s = active()
        val pointer = api.sqlite3_column_blob(s, index) ?: return ByteArray(0)
        val length = api.sqlite3_column_bytes(s, index)
        return pointer.getByteArray(0, length)
    }

    override fun getDouble(index: Int): Double = api.sqlite3_column_double(active(), index)

    override fun getLong(index: Int): Long = api.sqlite3_column_int64(active(), index)

    override fun getText(index: Int): String {
        val s = active()
        // The text pointer must be read before the byte count, as SQLite requires for text conversion.
        val pointer = api.sqlite3_column_text(s, index) ?: return ""
        val length = api.sqlite3_column_bytes(s, index)
        return pointer.getByteArray(0, length).decodeToString()
    }

    override fun isNull(index: Int): Boolean = api.sqlite3_column_type(active(), index) == SQLITE_NULL

    override fun getColumnCount(): Int = stmt?.let { api.sqlite3_column_count(it) } ?: 0

    override fun getColumnName(index: Int): String = api.sqlite3_column_name(active(), index) ?: ""

    override fun getColumnType(index: Int): Int = api.sqlite3_column_type(active(), index)

    override fun step(): Boolean {
        val s = stmt ?: return false
        check(!closed) { "statement is closed" }
        return when (val rc = api.sqlite3_step(s)) {
            SQLITE_ROW -> true
            SQLITE_DONE -> false
            else -> throw sqliteFailure(api, rc, db)
        }
    }

    override fun reset() {
        stmt?.let { api.sqlite3_reset(it) }
    }

    override fun clearBindings() {
        stmt?.let { api.sqlite3_clear_bindings(it) }
    }

    override fun close() {
        finalizeStatement()
    }

    internal fun finalizeStatement() {
        if (closed) return
        closed = true
        stmt?.let { api.sqlite3_finalize(it) }
        stmt = null
    }

    private fun active(): Pointer {
        check(!closed) { "statement is closed" }
        return checkNotNull(stmt) { "statement has no SQL" }
    }

    private fun requireOk(rc: Int) {
        if (rc != SQLITE_OK) throw sqliteFailure(api, rc, db)
    }
}

private fun sqliteFailure(api: Sqlite3mc, rc: Int, db: Pointer): SQLiteException =
    SQLiteException("Error code: $rc, message: ${api.sqlite3_errmsg(db).orEmpty()}")
