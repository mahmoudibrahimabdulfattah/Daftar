package com.mk.daftar.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.mk.daftar.core.database.schema.ValidationTriggersV2

/**
 * Every schema migration, in order. There is no destructive fallback: a database without a path to the current version
 * fails to open rather than being replaced.
 */
internal val daftarMigrations: Array<Migration> = arrayOf(Migration1To2)

/**
 * Version 1 to 2: the annual declarations, the tracking row, and the optional Hijri year on zakat payments. Existing
 * payments keep a null year, which is the Unassigned state. Nothing is inferred from old payment dates, and tracking
 * starts on the first unlock, not here. The table shapes must match the version 2 entities exactly, because Room
 * validates them when the file opens.
 */
internal object Migration1To2 : Migration(1, 2) {
    private val statements: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `zakat_years` (" +
            "`hijri_year` INTEGER NOT NULL, " +
            "`paid` INTEGER NOT NULL, " +
            "`marked_seconds` INTEGER, " +
            "`marked_nanos` INTEGER, " +
            "`override_days` INTEGER NOT NULL, " +
            "`user_adjusted` INTEGER NOT NULL, " +
            "PRIMARY KEY(`hijri_year`))",
        "CREATE TABLE IF NOT EXISTS `zakat_tracking` (" +
            "`id` INTEGER NOT NULL, " +
            "`tracking_start_year` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`))",
        "ALTER TABLE `zakat_payments` ADD COLUMN `for_hijri_year` INTEGER",
    )

    override fun migrate(connection: SQLiteConnection) {
        statements.forEach { connection.execute(it) }
        ValidationTriggersV2.statements.forEach { connection.execute(it) }
    }
}

private fun SQLiteConnection.execute(sql: String) {
    prepare(sql).use { statement: SQLiteStatement -> statement.step() }
}

private inline fun <T> SQLiteStatement.use(block: (SQLiteStatement) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}
