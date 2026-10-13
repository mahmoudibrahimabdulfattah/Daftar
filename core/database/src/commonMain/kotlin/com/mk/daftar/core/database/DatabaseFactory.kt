package com.mk.daftar.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import com.mk.daftar.core.database.schema.ValidationTriggersV1
import com.mk.daftar.core.database.schema.ValidationTriggersV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Builds the current database over [driver]. The platform supplies [builder] because Room needs its own location: the
 * Android builder takes a Context, the iOS builder takes a path. Each connection enables foreign keys, so the RESTRICT
 * rules hold on every pooled connection, and the schema callback installs the validation triggers when the file is
 * created and checks they are present when it is opened.
 */
fun buildDaftarDatabase(builder: RoomDatabase.Builder<DaftarDatabase>, driver: SQLiteDriver): DaftarDatabase = builder
    .setDriver(ForeignKeyEnforcingDriver(driver))
    .setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(*daftarMigrations)
    .addCallback(DaftarSchemaCallback)
    .build()

/** Turns on foreign-key enforcement for every connection, and proves that it took effect. */
internal class ForeignKeyEnforcingDriver(private val delegate: SQLiteDriver) : SQLiteDriver {
    override fun open(fileName: String): SQLiteConnection {
        val connection = delegate.open(fileName)
        try {
            connection.execute("PRAGMA foreign_keys = ON")
            val enabled = connection.scalarLong("PRAGMA foreign_keys")
            check(enabled == 1L) { "foreign key enforcement is not active" }
        } catch (error: Throwable) {
            connection.close()
            throw error
        }
        return connection
    }
}

/**
 * Installs every versioned validation trigger when the database is created, and refuses to open a file whose trigger
 * set does not match the current schema. Migrations install the triggers of each version they add.
 */
internal object DaftarSchemaCallback : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        ValidationTriggersV1.statements.forEach { connection.execute(it) }
        ValidationTriggersV2.statements.forEach { connection.execute(it) }
    }

    override fun onOpen(connection: SQLiteConnection) {
        val expected = (ValidationTriggersV1.triggerNames + ValidationTriggersV2.triggerNames).size.toLong()
        val present = connection.scalarLong(
            "SELECT count(*) FROM sqlite_master WHERE type = 'trigger' AND " +
                "(name LIKE 'daftar_v${ValidationTriggersV1.VERSION}_%' OR name LIKE 'daftar_v${ValidationTriggersV2.VERSION}_%')",
        )
        check(present == expected) { "validation triggers do not match schema version ${ValidationTriggersV2.VERSION}" }
    }
}

private fun SQLiteConnection.execute(sql: String) {
    prepare(sql).use { statement -> statement.step() }
}

private fun SQLiteConnection.scalarLong(sql: String): Long {
    prepare(sql).use { statement ->
        check(statement.step()) { "no row" }
        return statement.getLong(0)
    }
}

private inline fun <T> SQLiteStatement.use(block: (SQLiteStatement) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}
