package com.mk.daftar.core.security

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Chooses the envelope protector for this iOS target. Device binaries get the Secure Enclave protector. The simulator
 * binary gets a clearly named test protector, which is compiled into the simulator target only.
 */
internal expect fun createPlatformEnvelopeProtector(): PlatformEnvelopeProtector

/** Chooses the biometric key for this iOS target. Device binaries get the Secure Enclave key, never the test key. */
internal expect fun createPlatformBiometricKeyStore(): BiometricKeyStore

internal actual val platformSecurityModule: Module = module {
    single<PlatformEnvelopeProtector> { createPlatformEnvelopeProtector() }
    single<SecurityRecordStore> { KeychainRecordStore() }
    single<BackoffStore> { KeychainBackoffStore() }
    single<BiometricKeyStore> { createPlatformBiometricKeyStore() }
    single<BiometricSlotStore> { KeychainBiometricSlotStore() }
}
