package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class VaultSecurityTest {

    private suspend fun VaultHarness.created(text: String = "123456"): DefaultVaultSecurity {
        val vault = vault()
        vault.create(pin(text)).requireSuccess()
        vault.lock()
        return vault
    }

    @Test
    fun createUnlocksAndUnlockReturnsTheSameKey() = runTest {
        val harness = VaultHarness()
        val vault = harness.vault()
        val created = vault.create(pin("123456")).requireSuccess()
        val createdKey = created.useDatabaseKey { it.copyOf() }
        assertEquals(SecurityStatus.Unlocked, vault.status())

        vault.lock()
        assertEquals(SecurityStatus.Locked(null), vault.status())
        assertTrue(created.isClosed)

        val unlocked = vault.unlock(pin("123456")).requireSuccess()
        unlocked.useDatabaseKey { assertContentEquals(createdKey, it) }
        assertEquals(32, createdKey.size)
    }

    @Test
    fun statusReportsNotInitializedBeforeCreate() = runTest {
        val vault = VaultHarness().vault()
        assertEquals(SecurityStatus.NotInitialized, vault.status())
        assertEquals(SecurityError.NotInitialized, vault.unlock(pin("123456")).requireFailure())
    }

    @Test
    fun createNeverReplacesAnExistingVault() = runTest {
        val harness = VaultHarness()
        harness.created()
        val second = harness.vault().create(pin("654321")).requireFailure()
        assertEquals(SecurityError.AlreadyInitialized, second)
        harness.vault().unlock(pin("123456")).requireSuccess()
    }

    @Test
    fun invalidPinsAreRejectedBeforeAnyAttempt() = runTest {
        val harness = VaultHarness()
        val vault = harness.vault()
        assertEquals(
            SecurityError.InvalidPinFormat,
            vault.create(SecretBytes.owned("12345".encodeToByteArray())).requireFailure(),
        )
        assertEquals(
            SecurityError.InvalidPinFormat,
            vault.create(SecretBytes.owned("12a456".encodeToByteArray())).requireFailure(),
        )
        assertEquals(0, harness.kdf.derivations)
        assertEquals(0, harness.protector.prepareCalls)
    }

    @Test
    fun wrongPinIsAuthenticationFailedAndCounts() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        assertEquals(SecurityError.AuthenticationFailed, vault.unlock(pin("000000")).requireFailure())
        assertEquals(1, harness.storedRecord().failures)
        assertFalse(harness.storedRecord().pending)
    }

    @Test
    fun invalidFormatDoesNotCountAsAnAttempt() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        vault.unlock(SecretBytes.owned("abc".encodeToByteArray())).requireFailure()
        assertEquals(0, harness.storedRecord().failures)
    }

    @Test
    fun fourWrongAttemptsAreFreeAndTheFifthStartsThePenalty() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        repeat(4) {
            assertEquals(SecurityError.AuthenticationFailed, vault.unlock(pin("000000")).requireFailure())
        }
        assertEquals(SecurityError.AuthenticationFailed, vault.unlock(pin("000000")).requireFailure())
        assertEquals(SecurityError.Throttled(30.seconds), vault.unlock(pin("123456")).requireFailure())
    }

    @Test
    fun backoffFollowsTheSpecifiedScheduleAndCapsAtFifteenMinutes() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        val expectedPenalties = listOf(30, 60, 120, 240, 480, 900, 900, 900)
        repeat(4) { vault.unlock(pin("000000")) }
        for (penalty in expectedPenalties) {
            assertEquals(SecurityError.AuthenticationFailed, vault.unlock(pin("000000")).requireFailure())
            assertEquals(SecurityError.Throttled(penalty.seconds), vault.unlock(pin("123456")).requireFailure())
            harness.clock.advance(penalty.seconds - 1.seconds)
            assertEquals(SecurityError.Throttled(1.seconds), vault.unlock(pin("123456")).requireFailure())
            harness.clock.advance(1.seconds)
        }
        assertEquals(expectedPenalties.size + 4, harness.storedRecord().failures)
    }

    @Test
    fun correctPinAfterThePenaltyResetsTheCounter() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        repeat(5) { vault.unlock(pin("000000")) }
        harness.clock.advance(30.seconds)
        vault.unlock(pin("123456")).requireSuccess()
        val record = harness.storedRecord()
        assertEquals(0, record.failures)
        assertEquals(0L, record.penaltyMillis)
        assertFalse(record.penaltyOpen)
        assertFalse(record.pending)
    }

    @Test
    fun statusReportsTheRemainingPenaltyAndKeepsTheCountAfterItExpires() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        repeat(5) { vault.unlock(pin("000000")) }
        assertEquals(SecurityStatus.Locked(30.seconds), vault.status())
        harness.clock.advance(31.seconds)
        assertEquals(SecurityStatus.Locked(null), vault.status())
        val record = harness.storedRecord()
        assertFalse(record.penaltyOpen)
        assertEquals(5, record.failures)
    }

    @Test
    fun relaunchRestartsThePersistedPenaltyInFull() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        repeat(5) { vault.unlock(pin("000000")) }
        harness.clock.advance(25.seconds)
        val relaunched = harness.vault()
        assertEquals(SecurityError.Throttled(30.seconds), relaunched.unlock(pin("123456")).requireFailure())
        harness.clock.advance(30.seconds)
        relaunched.unlock(pin("123456")).requireSuccess()
    }

    @Test
    fun aCorrectPinNeverRunsWhileAPenaltyIsOpen() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        repeat(5) { vault.unlock(pin("000000")) }
        val derivationsBefore = harness.kdf.derivations
        assertIs<SecurityError.Throttled>(vault.unlock(pin("123456")).requireFailure())
        assertEquals(derivationsBefore, harness.kdf.derivations)
    }

    @Test
    fun concurrentAttemptsAreSerializedAndCountOneByOne() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        val attempts = List(5) { async { vault.unlock(pin("000000")) } }
        val results = attempts.map { it.await().requireFailure() }
        assertTrue(results.all { it == SecurityError.AuthenticationFailed })
        assertEquals(5, harness.storedRecord().failures)
        assertEquals(SecurityError.Throttled(30.seconds), vault.unlock(pin("123456")).requireFailure())
    }

    @Test
    fun cancelledAttemptStaysPendingAndCountsOnTheNextLoad() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        val entered = CompletableDeferred<Unit>()
        harness.kdf.entered = entered
        harness.kdf.gate = CompletableDeferred()
        val job = launch { vault.unlock(pin("123456")) }
        entered.await()
        job.cancelAndJoin()
        assertTrue(harness.storedRecord().pending)
        assertEquals(1, harness.storedRecord().failures)

        harness.kdf.gate = null
        harness.kdf.entered = null
        repeat(4) { vault.unlock(pin("000000")) }
        assertEquals(SecurityError.Throttled(30.seconds), vault.unlock(pin("123456")).requireFailure())
    }

    @Test
    fun cancelledAttemptsCountEvenWhenTheyAreAllCorrect() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        repeat(5) {
            val entered = CompletableDeferred<Unit>()
            harness.kdf.entered = entered
            harness.kdf.gate = CompletableDeferred()
            val job = launch { vault.unlock(pin("123456")) }
            entered.await()
            job.cancelAndJoin()
        }
        harness.kdf.gate = null
        harness.kdf.entered = null
        assertEquals(SecurityError.Throttled(30.seconds), vault.unlock(pin("123456")).requireFailure())
    }

    @Test
    fun reservationPersistenceFailureRunsNoDerivation() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        val before = harness.kdf.derivations
        harness.backoff.failingWrites += harness.backoff.writes + 1
        assertEquals(SecurityError.PersistenceFailure, vault.unlock(pin("123456")).requireFailure())
        assertEquals(before, harness.kdf.derivations)
    }

    @Test
    fun resetPersistenceFailureReleasesNoKey() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        harness.backoff.failingWrites += harness.backoff.writes + 2
        assertEquals(SecurityError.PersistenceFailure, vault.unlock(pin("123456")).requireFailure())
        assertEquals(SecurityStatus.Locked(null), vault.status())
        assertEquals(1, harness.storedRecord().failures)
    }

    @Test
    fun tamperedSealedEnvelopeFailsAsCorruptAndRollsBack() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        val record = checkNotNull(harness.records.record)
        record[record.size - 1] = (record[record.size - 1] + 1).toByte()
        assertEquals(SecurityError.CorruptState, vault.unlock(pin("123456")).requireFailure())
        assertEquals(0, harness.storedRecord().failures)
    }

    @Test
    fun oversizedSealedRecordIsRejectedBeforeAnyPlatformCall() = runTest {
        val harness = VaultHarness()
        harness.created()
        harness.records.record = ByteArray(EnvelopeCodec.MAX_SEALED_BYTES + 1)
        assertEquals(SecurityStatus.Unavailable(SecurityError.CorruptState), harness.vault().status())
    }

    @Test
    fun tamperedInnerCiphertextIsAnAuthenticationFailure() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        harness.protector.tamper = { plain ->
            plain.copyOf().also { it[plain.size - 1] = (it[plain.size - 1] + 1).toByte() }
        }
        assertEquals(SecurityError.AuthenticationFailed, vault.unlock(pin("123456")).requireFailure())
        assertEquals(1, harness.storedRecord().failures)
    }

    @Test
    fun tamperedGenerationIsAnAuthenticationFailureBecauseTheHeaderIsAssociatedData() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        harness.protector.tamper = { plain -> plain.copyOf().also { it[11] = (it[11] + 1).toByte() } }
        assertEquals(SecurityError.AuthenticationFailed, vault.unlock(pin("123456")).requireFailure())
    }

    @Test
    fun tamperedVaultIdFailsTheBindingAsCorruptAndRollsBack() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        harness.protector.tamper = { plain -> plain.copyOf().also { it[12] = (it[12] + 1).toByte() } }
        assertEquals(SecurityError.CorruptState, vault.unlock(pin("123456")).requireFailure())
        assertEquals(0, harness.storedRecord().failures)
    }

    @Test
    fun missingBackoffStateBesideAVaultIsCorruptNotZeroAttempts() = runTest {
        val harness = VaultHarness()
        harness.created()
        harness.backoff.record = null
        assertEquals(SecurityStatus.Unavailable(SecurityError.CorruptState), harness.vault().status())
        assertEquals(SecurityError.CorruptState, harness.vault().unlock(pin("123456")).requireFailure())
    }

    @Test
    fun corruptBackoffStateIsNotTreatedAsZeroAttempts() = runTest {
        val harness = VaultHarness()
        harness.created()
        harness.backoff.record = ByteArray(BackoffCodec.RECORD_BYTES)
        assertEquals(SecurityError.CorruptState, harness.vault().unlock(pin("123456")).requireFailure())
        assertEquals(SecurityError.CorruptState, harness.vault().unlock(pin("000000")).requireFailure())
    }

    @Test
    fun deviceKeyLossIsReportedAndNeverReplacesTheVault() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        harness.protector.keyPresent = false
        assertEquals(SecurityError.DeviceKeyUnavailable, vault.unlock(pin("123456")).requireFailure())
        assertEquals(0, harness.storedRecord().failures)
        harness.protector.keyPresent = true
        vault.unlock(pin("123456")).requireSuccess()
    }

    @Test
    fun failedPinChangeLeavesTheOldEnvelopeUsable() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        val before = harness.records.record?.copyOf()
        harness.records.failReplace = SecurityError.PersistenceFailure
        assertEquals(
            SecurityError.PersistenceFailure,
            vault.changePin(pin("123456"), pin("654321")).requireFailure(),
        )
        assertContentEquals(before, harness.records.record)
        harness.records.failReplace = null
        vault.unlock(pin("123456")).requireSuccess()
        assertEquals(SecurityError.AuthenticationFailed, vault.unlock(pin("654321")).requireFailure())
    }

    @Test
    fun pinChangeKeepsTheDekAndDatabaseKeyIdentical() = runTest {
        val harness = VaultHarness()
        val vault = harness.vault()
        val lease = vault.create(pin("123456")).requireSuccess()
        val key = lease.useDatabaseKey { it.copyOf() }
        val envelopeBefore = harness.records.record?.copyOf()

        vault.changePin(pin("123456"), pin("654321")).requireSuccess()

        assertFalse(envelopeBefore.contentEquals(harness.records.record))
        assertEquals(SecurityError.AuthenticationFailed, vault.unlock(pin("123456")).requireFailure())
        vault.lock()
        vault.unlock(pin("654321")).requireSuccess().useDatabaseKey { assertContentEquals(key, it) }
    }

    @Test
    fun pinChangeRequiresTheCurrentPinAndCountsWrongAttempts() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        assertEquals(
            SecurityError.AuthenticationFailed,
            vault.changePin(pin("000000"), pin("654321")).requireFailure(),
        )
        assertEquals(1, harness.storedRecord().failures)
        vault.unlock(pin("123456")).requireSuccess()
    }

    @Test
    fun pinChangeInvalidNewPinIsRejectedBeforeAnyWork() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        val before = harness.kdf.derivations
        assertEquals(
            SecurityError.InvalidPinFormat,
            vault.changePin(pin("123456"), SecretBytes.owned("12".encodeToByteArray())).requireFailure(),
        )
        assertEquals(before, harness.kdf.derivations)
    }

    @Test
    fun resourceFailureDuringRewrapKeepsTheOldEnvelope() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        val before = harness.records.record?.copyOf()
        harness.kdf.failingCalls[harness.kdf.derivations + 2] = SecurityError.ResourceUnavailable
        assertEquals(
            SecurityError.ResourceUnavailable,
            vault.changePin(pin("123456"), pin("654321")).requireFailure(),
        )
        assertContentEquals(before, harness.records.record)
        assertEquals(0, harness.storedRecord().failures)
        vault.unlock(pin("123456")).requireSuccess()
    }

    @Test
    fun lateUnlockAfterLockIsRejected() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        vault.lock()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        harness.kdf.entered = entered
        harness.kdf.gate = release
        val pending = async { vault.unlock(pin("123456")) }
        entered.await()
        vault.lock()
        release.complete(Unit)
        assertEquals(SecurityError.SessionSuperseded, pending.await().requireFailure())
        assertEquals(SecurityStatus.Locked(null), vault.status())
    }

    @Test
    fun lockWipesTheLeaseAndCannotBeUsedAfterwards() = runTest {
        val harness = VaultHarness()
        val vault = harness.vault()
        val lease = vault.create(pin("123456")).requireSuccess()
        vault.lock()
        assertTrue(lease.isClosed)
        var failed = false
        try {
            lease.useDatabaseKey { }
        } catch (expected: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun unlockAfterLockStartsANewSession() = runTest {
        val harness = VaultHarness()
        val vault = harness.created()
        vault.lock()
        vault.unlock(pin("123456")).requireSuccess()
        assertEquals(SecurityStatus.Unlocked, vault.status())
    }

    @Test
    fun calibrationPicksTheLargestPassCountWithinTheBudget() = runTest {
        for ((costPerPass, expectedPasses) in listOf(50L to 6, 250L to 4, 400L to 3)) {
            val clock = FakeClock()
            val kdf = FakeKdf(clock, costPerPass.milliseconds)
            val passes = KdfCalibrator(kdf, clock, LibsodiumSecureRandom()).selectPasses().requireSuccess()
            assertEquals(expectedPasses, passes, "cost $costPerPass ms per pass")
        }
    }

    @Test
    fun createPersistsTheCalibratedPassCountInTheEnvelope() = runTest {
        val clock = FakeClock()
        val harness = VaultHarness(clock = clock, kdf = FakeKdf(clock, 250L.milliseconds))
        harness.created()
        val parsed = (
            EnvelopeCodec.decode(
                harness.protector.unseal(checkNotNull(harness.records.record)).requireSuccess(),
            ) as Outcome.Success
            ).data
        assertEquals(4, parsed.header.kdf.passes)
        assertEquals(KdfPolicy.MEMORY_KIB, parsed.header.kdf.memoryKiB)
        assertEquals(1L, parsed.header.generation)
    }
}
