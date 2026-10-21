package com.mk.daftar.core.backup

import com.mk.daftar.core.domain.storage.StorageError

/**
 * Why an export or a decode failed. No variant carries a password, a key, record values or file contents, so the
 * message a screen shows can never leak them.
 */
sealed interface BackupError {
    /** The vault could not be read consistently. The data layer's reason is kept, without any value. */
    data class Storage(val error: StorageError) : BackupError

    /** The snapshot exceeds the plaintext limit, so nothing is written. */
    data object TooLarge : BackupError

    /** The snapshot exceeds the record limit, so nothing is written. */
    data object TooManyRecords : BackupError

    /** A required resource (memory, the platform RNG or libsodium) was unavailable. */
    data object ResourceUnavailable : BackupError

    /**
     * Argon2id could not allocate its memory for this profile. Nothing was written or changed. The export tries LowMemory
     * once, so this reaches the caller only when that also failed, or when a restore cannot allocate.
     */
    data object InsufficientMemory : BackupError

    /**
     * The file is a newer or unknown format: its version, KDF, cipher or parameters are outside this app's allowlist.
     */
    data object UnsupportedFormat : BackupError

    /** The file is not a Daftar backup, or its header is malformed or truncated. */
    data object NotABackup : BackupError

    /** The password is wrong, or the header or ciphertext was modified. The two are deliberately not told apart. */
    data object AuthenticationFailed : BackupError

    /** The file decrypted, but its payload does not match schema version one. */
    data object CorruptPayload : BackupError

    /** Writing the staged file, or the destination, failed. The destination is never reported as complete. */
    data object WriteFailed : BackupError
}
