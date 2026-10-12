package com.mk.daftar.core.security

import com.mk.daftar.core.security.di.securityCoreModule
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class SecurityModuleTest {

    @Test
    fun securityCoreModuleResolvesTheVaultAsASingletonOverPlatformBindings() {
        val harness = VaultHarness()
        val app = koinApplication {
            modules(
                securityCoreModule,
                module {
                    single<PlatformEnvelopeProtector> { harness.protector }
                    single<SecurityRecordStore> { harness.records }
                    single<BackoffStore> { harness.backoff }
                    single<MonotonicClock> { harness.clock }
                    single<BiometricKeyStore> { harness.biometricKeys }
                    single<BiometricSlotStore> { harness.biometricSlots }
                },
            )
        }
        try {
            val koin = app.koin
            assertIs<DefaultVaultSecurity>(koin.get<VaultSecurity>())
            assertSame(koin.get<VaultSecurity>(), koin.get<VaultSecurity>())
        } finally {
            stopKoin()
        }
    }
}
