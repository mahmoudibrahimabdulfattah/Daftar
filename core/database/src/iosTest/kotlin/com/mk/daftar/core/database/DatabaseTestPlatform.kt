@file:OptIn(ExperimentalForeignApi::class)

package com.mk.daftar.core.database

import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** Test database files on the iOS simulator. Each test database lives in its own temporary directory. */
internal object TestStorage {
    fun newDatabasePath(): String {
        val directory = NSTemporaryDirectory() + "daftar-db-test-" + NSUUID().UUIDString()
        NSFileManager.defaultManager.createDirectoryAtPath(
            directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return "$directory/$DAFTAR_DATABASE_FILE_NAME"
    }

    /** Removes the directory that holds the database and its WAL and SHM companions. */
    fun deleteDatabase(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path.substringBeforeLast('/'), error = null)
    }
}

/** The production builder for a test path. */
internal fun testDatabaseBuilder(path: String): RoomDatabase.Builder<DaftarDatabase> = daftarDatabaseBuilder(path)
