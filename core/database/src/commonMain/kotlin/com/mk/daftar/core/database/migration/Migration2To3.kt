package com.mk.daftar.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.mk.daftar.core.database.schema.ValidationTriggersV1
import com.mk.daftar.core.database.schema.ValidationTriggersV3

/**
 * Version 2 to 3: bank identity, and the FX basis of cached remote quotes. Existing names, IDs, relationships and
 * timestamps are preserved and no bank is merged. Only the exact, unique alias matches are written, once, here.
 *
 * - Banks gain `institution_id` (null) and `identity_reviewed` (false), then each name is matched against the frozen
 *   table. A unique match sets the institution; anything else stays null. The match never sets the reviewed flag.
 * - Remote quotes gain `basis`. Gold rows keep null. USD to EGP rows from the two market-rate providers become
 *   mid-market estimates, which their provider identity proves; any other stored FX row becomes legacy unknown.
 * - The version 1 bank update trigger requires the revision to advance, so it is replaced only for the duration of the
 *   matching update. The row revision and timestamps stay as they were.
 * - The version 1 remote-quote triggers are replaced by their current definitions, which accept BANQUE_MISR.
 */
internal object Migration2To3 : Migration(2, 3) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execute("ALTER TABLE banks ADD COLUMN institution_id TEXT")
        connection.execute("ALTER TABLE banks ADD COLUMN identity_reviewed INTEGER NOT NULL DEFAULT 0")
        connection.execute("ALTER TABLE remote_quotes ADD COLUMN basis TEXT")
        connection.execute(
            "UPDATE remote_quotes SET basis = CASE " +
                "WHEN quote_key <> 'USD_EGP' THEN NULL " +
                "WHEN provider IN ('EXCHANGE_RATE_API', 'FAWAZ_CURRENCY_API') THEN 'MID_MARKET_ESTIMATE' " +
                "ELSE 'LEGACY_UNKNOWN' END",
        )

        matchBankIdentities(connection)
        recreateRemoteQuoteTriggers(connection)

        ValidationTriggersV3.statements.forEach { connection.execute(it) }
    }

    /**
     * The stored version 1 remote-quote triggers list the providers as they were before Banque Misr. They are replaced by
     * the current definitions, which accept BANQUE_MISR; the basis rule is a version 3 trigger.
     */
    private fun recreateRemoteQuoteTriggers(connection: SQLiteConnection) {
        val names = listOf("daftar_v1_remote_quotes_insert", "daftar_v1_remote_quotes_update")
        names.forEach { connection.execute("DROP TRIGGER IF EXISTS $it") }
        ValidationTriggersV1.statements
            .filter { statement -> names.any { statement.startsWith("CREATE TRIGGER IF NOT EXISTS $it ") } }
            .forEach { connection.execute(it) }
    }

    private fun matchBankIdentities(connection: SQLiteConnection) {
        val rows = mutableListOf<Pair<String, String>>()
        connection.prepare("SELECT id, name FROM banks").use { statement ->
            while (statement.step()) rows += statement.getText(0) to statement.getText(1)
        }
        val matches = rows.mapNotNull { (id, name) ->
            FrozenBankIdentities.uniqueMatch(name)?.let { id to it }
        }
        if (matches.isEmpty()) return

        // The version 1 update trigger requires a revision step, which this identity-only write must not take.
        connection.execute("DROP TRIGGER IF EXISTS daftar_v1_banks_update")
        connection.prepare("UPDATE banks SET institution_id = ? WHERE id = ?").use { statement ->
            matches.forEach { (id, institution) ->
                statement.reset()
                statement.bindText(1, institution)
                statement.bindText(2, id)
                statement.step()
            }
        }
        ValidationTriggersV1.statements
            .single { it.startsWith("CREATE TRIGGER IF NOT EXISTS daftar_v1_banks_update ") }
            .let { connection.execute(it) }
    }
}

private fun SQLiteConnection.execute(sql: String) {
    prepare(sql).use { statement -> statement.step() }
}

private inline fun <T> SQLiteStatement.use(block: (SQLiteStatement) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}
