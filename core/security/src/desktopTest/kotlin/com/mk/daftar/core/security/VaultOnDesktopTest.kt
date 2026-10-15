package com.mk.daftar.core.security

import com.mk.daftar.core.common.desktop.DesktopAppDirectories
import com.mk.daftar.core.security.di.securityModule
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication

/**
 * The vault over the desktop storage: calibrated Argon2id, the debug-only software protector, file stores with atomic
 * replace, a restart and the persistent backoff. A release policy must refuse to create a vault without hardware.
 */
class VaultOnDesktopTest {
    init {
        DesktopAppDirectories.configureAt(directory)
    }

    private fun vault(storage: PlatformVaultStorage) = DefaultVaultSecurity(
        kdf = LibsodiumPinKdf(),
        aead = LibsodiumAead(),
        random = LibsodiumSecureRandom(),
        protector = storage.protector,
        records = storage.records,
        coordinator = BackoffUnlockCoordinator(storage.backoff, SystemMonotonicClock),
        clock = SystemMonotonicClock,
        biometrics = BiometricSlots(storage.biometricKeys, storage.biometricSlots, LibsodiumSecureRandom()),
        recovery = RecoverySlots(storage.protector, storage.recoverySlot),
    )

    @Test
    fun createUnlockWrongPinRestartAndEraseWithTheDebugProtector() = runTest {
        val factory = DesktopVaultStorageFactory(permitsSoftwareKeys = true)
        val generation = freshGeneration()
        val storage = factory.forGeneration(generation)
        try {
            val key = vault(storage).create(pin("246810")).requireSuccess().useDatabaseKey { it.copyOf() }
            vault(storage).lock()

            // A fresh instance over the same files is a relaunch: the PIN opens the same data key.
            vault(factory.forGeneration(generation)).unlock(pin("246810")).requireSuccess()
                .useDatabaseKey { assertContentEquals(key, it) }

            val relaunched = vault(factory.forGeneration(generation))
            repeat(5) { attempt ->
                assertEquals(
                    SecurityError.AuthenticationFailed,
                    relaunched.unlock(pin("00000$attempt")).requireFailure(),
                )
            }
            assertIs<SecurityError.Throttled>(
                vault(factory.forGeneration(generation)).unlock(pin("246810")).requireFailure(),
            )

            // The sealed envelope on disk carries no plaintext key material.
            val sealed = checkNotNull(storage.records.read().requireSuccess())
            assertFalse(sealed.toList().windowed(key.size).any { it == key.toList() })
        } finally {
            storage.eraseSecurity().requireSuccess()
        }
        assertEquals(null, storage.records.read().requireSuccess())
    }

    @Test
    fun theDebugProtectorInitializesLibsodiumItself() = runTest {
        // A cold launch reads the sealed backoff record before any other code touched libsodium.
        val protector = DebugOnlySoftwareProtector(Files.createTempDirectory("daftar-key").toFile().resolve("k"))
        protector.prepare().requireSuccess()
        val sealed = protector.seal(byteArrayOf(1, 2, 3)).requireSuccess()
        assertContentEquals(byteArrayOf(1, 2, 3), protector.unseal(sealed).requireSuccess())
    }

    @Test
    fun aReleasePolicyRefusesSoftwareKeysAndCreatesNothing() = runTest {
        val storage = DesktopVaultStorageFactory(permitsSoftwareKeys = false).forGeneration(freshGeneration())
        assertEquals(SecurityError.UnsupportedHardware, vault(storage).create(pin("246810")).requireFailure())
        assertEquals(null, storage.records.read().requireSuccess())
    }

    @Test
    fun theModuleDefaultsToRequiringHardware() = runTest {
        val app = koinApplication { modules(securityModule) }
        try {
            val factory = app.koin.get<PlatformVaultStorageFactory>()
            assertIs<DesktopVaultStorageFactory>(factory)
            assertSame(UnsupportedHardwareProtector, factory.forGeneration(freshGeneration()).protector)
            assertTrue(app.koin.getOrNull<DesktopSecurityConfiguration>() == null)
        } finally {
            app.close()
        }
    }

    private suspend fun freshGeneration(): VaultGeneration {
        Libsodium.ensureReady().requireSuccess()
        return VaultGeneration.fresh(LibsodiumSecureRandom().bytes(16))
    }

    private companion object {
        val directory = Files.createTempDirectory("daftar-security-test").toFile()
    }
}
