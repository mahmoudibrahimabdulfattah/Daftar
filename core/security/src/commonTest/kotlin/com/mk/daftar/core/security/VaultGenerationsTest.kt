package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class VaultGenerationsTest {

    private val payload = "theme=DARK;language=EN;timeout=300"

    /** Creates the legacy vault with [text] as its PIN, and leaves no session open. */
    private suspend fun VaultGenerations.createLegacy(text: String) {
        security.create(pin(text)).requireSuccess().close()
        security.lock()
    }

    /** Builds a complete second generation as a staged restore would, and returns it, leaving the pointer staging it. */
    private suspend fun VaultGenerations.stageGeneration(text: String): VaultGeneration {
        val target = newGeneration()
        update { it.copy(staging = target) }.requireSuccess()
        securityFor(target).create(pin(text)).requireSuccess().close()
        return target
    }

    @Test
    fun withoutAPointerTheLegacyVaultIsActiveAndNotInitialized() = runTest {
        val harness = GenerationHarness()
        val generations = harness.generations()
        assertEquals(GenerationPointer.DEFAULT, generations.pointer().requireSuccess())
        assertEquals(SecurityStatus.NotInitialized, generations.security.status())
    }

    @Test
    fun createLandsInTheActiveGenerationOnly() = runTest {
        val harness = GenerationHarness()
        val generations = harness.generations()
        generations.createLegacy("111111")
        assertTrue(harness.storage.of(VaultGeneration.LEGACY).hasVault)
        val other = generations.newGeneration()
        assertFalse(harness.storage.of(other).hasVault)
    }

    @Test
    fun generationsAreIsolatedByKeyAndEnvelope() = runTest {
        val harness = GenerationHarness()
        val generations = harness.generations()
        generations.createLegacy("111111")
        val target = generations.stageGeneration("222222")

        // Each generation opens only with its own PIN; the other generation's PIN is refused.
        assertIs<Outcome.Failure<SecurityError>>(generations.securityFor(target).unlock(pin("111111")))
        assertIs<Outcome.Failure<SecurityError>>(generations.securityFor(VaultGeneration.LEGACY).unlock(pin("222222")))
        generations.securityFor(target).unlock(pin("222222")).requireSuccess().close()
    }

    @Test
    fun committedGenerationIsTheOnlyLiveVaultUntilFinishingErasesTheOriginal() = runTest {
        val harness = GenerationHarness()
        val generations = harness.generations()
        generations.createLegacy("111111")
        val target = generations.stageGeneration("222222")

        generations.update {
            it.copy(active = target, previous = it.active, staging = null, preferences = payload)
        }.requireSuccess()

        // The original is still on disk until the pending work completes, but it is no longer the vault.
        assertTrue(harness.storage.of(VaultGeneration.LEGACY).hasVault)
        assertIs<Outcome.Failure<SecurityError>>(generations.security.unlock(pin("111111")))
        generations.security.unlock(pin("222222")).requireSuccess().close()
        generations.security.lock()

        generations.finishPendingWork().requireSuccess()
        assertEquals(listOf(payload), harness.installer.installed)
        assertFalse(harness.storage.of(VaultGeneration.LEGACY).hasVault)
        assertTrue(harness.files.erased.contains(VaultGeneration.LEGACY.id))
        val pointer = generations.pointer().requireSuccess()
        assertNull(pointer.previous)
        assertNull(pointer.preferences)
    }

    @Test
    fun relaunchAfterCommitCompletesThePendingWorkBeforeTheFirstAnswer() = runTest {
        val harness = GenerationHarness()
        val before = harness.generations()
        before.createLegacy("111111")
        val target = before.stageGeneration("222222")
        before.update {
            it.copy(active = target, previous = it.active, staging = null, preferences = payload)
        }.requireSuccess()

        // The process stops here. The next start recovers on its first read of the pointer.
        val after = harness.generations()
        val pointer = after.pointer().requireSuccess()

        assertEquals(target, pointer.active)
        assertNull(pointer.previous)
        assertNull(pointer.preferences)
        assertEquals(listOf(payload), harness.installer.installed)
        assertFalse(harness.storage.of(VaultGeneration.LEGACY).hasVault)
        assertIs<SecurityStatus.Locked>(after.security.status())
    }

    @Test
    fun aFailedPreferenceInstallKeepsThePointerAndIsRetriedOnTheNextRead() = runTest {
        val harness = GenerationHarness()
        val before = harness.generations()
        before.createLegacy("111111")
        val target = before.stageGeneration("222222")
        before.update {
            it.copy(active = target, previous = it.active, staging = null, preferences = payload)
        }.requireSuccess()

        val after = harness.generations()
        harness.installer.failNext = true
        assertIs<Outcome.Failure<SecurityError>>(after.pointer())
        assertTrue(harness.pointers.bytes?.decodeToString()?.contains("preferences=theme=DARK") == true)

        val retried = after.pointer().requireSuccess()
        assertNull(retried.preferences)
        assertEquals(listOf(payload), harness.installer.installed)
        assertFalse(harness.storage.of(VaultGeneration.LEGACY).hasVault)
    }

    @Test
    fun anAbandonedStagingGenerationIsErasedAndTheActiveVaultIsKept() = runTest {
        val harness = GenerationHarness()
        val generations = harness.generations()
        generations.createLegacy("111111")
        val abandoned = generations.stageGeneration("222222")
        // The process stopped before the commit: the pointer still names the original vault.
        val restarted = harness.generations()
        val pointer = restarted.pointer().requireSuccess()

        assertEquals(VaultGeneration.LEGACY, pointer.active)
        assertNull(pointer.staging)
        assertFalse(harness.storage.of(abandoned).hasVault)
        assertTrue(harness.files.erased.contains(abandoned.id))
        restarted.security.unlock(pin("111111")).requireSuccess().close()
    }

    @Test
    fun aFailedPointerWriteLeavesTheOldPointerAsTheAnswer() = runTest {
        val harness = GenerationHarness()
        val generations = harness.generations()
        generations.createLegacy("111111")
        val before = harness.pointers.bytes?.copyOf()

        harness.pointers.failNextWrite = SecurityError.PersistenceFailure
        val other = generations.newGeneration()
        assertIs<Outcome.Failure<SecurityError>>(generations.update { it.copy(active = other) })

        assertEquals(before?.decodeToString(), harness.pointers.bytes?.decodeToString())
        assertEquals(VaultGeneration.LEGACY, generations.pointer().requireSuccess().active)
    }

    @Test
    fun anUncertainPointerWriteIsResolvedByWhatTheStoreHoldsAfterRelaunch() = runTest {
        val harness = GenerationHarness()
        val generations = harness.generations()
        generations.createLegacy("111111")
        val target = generations.stageGeneration("222222")

        harness.pointers.failNextWrite = SecurityError.PersistenceFailure
        harness.pointers.landOnFailure = true
        assertIs<Outcome.Failure<SecurityError>>(
            generations.update {
                it.copy(active = target, previous = it.active, staging = null, preferences = payload)
            },
        )
        // The write landed, so the next start reads the new pointer and completes the replacement.
        val restarted = harness.generations()
        assertEquals(target, restarted.pointer().requireSuccess().active)
    }

    @Test
    fun aDamagedPointerIsUnavailableAndNeverSelectsALegacyVault() = runTest {
        val harness = GenerationHarness()
        harness.pointers.bytes = "not a pointer".encodeToByteArray()
        val generations = harness.generations()
        assertIs<Outcome.Failure<SecurityError>>(generations.pointer())
        assertEquals(SecurityStatus.Unavailable(SecurityError.CorruptState), generations.security.status())
    }

    @Test
    fun aPointerThatCannotBeReadIsUnavailable() = runTest {
        val harness = GenerationHarness()
        harness.pointers.failRead = SecurityError.PersistenceFailure
        val generations = harness.generations()
        assertEquals(
            SecurityStatus.Unavailable(SecurityError.PersistenceFailure),
            generations.security.status(),
        )
    }

    @Test
    fun eraseIsIdempotentAndOnlyTouchesTheNamedGeneration() = runTest {
        val harness = GenerationHarness()
        val generations = harness.generations()
        generations.createLegacy("111111")
        val other = generations.stageGeneration("222222")
        generations.erase(other).requireSuccess()
        generations.erase(other).requireSuccess()
        assertFalse(harness.storage.of(other).hasVault)
        assertTrue(harness.storage.of(VaultGeneration.LEGACY).hasVault)
    }

    @Test
    fun lockRevokesTheActiveSession() = runTest {
        val harness = GenerationHarness()
        val generations = harness.generations()
        generations.createLegacy("111111")
        generations.security.unlock(pin("111111")).requireSuccess()
        assertEquals(SecurityStatus.Unlocked, generations.security.status())
        generations.security.lock()
        assertIs<SecurityStatus.Locked>(generations.security.status())
    }

    @Test
    fun aFailedErasureIsReportedAndLeavesTheGenerationNamed() = runTest {
        val harness = GenerationHarness()
        val generations = harness.generations()
        harness.files.failNext = SecurityError.PersistenceFailure
        val target = generations.newGeneration()
        assertIs<Outcome.Failure<SecurityError>>(generations.erase(target))
    }
}
