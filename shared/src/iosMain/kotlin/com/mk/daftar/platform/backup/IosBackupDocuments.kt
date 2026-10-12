@file:OptIn(ExperimentalForeignApi::class)

package com.mk.daftar.platform.backup

import com.mk.daftar.core.backup.BackupDocuments
import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupStaging
import com.mk.daftar.core.backup.PickedDestination
import com.mk.daftar.core.backup.StagedBackup
import com.mk.daftar.core.common.Outcome
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fwrite

/** The encrypted staging file under Application Support, excluded from backup, with file protection. */
internal class IosBackupStaging : BackupStaging {
    private val directory: String = stagingDirectory()

    init {
        // Files left by an earlier process are removed before the first export of this process.
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(directory, error = null)?.forEach { name ->
            NSFileManager.defaultManager.removeItemAtPath("$directory/$name", error = null)
        }
    }

    override suspend fun stage(encrypted: ByteArray): Outcome<StagedBackup, BackupError> =
        withContext(Dispatchers.Default) {
            val path = "$directory/${NSUUID().UUIDString}"
            if (writeFile(path, encrypted)) {
                Outcome.Success(IosStagedFile(path))
            } else {
                Outcome.Failure(BackupError.ResourceUnavailable)
            }
        }
}

/** A staged encrypted file. [exportCopy] is the copy named for the picker, created when the picker opens. */
internal class IosStagedFile(val path: String) : StagedBackup {
    var exportCopy: String? = null

    override suspend fun discard() {
        val fileManager = NSFileManager.defaultManager
        fileManager.removeItemAtPath(path, error = null)
        exportCopy?.let { fileManager.removeItemAtPath(it, error = null) }
        exportCopy = null
    }
}

/** The destination the document picker returned after it finished copying the file. */
internal class IosPickedDestination(val url: NSURL) : PickedDestination

/**
 * The iOS document picker in export mode. It copies the staged file to the location the user chose, so the picker's
 * completion callback is the signal that the export finished. Cancelling the picker reports no destination.
 */
internal class IosBackupDocuments : BackupDocuments {

    override suspend fun choose(staged: StagedBackup, suggestedName: String): Outcome<PickedDestination?, BackupError> {
        val file = staged as? IosStagedFile ?: return Outcome.Failure(BackupError.WriteFailed)
        val named = "${file.path}-export/$suggestedName"
        val fileManager = NSFileManager.defaultManager
        fileManager.createDirectoryAtPath(
            "${file.path}-export",
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        if (!fileManager.copyItemAtPath(file.path, named, error = null)) return Outcome.Failure(BackupError.WriteFailed)
        file.exportCopy = "${file.path}-export"
        val url = NSURL.fileURLWithPath(named)
        val picked = presentPicker(url) ?: run {
            fileManager.removeItemAtPath("${file.path}-export", error = null)
            file.exportCopy = null
            return Outcome.Success(null)
        }
        return Outcome.Success(IosPickedDestination(picked))
    }

    override suspend fun write(staged: StagedBackup, destination: PickedDestination): Outcome<Unit, BackupError> {
        // The picker reports completion only after it has copied the file, so there is no further write here.
        return if (destination is IosPickedDestination) {
            Outcome.Success(
                Unit,
            )
        } else {
            Outcome.Failure(BackupError.WriteFailed)
        }
    }

    override suspend fun discard(destination: PickedDestination) {
        val picked = destination as? IosPickedDestination ?: return
        NSFileManager.defaultManager.removeItemAtURL(picked.url, error = null)
    }
}

/** The delegate must stay alive while the picker is on screen, so it is held here until the picker answers. */
private var activeDelegate: PickerDelegate? = null

private class PickerDelegate(private val onResult: (NSURL?) -> Unit) :
    NSObject(),
    UIDocumentPickerDelegateProtocol {
    private var answered = false

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        finish(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(null)
    }

    private fun finish(url: NSURL?) {
        if (answered) return
        answered = true
        activeDelegate = null
        onResult(url)
    }
}

/** Presents the export picker over the top view controller and suspends until the user answers. */
private suspend fun presentPicker(url: NSURL): NSURL? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->
        val presenter = topViewController()
        if (presenter == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val delegate = PickerDelegate { picked -> if (continuation.isActive) continuation.resume(picked) }
        activeDelegate = delegate
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
        picker.delegate = delegate
        continuation.invokeOnCancellation { activeDelegate = null }
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}

private fun topViewController(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().firstOrNull()
    val window = scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.keyWindow }
    var top: UIViewController? = window?.rootViewController
    while (true) {
        val next = top?.presentedViewController ?: break
        top = next
    }
    return top
}

/** Writes [bytes] to a new file and reports success only when every byte was written, flushed and closed. */
private fun writeFile(path: String, bytes: ByteArray): Boolean {
    val file = fopen(path, "wb") ?: return false
    val written = bytes.usePinned { pinned -> fwrite(pinned.addressOf(0), 1.toULong(), bytes.size.toULong(), file) }
    val flushed = fflush(file) == 0
    val closed = fclose(file) == 0
    return written == bytes.size.toULong() && flushed && closed
}

/** Application Support, excluded from backup and protected like the database directory. */
private fun stagingDirectory(): String {
    val fileManager = NSFileManager.defaultManager
    val support = fileManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path ?: error("Application Support is unavailable")
    val directory = "$support/Daftar/backup-staging"
    fileManager.createDirectoryAtPath(
        directory,
        withIntermediateDirectories = true,
        attributes = mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
        error = null,
    )
    NSURL.fileURLWithPath(directory).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return directory
}
