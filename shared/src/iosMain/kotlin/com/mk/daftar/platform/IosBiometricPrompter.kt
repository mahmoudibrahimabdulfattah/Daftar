package com.mk.daftar.platform

import com.mk.daftar.core.security.BiometricAuthResult
import com.mk.daftar.core.security.BiometricCrypto
import com.mk.daftar.core.security.BiometricPrompter
import com.mk.daftar.core.security.IosBiometricCrypto
import com.mk.daftar.shared.resources.Res
import com.mk.daftar.shared.resources.biometric_prompt_title
import org.jetbrains.compose.resources.getString

/**
 * The iOS prompt. The Secure Enclave operation itself shows the system biometric sheet, so this adapter only supplies
 * the reason text on the operation's [com.mk.daftar.core.security.IosBiometricCrypto] context. The operation completes
 * or fails from the system's answer, which keeps a cancelled sheet from ever counting as authentication.
 */
internal class IosBiometricPrompter : BiometricPrompter {
    override suspend fun authorize(crypto: BiometricCrypto): BiometricAuthResult {
        val ios = crypto as? IosBiometricCrypto ?: return BiometricAuthResult.Failed
        ios.context.localizedReason = getString(Res.string.biometric_prompt_title)
        return BiometricAuthResult.Authenticated(crypto)
    }
}
