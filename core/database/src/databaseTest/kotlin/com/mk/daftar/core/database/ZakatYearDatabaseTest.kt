package com.mk.daftar.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.mk.daftar.core.database.driver.DatabaseKeyAccess
import com.mk.daftar.core.database.driver.createSqlCipherDriver
import com.mk.daftar.core.database.entity.ZakatTrackingEntity
import com.mk.daftar.core.database.mapper.toEntity
import com.mk.daftar.core.database.mapper.toModel
import com.mk.daftar.core.database.mapper.trackingStartYear
import com.mk.daftar.core.database.schema.ValidationTriggersV1
import com.mk.daftar.core.database.schema.ValidationTriggersV2
import com.mk.daftar.core.database.schema.ValidationTriggersV3
import com.mk.daftar.core.database.schema.ValidationTriggersV4
import com.mk.daftar.core.model.ZakatYear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Schema version 2 on a real engine: the migration from version 1 keeps every payment unassigned, the declaration and
 * tracking tables reject invalid rows through their triggers, and the DAO round-trips the rows the repository writes.
 */
class ZakatYearDatabaseTest {

    private val key = ByteArray(32) { (0x40 + it).toByte() }
    private val created = Instant.fromEpochSeconds(1_700_000_000, 0)
    private val marked = Instant.fromEpochSeconds(1_750_000_000, 123)

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

    private suspend fun createDatabase(path: String) {
        val db = open(path)
        db.zakatYearDao().observeYears().first()
        db.close()
    }

    private fun <R> withRawConnection(path: String, block: (SQLiteConnection) -> R): R {
        val connection = createSqlCipherDriver(keyOf(key)).open(path)
        try {
            return block(connection)
        } finally {
            connection.close()
        }
    }

    private fun paymentSql(id: String, forHijriYear: String) = "INSERT INTO zakat_payments " +
        "(id, created_seconds, created_nanos, updated_seconds, updated_nanos, revision, paid_on, amount, currency, " +
        "note, for_hijri_year) VALUES ('$id', 1700000000, 0, 1700000000, 0, 1, '2026-03-01', '250', 'EGP', NULL, " +
        "$forHijriYear)"

    private fun yearSql(
        year: Int,
        paid: Int,
        markedSeconds: String,
        markedNanos: String,
        override: Int,
        adjusted: Int,
    ) = "INSERT INTO zakat_years (hijri_year, paid, marked_seconds, marked_nanos, override_days, user_adjusted) " +
        "VALUES ($year, $paid, $markedSeconds, $markedNanos, $override, $adjusted)"

    @Test
    fun migratingFromVersionOneKeepsEveryPaymentUnassignedAndAddsTheTables() = inDatabase { path ->
        createDatabase(path)
        withRawConnection(path) { connection ->
            connection.execute(paymentSql("legacy", "NULL"))
            // Return the file to its version one shape: no declarations, no tracking row, no payment year column, and
            // none of the version two or three additions.
            ValidationTriggersV4.triggerNames.forEach { connection.execute("DROP TRIGGER IF EXISTS $it") }
            connection.execute("DROP TABLE IF EXISTS investment_accounts")
            ValidationTriggersV3.triggerNames.forEach { connection.execute("DROP TRIGGER IF EXISTS $it") }
            ValidationTriggersV2.triggerNames.forEach { connection.execute("DROP TRIGGER IF EXISTS $it") }
            connection.execute("ALTER TABLE banks DROP COLUMN identity_reviewed")
            connection.execute("ALTER TABLE banks DROP COLUMN institution_id")
            connection.execute("ALTER TABLE remote_quotes DROP COLUMN basis")
            connection.execute("DROP TABLE zakat_years")
            connection.execute("DROP TABLE zakat_tracking")
            connection.execute("ALTER TABLE zakat_payments DROP COLUMN for_hijri_year")
            connection.execute("PRAGMA user_version = 1")
        }

        val db = open(path)
        val payments = db.zakatPaymentDao().observeAll().first()
        assertEquals(listOf("legacy"), payments.map { it.id })
        assertNull(payments.single().forHijriYear)

        assertEquals(emptyList(), db.zakatYearDao().years())
        assertEquals(emptyList(), db.zakatYearDao().tracking())
        db.close()

        withRawConnection(path) { connection ->
            val triggers = connection.names("SELECT name FROM sqlite_master WHERE type = 'trigger'")
            assertEquals(
                (
                    ValidationTriggersV1.triggerNames + ValidationTriggersV2.triggerNames +
                        ValidationTriggersV3.triggerNames + ValidationTriggersV4.triggerNames
                    ).toSet(),
                triggers.toSet(),
            )
        }
    }

