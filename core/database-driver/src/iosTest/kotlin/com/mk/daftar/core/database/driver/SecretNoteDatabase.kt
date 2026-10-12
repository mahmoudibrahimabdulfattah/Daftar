package com.mk.daftar.core.database.driver

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.SQLiteDriver

@Database(entities = [SecretNote::class], version = 1, exportSchema = false)
@ConstructedBy(SecretNoteDatabaseConstructor::class)
abstract class SecretNoteDatabase : RoomDatabase() {
    abstract fun notes(): SecretNoteDao
}

/** Room's KSP processor generates the actual constructor for each iOS test compilation. */
expect object SecretNoteDatabaseConstructor : RoomDatabaseConstructor<SecretNoteDatabase>

/** Room over the keyed driver, addressed by absolute path on iOS. */
internal fun buildTestDatabase(path: String, driver: SQLiteDriver): SecretNoteDatabase =
    Room.databaseBuilder<SecretNoteDatabase>(name = path)
        .setDriver(driver)
        .build()
