@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.auth

import com.mk.daftar.core.common.MonotonicTime
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.security.BiometricCapability
import com.mk.daftar.core.security.RecoveryKeyFormat
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.SecurityStatus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    var onCreateWithBiometrics: suspend (SecretBytes) -> AuthResult = { AuthResult.Opened }
    var onUnlockWithBiometrics: suspend () -> AuthResult = { AuthResult.Opened }
    var capability: BiometricCapability = BiometricCapability.Available
    var ready = false
    var statusCalls = 0
    var createCalls = 0
    var unlockCalls = 0
    var createWithBiometricsCalls = 0
    var biometricUnlockCalls = 0
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

    override suspend fun createWithBiometrics(pin: SecretBytes): AuthResult {
        createWithBiometricsCalls++
        pins += pin.use { it.decodeToString() }
        return onCreateWithBiometrics(pin)
    }

    override suspend fun unlockWithBiometrics(): AuthResult {
        biometricUnlockCalls++
        return onUnlockWithBiometrics()
    }

    override suspend fun biometricCapability(): BiometricCapability = capability

    override suspend fun biometricReady(): Boolean = ready

    /** The automatic-attempt flag, standing in for the non-sensitive store that survives restarts. */
    var autoConsumed = false
    var consumeSucceeds = true
    var consumeCalls = 0
    var clearCalls = 0

    override suspend fun autoAttemptConsumed(): Boolean = autoConsumed

    override suspend fun consumeAutoAttempt(): Boolean {
        consumeCalls++
        if (consumeSucceeds) autoConsumed = true
        return consumeSucceeds
    }

    override suspend fun clearAutoAttempt() {
        clearCalls++
        autoConsumed = false
    }

    var recoveryAvailable = true
    var onRecover: suspend (String, SecretBytes) -> AuthResult = { _, _ -> AuthResult.Opened }
    val recoveries = mutableListOf<Pair<String, String>>()

    override suspend fun recoveryAvailable(): Boolean = recoveryAvailable

    override suspend fun recover(recoveryKey: String, pin: SecretBytes): AuthResult {
        recoveries += recoveryKey to pin.use { it.decodeToString() }
        return onRecover(recoveryKey, pin)
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
        assertEquals(AuthPhase.Biometric, vm.state.value.phase)
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
        assertEquals(AuthPhase.Biometric, vm.state.value.phase)
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
        assertEquals(AuthPhase.Biometric, vm.state.value.phase)

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

    @Test
    fun enableBiometricsCreatesTheVaultOnceWithTheChosenPinAndOpens() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()
        assertEquals(BiometricOffer.Available, vm.state.value.biometricOffer)

        vm.onIntent(AuthIntent.EnableBiometrics)
        advanceUntilIdle()
        assertEquals(1, gateway.createWithBiometricsCalls)
        assertEquals(0, gateway.createCalls, "the PIN-only path is not taken")
        assertEquals(listOf("123456"), gateway.pins)
        assertNull(vm.state.value.busy)
    }

    @Test
    fun aNotEnrolledOfferCannotEnableBiometrics() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.capability = BiometricCapability.NotEnrolled
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()
        assertEquals(BiometricOffer.NotEnrolled, vm.state.value.biometricOffer)

        vm.onIntent(AuthIntent.EnableBiometrics)
        advanceUntilIdle()
        assertEquals(0, gateway.createWithBiometricsCalls)
        assertEquals(AuthPhase.Biometric, vm.state.value.phase)
    }

    @Test
    fun aFailedEnrollmentOpensThePinScreenWithANoticeAndNeverRepeatsCreation() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.onCreateWithBiometrics = {
            gateway.status = SecurityStatus.Locked(null)
            AuthResult.EnrollmentFailed(AuthBiometricFailure.Cancelled)
        }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()
        vm.onIntent(AuthIntent.EnableBiometrics)
        advanceUntilIdle()

        assertEquals(AuthPhase.Unlock, vm.state.value.phase)
        assertEquals(AuthNotice.BiometricsNotEnabled, vm.state.value.notice)
        assertEquals(1, gateway.createWithBiometricsCalls)
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun theBiometricKeyAppearsOnlyWhenReadyAndNeverPromptsOnItsOwn() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.ready = true
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        assertTrue(vm.state.value.biometricReady)
        assertEquals(0, gateway.biometricUnlockCalls, "the prompt never starts on its own")

        vm.onIntent(AuthIntent.BiometricUnlock)
        advanceUntilIdle()
        assertEquals(1, gateway.biometricUnlockCalls)
    }

    @Test
    fun theBiometricKeyIsIgnoredWhenNotReady() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.ready = false
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.onIntent(AuthIntent.BiometricUnlock)
        advanceUntilIdle()
        assertEquals(0, gateway.biometricUnlockCalls)
    }

    @Test
    fun aCancelledPromptReturnsToThePinSilentlyAndPinEntryStillWorks() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.ready = true
        gateway.onUnlockWithBiometrics = { AuthResult.Biometric(AuthBiometricFailure.Cancelled) }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.onIntent(AuthIntent.BiometricUnlock)
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertNull(vm.state.value.notice)
        assertNull(vm.state.value.busy)
        assertEquals(1, gateway.biometricUnlockCalls, "a cancelled prompt is never shown again")
        assertTrue(vm.state.value.biometricReady, "a cancel does not erase the enrollment")

        gateway.onUnlock = { AuthResult.Opened }
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()
        assertEquals(1, gateway.unlockCalls)
    }

    @Test
    fun aLockedOutPromptShowsANoticeAndNeverCountsAsAPinFailure() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.ready = true
        gateway.onUnlockWithBiometrics = { AuthResult.Biometric(AuthBiometricFailure.LockedOut) }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.onIntent(AuthIntent.BiometricUnlock)
        advanceUntilIdle()

        assertEquals(AuthNotice.BiometricsLockedOut, vm.state.value.notice)
        assertNull(vm.state.value.error)
        assertEquals(0, gateway.unlockCalls)
    }

    @Test
    fun anEnrollmentChangeTurnsTheKeyOffAndExplainsThePinFallback() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.ready = true
        gateway.onUnlockWithBiometrics = {
            gateway.ready = false
            AuthResult.Biometric(AuthBiometricFailure.EnrollmentChanged)
        }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.onIntent(AuthIntent.BiometricUnlock)
        advanceUntilIdle()

        assertFalse(vm.state.value.biometricReady)
        assertEquals(AuthNotice.BiometricsUnavailable, vm.state.value.notice)
        assertEquals(AuthPhase.Unlock, vm.state.value.phase)
    }

    @Test
    fun privacyDuringABiometricPromptKeepsTheOperationRunning() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.ready = true
        val release = CompletableDeferred<AuthResult>()
        gateway.onUnlockWithBiometrics = { release.await() }
        val (vm, drafts) = rig(gateway)
        advanceUntilIdle()
        vm.onIntent(AuthIntent.BiometricUnlock)
        advanceUntilIdle()

        // The prompt deactivates the app, which raises the cover and clears the drafts.
        drafts.invalidateAll()
        advanceUntilIdle()
        assertEquals(AuthBusy.UnlockingBiometric, vm.state.value.busy, "the prompt is not cancelled by inactivity")

        release.complete(AuthResult.Opened)
        advanceUntilIdle()
        assertNull(vm.state.value.busy)
    }

    @Test
    fun aLateBiometricResultAfterALockIsSilent() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.ready = true
        gateway.onUnlockWithBiometrics = { AuthResult.Superseded }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.onIntent(AuthIntent.BiometricUnlock)
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertNull(vm.state.value.notice)
        assertNull(vm.state.value.busy)
    }

    /** Types a PIN, confirms it, and leaves the vault at the optional biometric offer. */
    private fun AuthViewModel.reachBiometricOffer(pin: String = "123456") {
        type(pin)
        onIntent(AuthIntent.Submit)
        type(pin)
        onIntent(AuthIntent.Submit)
    }

    @Test
    fun unsupportedHardwareOnCreateShowsItsStateAndSurvivesAStatusRecheck() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.onCreate = { AuthResult.Security(SecurityError.UnsupportedHardware) }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.reachBiometricOffer()

        vm.onIntent(AuthIntent.SkipBiometrics)
        advanceUntilIdle()
        assertEquals(AuthPhase.Unavailable(AuthUnavailableReason.UnsupportedHardware), vm.state.value.phase)

        // The gate rechecks after the failed create. The status still says "not initialized", and the reason stays.
        assertEquals(SecurityStatus.NotInitialized, gateway.status)
        vm.onIntent(AuthIntent.Recheck)
        advanceUntilIdle()
        assertEquals(AuthPhase.Unavailable(AuthUnavailableReason.UnsupportedHardware), vm.state.value.phase)
        assertEquals(1, gateway.createCalls, "a failed create is never repeated")
    }

    @Test
    fun everyPermanentCreateFailureIsShownInsteadOfReturningToStepOne() = runTest(dispatcher) {
        val cases = listOf(
            SecurityError.UnsupportedHardware to AuthUnavailableReason.UnsupportedHardware,
            SecurityError.DeviceKeyUnavailable to AuthUnavailableReason.DeviceKeyUnavailable,
            SecurityError.CorruptState to AuthUnavailableReason.CorruptState,
            SecurityError.UnsupportedFormat to AuthUnavailableReason.UnsupportedFormat,
        )
        for ((error, reason) in cases) {
            val gateway = FakeGateway()
            gateway.onCreate = { AuthResult.Security(error) }
            val (vm, _) = rig(gateway)
            advanceUntilIdle()
            vm.reachBiometricOffer()
            vm.onIntent(AuthIntent.SkipBiometrics)
            advanceUntilIdle()
            vm.onIntent(AuthIntent.Recheck)
            advanceUntilIdle()
            assertEquals(AuthPhase.Unavailable(reason), vm.state.value.phase, "for $error")
            assertNull(vm.state.value.error, "for $error")
        }
    }

    @Test
    fun aTransientCreateFailureReturnsToTheStartWithItsMessage() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.onCreate = { AuthResult.Security(SecurityError.ResourceUnavailable) }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.reachBiometricOffer()
        vm.onIntent(AuthIntent.SkipBiometrics)
        advanceUntilIdle()

        assertEquals(AuthPhase.Create, vm.state.value.phase)
        assertEquals(AuthError.OpenFailed, vm.state.value.error)
        assertEquals(1, gateway.createCalls)
    }

    @Test
    fun aStorageFailureOnCreateReturnsToTheStartWithItsMessage() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.onCreate = { AuthResult.Storage(StorageError.Conflict) }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.reachBiometricOffer()
        vm.onIntent(AuthIntent.SkipBiometrics)
        advanceUntilIdle()

        assertEquals(AuthPhase.Create, vm.state.value.phase)
        assertEquals(AuthError.OpenFailed, vm.state.value.error)
    }

    private fun TestScope.lockedWithBiometrics(gateway: FakeGateway): AuthViewModel {
        gateway.status = SecurityStatus.Locked(null)
        gateway.ready = true
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun coldLaunchQueuesOneAutomaticRequestAndStartsItOnlyWhenResumed() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.onUnlockWithBiometrics = { AuthResult.Biometric(AuthBiometricFailure.Cancelled) }
        val vm = lockedWithBiometrics(gateway)
        assertTrue(vm.state.value.biometricAutoPending, "the first lock of a cold launch is eligible")
        assertEquals(0, gateway.biometricUnlockCalls, "queuing alone never prompts")

        vm.onIntent(AuthIntent.AutomaticBiometric)
        advanceUntilIdle()
        assertEquals(1, gateway.biometricUnlockCalls)
        assertEquals(1, gateway.consumeCalls, "the attempt is recorded before the platform is asked")
        assertTrue(gateway.autoConsumed)

        // A second lock-surface composition or a rotation sends the intent again: nothing is queued any more.
        vm.onIntent(AuthIntent.AutomaticBiometric)
        advanceUntilIdle()
        assertEquals(1, gateway.biometricUnlockCalls)
    }

    @Test
    fun everyLockAfterAnUnlockQueuesANewAutomaticRequest() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.onUnlockWithBiometrics = { AuthResult.Opened }
        val vm = lockedWithBiometrics(gateway)
        vm.onIntent(AuthIntent.AutomaticBiometric)
        advanceUntilIdle()
        assertEquals(1, gateway.biometricUnlockCalls)

        // The app goes to the background and locks. This view model lives on, and the gate rechecks the status: the
        // screen still read Unlock from the last episode, yet this lock is a new one.
        vm.onIntent(AuthIntent.Recheck)
        advanceUntilIdle()
        assertTrue(vm.state.value.biometricAutoPending, "a lock after a successful unlock is eligible again")

        vm.onIntent(AuthIntent.AutomaticBiometric)
        advanceUntilIdle()
        assertEquals(2, gateway.biometricUnlockCalls)
    }

    @Test
    fun aRequestQueuedInTheBackgroundSurvivesTheRecheckOnResume() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.onUnlockWithBiometrics = { AuthResult.Opened }
        // The vault locked while the app was in the background: the request is queued but the host is not resumed.
        val vm = lockedWithBiometrics(gateway)
        assertTrue(vm.state.value.biometricAutoPending)

        // Returning to the foreground rechecks the status before the lock surface sees the host resumed.
        vm.onIntent(AuthIntent.Recheck)
        advanceUntilIdle()
        assertTrue(vm.state.value.biometricAutoPending, "the resume recheck must not drop the queued request")

        vm.onIntent(AuthIntent.AutomaticBiometric)
        advanceUntilIdle()
        assertEquals(1, gateway.biometricUnlockCalls)
    }

    @Test
    fun cancelledAutomaticRequestDoesNotRepeatAfterRecheckOrRelaunch() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.onUnlockWithBiometrics = { AuthResult.Biometric(AuthBiometricFailure.Cancelled) }
        val vm = lockedWithBiometrics(gateway)
        vm.onIntent(AuthIntent.AutomaticBiometric)
        advanceUntilIdle()
        assertEquals(AuthPhase.Unlock, vm.state.value.phase)
        assertNull(vm.state.value.error, "a cancelled prompt is not an error")

        // Resume and recheck: the episode is already spent.
        vm.onIntent(AuthIntent.Recheck)
        advanceUntilIdle()
        assertFalse(vm.state.value.biometricAutoPending)

        // A process restart reads the stored flag and starts no request.
        val restarted = rig(gateway).first
        advanceUntilIdle()
        assertFalse(restarted.state.value.biometricAutoPending)
        restarted.onIntent(AuthIntent.AutomaticBiometric)
        advanceUntilIdle()
        assertEquals(1, gateway.biometricUnlockCalls, "no repeat until a successful unlock")

        // The explicit key still works.
        restarted.onIntent(AuthIntent.BiometricUnlock)
        advanceUntilIdle()
        assertEquals(2, gateway.biometricUnlockCalls)
    }

    @Test
    fun terminalFailureAndLockoutNeverRetryAutomatically() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.onUnlockWithBiometrics = { AuthResult.Biometric(AuthBiometricFailure.LockedOut) }
        val vm = lockedWithBiometrics(gateway)
        vm.onIntent(AuthIntent.AutomaticBiometric)
        advanceUntilIdle()
        assertEquals(AuthNotice.BiometricsLockedOut, vm.state.value.notice)
        vm.onIntent(AuthIntent.Recheck)
        advanceUntilIdle()
        assertEquals(1, gateway.biometricUnlockCalls)
    }

    @Test
    fun typingTakesOverQueuedRequestAndConsumesIt() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val vm = lockedWithBiometrics(gateway)
        vm.type("1")
        advanceUntilIdle()
        assertFalse(vm.state.value.biometricAutoPending)
        assertEquals(1, gateway.consumeCalls)
        vm.onIntent(AuthIntent.AutomaticBiometric)
        advanceUntilIdle()
        assertEquals(0, gateway.biometricUnlockCalls, "the PIN entry is never interrupted")
    }

    @Test
    fun forgotPinOrRestoreCancelsTheQueuedRequest() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val vm = lockedWithBiometrics(gateway)
        vm.onIntent(AuthIntent.ShowRecovery)
        advanceUntilIdle()
        assertFalse(vm.state.value.biometricAutoPending)
        assertEquals(1, gateway.consumeCalls)
        vm.onIntent(AuthIntent.LeaveRecovery)
        advanceUntilIdle()
        assertEquals(0, gateway.biometricUnlockCalls, "returning from recovery never prompts")
        assertFalse(vm.state.value.biometricAutoPending)
    }

    @Test
    fun backoffExpiryAndDisabledBiometricsNeverAutoPrompt() = runTest(dispatcher) {
        val throttled = FakeGateway()
        throttled.status = SecurityStatus.Locked(5.seconds)
        throttled.ready = true
        val (blocked, _) = rig(throttled)
        runCurrent()
        assertFalse(blocked.state.value.biometricAutoPending, "a running penalty blocks the automatic request")
        // The penalty ends: the status is read again and reports no penalty, so the countdown stops.
        throttled.status = SecurityStatus.Locked(null)
        advanceUntilIdle()
        assertFalse(blocked.state.value.biometricAutoPending, "expiry never starts an automatic request")

        val disabled = FakeGateway()
        disabled.status = SecurityStatus.Locked(null)
        disabled.ready = false
        val (noBiometrics, _) = rig(disabled)
        advanceUntilIdle()
        assertFalse(noBiometrics.state.value.biometricAutoPending)
        noBiometrics.onIntent(AuthIntent.AutomaticBiometric)
        advanceUntilIdle()
        assertEquals(0, disabled.biometricUnlockCalls)
    }

    @Test
    fun consumedFlagFromAnEarlierEpisodeBlocksTheRequest() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.autoConsumed = true
        val vm = lockedWithBiometrics(gateway)
        assertFalse(vm.state.value.biometricAutoPending)
    }

    @Test
    fun failedWriteDoesNotStartTheRequest() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.consumeSucceeds = false
        val vm = lockedWithBiometrics(gateway)
        vm.onIntent(AuthIntent.AutomaticBiometric)
        advanceUntilIdle()
        assertEquals(0, gateway.biometricUnlockCalls, "no prompt without a recorded attempt")
    }

    @Test
    fun successfulUnlockClearsTheFlagForTheNextEpisode() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.onUnlockWithBiometrics = { AuthResult.Opened }
        val vm = lockedWithBiometrics(gateway)
        vm.onIntent(AuthIntent.AutomaticBiometric)
        advanceUntilIdle()
        assertEquals(1, gateway.clearCalls)
        assertFalse(gateway.autoConsumed)
    }

    /** A recovery key with a valid checksum, built the way the vault formats one. */
    private val validKey: String = RecoveryKeyFormat.format(ByteArray(RecoveryKeyFormat.SECRET_BYTES) { 5 })

    private fun TestScope.lockedForRecovery(gateway: FakeGateway): AuthViewModel {
        gateway.status = SecurityStatus.Locked(null)
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.onIntent(AuthIntent.ShowRecovery)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun aRecoveryKeyAndANewPinOpenTheVault() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val vm = lockedForRecovery(gateway)
        assertTrue(vm.state.value.recoveryOffered)

        vm.onIntent(AuthIntent.UseRecoveryKey)
        assertEquals(AuthPhase.RecoveryKey, vm.state.value.phase)
        vm.onIntent(AuthIntent.RecoveryKeyChanged(validKey.lowercase()))
        assertTrue(vm.state.value.recoveryKeyReady)
        vm.onIntent(AuthIntent.SubmitRecoveryKey)
        assertEquals(AuthPhase.Create, vm.state.value.phase)
        assertTrue(vm.state.value.recovering)

        vm.type("975310")
        vm.onIntent(AuthIntent.Submit)
        vm.type("975310")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()

        assertEquals(listOf(validKey.lowercase() to "975310"), gateway.recoveries)
        assertEquals(0, gateway.createCalls, "a recovery never creates a new vault")
        assertFalse(vm.state.value.recovering)
    }

    @Test
    fun anIncompleteKeyCannotContinue() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val vm = lockedForRecovery(gateway)
        vm.onIntent(AuthIntent.UseRecoveryKey)
        vm.onIntent(AuthIntent.RecoveryKeyChanged(validKey.dropLast(2)))
        assertFalse(vm.state.value.recoveryKeyReady)
        vm.onIntent(AuthIntent.SubmitRecoveryKey)
        assertEquals(AuthPhase.RecoveryKey, vm.state.value.phase)
    }

    @Test
    fun aRejectedKeyReturnsToTheKeyEntryWithItsError() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.onRecover = { _, _ -> AuthResult.Security(SecurityError.RecoveryKeyRejected) }
        val vm = lockedForRecovery(gateway)
        vm.onIntent(AuthIntent.UseRecoveryKey)
        vm.onIntent(AuthIntent.RecoveryKeyChanged(validKey))
        vm.onIntent(AuthIntent.SubmitRecoveryKey)
        vm.type("975310")
        vm.onIntent(AuthIntent.Submit)
        vm.type("975310")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()

        assertEquals(AuthPhase.RecoveryKey, vm.state.value.phase)
        assertEquals(AuthError.RecoveryKeyRejected, vm.state.value.error)
        assertFalse(vm.state.value.recovering)
    }

    @Test
    fun withoutARecoveryKeyTheScreenOffersOnlyBackups() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.recoveryAvailable = false
        val vm = lockedForRecovery(gateway)
        assertFalse(vm.state.value.recoveryOffered)
    }

    @Test
    fun aStatusRecheckDropsARecoveryInProgress() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val vm = lockedForRecovery(gateway)
        vm.onIntent(AuthIntent.UseRecoveryKey)
        vm.onIntent(AuthIntent.RecoveryKeyChanged(validKey))
        vm.onIntent(AuthIntent.SubmitRecoveryKey)
        vm.onIntent(AuthIntent.Recheck)
        advanceUntilIdle()

        assertEquals(AuthPhase.Unlock, vm.state.value.phase)
        assertFalse(vm.state.value.recovering)
        assertFalse(vm.state.value.biometricAutoPending, "returning from recovery never prompts by itself")
    }

    @Test
    fun createMemoryFailureKeepsTheCandidateAndRetriesTheSameCreate() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.NotInitialized
        gateway.onCreate =
            {
                if (gateway.createCalls ==
                    1
                ) {
                    AuthResult.Security(SecurityError.InsufficientMemory)
                } else {
                    AuthResult.Opened
                }
            }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()
        vm.onIntent(AuthIntent.SkipBiometrics)
        advanceUntilIdle()

        assertEquals(AuthError.InsufficientMemoryCreate, vm.state.value.error)
        assertEquals(AuthPhase.Biometric, vm.state.value.phase)
        assertNull(vm.state.value.busy)
        assertEquals(1, gateway.createCalls)

        vm.onIntent(AuthIntent.RetryAfterMemory)
        advanceUntilIdle()
        assertEquals(2, gateway.createCalls)
        assertEquals(listOf("123456", "123456"), gateway.pins)
        assertNull(vm.state.value.error)
    }

    @Test
    fun cancelAfterCreateMemoryFailureCreatesNothingAndKeepsTheScreen() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.NotInitialized
        gateway.onCreate = { AuthResult.Security(SecurityError.InsufficientMemory) }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()
        vm.onIntent(AuthIntent.SkipBiometrics)
        advanceUntilIdle()

        vm.onIntent(AuthIntent.CancelAfterMemory)
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(AuthPhase.Biometric, vm.state.value.phase)
        assertEquals(1, gateway.createCalls)
        vm.onIntent(AuthIntent.RetryAfterMemory)
        advanceUntilIdle()
        assertEquals(1, gateway.createCalls)
    }

    @Test
    fun unlockMemoryFailureIsNotAWrongPinAndRetryReusesTheDigits() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.onUnlock =
            {
                if (gateway.unlockCalls ==
                    1
                ) {
                    AuthResult.Security(SecurityError.InsufficientMemory)
                } else {
                    AuthResult.Opened
                }
            }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()

        assertEquals(AuthError.InsufficientMemoryUnlock, vm.state.value.error)
        assertEquals(AuthPhase.Unlock, vm.state.value.phase)
        assertEquals(6, vm.state.value.enteredCount)
        assertNull(vm.state.value.retrySeconds)
        assertNull(vm.state.value.busy)

        vm.onIntent(AuthIntent.RetryAfterMemory)
        advanceUntilIdle()
        assertEquals(2, gateway.unlockCalls)
        assertEquals(listOf("123456", "123456"), gateway.pins)
    }

    @Test
    fun cancelAfterUnlockMemoryFailureClearsTheDigits() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.onUnlock = { AuthResult.Security(SecurityError.InsufficientMemory) }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(AuthIntent.Submit)
        advanceUntilIdle()

        vm.onIntent(AuthIntent.CancelAfterMemory)
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(0, vm.state.value.enteredCount)
        assertEquals(1, gateway.unlockCalls)
    }

    @Test
    fun biometricMemoryFailureShowsTheUnlockNoticeWithoutAPinAttempt() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.status = SecurityStatus.Locked(null)
        gateway.ready = true
        gateway.onUnlockWithBiometrics = { AuthResult.Security(SecurityError.InsufficientMemory) }
        val (vm, _) = rig(gateway)
        advanceUntilIdle()
        vm.onIntent(AuthIntent.BiometricUnlock)
        advanceUntilIdle()

        assertEquals(AuthError.InsufficientMemoryUnlock, vm.state.value.error)
        assertEquals(0, gateway.unlockCalls)
        assertEquals(1, gateway.biometricUnlockCalls)
        assertNull(vm.state.value.retrySeconds)
    }
}
