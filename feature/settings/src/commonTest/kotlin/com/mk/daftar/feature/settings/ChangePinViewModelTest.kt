@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.settings

import com.mk.daftar.core.common.Outcome
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class ChangePinViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun kotlinx.coroutines.test.TestScope.rig(
        gateway: FakeSettingsGateway,
        drafts: SettingsDraftRegistry = SettingsDraftRegistry(),
    ): ChangePinViewModel = ChangePinViewModel(gateway = gateway, clock = testClock(), drafts = drafts)

    private fun ChangePinViewModel.type(digits: String) {
        digits.forEach { onIntent(ChangePinIntent.Digit(it.code)) }
    }

    /**
     * Runs the three steps. Each submission is a suspending check, so the test lets it settle before the next step is typed,
     * as a person would.
     */
    private fun TestScope.completeSteps(vm: ChangePinViewModel, current: String, new: String) {
        vm.type(current)
        vm.onIntent(ChangePinIntent.Submit)
        advanceUntilIdle()
        vm.type(new)
        vm.onIntent(ChangePinIntent.Submit)
        advanceUntilIdle()
        vm.type(new)
        vm.onIntent(ChangePinIntent.Submit)
    }

    @Test
    fun theThreeStepsCommitWithTheCurrentAndNewPinOnce() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway()
        val vm = rig(gateway)
        advanceUntilIdle()
        assertEquals(ChangePinStage.Verify, vm.state.value.stage)

        completeSteps(vm, current = "123456", new = "654321")
        advanceUntilIdle()
        assertEquals(listOf("123456"), gateway.verified)
        assertEquals(listOf("123456" to "654321"), gateway.changed)
        assertEquals(ChangePinStage.Saving, vm.state.value.stage)
        assertNull(vm.state.value.failure)
    }

    @Test
    fun aWrongCurrentPinStopsBeforeAnyNewPinIsAsked() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway().apply {
            onVerify = { Outcome.Failure(SecurityActionFailure.WrongPin) }
        }
        val vm = rig(gateway)
        advanceUntilIdle()

        vm.type("000000")
        vm.onIntent(ChangePinIntent.Submit)
        advanceUntilIdle()
        assertEquals(ChangePinStage.Verify, vm.state.value.stage)
        assertEquals(SecurityActionFailure.WrongPin, vm.state.value.failure)
        assertEquals(0, vm.state.value.enteredCount)
        assertEquals(emptyList(), gateway.changed)
    }

    @Test
    fun thePenaltyCountsDownAndTheKeypadReturns() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway().apply {
            onVerify = { Outcome.Failure(SecurityActionFailure.Throttled(5.seconds)) }
        }
        val vm = rig(gateway)
        advanceUntilIdle()

        vm.type("000000")
        vm.onIntent(ChangePinIntent.Submit)
        runCurrent()
        assertEquals(5, vm.state.value.retrySeconds)
        assertFalse(vm.state.value.inputEnabled)

        advanceTimeBy(5_100)
        advanceUntilIdle()
        assertNull(vm.state.value.retrySeconds)
        assertTrue(vm.state.value.inputEnabled)
    }

    @Test
    fun aShortNewPinCannotBeSubmitted() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway()
        val vm = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(ChangePinIntent.Submit)
        advanceUntilIdle()

        vm.type("12345")
        assertEquals(5, vm.state.value.enteredCount)
        assertFalse(vm.state.value.submitEnabled)
        vm.onIntent(ChangePinIntent.Submit)
        assertEquals(ChangePinStage.NewPin, vm.state.value.stage)
    }

    @Test
    fun aMismatchKeepsTheNewPinAndAsksForTheConfirmationAgain() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway()
        val vm = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(ChangePinIntent.Submit)
        advanceUntilIdle()
        vm.type("654321")
        vm.onIntent(ChangePinIntent.Submit)
        vm.type("654320")
        vm.onIntent(ChangePinIntent.Submit)
        advanceUntilIdle()
        assertEquals(ChangePinStage.ConfirmPin, vm.state.value.stage)
        assertTrue(vm.state.value.mismatch)
        assertEquals(emptyList(), gateway.changed)

        vm.type("654321")
        vm.onIntent(ChangePinIntent.Submit)
        advanceUntilIdle()
        assertEquals(listOf("123456" to "654321"), gateway.changed)
    }

    @Test
    fun aConfirmedStorageFailureSaysThePreviousPinStillWorks() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway().apply {
            onChange = { _, _ -> Outcome.Failure(SecurityActionFailure.StorageFailure) }
        }
        val vm = rig(gateway)
        advanceUntilIdle()
        completeSteps(vm, current = "123456", new = "654321")
        advanceUntilIdle()
        assertEquals(ChangePinStage.Failed(previousPinStillWorks = true), vm.state.value.stage)

        vm.onIntent(ChangePinIntent.Restart)
        assertEquals(ChangePinStage.Verify, vm.state.value.stage)
        assertEquals(0, vm.state.value.enteredCount)
    }

    @Test
    fun anUnconfirmedFailureDoesNotClaimThePreviousPinWorks() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway().apply {
            onChange = { _, _ -> Outcome.Failure(SecurityActionFailure.Failed) }
        }
        val vm = rig(gateway)
        advanceUntilIdle()
        completeSteps(vm, current = "123456", new = "654321")
        advanceUntilIdle()
        assertEquals(ChangePinStage.Failed(previousPinStillWorks = false), vm.state.value.stage)
    }

    @Test
    fun aLockDuringTheCommitReturnsToTheFirstStepWithoutAnError() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway().apply {
            onChange = { _, _ -> Outcome.Failure(SecurityActionFailure.SessionEnded) }
        }
        val vm = rig(gateway)
        advanceUntilIdle()
        completeSteps(vm, current = "123456", new = "654321")
        advanceUntilIdle()
        assertEquals(ChangePinStage.Verify, vm.state.value.stage)
        assertNull(vm.state.value.failure)
    }

    @Test
    fun aPrivacyEventBeforeTheCommitDropsEveryDigit() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val gateway = FakeSettingsGateway().apply {
            onVerify = {
                release.await()
                Outcome.Success(Unit)
            }
        }
        val vm = rig(gateway)
        advanceUntilIdle()
        vm.type("123456")
        vm.onIntent(ChangePinIntent.Submit)
        advanceUntilIdle()
        vm.onIntent(ChangePinIntent.PrivacyInvalidated)
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(ChangePinStage.Verify, vm.state.value.stage, "a cancelled check never moves on")
        assertEquals(0, vm.state.value.enteredCount)
        assertEquals(emptyList(), gateway.changed)
    }

    @Test
    fun aPrivacyEventDuringTheCommitDoesNotCancelIt() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val gateway = FakeSettingsGateway().apply {
            onChange = { _, _ ->
                release.await()
                Outcome.Success(Unit)
            }
        }
        val vm = rig(gateway)
        advanceUntilIdle()
        completeSteps(vm, current = "123456", new = "654321")
        advanceUntilIdle()
        vm.onIntent(ChangePinIntent.PrivacyInvalidated)
        assertEquals(ChangePinStage.Saving, vm.state.value.stage)

        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("123456" to "654321"), gateway.changed)
    }

    @Test
    fun theDraftRegistryClearsTheDigitsToo() = runTest(dispatcher) {
        val drafts = SettingsDraftRegistry()
        val vm = rig(FakeSettingsGateway(), drafts)
        advanceUntilIdle()
        vm.type("1234")
        drafts.invalidateAll()
        assertEquals(0, vm.state.value.enteredCount)
    }

    @Test
    fun aMemoryShortageAtTheCommitLeavesThePinUnchangedAndRestartsFromTheCurrentPin() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway()
        gateway.onChange = { _, _ -> Outcome.Failure(SecurityActionFailure.InsufficientMemory) }
        val vm = rig(gateway)
        advanceUntilIdle()
        completeSteps(vm, current = "123456", new = "654321")
        advanceUntilIdle()

        assertEquals(ChangePinStage.MemoryUnavailable, vm.state.value.stage)
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.failure)

        gateway.onChange = { _, _ -> Outcome.Success(Unit) }
        vm.onIntent(ChangePinIntent.Restart)
        advanceUntilIdle()
        assertEquals(ChangePinStage.Verify, vm.state.value.stage)
        assertEquals(0, vm.state.value.enteredCount)
    }
}
