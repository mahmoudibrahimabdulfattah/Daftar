package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

/**
 * Platform biometric key operations. Each key is identified by its [keyId], which is also bound into the slot's
 * associated data. A key is created only by [createKey], which refuses to replace one, and it is deleted only by
 * [deleteKey]. Nothing here is regenerated silently.
 */
internal interface BiometricKeyStore {
    suspend fun capability(): BiometricCapability

    suspend fun createKey(keyId: ByteArray): Outcome<Unit, BiometricError>

    /** Idempotent. */
    suspend fun deleteKey(keyId: ByteArray): Outcome<Unit, BiometricError>

    /**
     * Seals [plaintext] under the key, with [aad] authenticated. [prompter] authorizes the platform operation when the
     * platform requires it. The returned bytes are opaque to common code.
     */
    suspend fun wrap(
        keyId: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError>

    suspend fun unwrap(
        keyId: ByteArray,
        aad: ByteArray,
        sealed: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError>
}

/** Durable storage for the one encoded biometric slot. [write] is atomic. */
internal interface BiometricSlotStore {
    suspend fun read(): Outcome<ByteArray?, BiometricError>

    suspend fun write(slot: ByteArray): Outcome<Unit, BiometricError>

    suspend fun delete(): Outcome<Unit, BiometricError>
}

/** The biometric failure for a platform result that did not authorize the operation. */
internal fun BiometricAuthResult.failureOrNull(): BiometricError? = when (this) {
    is BiometricAuthResult.Authenticated -> null
    BiometricAuthResult.Cancelled -> BiometricError.Cancelled
    BiometricAuthResult.LockedOut -> BiometricError.LockedOut
    BiometricAuthResult.NotEnrolled -> BiometricError.NotEnrolled
    BiometricAuthResult.Unavailable -> BiometricError.Unavailable
    BiometricAuthResult.Failed -> BiometricError.AuthenticationFailed
}

/** Storage failures keep their meaning; anything else is reported as a security error without payloads. */
internal fun SecurityError.toBiometricError(): BiometricError = when (this) {
    SecurityError.PersistenceFailure -> BiometricError.StorageFailure
    SecurityError.CorruptState -> BiometricError.CorruptSlot
    else -> BiometricError.Security(this)
}
