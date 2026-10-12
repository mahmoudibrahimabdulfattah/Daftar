package com.mk.daftar.core.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The production KDF end to end: 64 MiB Argon2id with the calibrated pass count, the real AEAD and RNG, and the real
 * monotonic clock. Only the platform key protector and the stores are fakes. Each derivation takes a fraction of a
 * second on the host.
 */
class RealArgon2VaultTest {

    @Test
    fun createUnlockRejectWrongPinAndRewrapWithRealArgon2id() = runTest {
        val protector = FakeProtector()
        val records = FakeRecordStore()
        val backoff = FakeBackoffStore()
        val clock = SystemMonotonicClock
        fun vault() = DefaultVaultSecurity(
            kdf = LibsodiumPinKdf(),
            aead = LibsodiumAead(),
            random = LibsodiumSecureRandom(),
            protector = protector,
            records = records,
            coordinator = BackoffUnlockCoordinator(backoff, clock),
            clock = clock,
            biometrics = BiometricSlots(FakeBiometricKeyStore(), FakeBiometricSlotStore(), LibsodiumSecureRandom()),
        )

        val first = vault()
        val lease = first.create(pin("123456")).requireSuccess()
        val key = lease.useDatabaseKey { it.copyOf() }
        first.lock()

        val parsed = EnvelopeCodec.decode(
            protector.unseal(checkNotNull(records.record)).requireSuccess(),
        ).requireSuccess()
        assertEquals(KdfPolicy.MEMORY_KIB, parsed.header.kdf.memoryKiB)
        assertTrue(parsed.header.kdf.passes in KdfPolicy.MIN_PASSES..KdfPolicy.MAX_PASSES)

        assertEquals(SecurityError.AuthenticationFailed, vault().unlock(pin("000000")).requireFailure())
        vault().unlock(pin("123456")).requireSuccess().useDatabaseKey { assertContentEquals(key, it) }
        vault().changePin(pin("123456"), pin("0654321")).requireSuccess()
        assertEquals(SecurityError.AuthenticationFailed, vault().unlock(pin("123456")).requireFailure())
        vault().unlock(pin("0654321")).requireSuccess().useDatabaseKey { assertContentEquals(key, it) }
    }
}
