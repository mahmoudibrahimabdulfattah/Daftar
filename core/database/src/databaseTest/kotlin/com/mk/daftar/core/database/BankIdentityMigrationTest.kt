package com.mk.daftar.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.mk.daftar.core.database.driver.DatabaseKeyAccess
import com.mk.daftar.core.database.driver.createSqlCipherDriver
import com.mk.daftar.core.database.schema.ValidationTriggersV1
import com.mk.daftar.core.database.schema.ValidationTriggersV2
import com.mk.daftar.core.database.schema.ValidationTriggersV3
import com.mk.daftar.core.database.schema.ValidationTriggersV4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Schema version 3 on a real engine. A version two file with banks, quotes and a Phase 22 payment is opened, so the
 * migration runs. Exact unique names are identified, ambiguous and fuzzy names stay unmatched, nothing is renamed,
 * merged or re-timestamped, and the version three triggers refuse malformed identity and basis values.
 */
class BankIdentityMigrationTest {

    private val key = ByteArray(32) { (0x40 + it).toByte() }

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

    /** Returns the file to its version two shape: no identity or basis columns, no version three triggers. */
    private fun revertToVersionTwo(connection: SQLiteConnection) {
        ValidationTriggersV4.triggerNames.forEach { connection.execute("DROP TRIGGER IF EXISTS $it") }
        connection.execute("DROP TABLE IF EXISTS investment_accounts")
        ValidationTriggersV3.triggerNames.forEach { connection.execute("DROP TRIGGER IF EXISTS $it") }
        connection.execute("ALTER TABLE banks DROP COLUMN identity_reviewed")
        connection.execute("ALTER TABLE banks DROP COLUMN institution_id")
        connection.execute("ALTER TABLE remote_quotes DROP COLUMN basis")
        connection.execute("PRAGMA user_version = 2")
    }

    private fun bankSql(id: String, name: String, revision: Long = 3) =
        "INSERT INTO banks (id, created_seconds, created_nanos, updated_seconds, updated_nanos, revision, name) " +
            "VALUES ('$id', 1700000000, 0, 1700000100, 0, $revision, '$name')"

    private fun fxRemoteSql(provider: String) =
        "INSERT INTO remote_quotes (quote_key, provider, value, source_url, source_time_kind, source_seconds, " +
            "source_nanos, source_date, fetched_seconds, fetched_nanos, recorded_seconds, recorded_nanos, note, " +
            "next_update_seconds, next_update_nanos, attribution_url) VALUES ('USD_EGP', '$provider', '50.5', " +
            "'https://example.com/fx', 'UNKNOWN', NULL, NULL, NULL, 1700000000, 0, 1700000000, 0, NULL, NULL, NULL, " +
            "NULL)"

    private fun goldRemoteSql() =
        "INSERT INTO remote_quotes (quote_key, provider, value, source_url, source_time_kind, source_seconds, " +
            "source_nanos, source_date, fetched_seconds, fetched_nanos, recorded_seconds, recorded_nanos, note, " +
            "next_update_seconds, next_update_nanos, attribution_url) VALUES ('GOLD_24_SELL', 'ISAGHA', '4100', " +
            "'https://example.com/gold', 'UNKNOWN', NULL, NULL, NULL, 1700000000, 0, 1700000000, 0, NULL, NULL, " +
            "NULL, " +
            "NULL)"

