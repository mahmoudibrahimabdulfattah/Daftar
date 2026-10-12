package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Chooses the envelope protector for this iOS target, for one generation. Device binaries get the Secure Enclave key.
 * The simulator binary gets a clearly named test protector, which is compiled into the simulator target only.
 */
internal expect fun createPlatformEnvelopeProtector(generation: VaultGeneration): PlatformEnvelopeProtector

/** Chooses the biometric key store for this iOS target, for one generation. Device binaries never get the test key. */
internal expect fun createPlatformBiometricKeyStore(generation: VaultGeneration): BiometricKeyStore

/** Removes the envelope key of [generation]. Idempotent. */
internal expect fun erasePlatformEnvelopeKey(generation: VaultGeneration): Outcome<Unit, SecurityError>

internal actual val platformSecurityModule: Module = module {
    single<PlatformVaultStorageFactory> { IosVaultStorageFactory() }
}
