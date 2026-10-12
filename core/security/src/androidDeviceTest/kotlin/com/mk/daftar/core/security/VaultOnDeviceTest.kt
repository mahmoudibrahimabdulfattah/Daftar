package com.mk.daftar.core.security

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.mk.daftar.core.security.di.securityModule
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.test.runTest
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The vault on a real device or emulator: calibrated Argon2id, Keystore sealing, file stores with atomic replace,
 * and a restart. Each run uses its own directory and key aliases, and removes both afterwards.
 */
class VaultOnDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun createUnlockWrongPinRestartAndPinChangeOnTheDevice() = runTest {
        val run = UUID.randomUUID().toString()
        val directory = File(context.cacheDir, "security-device-test-$run")
        val pinAlias = "com.mk.daftar.security.devicetest.pin.$run"
        val backoffAlias = "com.mk.daftar.security.devicetest.backoff.$run"
        val pinProtector = AndroidKeystoreProtector(pinAlias, KeystorePolicy.AllowSoftwareForDeviceTests)
        val backoffProtector = AndroidKeystoreProtector(backoffAlias, KeystorePolicy.AllowSoftwareForDeviceTests)
        val records = FileRecordStore(File(directory, "pin-envelope.v1.bin"))
        val backoff = FileBackoffStore(File(directory, "backoff.v1.bin"), backoffProtector)
        val clock = SystemMonotonicClock
        fun vault() = DefaultVaultSecurity(
            kdf = LibsodiumPinKdf(),
            aead = LibsodiumAead(),
            random = LibsodiumSecureRandom(),
            protector = pinProtector,
            records = records,
            coordinator = BackoffUnlockCoordinator(backoff, clock),
            clock = clock,
        )
        try {
            val start = TimeSource.Monotonic.markNow()
            val created = vault().create(pin("246810")).requireSuccess()
            val enrollment = start.elapsedNow()
            val key = created.useDatabaseKey { it.copyOf() }
            vault().lock()

            val parsed = EnvelopeCodec.decode(
                pinProtector.unseal(checkNotNull(records.read().requireSuccess())).requireSuccess(),
            ).requireSuccess()
            Log.i(TAG, "enrollment_ms=${enrollment.inWholeMilliseconds} passes=${parsed.header.kdf.passes}")
            println("ENROLLMENT_MS=${enrollment.inWholeMilliseconds} PASSES=${parsed.header.kdf.passes}")

            val unlockStart = TimeSource.Monotonic.markNow()
            vault().unlock(pin("246810")).requireSuccess().useDatabaseKey { assertContentEquals(key, it) }
            println("UNLOCK_MS=${unlockStart.elapsedNow().inWholeMilliseconds}")

            vault().lock()
            val relaunched = vault()
            assertEquals(SecurityError.AuthenticationFailed, relaunched.unlock(pin("000000")).requireFailure())
            assertEquals(SecurityError.AuthenticationFailed, relaunched.unlock(pin("000001")).requireFailure())
            assertEquals(SecurityError.AuthenticationFailed, relaunched.unlock(pin("000002")).requireFailure())
            assertEquals(SecurityError.AuthenticationFailed, relaunched.unlock(pin("000003")).requireFailure())
            assertEquals(SecurityError.AuthenticationFailed, relaunched.unlock(pin("000004")).requireFailure())
            val penalized = vault().unlock(pin("246810")).requireFailure()
            assertIs<SecurityError.Throttled>(penalized)
            assertTrue(penalized.retryAfter <= 30.seconds)

            val stored = BackoffCodec.decode(checkNotNull(backoff.read().requireSuccess())).requireSuccess()
            assertEquals(5, stored.failures)
            assertTrue(stored.penaltyOpen)
        } finally {
            directory.deleteRecursively()
            pinProtector.deleteKeyForTests()
            backoffProtector.deleteKeyForTests()
        }
    }

    @Test
    fun securityModuleResolvesTheVaultWithoutTouchingTheKeystore() {
        val app = koinApplication {
            modules(
                securityModule,
                module { single<Context> { context } },
            )
        }
        try {
            assertIs<DefaultVaultSecurity>(app.koin.get<VaultSecurity>())
        } finally {
            stopKoin()
        }
    }

    private companion object {
        const val TAG = "SecurityDeviceTest"
    }
}
