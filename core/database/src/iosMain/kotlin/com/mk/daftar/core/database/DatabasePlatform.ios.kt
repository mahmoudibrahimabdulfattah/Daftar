@file:OptIn(ExperimentalForeignApi::class)

package com.mk.daftar.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * The Daftar directory under Application Support. It is excluded from backup, so the database and its WAL and SHM
 * siblings are never backed up, and it carries file protection for the first unlock after boot.
 */
fun daftarDatabaseDirectory(): String {
    val fileManager = NSFileManager.defaultManager
    val support = fileManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path ?: error("Application Support is unavailable")
    val directory = "$support/Daftar"
    fileManager.createDirectoryAtPath(
        directory,
        withIntermediateDirectories = true,
        attributes = mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
        error = null,
    )
    NSURL.fileURLWithPath(directory).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return directory
}

/** The legacy database path: the first release's file in the Daftar directory. */
fun daftarDatabasePath(): String = "${daftarDatabaseDirectory()}/$DAFTAR_DATABASE_FILE_NAME"

/** Room's builder for the database at [path]. */
fun daftarDatabaseBuilder(path: String): RoomDatabase.Builder<DaftarDatabase> =
    Room.databaseBuilder<DaftarDatabase>(name = path)
