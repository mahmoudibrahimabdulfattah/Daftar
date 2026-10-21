package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.runTest

class KdfFallbackTest {

    private suspend fun storedKdf(harness: VaultHarness): KdfParams {
        val plain = harness.protector.unseal(checkNotNull(harness.records.record)).requireSuccess()
        return (EnvelopeCodec.decode(plain) as Outcome.Success).data.header.kdf
    }

    private suspend fun created(harness: VaultHarness): DefaultVaultSecurity {
        val vault = harness.vault()
        vault.create(pin("123456")).requireSuccess()
        vault.lock()
        return vault
    }

    private val lowMemory = KdfParams(KdfPolicy.LOW_MEMORY_KIB, 3, KdfPolicy.LANES)

    @Test
    fun creationFallsBackToLowMemoryWhenTheFirstStandardDerivationFails() = runTest {
        val harness = VaultHarness()
        harness.kdf.failingCalls[1] = SecurityError.InsufficientMemory
        harness.vault().create(pin("123456")).requireSuccess()
        assertEquals(lowMemory, storedKdf(harness))
        assertEquals(listOf(3, 3), harness.kdf.passesSeen)
    }

    @Test
    fun creationFailsWithoutPersistingWhenBothProfilesFail() = runTest {
        val harness = VaultHarness()
        harness.kdf.failingCalls[1] = SecurityError.InsufficientMemory
        harness.kdf.failingCalls[2] = SecurityError.InsufficientMemory
        val error = harness.vault().create(pin("123456")).requireFailure()
        assertEquals(SecurityError.InsufficientMemory, error)
        assertNull(harness.records.record)
        assertNull(harness.backoff.record)
        assertEquals(SecurityStatus.NotInitialized, harness.vault().status())
    }

    @Test
    fun laterCalibrationFailureKeepsTheStandardPassesThatSucceeded() = runTest {
        val clock = FakeClock()
        val harness = VaultHarness(clock = clock, kdf = FakeKdf(clock, 50L.milliseconds))
        // Derivation 1 is pass 3 (succeeds), derivation 2 is pass 4 (fails): Standard t=3 is kept, not LowMemory.
        harness.kdf.failingCalls[2] = SecurityError.InsufficientMemory
        harness.vault().create(pin("123456")).requireSuccess()
        assertEquals(KdfParams(KdfPolicy.MEMORY_KIB, 3, KdfPolicy.LANES), storedKdf(harness))
    }

    @Test
    fun sealingFallsBackToLowMemoryOnceWhenStandardSealingFails() = runTest {
        val clock = FakeClock()
        // Cost 400 ms per pass: calibration stops after pass 3, so derivation 2 is the Standard sealing derivation.
        val harness = VaultHarness(clock = clock, kdf = FakeKdf(clock, 400L.milliseconds))
        harness.kdf.failingCalls[2] = SecurityError.InsufficientMemory
        harness.vault().create(pin("123456")).requireSuccess()
        assertEquals(lowMemory, storedKdf(harness))
    }

    @Test
    fun unlockUsesTheStoredParametersAndAllocationFailureIsNotAWrongPin() = runTest {
        val clock = FakeClock()
        val harness = VaultHarness(clock = clock, kdf = FakeKdf(clock, 250L.milliseconds))
        val vault = harness.vault()
        vault.create(pin("123456")).requireSuccess()
        vault.lock()
        val before = harness.records.record?.copyOf()
        val storedPasses = storedKdf(harness).passes
        assertEquals(4, storedPasses)

        harness.kdf.failingCalls[harness.kdf.derivations + 1] = SecurityError.InsufficientMemory
        val failure = vault.unlock(pin("123456")).requireFailure()
        assertEquals(SecurityError.InsufficientMemory, failure)
        assertEquals(0, harness.storedRecord().failures)
        assertFalse(harness.storedRecord().pending)
        assertEquals(SecurityStatus.Locked(null), vault.status())
        assertEquals(storedPasses, harness.kdf.passesSeen.last())
        assertTrue(before.contentEquals(harness.records.record))

        vault.unlock(pin("123456")).requireSuccess()
    }

