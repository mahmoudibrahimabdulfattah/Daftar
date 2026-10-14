package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

/**
 * The PIN-unlocked vault. The caller owns the PIN buffers passed in and wipes them after use. The vault never
 * retains them.
 */
interface VaultSecurity {
    suspend fun status(): SecurityStatus

    /** Creates the vault once, calibrates the KDF on this device, and returns an unlocked session. */
    suspend fun create(pin: SecretBytes): Outcome<KeyLease, SecurityError>

    /** Unlocks with the PIN. Attempts are serialized and subject to the persistent backoff. */
    suspend fun unlock(pin: SecretBytes): Outcome<KeyLease, SecurityError>

    /**
     * Checks [pin] against the stored envelope through the same attempt and backoff coordinator as unlock. A wrong PIN
     * counts toward the penalty. A correct PIN resets the count. No session lease is published or changed.
     */
    suspend fun verifyCurrentPin(pin: SecretBytes): Outcome<Unit, SecurityError>

    /**
     * Rewraps the existing DEK under [newPin]. The database bytes never change, and the old envelope stays in
     * place unless the new one has been verified and committed. A lock or unlock that happens before the envelope commit
     * makes the change fail with [SecurityError.SessionSuperseded], and the previous envelope stays in effect.
     */
    suspend fun changePin(currentPin: SecretBytes, newPin: SecretBytes): Outcome<Unit, SecurityError>

    /** Closes the session. Any in-flight unlock that completes afterwards is rejected. */
    suspend fun lock()

    /** Whether the platform can authenticate with a strong biometric right now. */
    suspend fun biometricCapability(): BiometricCapability

    /** Whether the stored biometric slot is absent, valid or unusable. The PIN envelope is never consulted. */
    suspend fun biometricSlotState(): Outcome<BiometricSlotState, BiometricError>

    /**
     * Wraps the open session's DEK under a new biometric-bound key. [lease] must have been proven with the PIN in this
     * session: a session opened with biometrics cannot authorize enrollment. Commits the slot only after it is written.
     * The PIN envelope and the database are unchanged.
     */
    suspend fun enrollBiometrics(lease: KeyLease, prompter: BiometricPrompter): Outcome<Unit, BiometricError>

    /** Re-enrollment from settings: verifies [pin] first, then enrolls the current DEK. Never opens a session. */
    suspend fun reenrollBiometrics(pin: SecretBytes, prompter: BiometricPrompter): Outcome<Unit, BiometricError>

    /** Opens the vault with the biometric slot. A successful prompt alone never opens it: the DEK must unwrap. */
    suspend fun unlockWithBiometrics(prompter: BiometricPrompter): Outcome<KeyLease, BiometricError>

    /** Destroys the biometric slot and its key. Idempotent. The PIN stays usable. */
    suspend fun disableBiometrics(): Outcome<Unit, BiometricError>

    /** Whether this vault has a recovery key. */
    suspend fun recoveryState(): Outcome<RecoveryState, SecurityError> = Outcome.Success(RecoveryState.Absent)

    /**
     * Verifies [pin] (counted like any attempt), then creates a new recovery key for the current DEK and returns it,
     * formatted for display. It is never stored: only its public key is kept in the hardware-sealed slot. A previous
     * recovery key stops working once the new slot is committed. Never opens a session.
     */
    suspend fun setUpRecovery(pin: SecretBytes): Outcome<String, SecurityError> =
        Outcome.Failure(SecurityError.RecoveryNotSetUp)

    /**
     * Opens the vault with [recoveryKey] when the PIN is forgotten: the DEK is recovered from the slot on this device,
     * wrapped under [newPin], and the backoff starts fresh. The database and the biometric slot are unchanged.
     */
    suspend fun recover(recoveryKey: String, newPin: SecretBytes): Outcome<KeyLease, SecurityError> =
        Outcome.Failure(SecurityError.RecoveryNotSetUp)

    /** The recovery public key, so a backup's key can be sealed to it. Null when no recovery key is set up. */
    suspend fun recoveryPublicKey(): Outcome<ByteArray?, SecurityError> = Outcome.Success(null)
}
