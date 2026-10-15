package com.mk.daftar.core.database.driver

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver

/**
 * The test database. The desktop test compilation is a single JVM source set, so Room finds the generated
 * implementation by name instead of through an expect/actual constructor.
 */
@Database(entities = [SecretNote::class], version = 1, exportSchema = false)
abstract class SecretNoteDatabase : RoomDatabase() {
    abstract fun notes(): SecretNoteDao
}

/** Room over the keyed driver, addressed by absolute path on desktop. */
internal fun buildTestDatabase(path: String, driver: SQLiteDriver): SecretNoteDatabase =
    Room.databaseBuilder<SecretNoteDatabase>(name = path)
        .setDriver(driver)
        .build()
