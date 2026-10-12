package com.mk.daftar.core.security

internal actual fun createPlatformEnvelopeProtector(): PlatformEnvelopeProtector = SecureEnclaveProtector()

internal actual fun createPlatformBiometricKeyStore(): BiometricKeyStore = SecureEnclaveBiometricKeyStore()
