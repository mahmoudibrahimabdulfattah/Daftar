package com.mk.daftar.feature.settings

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.SecretBytes

/**
 * Every security action settings may take. `shared` binds it to the security layer and the authenticator, so this feature
 * never sees a key lease or the data layer. The caller owns each [SecretBytes] it passes and wipes it.
 */
interface SettingsSecurityGateway {
    /** The platform and stored-key facts for biometrics. Never throws: an unreadable slot is reported as unreadable. */
    suspend fun biometricFacts(): BiometricFacts

    /** Checks the current PIN without opening or changing the vault. A wrong PIN counts toward the penalty. */
    suspend fun verifyCurrentPin(pin: SecretBytes): Outcome<Unit, SecurityActionFailure>

    /**
     * Rewraps the data key under [newPin]. Success locks the vault, so the user authenticates with the new PIN. A failure
     * leaves the previous PIN in effect.
     */
    suspend fun changePin(currentPin: SecretBytes, newPin: SecretBytes): Outcome<Unit, SecurityActionFailure>

    /** Verifies [pin], then enrolls biometrics for the current key after the platform prompt succeeds. */
    suspend fun reenrollBiometrics(pin: SecretBytes): Outcome<Unit, SecurityActionFailure>

    /** Removes the biometric slot. Always available: no prompt and no PIN. */
    suspend fun disableBiometrics(): Outcome<Unit, SecurityActionFailure>
}
