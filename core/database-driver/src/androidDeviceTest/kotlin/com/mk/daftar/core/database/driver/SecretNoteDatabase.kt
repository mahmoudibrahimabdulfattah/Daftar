package com.mk.daftar.core.database.driver

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

@Database(entities = [SecretNote::class], version = 1, exportSchema = false)
abstract class SecretNoteDatabase : RoomDatabase() {
    abstract fun notes(): SecretNoteDao
}

/** Room over the keyed driver. The database is named inside the app's databases directory, as Room requires. */
internal fun buildTestDatabase(path: String, driver: SQLiteDriver): SecretNoteDatabase {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return Room.databaseBuilder(context, SecretNoteDatabase::class.java, File(path).name)
        .setDriver(driver)
        .build()
}
