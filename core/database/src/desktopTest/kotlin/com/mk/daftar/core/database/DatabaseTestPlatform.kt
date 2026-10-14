package com.mk.daftar.core.database

import androidx.room.RoomDatabase
import java.io.File
import java.nio.file.Files

/** Test database files on the desktop JVM. Each test database lives in its own temporary directory. */
internal object TestStorage {
    fun newDatabasePath(): String =
        Files.createTempDirectory("daftar-db-test-").toFile().resolve(DAFTAR_DATABASE_FILE_NAME).absolutePath

    /** Removes the directory that holds the database and its WAL and SHM companions. */
    fun deleteDatabase(path: String) {
        File(path).parentFile.deleteRecursively()
    }
}

/** The production builder for a test path. */
internal fun testDatabaseBuilder(path: String): RoomDatabase.Builder<DaftarDatabase> = daftarDatabaseBuilder(File(path))
