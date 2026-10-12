package com.mk.daftar.core.database.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Host tests for the keying sequence, using an engine double. The real SQLCipher engine is covered by the device
 * and simulator tests, because the Android host has no native library.
 */
class SqlCipherDriverTest {

    private val key = ByteArray(RAW_KEY_BYTES) { (it * 7 + 3).toByte() }

    private class FixedKeys(private val key: ByteArray?) : DatabaseKeyAccess {
        override fun <R> useKey(block: (ByteArray) -> R): R {
            val current = key ?: throw DatabaseDriverException(DatabaseDriverError.KeyUnavailable)
            return block(current.copyOf())
        }
    }

    private class Engine(
        val cipherVersion: String = "4.6.1 community",
        val masterReadFails: Boolean = false,
        val masterReadMessage: String = "file is not a database",
        val keyRejected: Boolean = false,
    ) {
        val statements = mutableListOf<String>()
        var openCount = 0
        var closedCount = 0
    }

    private class Connection(private val engine: Engine) : SQLiteConnection {
        override fun prepare(sql: String): SQLiteStatement = Statement(engine, sql)
        override fun inTransaction(): Boolean = false
        override fun close() {
            engine.closedCount++
        }
    }

    private class Statement(private val engine: Engine, private val sql: String) : SQLiteStatement {
        override fun step(): Boolean {
            engine.statements += sql
            if (sql == "SELECT count(*) FROM sqlite_master" && engine.masterReadFails) {
                throw IllegalStateException(engine.masterReadMessage)
            }
            return sql == "PRAGMA cipher_version" || sql.startsWith("SELECT count")
        }

        override fun getText(index: Int): String = if (sql == "PRAGMA cipher_version") engine.cipherVersion else ""
        override fun getLong(index: Int): Long = 0
        override fun getDouble(index: Int): Double = 0.0
        override fun getBlob(index: Int): ByteArray = ByteArray(0)
        override fun isNull(index: Int): Boolean = false
        override fun getColumnCount(): Int = 0
        override fun getColumnName(index: Int): String = ""
        override fun getColumnType(index: Int): Int = 0
        override fun bindBlob(index: Int, value: ByteArray) = Unit
        override fun bindDouble(index: Int, value: Double) = Unit
        override fun bindLong(index: Int, value: Long) = Unit
        override fun bindText(index: Int, value: String) = Unit
        override fun bindNull(index: Int) = Unit
        override fun reset() = Unit
        override fun clearBindings() = Unit
        override fun close() = Unit
    }

    /** The platform open applies the key first; the double records that statement, as the real bridge does. */
    private fun driver(engine: Engine, keys: DatabaseKeyAccess = FixedKeys(key)) = SqlCipherDriver(keys) { _, literal ->
        engine.openCount++
        engine.statements += rawKeyPragma(literal)
        if (engine.keyRejected) throw IllegalStateException("file is not a database")
        Connection(engine)
    }

    @Test
    fun keyIsAppliedBeforeAnySchemaRead() {
        val engine = Engine()
        val connection = driver(engine).open("notes.db")

        assertEquals(
            listOf(
                "PRAGMA key = \"x'" + key.joinToString("") { hexOf(it) } + "'\"",
                "PRAGMA cipher_version",
                "SELECT count(*) FROM sqlite_master",
                "PRAGMA temp_store = MEMORY",
            ),
            engine.statements,
        )
        assertEquals(0, engine.closedCount)
        connection.close()
    }

    @Test
    fun everyOpenedConnectionRunsTheKeyingSequenceItself() {
        val engine = Engine()
        val driver = driver(engine)
        driver.open("notes.db").close()
        driver.open("notes.db").close()
        assertEquals(2, engine.openCount)
        assertEquals(2 * 4, engine.statements.size)
        assertTrue(engine.statements.chunked(4).all { it.first().startsWith("PRAGMA key") })
    }

    @Test
    fun aMissingSqlCipherEngineIsRejectedAndClosed() {
        val engine = Engine(cipherVersion = "")
        val error = assertFailsWith<DatabaseDriverException> { driver(engine).open("notes.db") }
        assertEquals(DatabaseDriverError.SqlCipherUnavailable, error.error)
        assertEquals(1, engine.closedCount)
    }

