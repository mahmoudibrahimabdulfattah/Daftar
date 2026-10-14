package com.mk.daftar.feature.backup.restore

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.backup.BackupDecryptor
import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.DecodedBackup
import com.mk.daftar.core.backup.RestoreEngine
import com.mk.daftar.core.backup.RestoreFailure
import com.mk.daftar.core.backup.RestoreFilePicker
import com.mk.daftar.core.backup.StagedCiphertext
import com.mk.daftar.core.backup.StagedRestore
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.PinEntryBuffer
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.ui.mvi.MviViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The restore wizard: pick, decrypt, review, choose a new PIN, confirm, prepare, commit. It holds the copied file only
 * until it is decrypted, the password only until decryption starts, the decoded backup until it is staged, and the PIN
 * drafts until they are used. Every one of these is dropped on a privacy event, and the replacement is never started
 * without an explicit confirmation.
 *
 * Preparing can be cancelled, and the engine then erases what it built. Once committing starts, nothing here cancels it.
 */
class RestoreViewModel(
    private val picker: RestoreFilePicker,
    private val decryptor: BackupDecryptor,
    private val engine: RestoreEngine,
    private val drafts: RestoreDraftRegistry,
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MviViewModel<RestoreUiState, RestoreIntent, RestoreEffect>(RestoreUiState()) {

    private var entry = PinEntryBuffer()

    /** The first PIN, owned here until it is confirmed or the wizard leaves the step. */
    private var candidate: SecretBytes? = null

    /** The copied ciphertext, present only between the pick and the decryption. */
    private var ciphertext: StagedCiphertext? = null

    /** The decoded backup, present only between the decryption and the staging. Never shown beyond its summary. */
    private var decoded: DecodedBackup? = null

    /** The verified staged restore, present only between the staging and the commit. */
    private var staged: StagedRestore? = null

    private var work: Job? = null
    private val unregister: () -> Unit = drafts.register { onIntent(RestoreIntent.PrivacyInvalidated) }

    override fun onIntent(intent: RestoreIntent) {
        when (intent) {
            RestoreIntent.ChooseFile -> chooseFile()

            is RestoreIntent.PasswordChanged -> reduce { copy(password = intent.text) }

            RestoreIntent.SubmitPassword -> submitPassword()

            RestoreIntent.ToggleRecoveryKey -> reduce {
                if (phase == RestorePhase.Password) copy(usingRecoveryKey = !usingRecoveryKey, password = "") else this
            }

            RestoreIntent.ContinueToPin -> continueToPin()

            is RestoreIntent.Digit -> digit(intent.codePoint)

            RestoreIntent.Erase -> erase()

            RestoreIntent.SubmitPin -> submitPin()

            RestoreIntent.ConfirmReplacement -> confirmReplacement()

            RestoreIntent.CancelReplacement -> cancelReplacement()

            RestoreIntent.CancelPreparing -> cancelPreparing()

            RestoreIntent.Back -> back()

            RestoreIntent.Acknowledge -> acknowledge()

            RestoreIntent.Close -> close()

            RestoreIntent.PrivacyInvalidated -> invalidate()
        }
    }

    override fun onCleared() {
        unregister()
        work?.cancel()
        dropAll()
        super.onCleared()
    }

    private fun chooseFile() {
        val phase = currentState.phase
        if (phase != RestorePhase.ChooseFile || work?.isActive == true) return
        work = viewModelScope.launch {
            when (val picked = picker.pick()) {
                is Outcome.Failure -> fail(RestoreProblem.FileUnavailable)

                is Outcome.Success -> {
                    val file = picked.data
                    if (file == null) {
                        // The user cancelled the picker. Nothing was copied, so the wizard stays where it is.
                        return@launch
                    }
                    ciphertext?.discard()
                    ciphertext = file
                    reduce { copy(phase = RestorePhase.Password, password = "") }
                }
            }
        }
    }

    private fun submitPassword() {
        if (!currentState.canSubmitPassword) return
        val file = ciphertext ?: return fail(RestoreProblem.FileUnavailable)
        val recoveryKey = currentState.password.takeIf { currentState.usingRecoveryKey }
        val password = if (recoveryKey == null) currentState.password.encodeToByteArray() else ByteArray(0)
        reduce { copy(phase = RestorePhase.Decrypting, password = "", usingRecoveryKey = false) }
        work = viewModelScope.launch {
            val outcome = try {
                val bytes = file.readAll()
                withContext(decodeDispatcher) {
                    if (recoveryKey != null) {
                        decryptor.decryptWithRecoveryKey(bytes, recoveryKey)
                    } else {
                        decryptor.decrypt(bytes, password)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Outcome.Failure(BackupError.ResourceUnavailable)
            } finally {
                password.fill(0)
                file.discard()
                if (ciphertext === file) ciphertext = null
            }
            when (outcome) {
                is Outcome.Failure -> fail(outcome.error.toProblem())

                is Outcome.Success -> {
                    val backup = outcome.data
                    decoded = backup
                    reduce { copy(phase = RestorePhase.Review, summary = backup.summary()) }
                }
            }
        }
    }

    private fun continueToPin() {
        if (currentState.phase != RestorePhase.Review) return
        entry.clear()
        reduce { copy(phase = RestorePhase.NewPin(confirming = false), pinCount = 0, pinMismatch = false) }
    }

    private fun digit(codePoint: Int) {
        if (!inputEnabled()) return
        if (!entry.append(codePoint)) return
        reduce { copy(pinCount = entry.size, pinMismatch = false) }
    }

    private fun erase() {
        if (!inputEnabled()) return
        if (!entry.erase()) return
        reduce { copy(pinCount = entry.size) }
    }

    private fun inputEnabled(): Boolean = currentState.phase is RestorePhase.NewPin

    private fun submitPin() {
        if (!currentState.canSubmitPin) return
        when (currentState.phase) {
            RestorePhase.ChooseFile, RestorePhase.Password, RestorePhase.Decrypting, RestorePhase.Review,
            RestorePhase.ConfirmReplacement, RestorePhase.Preparing, RestorePhase.Committing,
            RestorePhase.Complete, is RestorePhase.Failed,
            -> return

            is RestorePhase.NewPin -> {
                val confirming = (currentState.phase as RestorePhase.NewPin).confirming
                if (!confirming) {
                    val chosen = entry.snapshot() ?: return
                    candidate?.wipe()
                    candidate = chosen
                    entry.clear()
                    reduce { copy(phase = RestorePhase.NewPin(confirming = true), pinCount = 0, pinMismatch = false) }
                } else {
                    val expected = candidate
                    if (expected == null) {
                        entry.clear()
                        reduce { copy(phase = RestorePhase.NewPin(confirming = false), pinCount = 0) }
                        return
                    }
                    val matched = entry.matches(expected)
                    entry.clear()
                    if (matched) {
                        reduce { copy(phase = RestorePhase.ConfirmReplacement, pinCount = 0, pinMismatch = false) }
                    } else {
                        // Only the confirmation is cleared. The first PIN stays, so the user can type it again.
                        reduce { copy(pinCount = 0, pinMismatch = true) }
                    }
                }
            }
        }
    }

    private fun confirmReplacement() {
        if (currentState.phase != RestorePhase.ConfirmReplacement) return
        val pin = candidate ?: return restartPin()
        candidate = null
        val backup = decoded ?: return restartPin()
        decoded = null
        reduce { copy(phase = RestorePhase.Preparing) }
        work = viewModelScope.launch {
            val prepared = try {
                engine.stage(backup.snapshot, pin)
            } finally {
                pin.wipe()
            }
            when (prepared) {
                is Outcome.Failure -> fail(prepared.error.toProblem())

                is Outcome.Success -> {
                    staged = prepared.data
                    commit(prepared.data)
                }
            }
        }
    }

    private suspend fun commit(restore: StagedRestore) {
        reduce { copy(phase = RestorePhase.Committing) }
        val committed = withContext(NonCancellable) { engine.commit(restore) }
        staged = null
        when (committed) {
            is Outcome.Success -> reduce { copy(phase = RestorePhase.Complete, summary = null) }
            is Outcome.Failure -> fail(committed.error.toProblem())
        }
    }

    private fun cancelReplacement() {
        if (currentState.phase != RestorePhase.ConfirmReplacement) return
        candidate?.wipe()
        candidate = null
        reduce { copy(phase = RestorePhase.Review) }
    }

    private fun cancelPreparing() {
        if (currentState.phase != RestorePhase.Preparing) return
        // Cancelling stops the staging, and the engine erases the vault it had built before this returns.
        work?.cancel()
        dropAll()
        reduce { copy(phase = RestorePhase.ChooseFile, password = "", summary = null, pinCount = 0) }
    }

    private fun back() {
        when (currentState.phase) {
            RestorePhase.Password -> {
                ciphertext?.discard()
                ciphertext = null
                reduce { copy(phase = RestorePhase.ChooseFile, password = "") }
            }

            RestorePhase.Review -> {
                decoded = null
                reduce { copy(phase = RestorePhase.ChooseFile, summary = null) }
            }

            is RestorePhase.NewPin -> {
                if ((currentState.phase as RestorePhase.NewPin).confirming) {
                    entry.clear()
                    candidate?.wipe()
                    candidate = null
                    reduce { copy(phase = RestorePhase.NewPin(confirming = false), pinCount = 0, pinMismatch = false) }
                } else {
                    dropPin()
                    reduce { copy(phase = RestorePhase.Review, pinCount = 0, pinMismatch = false) }
                }
            }

            RestorePhase.ConfirmReplacement -> cancelReplacement()

            is RestorePhase.Failed -> reduce { copy(phase = RestorePhase.ChooseFile, password = "") }

            else -> Unit
        }
    }

    private fun acknowledge() {
        when (currentState.phase) {
            RestorePhase.Complete -> close()

            is RestorePhase.Failed -> {
                dropAll()
                reduce { copy(phase = RestorePhase.ChooseFile, password = "", summary = null, pinCount = 0) }
            }

            else -> Unit
        }
    }

    private fun close() {
        if (currentState.phase == RestorePhase.Committing) return
        work?.cancel()
        dropAll()
        emitEffect(RestoreEffect.Close)
    }

    /**
     * Privacy: drop every draft. Preparing is cancelled, since nothing has been committed. Committing is left alone: it
     * is the one step that cannot be stopped, and it reports its own outcome.
     *
     * A pick that is waiting on the system picker is left running. It holds no secret, and the picker is outside the app,
     * so the app always goes through this path when the user opens it. Its answer then arrives as a fresh password step.
     */
    private fun invalidate() {
        if (currentState.phase == RestorePhase.ChooseFile && work?.isActive == true) return
        when (currentState.phase) {
            RestorePhase.Committing -> return

            RestorePhase.Preparing -> {
                work?.cancel()
                dropAll()
                reduce { copy(phase = RestorePhase.ChooseFile, password = "", summary = null, pinCount = 0) }
            }

            is RestorePhase.Complete, is RestorePhase.Failed -> {
                dropAll()
                reduce { copy(password = "", pinCount = 0) }
            }

            else -> {
                work?.cancel()
                dropAll()
                reduce {
                    copy(
                        phase = RestorePhase.ChooseFile,
                        password = "",
                        summary = null,
                        pinCount = 0,
                        pinMismatch = false,
                    )
                }
            }
        }
    }

    private fun fail(problem: RestoreProblem) {
        dropPin()
        reduce { copy(phase = RestorePhase.Failed(problem), password = "", summary = null, pinCount = 0) }
    }

    private fun restartPin() {
        dropPin()
        reduce { copy(phase = RestorePhase.NewPin(confirming = false), pinCount = 0, pinMismatch = false) }
    }

    /** Clears the PIN drafts. The entry and the candidate are the only PIN material this wizard keeps. */
    private fun dropPin() {
        entry.clear()
        candidate?.wipe()
        candidate = null
    }

    /** Drops every secret the wizard holds, in one place, so no path can forget one. */
    private fun dropAll() {
        dropPin()
        ciphertext?.discard()
        ciphertext = null
        decoded = null
        staged = null
    }
}

/** The review summary, derived from the validated snapshot. Counts and the date only. */
private fun DecodedBackup.summary(): RestoreSummary = RestoreSummary(
    exportedAt = exportedAt,
    goldHoldings = snapshot.goldHoldings.size,
    banks = snapshot.banks.size,
    bankAccounts = snapshot.bankAccounts.size,
    cashBalances = snapshot.cashBalances.size,
    zakatPayments = snapshot.zakatPayments.size,
    personalNotes = snapshot.personalNotes.size,
    remoteQuotes = snapshot.remoteQuotes.size,
    manualQuotes = snapshot.manualQuotes.size,
)

/** A decryption or file failure, in the screen's terms. Wrong password and damage share one answer. */
internal fun BackupError.toProblem(): RestoreProblem = when (this) {
    BackupError.UnsupportedFormat -> RestoreProblem.NewerFormat

    BackupError.AuthenticationFailed,
    BackupError.NotABackup,
    BackupError.CorruptPayload,
    BackupError.TooLarge,
    BackupError.TooManyRecords,
    -> RestoreProblem.Unreadable

    BackupError.ResourceUnavailable,
    BackupError.WriteFailed,
    is BackupError.Storage,
    -> RestoreProblem.FileUnavailable
}

/** A replacement failure. Anything before the commit leaves the original vault unchanged. */
internal fun RestoreFailure.toProblem(): RestoreProblem = when (this) {
    RestoreFailure.Staging, RestoreFailure.Verification, RestoreFailure.NotReplaced -> RestoreProblem.NotReplaced
    RestoreFailure.RecoveryRequired -> RestoreProblem.RecoveryRequired
}
