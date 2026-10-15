package com.mk.daftar.platform.backup

import com.mk.daftar.core.backup.BackupDocuments
import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupFormat
import com.mk.daftar.core.backup.BackupStaging
import com.mk.daftar.core.backup.PickedDestination
import com.mk.daftar.core.backup.RestoreFilePicker
import com.mk.daftar.core.backup.StagedBackup
import com.mk.daftar.core.backup.StagedCiphertext
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.desktop.DesktopAppDirectories
import com.mk.daftar.platform.DesktopWindowHost
import java.awt.FileDialog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The encrypted staging file, in the app's private directory where the database also lives. It is excluded from
 * backup and written only from already-encrypted bytes. Files left behind by a process that died are removed when the
 * staging area is created.
 */
internal class DesktopBackupStaging : BackupStaging {
    private val directory: File by lazy {
        DesktopAppDirectories.directory(STAGING_DIRECTORY).apply { listFiles()?.forEach { it.delete() } }
    }

    override suspend fun stage(encrypted: ByteArray): Outcome<StagedBackup, BackupError> = withContext(Dispatchers.IO) {
        val file = File(directory, "${UUID.randomUUID()}.${BackupFormat.FILE_EXTENSION}")
        try {
            FileOutputStream(file).use { output ->
                output.write(encrypted)
                output.flush()
                output.fd.sync()
            }
            Outcome.Success(DesktopStagedFile(file))
        } catch (failure: IOException) {
            file.delete()
            Outcome.Failure(BackupError.ResourceUnavailable)
        }
    }

    private companion object {
        const val STAGING_DIRECTORY = "backup-staging"
    }
}

/** A staged encrypted file. Discarding deletes it; a failed delete is harmless because the next launch clears the area. */
internal class DesktopStagedFile(val file: File) : StagedBackup {
    override suspend fun discard() {
        file.delete()
    }
}

/** The file the user chose in the native Save dialog. Only that path is ever written. */
internal class DesktopPickedDestination(val file: File) : PickedDestination

/**
 * The native Save dialog and writer. The copy goes to a temporary sibling first and is moved over the chosen file only
 * when every byte is on disk, so a failed write never leaves a truncated backup under the chosen name.
 */
internal class DesktopBackupDocuments : BackupDocuments {
    override suspend fun choose(staged: StagedBackup, suggestedName: String): Outcome<PickedDestination?, BackupError> {
        val title = if (DesktopWindowHost.arabic) "حفظ نسخة مشفّرة" else "Save encrypted backup"
        val chosen = showDialog(title, FileDialog.SAVE, suggestedName) ?: return Outcome.Success(null)
        val file = if (chosen.name.endsWith(".${BackupFormat.FILE_EXTENSION}")) {
            chosen
        } else {
            File(chosen.parentFile, "${chosen.name}.${BackupFormat.FILE_EXTENSION}")
        }
        return Outcome.Success(DesktopPickedDestination(file))
    }

    override suspend fun write(staged: StagedBackup, destination: PickedDestination): Outcome<Unit, BackupError> {
        val source = (staged as? DesktopStagedFile)?.file ?: return Outcome.Failure(BackupError.WriteFailed)
        val target = (destination as? DesktopPickedDestination)?.file ?: return Outcome.Failure(BackupError.WriteFailed)
        return withContext(Dispatchers.IO) {
            val partial = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.part")
            try {
                FileOutputStream(partial).use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                    output.flush()
                    output.fd.sync()
                }
                if (partial.length() != source.length()) {
                    partial.delete()
                    return@withContext Outcome.Failure(BackupError.WriteFailed)
                }
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                Outcome.Success(Unit)
            } catch (failure: IOException) {
                partial.delete()
                Outcome.Failure(BackupError.WriteFailed)
            } catch (failure: SecurityException) {
                partial.delete()
                Outcome.Failure(BackupError.WriteFailed)
            }
        }
    }

    override suspend fun discard(destination: PickedDestination) {
        // The chosen file is replaced only after a complete copy, so a failed write leaves nothing to remove.
    }
}

/**
 * The native Open dialog and bounded copy. The bytes are copied into private storage, and reading stops at the
 * format's largest possible file, so neither the extension, the file name nor the reported length is trusted.
 */
internal class DesktopRestoreFilePicker : RestoreFilePicker {
    override suspend fun pick(): Outcome<StagedCiphertext?, BackupError> {
        val title = if (DesktopWindowHost.arabic) "فتح نسخة مشفّرة" else "Open encrypted backup"
        val chosen = showDialog(title, FileDialog.LOAD, null) ?: return Outcome.Success(null)
        return withContext(Dispatchers.IO) { copyBounded(chosen) }
    }
}

/** The largest file the format can hold: the header, the largest allowed plaintext and the tag. */
internal const val MAX_RESTORE_FILE_BYTES: Long =
    (BackupFormat.HEADER_BYTES + BackupFormat.MAX_PLAINTEXT_BYTES + BackupFormat.TAG_BYTES).toLong()

private const val RESTORE_DIRECTORY = "restore-staging"
private const val COPY_CHUNK_BYTES = 64 * 1024

private fun copyBounded(source: File): Outcome<StagedCiphertext, BackupError> {
    val directory = DesktopAppDirectories.directory(RESTORE_DIRECTORY).apply {
        // Copies left by a process that died are removed before a new one is made.
        listFiles()?.forEach { it.delete() }
    }
    val file = File(directory, "${UUID.randomUUID()}.${BackupFormat.FILE_EXTENSION}")
    return try {
        source.inputStream().use { input ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(COPY_CHUNK_BYTES)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_RESTORE_FILE_BYTES) {
                        file.delete()
                        return Outcome.Failure(BackupError.TooLarge)
                    }
                    output.write(buffer, 0, read)
                }
                output.flush()
                output.fd.sync()
            }
        }
        Outcome.Success(DesktopStagedCiphertext(file))
    } catch (failure: IOException) {
        file.delete()
        Outcome.Failure(BackupError.WriteFailed)
    } catch (failure: SecurityException) {
        file.delete()
        Outcome.Failure(BackupError.WriteFailed)
    }
}

/** A private copy of the picked file. Reading it again is cheap and it is deleted when the restore ends. */
internal class DesktopStagedCiphertext(private val file: File) : StagedCiphertext {
    override val size: Int = file.length().toInt()

    override suspend fun readAll(): ByteArray = withContext(Dispatchers.IO) { file.readBytes() }

    override fun discard() {
        file.delete()
    }
}

/**
 * Shows the native file dialog on the UI thread and returns the chosen file, or null when the user cancels. The dialog
 * is modal to the vault window. Only `.dftar` files are suggested; the bytes decide whether a file is a backup.
 */
private suspend fun showDialog(title: String, mode: Int, suggestedName: String?): File? =
    withContext(Dispatchers.Main) {
        val dialog = FileDialog(DesktopWindowHost.frame, title, mode).apply {
            if (suggestedName != null) file = suggestedName
            setFilenameFilter { _, name -> name.endsWith(".${BackupFormat.FILE_EXTENSION}") }
        }
        dialog.isVisible = true
        val directory = dialog.directory
        val name = dialog.file
        dialog.dispose()
        if (directory == null || name == null) null else File(directory, name)
    }
