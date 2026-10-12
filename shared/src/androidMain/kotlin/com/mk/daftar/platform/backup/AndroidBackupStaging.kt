package com.mk.daftar.platform.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import com.mk.daftar.core.backup.BackupDocuments
import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupFormat
import com.mk.daftar.core.backup.BackupStaging
import com.mk.daftar.core.backup.PickedDestination
import com.mk.daftar.core.backup.PickerOperationCoordinator
import com.mk.daftar.core.backup.StagedBackup
import com.mk.daftar.core.common.Outcome
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The encrypted staging file, in `noBackupFilesDir` where the database also lives. It is never backed up or
 * transferred, and it is written only from already-encrypted bytes. Files left behind by a process that died are
 * removed when the staging area is created.
 */
internal class AndroidBackupStaging(context: Context) : BackupStaging {
    private val directory = File(context.noBackupFilesDir, STAGING_DIRECTORY)

    init {
        directory.listFiles()?.forEach { it.delete() }
    }

    override suspend fun stage(encrypted: ByteArray): Outcome<StagedBackup, BackupError> = withContext(Dispatchers.IO) {
        val file = File(directory, "${UUID.randomUUID()}.${BackupFormat.FILE_EXTENSION}")
        try {
            directory.mkdirs()
            FileOutputStream(file).use { output ->
                output.write(encrypted)
                output.flush()
                output.fd.sync()
            }
            Outcome.Success(StagedFile(file))
        } catch (failure: IOException) {
            file.delete()
            Outcome.Failure(BackupError.ResourceUnavailable)
        }
    }

    private companion object {
        const val STAGING_DIRECTORY = "backup-staging"
    }
}

/**
 * A staged encrypted file. Discarding deletes it; a failed delete is harmless because the next launch clears the area.
 */
internal class StagedFile(val file: File) : StagedBackup {
    override suspend fun discard() {
        file.delete()
    }
}

/** A destination the storage picker created. Only the URI the picker returned is ever written to. */
internal class AndroidPickedDestination(val uri: Uri) : PickedDestination

/**
 * The Android save picker and writer. The picker is the system's `CreateDocument`, which grants a scoped, temporary
 * URI for the one file the user chooses, so no storage permission is needed. Success is reported only after the output
 * stream has been closed and every staged byte was copied.
 */
internal class AndroidBackupDocuments(private val context: Context) : BackupDocuments {

    override suspend fun choose(staged: StagedBackup, suggestedName: String): Outcome<PickedDestination?, BackupError> {
        val uri = AndroidBackupPicker.pick(staged, suggestedName) ?: return Outcome.Success(null)
        return Outcome.Success(AndroidPickedDestination(uri))
    }

    override suspend fun write(staged: StagedBackup, destination: PickedDestination): Outcome<Unit, BackupError> {
        val source = (staged as? StagedFile)?.file ?: return Outcome.Failure(BackupError.WriteFailed)
        val target = (destination as? AndroidPickedDestination)?.uri ?: return Outcome.Failure(BackupError.WriteFailed)
        return withContext(Dispatchers.IO) {
            try {
                val output = context.contentResolver.openOutputStream(target, "wt")
                    ?: return@withContext Outcome.Failure(BackupError.WriteFailed)
                val copied = output.use { stream ->
                    source.inputStream().use { input -> input.copyTo(stream) }.also { stream.flush() }
                }
                if (copied == source.length()) Outcome.Success(Unit) else Outcome.Failure(BackupError.WriteFailed)
            } catch (failure: IOException) {
                Outcome.Failure(BackupError.WriteFailed)
            } catch (failure: SecurityException) {
                Outcome.Failure(BackupError.WriteFailed)
            }
        }
    }

    override suspend fun discard(destination: PickedDestination) {
        val uri = (destination as? AndroidPickedDestination)?.uri ?: return
        deleteQuietly(context, uri)
    }
}

/** Removes a partly written document, where the provider allows it. Best effort: it never throws. */
internal fun deleteQuietly(context: Context, uri: Uri) {
    runCatching {
        if (DocumentsContract.isDocumentUri(
                context,
                uri,
            )
        ) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
    }
}

/**
 * The bridge between the app's activity and the save picker. The activity registers [contract] once and calls [deliver]
 * with the result. The wait is an operation of [operations], which owns the encrypted staging while the picker is open.
 * A lock or a pause never cancels it. Its answer is accepted once, within the lifetime; a late answer is refused, and
 * the document the picker created is removed so no empty file is left behind.
 */
object AndroidBackupPicker {
    /** The system save picker for one binary file. It creates the document only when the user confirms. */
    val contract: ActivityResultContract<String, Uri?> = ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE)

    private val operations = PickerOperationCoordinator<Uri>(scope = androidPickerScope)

    private var launcher: ActivityResultLauncher<String>? = null
    private var appContext: Context? = null

    fun attach(context: Context, launcher: ActivityResultLauncher<String>) {
        this.appContext = context.applicationContext
        this.launcher = launcher
    }

    fun detach(launcher: ActivityResultLauncher<String>) {
        if (this.launcher === launcher) this.launcher = null
    }

    /** Called with the picker's result on the main thread. A null [uri] is the user's cancellation. */
    fun deliver(uri: Uri?) {
        if (operations.completePending(uri)) return
        val context = appContext
        if (uri != null && context != null) deleteQuietly(context, uri)
    }

    /** Opens the picker and suspends until the user answers. Returns null when the user cancels or the operation ends. */
    internal suspend fun pick(staging: StagedBackup, suggestedName: String): Uri? =
        withContext(Dispatchers.Main.immediate) {
            val active = checkNotNull(launcher) { "no save picker is attached" }
            val token = operations.begin(staging)
            active.launch(suggestedName)
            operations.awaitResult(token)
        }
}