    @Test
    fun declarationTriggersRejectInvalidRowsBeforeTheyReachATable() = inDatabase { path ->
        createDatabase(path)
        withRawConnection(path) { connection ->
            connection.execute(yearSql(1447, 0, "NULL", "NULL", 0, 0))
            assertRejected(connection, yearSql(1601, 0, "NULL", "NULL", 0, 0), "Hijri year must be within")
            assertRejected(connection, yearSql(1299, 0, "NULL", "NULL", 0, 0), "Hijri year must be within")
            assertRejected(connection, yearSql(1448, 0, "NULL", "NULL", 2, 0), "date adjustment")
            assertRejected(connection, yearSql(1448, 0, "NULL", "NULL", 1, 0), "user adjusted")
            assertRejected(connection, yearSql(1448, 1, "NULL", "NULL", 0, 0), "mark time")
            assertRejected(connection, yearSql(1448, 0, "1750000000", "123", 0, 0), "mark time")
            assertRejected(connection, yearSql(1448, 1, "1750000000", "NULL", 0, 0), "seconds and nanos")
            assertRejected(connection, yearSql(1448, 1, "1750000000", "-1", 0, 0), "nanoseconds")
            assertRejected(connection, "UPDATE zakat_years SET hijri_year = 1500 WHERE hijri_year = 1447", "immutable")
            connection.execute(yearSql(1448, 1, "1750000000", "123", 0, 0))
        }
    }

    @Test
    fun trackingRowAndPaymentYearAreValidatedByTheirTriggers() = inDatabase { path ->
        createDatabase(path)
        withRawConnection(path) { connection ->
            assertRejected(connection, "INSERT INTO zakat_tracking (id, tracking_start_year) VALUES (2, 1447)", "row 1")
            assertRejected(
                connection,
                "INSERT INTO zakat_tracking (id, tracking_start_year) VALUES (1, 1299)",
                "tracking start",
            )
            connection.execute("INSERT INTO zakat_tracking (id, tracking_start_year) VALUES (1, 1447)")
            assertRejected(connection, paymentSql("p-low", "1200"), "linked Hijri year")
            assertRejected(connection, paymentSql("p-high", "1601"), "linked Hijri year")
            connection.execute(paymentSql("p-unassigned", "NULL"))
            connection.execute(paymentSql("p-linked", "1447"))
        }
    }

    @Test
    fun theDataAccessLayerRoundTripsDeclarationsAndTheTrackingStart() = inDatabase { path ->
        val db = open(path)
        val dao = db.zakatYearDao()
        val years = listOf(ZakatYear.unchecked(1447), ZakatYear.unchecked(1448))
        dao.applyTrackingPlan(ZakatTrackingEntity(id = 1, trackingStartYear = 1447), years.map { it.toEntity() })
        // A repeated plan changes nothing: the rows already exist and the tracking row is kept.
        dao.applyTrackingPlan(ZakatTrackingEntity(id = 1, trackingStartYear = 1300), years.map { it.toEntity() })
        assertEquals(1447, dao.tracking().trackingStartYear())
        assertEquals(years, dao.years().map { it.toModel() })

        val paid = ZakatYear(1447, paid = true, markedAt = marked, overrideDays = 0, userAdjusted = false)
        assertEquals(1, dao.updateYear(paid.toEntity()))
        val adjusted = ZakatYear(1448, paid = false, markedAt = null, overrideDays = 1, userAdjusted = true)
        assertEquals(1, dao.updateYear(adjusted.toEntity()))

        val stored = dao.years().map { it.toModel() }
        assertEquals(listOf(paid, adjusted), stored)
        assertEquals(marked, stored.first().markedAt)
        db.close()
    }

    private fun assertRejected(connection: SQLiteConnection, sql: String, fragment: String) {
        val error = assertFails { connection.execute(sql) }
        assertTrue(error.message.orEmpty().contains(fragment), "expected '$fragment' in '${error.message}'")
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).useStatement { statement -> statement.step() }
    }

    private fun SQLiteConnection.names(sql: String): List<String> = prepare(sql).useStatement { statement ->
        val names = mutableListOf<String>()
        while (statement.step()) names += statement.getText(0)
        names
    }

    private inline fun <T> SQLiteStatement.useStatement(block: (SQLiteStatement) -> T): T = try {
        block(this)
    } finally {
        close()
    }
}
