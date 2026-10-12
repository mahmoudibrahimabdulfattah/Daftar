package com.mk.daftar.platform.backup

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupFormat
import com.mk.daftar.core.backup.RestoreFilePicker
import com.mk.daftar.core.backup.StagedCiphertext
import com.mk.daftar.core.common.Outcome
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Android open picker and bounded copy. The system picker grants one temporary URI for the file the user chooses.
 * The bytes are copied into private, device-only storage, and reading stops at the format's largest possible file, so
 * neither the extension, the MIME type, the file name nor an advertised length is trusted.
 */
internal class AndroidRestoreFilePicker(private val context: Context) : RestoreFilePicker {
    override suspend fun pick(): Outcome<StagedCiphertext?, BackupError> {
        val uri = try {
            AndroidRestorePicker.pick()
        } catch (failure: IllegalStateException) {
            return Outcome.Failure(BackupError.ResourceUnavailable)
        } ?: return Outcome.Success(null)
        return withContext(Dispatchers.IO) { copyBounded(context, uri) }
    }
}

/** The largest file the format can hold: the header, the largest allowed plaintext and the tag. */
internal const val MAX_RESTORE_FILE_BYTES: Long =
    (BackupFormat.HEADER_BYTES + BackupFormat.MAX_PLAINTEXT_BYTES + BackupFormat.TAG_BYTES).toLong()

private const val RESTORE_DIRECTORY = "restore-staging"
private const val COPY_CHUNK_BYTES = 64 * 1024

/** Copies [uri] into a fresh private file, and fails with [BackupError.TooLarge] as soon as the limit is passed. */
private fun copyBounded(context: Context, uri: Uri): Outcome<StagedCiphertext, BackupError> {
    val directory = File(context.noBackupFilesDir, RESTORE_DIRECTORY).apply {
        // Copies left by a process that died are removed before a new one is made.
        listFiles()?.forEach { it.delete() }
        mkdirs()
    }
    val file = File(directory, "${UUID.randomUUID()}.${BackupFormat.FILE_EXTENSION}")
    return try {
        val source = context.contentResolver.openInputStream(uri)
            ?: return Outcome.Failure(BackupError.WriteFailed)
        source.use { input ->
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
        Outcome.Success(AndroidStagedCiphertext(file))
    } catch (failure: IOException) {
        file.delete()
        Outcome.Failure(BackupError.WriteFailed)
    } catch (failure: SecurityException) {
        file.delete()
        Outcome.Failure(BackupError.WriteFailed)
    }
}

/** A private copy of the picked file. Reading it again is cheap and it is deleted when the restore ends. */
internal class AndroidStagedCiphertext(private val file: File) : StagedCiphertext {
    override val size: Int = file.length().toInt()

    override suspend fun readAll(): ByteArray = withContext(Dispatchers.IO) { file.readBytes() }

    override fun discard() {
        file.delete()
    }
}

/**
 * The bridge between the activity and the open picker, as for the export picker. The activity registers [contract]
 * before it starts and calls [deliver] with the result. A picker result that nobody waits for is simply dropped: nothing
 * was created, so nothing is removed.
 */
object AndroidRestorePicker {
    /** The system open picker. Any document may be chosen, because the file is validated by its bytes, not its name. */
    val contract: ActivityResultContract<Array<String>, Uri?> = ActivityResultContracts.OpenDocument()

    private val accepted = arrayOf("*/*")

    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var pending: CompletableDeferred<Uri?>? = null

    fun attach(launcher: ActivityResultLauncher<Array<String>>) {
        this.launcher = launcher
    }

    fun detach(launcher: ActivityResultLauncher<Array<String>>) {
        if (this.launcher === launcher) this.launcher = null
    }

    /** Called with the picker's result on the main thread. */
    fun deliver(uri: Uri?) {
        val waiting = pending
        pending = null
        waiting?.complete(uri)
    }

    /** Opens the picker and suspends until the user answers. Returns null when the user cancels. */
    internal suspend fun pick(): Uri? = withContext(Dispatchers.Main.immediate) {
        val active = checkNotNull(launcher) { "no open picker is attached" }
        val waiting = CompletableDeferred<Uri?>()
        pending = waiting
        active.launch(accepted)
        waiting.await()
    }
}