    @Test
    fun unlockOfALowMemoryEnvelopeUsesLowMemoryParameters() = runTest {
        val harness = VaultHarness()
        harness.kdf.failingCalls[1] = SecurityError.InsufficientMemory
        val vault = harness.vault()
        vault.create(pin("123456")).requireSuccess()
        vault.lock()
        vault.unlock(pin("123456")).requireSuccess()
        assertEquals(3, harness.kdf.passesSeen.last())
        assertEquals(lowMemory, storedKdf(harness))
    }

    @Test
    fun pinChangeAllocationFailureKeepsTheOriginalEnvelopeAndPin() = runTest {
        val harness = VaultHarness()
        val vault = created(harness)
        val before = harness.records.record?.copyOf()
        harness.kdf.failingCalls[harness.kdf.derivations + 2] = SecurityError.InsufficientMemory
        assertEquals(
            SecurityError.InsufficientMemory,
            vault.changePin(pin("123456"), pin("654321")).requireFailure(),
        )
        assertTrue(before.contentEquals(harness.records.record))
        assertEquals(0, harness.storedRecord().failures)
        vault.unlock(pin("123456")).requireSuccess()
    }

    @Test
    fun pinChangeKeepsTheExactStoredParameters() = runTest {
        val harness = VaultHarness()
        harness.kdf.failingCalls[1] = SecurityError.InsufficientMemory
        val vault = harness.vault()
        vault.create(pin("123456")).requireSuccess()
        vault.lock()
        vault.changePin(pin("123456"), pin("654321")).requireSuccess()
        assertEquals(lowMemory, storedKdf(harness))
        vault.lock()
        vault.unlock(pin("654321")).requireSuccess()
    }

    @Test
    fun pinChangeKeepsALegacyPassCountWithoutUpgrading() = runTest {
        val clock = FakeClock()
        val harness = VaultHarness(clock = clock, kdf = FakeKdf(clock, 250L.milliseconds))
        val vault = harness.vault()
        vault.create(pin("123456")).requireSuccess()
        vault.lock()
        vault.changePin(pin("123456"), pin("654321")).requireSuccess()
        assertEquals(KdfParams(KdfPolicy.MEMORY_KIB, 4, KdfPolicy.LANES), storedKdf(harness))
    }

    @Test
    fun allowlistAcceptsOnlyTheTwoProfilesAndTheLegacyPassRange() {
        assertTrue(KdfPolicy.allows(KdfParams(KdfPolicy.MEMORY_KIB, 3, 1)))
        assertTrue(KdfPolicy.allows(KdfParams(KdfPolicy.MEMORY_KIB, 6, 1)))
        assertTrue(KdfPolicy.allows(KdfParams(KdfPolicy.LOW_MEMORY_KIB, 3, 1)))
        assertFalse(KdfPolicy.allows(KdfParams(KdfPolicy.MEMORY_KIB, 2, 1)))
        assertFalse(KdfPolicy.allows(KdfParams(KdfPolicy.MEMORY_KIB, 7, 1)))
        assertFalse(KdfPolicy.allows(KdfParams(KdfPolicy.LOW_MEMORY_KIB, 2, 1)))
        assertFalse(KdfPolicy.allows(KdfParams(KdfPolicy.LOW_MEMORY_KIB, 4, 1)))
        assertFalse(KdfPolicy.allows(KdfParams(32 * 1024, 3, 1)))
        assertFalse(KdfPolicy.allows(KdfParams(Int.MAX_VALUE, 3, 1)))
        assertFalse(KdfPolicy.allows(KdfParams(KdfPolicy.MEMORY_KIB, 3, 2)))
    }

    @Test
    fun outOfAllowlistParametersAreRejectedBeforeAnyDerivation() = runTest {
        // The real adapter rejects the parameters before libsodium is called, so no memory is allocated.
        val result = LibsodiumPinKdf().derive(
            "123456".encodeToByteArray(),
            ByteArray(KdfPolicy.SALT_BYTES),
            KdfParams(KdfPolicy.LOW_MEMORY_KIB, 4, 1),
        )
        assertEquals(SecurityError.UnsupportedFormat, result.requireFailure())
    }

    @Test
    fun wrongPinStillCountsAgainstTheBackoff() = runTest {
        val harness = VaultHarness()
        val vault = created(harness)
        assertEquals(SecurityError.AuthenticationFailed, vault.unlock(pin("000000")).requireFailure())
        assertEquals(1, harness.storedRecord().failures)
    }
}
