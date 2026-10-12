package com.mk.daftar.core.security.di

import com.mk.daftar.core.security.Aead
import com.mk.daftar.core.security.BackoffUnlockCoordinator
import com.mk.daftar.core.security.BiometricSlots
import com.mk.daftar.core.security.DefaultVaultSecurity
import com.mk.daftar.core.security.LibsodiumAead
import com.mk.daftar.core.security.LibsodiumPinKdf
import com.mk.daftar.core.security.LibsodiumSecureRandom
import com.mk.daftar.core.security.MonotonicClock
import com.mk.daftar.core.security.PinKdf
import com.mk.daftar.core.security.SecureRandom
import com.mk.daftar.core.security.SystemMonotonicClock
import com.mk.daftar.core.security.UnlockCoordinator
import com.mk.daftar.core.security.VaultSecurity
import com.mk.daftar.core.security.platformSecurityModule
import org.koin.core.module.Module
import org.koin.dsl.module

/** Platform-independent bindings. Tests load this with fakes for the platform part. */
internal val securityCoreModule: Module = module {
    single<MonotonicClock> { SystemMonotonicClock }
    single<PinKdf> { LibsodiumPinKdf() }
    single<Aead> { LibsodiumAead() }
    single<SecureRandom> { LibsodiumSecureRandom() }
    single<UnlockCoordinator> { BackoffUnlockCoordinator(get(), get()) }
    single { BiometricSlots(get(), get(), get()) }
    single<VaultSecurity> { DefaultVaultSecurity(get(), get(), get(), get(), get(), get(), get(), get()) }
}

/**
 * Security bindings: the platform key protector and record stores, plus the shared crypto and the vault itself.
 * `shared` does not load this module yet. The Android platform part needs a `Context` binding from the app.
 */
val securityModule: Module = module {
    includes(platformSecurityModule, securityCoreModule)
}
