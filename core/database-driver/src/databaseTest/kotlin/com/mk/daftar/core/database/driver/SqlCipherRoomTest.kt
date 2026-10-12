package com.mk.daftar.core.database.driver

import androidx.sqlite.SQLiteConnection
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest

/**
 * Room over the SQLCipher driver, on a real engine. Runs on the Android device and on the iOS simulator; every test
 * owns a fresh directory and removes it afterwards.
 */
class SqlCipherRoomTest {

    private val correctKey = ByteArray(RAW_KEY_BYTES) { (0x40 + it).toByte() }
    private val wrongKey = ByteArray(RAW_KEY_BYTES) { (0x90 + it).toByte() }
    private val sentinel = "SENTINEL-4f1c9a-do-not-leak".encodeToByteArray()

    private fun keyOf(bytes: ByteArray) = object : DatabaseKeyAccess {
        override fun <R> useKey(block: (ByteArray) -> R): R = block(bytes.copyOf())
    }

    private fun openRoom(path: String, key: ByteArray): SecretNoteDatabase =
        buildTestDatabase(path, createSqlCipherDriver(keyOf(key)))

    private fun inDatabaseDirectory(block: suspend (path: String) -> Unit) = runTest {
        val path = TestStorage.newDatabasePath()
        try {
            block(path)
        } finally {
            TestStorage.deleteDatabase(path)
        }
    }

    @Test
    fun keyedCrudTransactionRollbackAndReopen() = inDatabaseDirectory { path ->
        val db = openRoom(path, correctKey)
        val notes = db.notes()
        val first = notes.insert(SecretNote(title = "first", body = "alpha"))
        notes.insert(SecretNote(title = "second", body = "beta"))
        assertEquals(1, notes.updateBody(first, "alpha-2"))
        assertEquals(2, notes.count())
        assertEquals(listOf("alpha-2", "beta"), notes.all().map { it.body })

        assertFailsWith<IllegalStateException> {
            notes.insertThenFail(SecretNote(title = "lost", body = "gamma"))
        }
        assertEquals(2, notes.count())
        db.close()

        val reopened = openRoom(path, correctKey)
        assertEquals(listOf("alpha-2", "beta"), reopened.notes().all().map { it.body })
        reopened.close()
    }

    @Test
    fun closingTheDatabaseEndsAllAccess() = inDatabaseDirectory { path ->
        val db = openRoom(path, correctKey)
        db.notes().insert(SecretNote(title = "t", body = "b"))
        db.close()
        assertFailsWith<IllegalStateException> { db.notes().count() }
    }

    @Test
    fun wrongKeyFailsWithoutModifyingTheFiles() = inDatabaseDirectory { path ->
        val db = openRoom(path, correctKey)
        db.notes().insert(SecretNote(title = "t", body = "b"))
        db.close()

        val before = TestStorage.snapshot(path)
        assertTrue(before.isNotEmpty())
        val error = assertFailsWith<DatabaseDriverException> {
            createSqlCipherDriver(keyOf(wrongKey)).open(path).close()
        }
        assertEquals(DatabaseDriverError.KeyRejectedOrCorrupt, error.error)
        assertEquals(before.map { it.first }, TestStorage.snapshot(path).map { it.first })
        before.zip(TestStorage.snapshot(path)).forEach { (old, new) ->
            assertEquals(old.first, new.first)
            assertContentEquals(old.second, new.second, "${old.first} changed after a rejected key")
        }

        val reopened = openRoom(path, correctKey)
        assertEquals(1, reopened.notes().count())
        reopened.close()
    }

    @Test
    fun cipherVersionIsNonEmptyAndTempStoreIsMemory() = inDatabaseDirectory { path ->
        val connection = createSqlCipherDriver(keyOf(correctKey)).open(path)
        try {
            assertTrue(connection.scalarText("PRAGMA cipher_version").isNotEmpty())
            assertEquals(2L, connection.scalarLong("PRAGMA temp_store"))
        } finally {
            connection.close()
        }
    }

    @Test
    fun databaseFilesContainNoPlaintextSentinelOrSqliteHeader() = inDatabaseDirectory { path ->
        val db = openRoom(path, correctKey)
        db.notes().insert(SecretNote(title = "x", body = "SENTINEL-4f1c9a-do-not-leak"))
        db.notes().insert(SecretNote(title = "SENTINEL-4f1c9a-do-not-leak", body = "y"))
        db.close()

        val files = TestStorage.snapshot(path)
        assertTrue(files.isNotEmpty())
        val sqliteHeader = "SQLite format 3\u0000".encodeToByteArray()
        files.forEach { (name, bytes) ->
            assertFalse(bytes.containsSubsequence(sentinel), "plaintext sentinel found in $name")
            assertFalse(bytes.startsWithSubsequence(sqliteHeader), "plaintext SQLite header found in $name")
        }
    }

    @Test
    fun everyPooledConnectionIsKeyedBeforeUse() = inDatabaseDirectory { path ->
        openRoom(path, correctKey).also {
            it.notes().insert(SecretNote(title = "t", body = "b"))
            it.close()
        }
        val driver = createSqlCipherDriver(keyOf(correctKey))
        // Several readers open at once, on separate threads, and all stay open until every one is verified.
        val connections = coroutineScope {
            List(4) { async(Dispatchers.Default) { driver.open(path) } }.awaitAll()
        }
        try {
            connections.forEach { connection ->
                assertTrue(connection.scalarText("PRAGMA cipher_version").isNotEmpty())
                assertEquals(1L, connection.scalarLong("SELECT count(*) FROM secret_notes"))
            }
        } finally {
            connections.forEach { it.close() }
        }
    }

    @Test
    fun tamperedEncryptedPageFailsAuthentication() = inDatabaseDirectory { path ->
        val db = openRoom(path, correctKey)
        repeat(300) { index ->
            db.notes().insert(SecretNote(title = "note $index", body = "body-$index-".repeat(12)))
        }
        db.close()

        val pageSize = 4096
        val tamperedPage = 6
        TestStorage.flipByte(path, offset = (tamperedPage - 1) * pageSize + 128)

        val driver = createSqlCipherDriver(keyOf(correctKey))
        assertFailsWith<Exception> {
            val connection: SQLiteConnection = driver.open(path)
            try {
                val statement = connection.prepare("SELECT body FROM secret_notes")
                while (statement.step()) {
                    statement.getText(0)
                }
                statement.close()
            } finally {
                connection.close()
            }
        }
    }

    private fun SQLiteConnection.scalarText(sql: String): String {
        val statement = prepare(sql)
        try {
            check(statement.step()) { "no row" }
            return statement.getText(0)
        } finally {
            statement.close()
        }
    }

    private fun SQLiteConnection.scalarLong(sql: String): Long {
        val statement = prepare(sql)
        try {
            check(statement.step()) { "no row" }
            return statement.getLong(0)
        } finally {
            statement.close()
        }
    }

    private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        return (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }
    }

    private fun ByteArray.startsWithSubsequence(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
