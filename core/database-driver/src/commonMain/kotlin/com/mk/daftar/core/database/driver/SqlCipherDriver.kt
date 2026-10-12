package com.mk.daftar.core.database.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement

/** SQLCipher's raw-key length: a 256-bit key, written as 64 hexadecimal digits. */
internal const val RAW_KEY_BYTES = 32

private const val CIPHER_VERSION_QUERY = "PRAGMA cipher_version"
private const val SQLITE_MASTER_QUERY = "SELECT count(*) FROM sqlite_master"
private const val TEMP_STORE_PRAGMA = "PRAGMA temp_store = MEMORY"
private const val BUSY_MESSAGE = "database is locked"
private const val CANNOT_OPEN_MESSAGE = "unable to open database"

/**
 * Opens every connection through [openKeyed]. That function must return a connection that was keyed with the raw key
 * before any statement could read the file. This driver then proves the engine
 * is SQLCipher, reads the schema, and sets the temporary store. Any failure closes the connection and throws a typed
 * error. The file is never deleted, recreated or rekeyed.
 */
internal class SqlCipherDriver(
    private val keys: DatabaseKeyAccess,
    private val openKeyed: (fileName: String, rawKeyLiteral: ByteArray) -> SQLiteConnection,
) : SQLiteDriver {

    override fun open(fileName: String): SQLiteConnection = keys.useKey { key ->
        if (key.size != RAW_KEY_BYTES) throw DatabaseDriverException(DatabaseDriverError.InvalidKeyLength)
        val literal = rawKeyLiteral(key)
        val connection = try {
            openKeyed(fileName, literal)
        } catch (error: Exception) {
            throw DatabaseDriverException(classifyOpenFailure(error), error)
        } finally {
            literal.fill(0)
        }
        try {
            verifySqlCipher(connection)
            connection.executeSetting(TEMP_STORE_PRAGMA)
        } catch (error: DatabaseDriverException) {
            connection.closeQuietly()
            throw error
        } catch (error: Exception) {
            connection.closeQuietly()
            throw DatabaseDriverException(DatabaseDriverError.SetupFailed, error)
        }
        connection
    }

    private fun verifySqlCipher(connection: SQLiteConnection) {
        val version = connection.queryText(CIPHER_VERSION_QUERY)
        if (version.isNullOrEmpty()) {
            throw DatabaseDriverException(DatabaseDriverError.SqlCipherUnavailable)
        }
        try {
            connection.prepare(SQLITE_MASTER_QUERY).use { statement -> statement.step() }
        } catch (error: Exception) {
            throw DatabaseDriverException(classifyOpenFailure(error), error)
        }
    }

    /** Lock contention is transient; an unreadable file after keying means a wrong key or tampered pages. */
    private fun classifyOpenFailure(error: Exception): DatabaseDriverError {
        val message = error.message.orEmpty()
        return when {
            message.contains(BUSY_MESSAGE) -> DatabaseDriverError.Busy
            message.contains(CANNOT_OPEN_MESSAGE) -> DatabaseDriverError.OpenFailed
            else -> DatabaseDriverError.KeyRejectedOrCorrupt
        }
    }

    private fun SQLiteConnection.executeSetting(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private fun SQLiteConnection.queryText(sql: String): String? =
        prepare(sql).use { statement -> if (statement.step()) statement.getText(0) else null }

    private inline fun <T> SQLiteStatement.use(block: (SQLiteStatement) -> T): T {
        try {
            return block(this)
        } finally {
            close()
        }
    }

    private fun SQLiteConnection.closeQuietly() {
        try {
            close()
        } catch (ignored: Exception) {
            // The connection is already unusable; the original failure is the one reported.
        }
    }
}