    @Test
    fun anUnreadableSchemaMeansWrongKeyOrCorruptFileAndIsClosed() {
        val engine = Engine(masterReadFails = true)
        val error = assertFailsWith<DatabaseDriverException> { driver(engine).open("notes.db") }
        assertEquals(DatabaseDriverError.KeyRejectedOrCorrupt, error.error)
        assertEquals(1, engine.closedCount)
        assertFalse(engine.statements.contains("PRAGMA temp_store = MEMORY"))
    }

    @Test
    fun aLockedFileIsReportedAsBusyNotAsAWrongKey() {
        val engine = Engine(masterReadFails = true, masterReadMessage = "database is locked (code 5)")
        val error = assertFailsWith<DatabaseDriverException> { driver(engine).open("notes.db") }
        assertEquals(DatabaseDriverError.Busy, error.error)
        assertEquals(1, engine.closedCount)
    }

    @Test
    fun anEngineThatRejectsTheKeyIsReportedAsRejectedWithoutAConnection() {
        val engine = Engine(keyRejected = true)
        val error = assertFailsWith<DatabaseDriverException> { driver(engine).open("notes.db") }
        assertEquals(DatabaseDriverError.KeyRejectedOrCorrupt, error.error)
        assertEquals(0, engine.closedCount)
    }

    @Test
    fun aKeyOfTheWrongLengthIsRejectedBeforeAnyStatementUsesIt() {
        val engine = Engine()
        val shortKey = FixedKeys(ByteArray(31))
        val error = assertFailsWith<DatabaseDriverException> { driver(engine, shortKey).open("notes.db") }
        assertEquals(DatabaseDriverError.InvalidKeyLength, error.error)
        assertTrue(engine.statements.isEmpty())
        assertEquals(0, engine.closedCount)
    }

    @Test
    fun aClosedLeaseFailsWithKeyUnavailable() {
        val engine = Engine()
        val error = assertFailsWith<DatabaseDriverException> { driver(engine, FixedKeys(null)).open("notes.db") }
        assertEquals(DatabaseDriverError.KeyUnavailable, error.error)
        assertTrue(engine.statements.isEmpty())
    }

    @Test
    fun aFailedOpenIsReportedWithoutThePath() {
        val failing = SqlCipherDriver(FixedKeys(key)) { _, _ ->
            throw IllegalStateException("unable to open database file: /private/path/notes.db")
        }
        val error = assertFailsWith<DatabaseDriverException> { failing.open("notes.db") }
        assertEquals(DatabaseDriverError.OpenFailed, error.error)
        assertFalse(error.toString().contains("/private/path"))
        assertFalse(error.message.orEmpty().contains("/private/path"))
    }

    @Test
    fun rawKeyPragmaUsesTheRawKeyEncodingWithLowercaseHex() {
        val bytes = ByteArray(RAW_KEY_BYTES) { it.toByte() }
        val literal = rawKeyLiteral(bytes)
        assertEquals(
            "PRAGMA key = \"x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'\"",
            rawKeyPragma(literal),
        )
        literal.fill(0)
    }

    @Test
    fun rawKeyPragmaEncodesHighBytesAsTwoDigits() {
        val literal = rawKeyLiteral(ByteArray(RAW_KEY_BYTES) { 0xFF.toByte() })
        assertEquals("PRAGMA key = \"x'" + "ff".repeat(RAW_KEY_BYTES) + "'\"", rawKeyPragma(literal))
    }

    @Test
    fun rawKeyLiteralRejectsAnyLengthOtherThan32Bytes() {
        assertFailsWith<IllegalArgumentException> { rawKeyLiteral(ByteArray(16)) }
        assertFailsWith<IllegalArgumentException> { rawKeyLiteral(ByteArray(33)) }
    }

    private fun hexOf(byte: Byte): String {
        val value = byte.toInt() and 0xFF
        return "0123456789abcdef"[value ushr 4].toString() + "0123456789abcdef"[value and 0x0F]
    }
}
