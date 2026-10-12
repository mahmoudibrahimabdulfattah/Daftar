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
}
