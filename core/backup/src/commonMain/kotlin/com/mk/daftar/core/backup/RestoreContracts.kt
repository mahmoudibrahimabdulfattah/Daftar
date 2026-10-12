package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.SecretBytes

/**
 * An encrypted backup file that the picker copied into private, device-only storage. Only ciphertext ever reaches it;
 * the password and the decrypted content never do. [discard] removes the copy, and it is always called when a restore
 * ends, succeeds or not.
 */
interface StagedCiphertext {
    /** The number of bytes that were copied. The copy stops at the format's limit, so this never exceeds it. */
    val size: Int

    /** Returns the copied bytes. The caller owns them and wipes them once decoded. */
    suspend fun readAll(): ByteArray

    /** Deletes the copy. Synchronous and never throws, so it can run while the owner is being cleared. */
    fun discard()
}

/**
 * Opens the system picker for one file and copies it into private storage through a bounded stream. The extension, the
 * MIME type, the file name and any advertised length are never trusted. Null means the user cancelled.
 */
fun interface RestoreFilePicker {
    suspend fun pick(): Outcome<StagedCiphertext?, BackupError>
}

/** A restore that has been decoded, staged and verified: the new vault exists beside the original and nothing else. */
interface StagedRestore

/**
 * Why a restore stopped. Every value says what happened to the original vault: [NotReplaced] means it is exactly as it
 * was, and [RecoveryRequired] means the replacement may or may not have taken effect, so startup resolves it.
 */
enum class RestoreFailure {
    /** The new vault could not be created or written. The original vault is unchanged. */
    Staging,

    /** The new vault did not reproduce the backup. The original vault is unchanged. */
    Verification,

    /** The replacement was refused before it took effect. The original vault is unchanged. */
    NotReplaced,

    /** The replacement may have taken effect. The next start selects one complete vault and finishes the work. */
    RecoveryRequired,
}

/**
 * Stages, verifies and commits a restore. [stage] builds a complete new vault generation beside the original, with a
 * fresh key, a new PIN and no biometric slot. [commit] makes it the active vault in one durable pointer replacement, which
 * is the only point at which the original stops being the live vault. Nothing here merges records.
 */
interface RestoreEngine {
    suspend fun stage(snapshot: BackupSnapshot, pin: SecretBytes): Outcome<StagedRestore, RestoreFailure>

    suspend fun commit(staged: StagedRestore): Outcome<Unit, RestoreFailure>

    /** Erases a staged vault that will not be committed. The original vault is untouched. Never throws. */
    suspend fun discard(staged: StagedRestore)
}
