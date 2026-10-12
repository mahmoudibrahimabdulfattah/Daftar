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
    fun securityCoreModuleResolvesTheVaultAsASingletonOverTheActiveGeneration() {
        val pointers = FakePointerStore()
        val storage = FakeStorageFactory(pointers)
        val app = koinApplication {
            modules(
                securityCoreModule,
                module {
                    single<PlatformVaultStorageFactory> { storage }
                    single<GenerationFiles> { FakeGenerationFiles() }
                    single<PostCommitInstaller> { FakePostCommitInstaller() }
                    single<MonotonicClock> { FakeClock() }
                },
            )
        }
        try {
            val koin = app.koin
            assertIs<VaultGenerations>(koin.get<VaultGenerations>())
            assertSame(koin.get<VaultSecurity>(), koin.get<VaultSecurity>())
        } finally {
            stopKoin()
        }
    }
}
