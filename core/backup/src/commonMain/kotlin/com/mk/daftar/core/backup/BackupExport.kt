package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * An encrypted, no-backup temporary file the platform created. Common code only passes it back, and it is always
 * discarded once the export finishes, fails or is cancelled.
 */
interface StagedBackup {
    /** Deletes the staged file. Never throws; failures to delete are best effort. */
    suspend fun discard()
}

/** A destination the user chose in the system picker. Opaque to common code. */
interface PickedDestination

/** Writes the encrypted bytes into the platform's private, device-only, no-backup staging area. */
interface BackupStaging {
    suspend fun stage(encrypted: ByteArray): Outcome<StagedBackup, BackupError>
}

/** The system document services. Nothing here requests a broad storage permission. */
interface BackupDocuments {
    /** Shows the save picker for [staged]. Null means the user cancelled. */
    suspend fun choose(staged: StagedBackup, suggestedName: String): Outcome<PickedDestination?, BackupError>

    /** Copies [staged] to [destination]. Success is reported only after the destination is closed. */
    suspend fun write(staged: StagedBackup, destination: PickedDestination): Outcome<Unit, BackupError>

    /** Removes a partial destination where the platform allows it. Never throws. */
    suspend fun discard(destination: PickedDestination)
}

/** Where one export is. Screens render these; a finished export stays here until it is acknowledged. */
sealed interface BackupExportState {
    data object Idle : BackupExportState

    /** Reading the snapshot, encrypting it and staging the encrypted file. */
    data object Preparing : BackupExportState

    /** The save picker is open. */
    data object ChoosingDestination : BackupExportState

    /** The chosen destination is being written. */
    data object Writing : BackupExportState

    /** The file was written and its stream closed. [fileName] is the suggested name the user saw. */
    data class Complete(val fileName: String) : BackupExportState

    data class Failed(val error: BackupError) : BackupExportState
}

/** A finished export, held until it is acknowledged. It may finish while the vault is locked, so it is shown after unlock. */
val BackupExportState.awaitsAcknowledgment: Boolean
    get() = this is BackupExportState.Complete || this is BackupExportState.Failed

/**
 * Runs one export at a time, independent of any screen. It owns the snapshot, the staged file and the picker wait, so
 * a locked vault or a destroyed screen cannot strand a half-written file. The vault is not needed after staging: the
 * staged bytes are already encrypted.
 *
 * A caller starts an export with the password bytes, which the controller wipes. A finished export holds its terminal
 * state until [acknowledge]; a new export is refused until then. Cancelling before the write begins removes the staged
 * file and any partial destination, and it never changes the database.
 */
class BackupExportController(
    private val source: BackupSnapshotSource,
    private val encryptor: BackupEncryptor,
    private val staging: BackupStaging,
    private val documents: BackupDocuments,
    private val appVersion: String,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutableState = MutableStateFlow<BackupExportState>(BackupExportState.Idle)
    val state: StateFlow<BackupExportState> = mutableState.asStateFlow()

    private var job: Job? = null

    /**
     * Starts an export of the open vault. Returns false, and wipes [password], when an export is already running or a
     * finished one has not been acknowledged. Callers invoke this on the main thread.
     */
    fun start(password: ByteArray): Boolean {
        val current = mutableState.value
        if (current != BackupExportState.Idle) {
            password.fill(0)
            return false
        }
        mutableState.value = BackupExportState.Preparing
        job = scope.launch { run(password) }
        return true
    }

    /**
     * Cancels the export while it is preparing. The picker is modal and is dismissed by the user, and a write in
     * progress is never interrupted, so the result of a started copy is always reported.
     */
    fun cancel() {
        if (mutableState.value == BackupExportState.Preparing) job?.cancel()
    }

    /** Clears a finished export so the next one can start. */
    fun acknowledge() {
        when (mutableState.value) {
            is BackupExportState.Complete, is BackupExportState.Failed -> mutableState.value = BackupExportState.Idle
            else -> Unit
        }
    }

    private suspend fun run(password: ByteArray) {
        var staged: StagedBackup? = null
        var destination: PickedDestination? = null
        try {
            val snapshot = when (val read = source.read()) {
                is Outcome.Failure -> return fail(read.error)
                is Outcome.Success -> read.data
            }
            val exportedAt = clock.now()
            val encrypted = withContext(workDispatcher) {
                encryptor.encode(snapshot, password, exportedAt, appVersion)
            }
            val bytes = when (encrypted) {
                is Outcome.Failure -> return fail(encrypted.error)
                is Outcome.Success -> encrypted.data
            }
            staged = when (val stagedResult = staging.stage(bytes)) {
                is Outcome.Failure -> return fail(stagedResult.error)
                is Outcome.Success -> stagedResult.data
            }
            val fileName = BackupFormat.suggestedFileName(exportedAt.toLocalDateTime(timeZone).date)
            mutableState.value = BackupExportState.ChoosingDestination
            val picked = when (val chosen = documents.choose(staged, fileName)) {
                is Outcome.Failure -> return fail(chosen.error)

                is Outcome.Success -> chosen.data ?: run {
                    mutableState.value = BackupExportState.Idle
                    return
                }
            }
            destination = picked
            mutableState.value = BackupExportState.Writing
            when (val written = documents.write(staged, picked)) {
                is Outcome.Failure -> {
                    destination = null
                    withContext(NonCancellable) { documents.discard(picked) }
                    fail(written.error)
                }

                is Outcome.Success -> {
                    destination = null
                    mutableState.value = BackupExportState.Complete(fileName)
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                destination?.let { documents.discard(it) }
                mutableState.value = BackupExportState.Idle
            }
            throw cancelled
        } catch (failure: Exception) {
            withContext(NonCancellable) {
                destination?.let { documents.discard(it) }
            }
            fail(BackupError.ResourceUnavailable)
        } finally {
            password.fill(0)
            withContext(NonCancellable) { staged?.discard() }
        }
    }

    private fun fail(error: BackupError) {
        mutableState.value = BackupExportState.Failed(error)
    }
}
