@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.auth

import com.mk.daftar.core.common.MonotonicTime
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.SecurityStatus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** A gateway whose answers are scripted by each test. Pins are recorded only as the test's own assertions need them. */
private class FakeGateway : AuthGateway {
    var status: SecurityStatus = SecurityStatus.NotInitialized
    var onCreate: suspend (SecretBytes) -> AuthResult = { AuthResult.Opened }
    var onUnlock: suspend (SecretBytes) -> AuthResult = { AuthResult.Opened }
    var statusCalls = 0
    var createCalls = 0
    var unlockCalls = 0
    val pins = mutableListOf<String>()

    override suspend fun status(): SecurityStatus {
        statusCalls++
        return status
    }

    override suspend fun create(pin: SecretBytes): AuthResult {
        createCalls++
        pins += pin.use { it.decodeToString() }
        return onCreate(pin)
    }

    override suspend fun unlock(pin: SecretBytes): AuthResult {
        unlockCalls++
        pins += pin.use { it.decodeToString() }
        return onUnlock(pin)
    }
}

class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.rig(gateway: FakeGateway): Pair<AuthViewModel, AuthDraftRegistry> {
        val clock = object : MonotonicTime {
            override fun now(): Duration = testScheduler.currentTime.milliseconds
        }
        val drafts = AuthDraftRegistry()
        return AuthViewModel(gateway, clock, drafts) to drafts
    }

    private fun AuthViewModel.type(text: String) {
        text.forEach { onIntent(AuthIntent.Digit(it.code)) }
    }

    @Test
    fun firstRunCreatesTheVaultOnceAfterNotNow() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        assertEquals(AuthPhase.Create, vm.state.value.phase)

        vm.type("123456")
        assertEquals(6, vm.state.value.enteredCount)
        assertEquals(AuthPhase.Create, vm.state.value.phase, "six digits never submit on their own")
        vm.onIntent(AuthIntent.Submit)
        assertEquals(AuthPhase.Confirm, vm.state.value.phase)

        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        assertEquals(AuthPhase.BiometricStub, vm.state.value.phase)
        assertEquals(0, gateway.createCalls, "matching confirmation does not create yet")

        vm.onIntent(AuthIntent.SkipBiometrics)
        advanceUntilIdle()
        assertEquals(1, gateway.createCalls)
        assertEquals(listOf("123456"), gateway.pins)
        assertNull(vm.state.value.busy)
    }

    @Test
    fun leadingZerosSurviveAndTheEnteredCountIsTheOnlyPinDerivedState() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("000001")
        vm.onIntent(AuthIntent.Submit)
        vm.type("000001")
        vm.onIntent(AuthIntent.Submit)
        vm.onIntent(AuthIntent.SkipBiometrics)
        advanceUntilIdle()
        assertEquals(listOf("000001"), gateway.pins)
    }

    @Test
    fun mismatchClearsOnlyTheConfirmationAndKeepsTheCandidate() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        vm.type("654321")
        vm.onIntent(AuthIntent.Submit)
        assertEquals(AuthPhase.Confirm, vm.state.value.phase)
        assertEquals(AuthError.PinMismatch, vm.state.value.error)
        assertEquals(0, vm.state.value.enteredCount)

        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        assertEquals(AuthPhase.BiometricStub, vm.state.value.phase)
        vm.onIntent(AuthIntent.SkipBiometrics)
        advanceUntilIdle()
        assertEquals(listOf("123456"), gateway.pins)
    }

    @Test
    fun chooseAnotherPinWipesBothAndReturnsToAnEmptyCreate() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        vm.type("12")
        vm.onIntent(AuthIntent.RestartCreation)
        assertEquals(AuthPhase.Create, vm.state.value.phase)
        assertEquals(0, vm.state.value.enteredCount)
        vm.type("999999")
        vm.onIntent(AuthIntent.Submit)
        vm.onIntent(AuthIntent.Submit)
        assertEquals(AuthPhase.Confirm, vm.state.value.phase)
        vm.type("999999")
        vm.onIntent(AuthIntent.Submit)
        vm.onIntent(AuthIntent.SkipBiometrics)
        advanceUntilIdle()
        assertEquals(listOf("999999"), gateway.pins, "the earlier candidate never reaches the vault")
    }

    @Test
    fun aSecondSubmitWhileUnlockingIsIgnored() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        val release = CompletableDeferred<AuthResult>()
        gateway.onUnlock = { release.await() }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        vm.onIntent(AuthIntent.Submit)
        vm.onIntent(AuthIntent.Digit('7'.code))
        advanceUntilIdle()
        assertEquals(AuthBusy.Unlocking, vm.state.value.busy)
        assertEquals(1, gateway.unlockCalls)
        assertEquals(6, vm.state.value.enteredCount, "input is frozen while the attempt runs")

        release.complete(AuthResult.Opened)
        advanceUntilIdle()
        assertNull(vm.state.value.busy)
    }

    @Test
    fun aWrongPinClearsInputAndThenShowsTheRunningPenalty() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.onUnlock = { AuthResult.Security(SecurityError.AuthenticationFailed) }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        gateway.status = SecurityStatus.Locked(30.seconds)
        runCurrent()

        assertEquals(AuthPhase.Unlock, vm.state.value.phase)
        assertEquals(0, vm.state.value.enteredCount)
        assertEquals(30, vm.state.value.retrySeconds)
        assertTrue(!vm.state.value.inputEnabled)

        // Once the penalty ends, security reports it clear, and the countdown finishes without restarting.
        gateway.status = SecurityStatus.Locked(null)
        advanceUntilIdle()
        assertNull(vm.state.value.retrySeconds)
        assertTrue(vm.state.value.inputEnabled)
    }

    @Test
    fun theCountdownReachesZeroAndRechecksSecurityBeforeInputReturns() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(3.seconds)
        val (vm, _) = rig(gateway)
        runCurrent()
        assertEquals(3, vm.state.value.retrySeconds)
        val before = gateway.statusCalls
        // Security reports the penalty clear by the time the countdown ends.
        gateway.status = SecurityStatus.Locked(null)

        advanceTimeBy(1_500)
        assertEquals(2, vm.state.value.retrySeconds, "remaining time is rounded up to whole seconds")
        advanceTimeBy(2_000)
        advanceUntilIdle()
        assertNull(vm.state.value.retrySeconds)
        assertTrue(gateway.statusCalls > before, "expiry triggers a fresh status read")
        assertTrue(vm.state.value.inputEnabled)
    }

    @Test
    fun throttledAttemptStartsTheCountdownWithoutRetryingTheKdf() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.onUnlock = { AuthResult.Security(SecurityError.Throttled(10.seconds)) }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        runCurrent()
        assertEquals(1, gateway.unlockCalls)
        assertEquals(10, vm.state.value.retrySeconds)
        advanceUntilIdle()
        assertNull(vm.state.value.retrySeconds)
    }

    @Test
    fun aCommittedCreateIsNotRepeatedAndTheStatusDecidesTheNextScreen() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.onCreate = { AuthResult.Storage(StorageError.Conflict) }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        // The security envelope is already on disk, so the status now reports a locked vault.
        gateway.status = SecurityStatus.Locked(null)
        vm.onIntent(AuthIntent.SkipBiometrics)
        advanceUntilIdle()

        assertEquals(1, gateway.createCalls, "create is never repeated blindly")
        assertEquals(AuthPhase.Unlock, vm.state.value.phase)
        assertEquals(AuthError.OpenFailed, vm.state.value.error)
    }

    @Test
    fun aPermanentFaultBecomesAnUnavailableStateWithoutRetryingTheAttempt() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.onUnlock = { AuthResult.Security(SecurityError.CorruptState) }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()
        assertEquals(AuthPhase.Unavailable(AuthUnavailableReason.CorruptState), vm.state.value.phase)
        assertEquals(1, gateway.unlockCalls)
    }

    @Test
    fun unavailableStatusMapsEachSecurityErrorToItsOwnReason() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Unavailable(SecurityError.UnsupportedHardware)
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        assertEquals(AuthPhase.Unavailable(AuthUnavailableReason.UnsupportedHardware), vm.state.value.phase)

        gateway.status = SecurityStatus.Unavailable(SecurityError.ResourceUnavailable)
        vm.onIntent(AuthIntent.Recheck)
        advanceUntilIdle()
        assertEquals(AuthPhase.Unavailable(AuthUnavailableReason.Transient), vm.state.value.phase)
    }

    @Test
    fun aLateResultAfterPrivacyInvalidationIsDiscarded() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        val release = CompletableDeferred<AuthResult>()
        gateway.onUnlock = { release.await() }
        val (vm, drafts) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()

        drafts.invalidateAll()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.enteredCount)
        assertNull(vm.state.value.busy)

        release.complete(AuthResult.Opened)
        advanceUntilIdle()
        assertEquals(AuthPhase.Unlock, vm.state.value.phase, "a result from a cancelled attempt changes nothing")
    }

    @Test
    fun privacyInvalidationDuringOnboardingRestartsCreation() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val (vm, drafts) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        assertEquals(AuthPhase.BiometricStub, vm.state.value.phase)

        drafts.invalidateAll()
        advanceUntilIdle()
        assertEquals(AuthPhase.Create, vm.state.value.phase)
        assertEquals(0, vm.state.value.enteredCount)
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun supersededAttemptProducesNoError() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.onUnlock = { AuthResult.Superseded }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(0, vm.state.value.enteredCount)
    }

    @Test
    fun recoveryReturnsToAnEmptyUnlockAndRechecksStatus() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("12")
        vm.onIntent(AuthIntent.ShowRecovery)
        assertEquals(AuthPhase.Recovery, vm.state.value.phase)
        vm.onIntent(AuthIntent.LeaveRecovery)
        advanceUntilIdle()
        assertEquals(AuthPhase.Unlock, vm.state.value.phase)
        assertEquals(0, vm.state.value.enteredCount)
    }

    @Test
    fun resumingAnInterruptedOnboardingWithoutAVaultStartsCreationAgain() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        vm.onIntent(AuthIntent.Recheck)
        advanceUntilIdle()
        assertEquals(AuthPhase.Create, vm.state.value.phase)
        assertEquals(0, vm.state.value.enteredCount)
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun aPenaltyAlreadyExpiredAtRelaunchShowsUnlockWithoutCountdown() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        assertEquals(AuthPhase.Unlock, vm.state.value.phase)
        assertNull(vm.state.value.retrySeconds)
    }
}
