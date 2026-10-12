package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

internal actual fun createPlatformEnvelopeProtector(generation: VaultGeneration): PlatformEnvelopeProtector =
    SecureEnclaveProtector(keyTag = "com.mk.daftar.security." + generation.name(ENVELOPE_KEY_PURPOSE))

internal actual fun createPlatformBiometricKeyStore(generation: VaultGeneration): BiometricKeyStore =
    SecureEnclaveBiometricKeyStore(tagPrefix = "com.mk.daftar.security." + generation.name(BIOMETRIC_KEY_PURPOSE))

internal actual fun erasePlatformEnvelopeKey(generation: VaultGeneration): Outcome<Unit, SecurityError> =
    deleteSecureEnclaveKey("com.mk.daftar.security." + generation.name(ENVELOPE_KEY_PURPOSE))

private const val ENVELOPE_KEY_PURPOSE = "pin-envelope.secure-enclave.v1"
private const val BIOMETRIC_KEY_PURPOSE = "biometric.v1."
