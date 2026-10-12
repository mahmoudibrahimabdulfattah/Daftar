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
     * Rewraps the existing DEK under [newPin]. The database bytes never change, and the old envelope stays in
     * place unless the new one has been verified and committed.
     */
    suspend fun changePin(currentPin: SecretBytes, newPin: SecretBytes): Outcome<Unit, SecurityError>

    /** Closes the session. Any in-flight unlock that completes afterwards is rejected. */
    suspend fun lock()
}
