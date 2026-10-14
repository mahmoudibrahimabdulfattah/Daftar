package com.mk.daftar.core.data.restore

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.database.DAFTAR_DATABASE_FILE_NAME
import com.mk.daftar.core.database.daftarDatabaseDirectory
import com.mk.daftar.core.database.daftarDatabaseFile
import com.mk.daftar.core.security.GenerationFiles
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.VaultGeneration
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The directory of a non-legacy generation: its database, beside the generation's security files. */
internal fun desktopGenerationDirectory(generation: VaultGeneration): File =
    daftarDatabaseDirectory().resolve("generations/${generation.id}")

/** The database file of [generation]. The legacy generation keeps the first file. */
internal fun desktopDatabaseFile(generation: VaultGeneration): File = if (generation.isLegacy) {
    daftarDatabaseFile()
} else {
    desktopGenerationDirectory(generation).apply { mkdirs() }.resolve(DAFTAR_DATABASE_FILE_NAME)
}

/** Removes the database files of a generation. Missing files are not an error, so the erase is idempotent. */
internal class DesktopGenerationFiles : GenerationFiles {
    override suspend fun erase(generation: VaultGeneration): Outcome<Unit, SecurityError> =
        withContext(Dispatchers.IO) {
            try {
                if (generation.isLegacy) {
                    val database = daftarDatabaseFile()
                    listOf("", "-wal", "-shm", "-journal").forEach { suffix -> File(database.path + suffix).delete() }
                } else {
                    desktopGenerationDirectory(generation).deleteRecursively()
                }
                Outcome.Success(Unit)
            } catch (failure: IOException) {
                Outcome.Failure(SecurityError.PersistenceFailure)
            }
        }
}
