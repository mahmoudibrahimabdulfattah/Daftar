package com.mk.daftar.core.data.restore

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.database.DAFTAR_DATABASE_FILE_NAME
import com.mk.daftar.core.database.daftarDatabaseDirectory
import com.mk.daftar.core.database.daftarDatabasePath
import com.mk.daftar.core.security.GenerationFiles
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.VaultGeneration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey

/** The directory of a non-legacy generation, created on first use and excluded from backup. */
@OptIn(ExperimentalForeignApi::class)
private fun generationDirectory(generation: VaultGeneration): String {
    val directory = "${daftarDatabaseDirectory()}/generations/${generation.id}"
    NSFileManager.defaultManager.createDirectoryAtPath(
        directory,
        withIntermediateDirectories = true,
        attributes = mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
        error = null,
    )
    NSURL.fileURLWithPath(directory).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return directory
}

/** The database path of [generation]. The legacy generation keeps the first release's path. */
internal fun iosDatabasePath(generation: VaultGeneration): String =
    if (generation.isLegacy) daftarDatabasePath() else "${generationDirectory(generation)}/$DAFTAR_DATABASE_FILE_NAME"

/** Removes the database files of a generation. Missing files are not an error, so the erase is idempotent. */
@OptIn(ExperimentalForeignApi::class)
internal class IosGenerationFiles : GenerationFiles {
    override suspend fun erase(generation: VaultGeneration): Outcome<Unit, SecurityError> =
        withContext(Dispatchers.Default) {
            val fileManager = NSFileManager.defaultManager
            if (generation.isLegacy) {
                val database = daftarDatabasePath()
                listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                    fileManager.removeItemAtPath(database + suffix, error = null)
                }
            } else {
                fileManager.removeItemAtPath("${daftarDatabaseDirectory()}/generations/${generation.id}", error = null)
            }
            Outcome.Success(Unit)
        }
}
