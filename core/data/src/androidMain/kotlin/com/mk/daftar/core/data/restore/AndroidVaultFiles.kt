package com.mk.daftar.core.data.restore

import android.content.Context
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.database.DAFTAR_DATABASE_FILE_NAME
import com.mk.daftar.core.database.daftarDatabaseFile
import com.mk.daftar.core.security.GenerationFiles
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.VaultGeneration
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The database file of [generation]. The legacy generation keeps the first release's file. */
internal fun androidDatabaseFile(context: Context, generation: VaultGeneration): File = if (generation.isLegacy) {
    daftarDatabaseFile(context)
} else {
    androidGenerationDirectory(context, generation).apply { mkdirs() }.resolve(DAFTAR_DATABASE_FILE_NAME)
}

/** The directory of a non-legacy generation: its database and, beside it, its security files. */
internal fun androidGenerationDirectory(context: Context, generation: VaultGeneration): File =
    File(context.noBackupFilesDir, "daftar-generation/${generation.id}")

/**
 * Removes the database files of a generation. The legacy generation loses its database and SQLite side files; any other
 * generation loses its whole directory. A missing file is not an error, so the erase is idempotent.
 */
internal class AndroidGenerationFiles(private val context: Context) : GenerationFiles {
    override suspend fun erase(generation: VaultGeneration): Outcome<Unit, SecurityError> =
        withContext(Dispatchers.IO) {
            try {
                if (generation.isLegacy) {
                    val database = daftarDatabaseFile(context)
                    listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                        File(database.path + suffix).delete()
                    }
                } else {
                    androidGenerationDirectory(context, generation).deleteRecursively()
                }
                Outcome.Success(Unit)
            } catch (failure: IOException) {
                Outcome.Failure(SecurityError.PersistenceFailure)
            }
        }
}
