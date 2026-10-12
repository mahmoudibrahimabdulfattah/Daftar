@file:OptIn(ExperimentalForeignApi::class)

package com.mk.daftar.platform.backup

import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupFormat
import com.mk.daftar.core.backup.PickerOperationCoordinator
import com.mk.daftar.core.backup.RestoreFilePicker
import com.mk.daftar.core.backup.StagedCiphertext
import com.mk.daftar.core.common.Outcome
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSInputStream
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
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.memcpy

/** The largest file the format can hold, as for Android. Reading stops once it is passed. */
private val MAX_RESTORE_FILE_BYTES: Long =
    (BackupFormat.HEADER_BYTES + BackupFormat.MAX_PLAINTEXT_BYTES + BackupFormat.TAG_BYTES).toLong()

private const val COPY_CHUNK_BYTES: Int = 64 * 1024

/**
 * The iOS document picker in open mode. The file is read through a security-scoped URL and copied into private staging
 * under Application Support, with a bounded stream. The extension, type and advertised size are never trusted.
 */
internal class IosRestoreFilePicker : RestoreFilePicker {
    override suspend fun pick(): Outcome<StagedCiphertext?, BackupError> {
        val url = presentOpenPicker() ?: return Outcome.Success(null)
        return withContext(Dispatchers.Default) { copyBounded(url) }
    }
}

/** A private copy of the picked file, deleted when the restore ends. */
internal class IosStagedCiphertext(private val path: String, override val size: Int) : StagedCiphertext {
    override suspend fun readAll(): ByteArray = withContext(Dispatchers.Default) {
        NSFileManager.defaultManager.contentsAtPath(path)?.let { data ->
            ByteArray(data.length.toInt()).also { bytes ->
                bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
            }
        } ?: ByteArray(0)
    }

    override fun discard() {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }
}

/** Copies [url] into a fresh private file. Fails with [BackupError.TooLarge] once the limit is passed. */
private fun copyBounded(url: NSURL): Outcome<StagedCiphertext, BackupError> {
    val accessed = url.startAccessingSecurityScopedResource()
    try {
        val directory = stagingDirectory()
        val path = "$directory/${NSUUID().UUIDString}.${BackupFormat.FILE_EXTENSION}"
        val input = NSInputStream(uRL = url)
        input.open()
        val file = fopen(path, "wb") ?: run {
            input.close()
            return Outcome.Failure(BackupError.WriteFailed)
        }
        var total = 0L
        var failed: BackupError? = null
        val buffer = ByteArray(COPY_CHUNK_BYTES)
        memScopedRead(input, buffer) { read ->
            if (read < 0) {
                failed = BackupError.WriteFailed
                return@memScopedRead false
            }
            if (read == 0) return@memScopedRead false
            total += read
            if (total > MAX_RESTORE_FILE_BYTES) {
                failed = BackupError.TooLarge
                return@memScopedRead false
            }
            val written = buffer.usePinned { pinned -> fwrite(pinned.addressOf(0), 1.convert(), read.convert(), file) }
            if (written.toLong() != read.toLong()) {
                failed = BackupError.WriteFailed
                return@memScopedRead false
            }
            true
        }
        input.close()
        val closed = fclose(file) == 0
        val error = failed
        if (error != null || !closed) {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
            return Outcome.Failure(error ?: BackupError.WriteFailed)
        }
        return Outcome.Success(IosStagedCiphertext(path, total.toInt()))
    } finally {
        if (accessed) url.stopAccessingSecurityScopedResource()
    }
}

/** Reads [input] chunk by chunk into [buffer] until [sink] returns false. Returns the bytes read last, or negative. */
private inline fun memScopedRead(input: NSInputStream, buffer: ByteArray, sink: (Int) -> Boolean) {
    while (true) {
        val read = buffer.usePinned { pinned ->
            input.read(pinned.addressOf(0).reinterpret<UByteVar>(), buffer.size.convert()).toInt()
        }
        if (!sink(read)) return
    }
}

/** The Daftar directory under Application Support, where the restore copy waits for its decryption. */
private fun daftarStagingRoot(): String {
    val support = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path ?: error("Application Support is unavailable")
    return "$support/Daftar"
}

/** The private staging directory for picked files: excluded from backup, with file protection. */
private fun stagingDirectory(): String {
    val fileManager = NSFileManager.defaultManager
    val directory = "${daftarStagingRoot()}/restore-staging"
    fileManager.createDirectoryAtPath(
        directory,
        withIntermediateDirectories = true,
        attributes = mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
        error = null,
    )
    NSURL.fileURLWithPath(directory).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return directory
}

/** The delegate must stay alive while the picker is on screen, so it is held here until the picker answers. */
private var activeOpenDelegate: OpenPickerDelegate? = null

private class OpenPickerDelegate(private val onResult: (NSURL?) -> Unit) :
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
        activeOpenDelegate = null
        onResult(url)
    }
}

/** Main-thread scope for the picker operations. Shared with the export picker. */
internal val iosPickerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** The open picker's answers. One operation at a time, owned here rather than by the restore screen. */
private val openOperations = PickerOperationCoordinator<NSURL>(scope = iosPickerScope)

/**
 * Presents the open picker for data files and suspends until the user answers. Null means cancelled, expired or
 * abandoned. Only the coordinator's token ends the wait, so a privacy cover never cancels a picker that is on screen.
 */
private suspend fun presentOpenPicker(): NSURL? {
    val token = openOperations.begin()
    withContext(Dispatchers.Main) {
        val presenter = topViewController()
        if (presenter == null) {
            openOperations.complete(token, null)
            return@withContext
        }
        val delegate = OpenPickerDelegate { picked -> openOperations.complete(token, picked) }
        activeOpenDelegate = delegate
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData))
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }
    return openOperations.awaitResult(token)
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
