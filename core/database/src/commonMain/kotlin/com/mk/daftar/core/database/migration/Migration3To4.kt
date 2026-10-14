package com.mk.daftar.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.mk.daftar.core.database.schema.ValidationTriggersV4

/**
 * Version 3 to 4: the simple investment accounts. Creates `investment_accounts` and its two indexes, then installs the
 * version four triggers. Existing tables are not touched, no rows are written, and no legacy wallet is converted.
 * The shape must match [com.mk.daftar.core.database.entity.InvestmentAccountEntity] exactly, because Room validates it
 * when the file opens.
 */
internal object Migration3To4 : Migration(3, 4) {
    private val statements: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `investment_accounts` (" +
            "`id` TEXT NOT NULL, " +
            "`created_seconds` INTEGER NOT NULL, " +
            "`created_nanos` INTEGER NOT NULL, " +
            "`updated_seconds` INTEGER NOT NULL, " +
            "`updated_nanos` INTEGER NOT NULL, " +
            "`revision` INTEGER NOT NULL, " +
            "`provider_id` TEXT, " +
            "`name` TEXT NOT NULL, " +
            "`amount` TEXT NOT NULL, " +
            "`value_as_of_seconds` INTEGER NOT NULL, " +
            "`value_as_of_nanos` INTEGER NOT NULL, " +
            "`note` TEXT, " +
            "`converted_from_cash_id` TEXT, " +
            "PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_investment_accounts_created_seconds_created_nanos_id` " +
            "ON `investment_accounts` (`created_seconds`, `created_nanos`, `id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_investment_accounts_converted_from_cash_id` " +
            "ON `investment_accounts` (`converted_from_cash_id`)",
    )

    override fun migrate(connection: SQLiteConnection) {
        statements.forEach { connection.execute(it) }
        ValidationTriggersV4.statements.forEach { connection.execute(it) }
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
