package com.mk.daftar.core.database

import androidx.room.RoomDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID

/** Test database files on the Android device, under the app's private directories, one directory per test. */
internal object TestStorage {
    fun newDatabasePath(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "daftar-db-test-${UUID.randomUUID()}").apply { mkdirs() }
        return File(directory, DAFTAR_DATABASE_FILE_NAME).absolutePath
    }

    /** Removes the database directory and its WAL and SHM companions. */
    fun deleteDatabase(path: String) {
        File(path).parentFile?.deleteRecursively()
    }
}

/** The production builder for a test file, so tests exercise the same Room configuration as the app. */
internal fun testDatabaseBuilder(path: String): RoomDatabase.Builder<DaftarDatabase> {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return daftarDatabaseBuilder(context, File(path))
}