    @Test
    fun migrationIdentifiesExactUniqueNamesAndLeavesEverythingElseAlone() = inDatabase { path ->
        open(path).also { it.goldHoldingDao().observeAll().first() }.close()
        withRawConnection(path) { connection ->
            revertToVersionTwo(connection)
            connection.execute(bankSql("nbe", "National Bank of Egypt"))
            connection.execute(bankSql("arabic-nbe", "البنك الأهلي المصري", revision = 1))
            connection.execute(bankSql("ambiguous", "الأهلي"))
            connection.execute(bankSql("misr", "Bank Misr"))
            connection.execute(bankSql("cib", "cib"))
            connection.execute(bankSql("fuzzy", "National"))
            connection.execute(bankSql("custom", "My savings"))
            connection.execute(fxRemoteSql("EXCHANGE_RATE_API"))
            connection.execute(fxRemoteSql("FAWAZ_CURRENCY_API"))
            connection.execute(goldRemoteSql())
            connection.execute(
                "INSERT INTO zakat_payments (id, created_seconds, created_nanos, updated_seconds, updated_nanos, " +
                    "revision, paid_on, amount, currency, note) VALUES ('legacy', 1700000000, 0, 1700000000, 0, 1, " +
                    "'2026-03-01', '250', 'EGP', 'phase 22')",
            )
        }

        val db = open(path)
        val banks = db.bankDao().observeAll().first().associateBy { it.id }
        assertEquals("eg.nbe", banks.getValue("nbe").institutionId)
        assertEquals("eg.nbe", banks.getValue("arabic-nbe").institutionId)
        assertEquals("eg.banque-misr", banks.getValue("misr").institutionId)
        assertEquals("eg.cib", banks.getValue("cib").institutionId)
        assertNull(banks.getValue("ambiguous").institutionId)
        assertNull(banks.getValue("fuzzy").institutionId)
        assertNull(banks.getValue("custom").institutionId)
        assertEquals(setOf("nbe", "arabic-nbe", "ambiguous", "misr", "cib", "fuzzy", "custom"), banks.keys)
        banks.values.forEach { assertEquals(false, it.identityReviewed) }

        // Names, revisions and timestamps are exactly as they were.
        assertEquals("National Bank of Egypt", banks.getValue("nbe").name)
        assertEquals("الأهلي", banks.getValue("ambiguous").name)
        assertEquals(3L, banks.getValue("nbe").revision)
        assertEquals(1700000100L, banks.getValue("nbe").updatedSeconds)

        val quotes = db.remoteQuoteDao().observeAll().first().associateBy { "${it.quoteKey}/${it.provider}" }
        assertEquals("MID_MARKET_ESTIMATE", quotes.getValue("USD_EGP/EXCHANGE_RATE_API").basis)
        assertEquals("MID_MARKET_ESTIMATE", quotes.getValue("USD_EGP/FAWAZ_CURRENCY_API").basis)
        assertNull(quotes.getValue("GOLD_24_SELL/ISAGHA").basis)

        assertEquals(listOf("legacy"), db.zakatPaymentDao().observeAll().first().map { it.id })
        db.close()

        withRawConnection(path) { connection ->
            assertEquals(
                (
                    ValidationTriggersV1.triggerNames + ValidationTriggersV2.triggerNames +
                        ValidationTriggersV3.triggerNames + ValidationTriggersV4.triggerNames
                    ).toSet(),
                connection.names("SELECT name FROM sqlite_master WHERE type = 'trigger'").toSet(),
            )
            assertEquals(
                4L,
                connection.scalarLong("PRAGMA user_version"),
                "a version two file migrates through to four",
            )
            // The upgraded file accepts a Banque Misr rate with its basis; the old provider list refused it.
            connection.execute(
                "INSERT INTO remote_quotes (quote_key, provider, value, source_url, source_time_kind, " +
                    "source_seconds, source_nanos, source_date, fetched_seconds, fetched_nanos, recorded_seconds, " +
                    "recorded_nanos, note, next_update_seconds, next_update_nanos, attribution_url, basis) " +
                    "VALUES ('USD_EGP', 'BANQUE_MISR', '52.77', 'https://www.banquemisr.com/x', 'TIMESTAMP', " +
                    "1700000000, 0, NULL, 1700000000, 0, 1700000000, 0, NULL, NULL, NULL, NULL, 'BANK_TRANSFER_BUY')",
            )
        }
    }

    @Test
    fun versionThreeTriggersRefuseMalformedIdentityAndBasis() = inDatabase { path ->
        open(path).also { it.goldHoldingDao().observeAll().first() }.close()
        withRawConnection(path) { connection ->
            assertFails {
                connection.execute(
                    "INSERT INTO banks (id, created_seconds, created_nanos, updated_seconds, updated_nanos, " +
                        "revision, " +
                        "name, institution_id, identity_reviewed) VALUES ('bad', 1700000000, 0, 1700000000, 0, 1, " +
                        "'Bad', 'EG.NBE', 0)",
                )
            }
            connection.execute(
                "INSERT INTO banks (id, created_seconds, created_nanos, updated_seconds, updated_nanos, revision, " +
                    "name, institution_id, identity_reviewed) VALUES ('ok', 1700000000, 0, 1700000000, 0, 1, 'Ok', " +
                    "'eg.nbe', 1)",
            )
            assertFails {
                connection.execute("UPDATE banks SET institution_id = 'EG.NBE', revision = 2 WHERE id = 'ok'")
            }
            assertFails {
                val longId = "a".repeat(65)
                connection.execute("UPDATE banks SET institution_id = '$longId', revision = 2 WHERE id = 'ok'")
            }
            assertFails {
                connection.execute("UPDATE banks SET identity_reviewed = 2, revision = 2 WHERE id = 'ok'")
            }
            connection.execute(
                "UPDATE banks SET institution_id = NULL, identity_reviewed = 1, revision = 2 WHERE id = 'ok'",
            )

            // A Banque Misr quote without its basis, and a gold row carrying a basis, are both refused.
            assertFails { connection.execute(fxRemoteSql("BANQUE_MISR")) }
            assertFails {
                connection.execute(
                    "INSERT INTO remote_quotes (quote_key, provider, value, source_url, source_time_kind, " +
                        "fetched_seconds, fetched_nanos, recorded_seconds, recorded_nanos, basis) VALUES " +
                        "('GOLD_24_SELL', 'ISAGHA', '4100', 'https://example.com/gold', 'UNKNOWN', 1700000000, 0, " +
                        "1700000000, 0, 'MID_MARKET_ESTIMATE')",
                )
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
