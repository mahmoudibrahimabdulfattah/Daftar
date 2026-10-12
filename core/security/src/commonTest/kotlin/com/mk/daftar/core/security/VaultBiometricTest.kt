@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.core.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class VaultBiometricTest {

    /** One vault instance, created with the PIN. Its session is PIN-proven until it is locked. */
    private suspend fun VaultHarness.createdVault(text: String = "123456"): Pair<DefaultVaultSecurity, KeyLease> {
        val vault = vault()
        return vault to vault.create(pin(text)).requireSuccess()
    }

    /** One vault instance with an enrollment committed. The session is still PIN-proven. */
    private suspend fun VaultHarness.enrolled(text: String = "123456"): Pair<DefaultVaultSecurity, KeyLease> {
        val (vault, lease) = createdVault(text)
        vault.enrollBiometrics(lease, ScriptedPrompter()).requireSuccess()
        return vault to lease
    }

    @Test
    fun enrollmentWrapsTheSameDekThatTheLeaseHolds() = runTest {
        val harness = VaultHarness()
        val (vault, lease) = harness.createdVault()
        val dekBefore = lease.useDatabaseKey { it.copyOf() }
        vault.enrollBiometrics(lease, ScriptedPrompter()).requireSuccess()

        vault.lock()
        val unlocked = vault.unlockWithBiometrics(ScriptedPrompter()).requireSuccess()
        unlocked.useDatabaseKey { assertContentEquals(dekBefore, it) }
        assertEquals(UnlockProof.Biometric, unlocked.proof)
    }

    @Test
    fun enrollmentAndRemovalLeaveThePinEnvelopeUnchanged() = runTest {
        val harness = VaultHarness()
        val (vault, lease) = harness.createdVault()
        val envelopeBefore = harness.records.record?.copyOf()
        vault.enrollBiometrics(lease, ScriptedPrompter()).requireSuccess()
        assertContentEquals(envelopeBefore, harness.records.record)
        vault.disableBiometrics().requireSuccess()
        assertContentEquals(envelopeBefore, harness.records.record)

        // The PIN still opens the same vault after the biometric slot was added and removed.
        vault.lock()
        vault.unlock(pin("123456")).requireSuccess()
    }

    @Test
    fun biometricSessionCannotAuthorizeEnrollment() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.enrolled()
        vault.lock()
        val biometricLease = vault.unlockWithBiometrics(ScriptedPrompter()).requireSuccess()
        val failure = vault.enrollBiometrics(biometricLease, ScriptedPrompter()).requireFailure()
        assertEquals(BiometricError.PinRequired, failure)
    }

    @Test
    fun reenrollmentNeedsTheCurrentPinAndReplacesTheKey() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.enrolled()
        val firstKey = harness.biometricKeys.keys.keys.single()

        vault.reenrollBiometrics(pin("123456"), ScriptedPrompter()).requireSuccess()
        val afterKeys = harness.biometricKeys.keys.keys
        assertEquals(1, afterKeys.size)
        assertFalse(afterKeys.contains(firstKey))

        vault.lock()
        vault.unlockWithBiometrics(ScriptedPrompter()).requireSuccess()
    }

    @Test
    fun wrongPinForReenrollmentCountsAsAPinFailure() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.enrolled()
        val slotBefore = harness.biometricSlots.slot?.copyOf()
        val failure = vault.reenrollBiometrics(pin("654321"), ScriptedPrompter()).requireFailure()
        assertEquals(BiometricError.Security(SecurityError.AuthenticationFailed), failure)
        assertEquals(1, harness.storedRecord().failures)
        assertContentEquals(slotBefore, harness.biometricSlots.slot)
    }

    @Test
    fun invalidatedEnrollmentRemovesOnlyTheBiometricSlotAndKey() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.enrolled()
        vault.lock()

        harness.biometricKeys.invalidated = true
        assertEquals(BiometricError.EnrollmentChanged, vault.unlockWithBiometrics(ScriptedPrompter()).requireFailure())
        assertNull(harness.biometricSlots.slot)
        assertTrue(harness.biometricKeys.keys.isEmpty())
        assertEquals(BiometricSlotState.Absent, vault.biometricSlotState().requireSuccess())

        // The PIN is unaffected and still opens the vault.
        vault.unlock(pin("123456")).requireSuccess()
    }

    @Test
    fun cancelledUnlockKeepsTheEnrollment() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.enrolled()
        vault.lock()

        val cancelled = ScriptedPrompter(BiometricAuthResult.Cancelled)
        assertEquals(BiometricError.Cancelled, vault.unlockWithBiometrics(cancelled).requireFailure())
        assertNotNull(harness.biometricSlots.slot)
        vault.unlockWithBiometrics(ScriptedPrompter()).requireSuccess()
    }

    @Test
    fun cancelledFirstEnrollmentLeavesNoSlotAndNoKey() = runTest {
        val harness = VaultHarness()
        val (vault, lease) = harness.createdVault()
        val failure = vault.enrollBiometrics(lease, ScriptedPrompter(BiometricAuthResult.Cancelled))
        assertEquals(BiometricError.Cancelled, failure.requireFailure())
        assertNull(harness.biometricSlots.slot)
        assertTrue(harness.biometricKeys.keys.isEmpty())
    }

    @Test
    fun cancelledReenrollmentKeepsTheExistingEnrollment() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.enrolled()
        val slotBefore = harness.biometricSlots.slot?.copyOf()
        val keyBefore = harness.biometricKeys.keys.keys.single()

        vault.reenrollBiometrics(pin("123456"), ScriptedPrompter(BiometricAuthResult.Cancelled)).requireFailure()
        assertContentEquals(slotBefore, harness.biometricSlots.slot)
        assertEquals(setOf(keyBefore), harness.biometricKeys.keys.keys)
    }

    @Test
    fun lockoutIsReportedAndNeverCountsAgainstThePin() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.enrolled()
        vault.lock()

        val lockedOut = ScriptedPrompter(BiometricAuthResult.LockedOut)
        assertEquals(BiometricError.LockedOut, vault.unlockWithBiometrics(lockedOut).requireFailure())
        assertEquals(0, harness.storedRecord().failures)
        assertNotNull(harness.biometricSlots.slot)
    }

    @Test
    fun failedEnrollmentWriteDeletesTheNewKeyAndKeepsPinUsable() = runTest {
        val harness = VaultHarness()
        val (vault, lease) = harness.createdVault()
        harness.biometricSlots.failingWrites += 1
        val failure = vault.enrollBiometrics(lease, ScriptedPrompter()).requireFailure()
        assertEquals(BiometricError.StorageFailure, failure)
        assertTrue(harness.biometricKeys.keys.isEmpty())
        assertNull(harness.biometricSlots.slot)

        vault.lock()
        vault.unlock(pin("123456")).requireSuccess()
    }

    @Test
    fun tamperedSlotIsRejectedAndRemoved() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.enrolled()
        vault.lock()
        val slot = harness.biometricSlots.slot!!
        // The last byte is inside the AEAD ciphertext, so the platform rejects it.
        slot[slot.size - 1] = (slot[slot.size - 1].toInt() xor 0x01).toByte()
        harness.biometricSlots.slot = slot

        assertEquals(BiometricError.CorruptSlot, vault.unlockWithBiometrics(ScriptedPrompter()).requireFailure())
        assertNull(harness.biometricSlots.slot)
    }

    @Test
    fun disableDestroysTheSlotAndItsKey() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.enrolled()
        assertEquals(BiometricSlotState.Valid, vault.biometricSlotState().requireSuccess())
        vault.disableBiometrics().requireSuccess()
        assertEquals(BiometricSlotState.Absent, vault.biometricSlotState().requireSuccess())
        assertTrue(harness.biometricKeys.keys.isEmpty())
    }

    @Test
    fun aLateBiometricResultAfterLockIsRejected() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.enrolled()
        vault.lock()

        val gate = CompletableDeferred<Unit>()
        val prompter = ScriptedPrompter().apply { this.gate = gate }
        val pending = async { vault.unlockWithBiometrics(prompter) }
        runCurrent()
        // The prompt is on screen when the session is locked.
        assertEquals(1, prompter.calls)
        vault.lock()
        gate.complete(Unit)

        assertEquals(BiometricError.Superseded, pending.await().requireFailure())
        assertEquals(SecurityStatus.Locked(null), vault.status())
    }

    @Test
    fun aLateEnrollmentAfterLockDoesNotKeepTheSlot() = runTest {
        val harness = VaultHarness()
        val (vault, lease) = harness.createdVault()
        val gate = CompletableDeferred<Unit>()
        val prompter = ScriptedPrompter().apply { this.gate = gate }
        val pending = async { vault.enrollBiometrics(lease, prompter) }
        runCurrent()
        vault.lock()
        gate.complete(Unit)

        assertEquals(BiometricError.Superseded, pending.await().requireFailure())
        assertNull(harness.biometricSlots.slot)
        assertTrue(harness.biometricKeys.keys.isEmpty())
    }

    @Test
    fun unlockWithoutAnEnrollmentReportsNotEnrolled() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.createdVault()
        vault.lock()
        assertEquals(BiometricError.NotEnrolled, vault.unlockWithBiometrics(ScriptedPrompter()).requireFailure())
    }

    @Test
    fun capabilityIsReportedFromThePlatform() = runTest {
        val harness = VaultHarness()
        harness.biometricKeys.capabilityValue = BiometricCapability.NotEnrolled
        assertEquals(BiometricCapability.NotEnrolled, harness.vault().biometricCapability())
    }
}
