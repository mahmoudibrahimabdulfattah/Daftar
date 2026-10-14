package com.mk.daftar.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.mk.daftar.core.database.driver.DatabaseKeyAccess
import com.mk.daftar.core.database.driver.createSqlCipherDriver
import com.mk.daftar.core.database.schema.ValidationTriggersV4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Schema version 4 on a real engine. A version three file with a cash entry is opened, so the migration runs: the
 * investments table and its triggers appear, existing records are untouched, and the triggers refuse invalid rows and a
 * second investment converted from the same cash entry.
 */
class InvestmentMigrationTest {

    private val key = ByteArray(32) { (0x50 + it).toByte() }

    private fun keyOf(bytes: ByteArray) = object : DatabaseKeyAccess {
        override fun <R> useKey(block: (ByteArray) -> R): R = block(bytes.copyOf())
    }

    private fun open(path: String): DaftarDatabase = buildDaftarDatabase(
        testDatabaseBuilder(path),
        createSqlCipherDriver(keyOf(key)),
    )

    private fun inDatabase(block: suspend (path: String) -> Unit) = runTest {
        val path = TestStorage.newDatabasePath()
        try {
            block(path)
        } finally {
            TestStorage.deleteDatabase(path)
        }
    }

    private fun <R> withRawConnection(path: String, block: (SQLiteConnection) -> R): R {
        val connection = createSqlCipherDriver(keyOf(key)).open(path)
        try {
            return block(connection)
        } finally {
            connection.close()
        }
    }

    private fun investmentSql(id: String, amount: String, convertedFrom: String? = null) =
        "INSERT INTO investment_accounts (id, created_seconds, created_nanos, updated_seconds, updated_nanos, " +
            "revision, provider_id, name, amount, value_as_of_seconds, value_as_of_nanos, note, " +
            "converted_from_cash_id) VALUES ('$id', 1700000000, 0, 1700000000, 0, 1, 'eg.thndr', 'Thndr', " +
            "'$amount', 1700000000, 0, NULL, ${convertedFrom?.let { "'$it'" } ?: "NULL"})"

    @Test
    fun aVersionThreeFileGainsTheInvestmentsTableAndKeepsItsRecords() = inDatabase { path ->
        open(path).also { it.goldHoldingDao().observeAll().first() }.close()
        withRawConnection(path) { connection ->
            ValidationTriggersV4.triggerNames.forEach { connection.execute("DROP TRIGGER IF EXISTS $it") }
            connection.execute("DROP TABLE investment_accounts")
            connection.execute("PRAGMA user_version = 3")
            connection.execute(
                "INSERT INTO cash_balances (id, created_seconds, created_nanos, updated_seconds, updated_nanos, " +
                    "revision, name, kind, balance, currency, note) VALUES ('wallet', 1700000000, 0, 1700000000, 0, " +
                    "2, 'Thundr wallet', 'WALLET', '7500', 'EGP', 'broker cash')",
            )
        }

        val db = open(path)
        assertEquals(listOf("wallet"), db.cashBalanceDao().observeAll().first().map { it.id })
        assertEquals(emptyList(), db.investmentAccountDao().observeAll().first())
        db.close()

        withRawConnection(path) { connection ->
            assertEquals(4L, connection.scalarLong("PRAGMA user_version"))
            val triggers = connection.names("SELECT name FROM sqlite_master WHERE type = 'trigger'").toSet()
            assertEquals(true, triggers.containsAll(ValidationTriggersV4.triggerNames))
            connection.execute(investmentSql("ok", "50000.25", convertedFrom = "wallet-old"))
            assertFails("a negative amount is refused") { connection.execute(investmentSql("neg", "-1")) }
            assertFails("one cash entry converts once") {
                connection.execute(investmentSql("twice", "10", convertedFrom = "wallet-old"))
            }
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private fun SQLiteConnection.names(sql: String): List<String> = prepare(sql).use { statement ->
        buildList { while (statement.step()) add(statement.getText(0)) }
    }

    private fun SQLiteConnection.scalarLong(sql: String): Long = prepare(sql).use { statement ->
        check(statement.step())
        statement.getLong(0)
    }

    private inline fun <T> SQLiteStatement.use(block: (SQLiteStatement) -> T): T = try {
        block(this)
    } finally {
        close()
    }
}
