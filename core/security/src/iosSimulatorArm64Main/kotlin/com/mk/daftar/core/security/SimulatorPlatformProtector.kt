package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

internal actual fun createPlatformEnvelopeProtector(generation: VaultGeneration): PlatformEnvelopeProtector =
    SimulatorOnlyTestEnvelopeProtector(keyAccount = generation.name(ENVELOPE_KEY_PURPOSE))

internal actual fun createPlatformBiometricKeyStore(generation: VaultGeneration): BiometricKeyStore =
    SimulatorOnlyBiometricKeyStore(accountPrefix = generation.name(BIOMETRIC_KEY_PURPOSE))

internal actual fun erasePlatformEnvelopeKey(generation: VaultGeneration): Outcome<Unit, SecurityError> =
    when (KeychainItem(generation.name(ENVELOPE_KEY_PURPOSE)).delete()) {
        is Outcome.Failure -> Outcome.Failure(SecurityError.PersistenceFailure)
        is Outcome.Success -> Outcome.Success(Unit)
    }

private const val ENVELOPE_KEY_PURPOSE = "simulator-only-test-envelope-key.v1"
private const val BIOMETRIC_KEY_PURPOSE = "simulator-only-biometric-key."
