@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.settings

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.usecase.ObservePreferencesUseCase
import com.mk.daftar.core.security.BiometricCapability
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class SettingsViewModelTest {
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
        repository: FakePreferencesRepository,
        gateway: FakeSettingsGateway,
        drafts: SettingsDraftRegistry = SettingsDraftRegistry(),
    ): SettingsViewModel = SettingsViewModel(
        observePreferences = ObservePreferencesUseCase(repository),
        gateway = gateway,
        clock = testClock(),
        drafts = drafts,
    )

    private fun SettingsViewModel.type(digits: String) {
        digits.forEach { onIntent(SettingsIntent.Digit(it.code)) }
    }

    @Test
    fun theSwitchReflectsTheStoredValueAndThePlatformTogether() = runTest(dispatcher) {
        val repository = FakePreferencesRepository(UserPreferences(biometricEnabled = true))
        val gateway = FakeSettingsGateway().apply {
            facts = BiometricFacts(BiometricSlotFact.Valid, BiometricCapability.Available)
        }
        val vm = rig(repository, gateway)
        advanceUntilIdle()
        assertEquals(
            BiometricSettings(enabled = true, availability = BiometricAvailability.Ready),
            vm.state.value.biometric,
        )
    }

    @Test
    fun enablingAsksForThePinThenEnrollsAndShowsOnOnlyAfterTheGatewayCommits() = runTest(dispatcher) {
        val repository = FakePreferencesRepository()
        val gateway = FakeSettingsGateway()
        gateway.onReenroll = {
            gateway.facts = BiometricFacts(BiometricSlotFact.Valid, BiometricCapability.Available)
            repository.setBiometricEnabled(true)
            Outcome.Success(Unit)
        }
        val vm = rig(repository, gateway)
        advanceUntilIdle()
        assertFalse(vm.state.value.biometric!!.enabled)

        vm.onIntent(SettingsIntent.SetBiometricEnabled(true))
        assertTrue(vm.state.value.biometricSheet != null, "turning on asks for the PIN first")
        assertEquals(emptyList(), gateway.reenrolled)

        vm.type("654321")
        vm.onIntent(SettingsIntent.Submit)
        advanceUntilIdle()
        assertEquals(listOf("654321"), gateway.reenrolled)
        assertNull(vm.state.value.biometricSheet)
        assertTrue(vm.state.value.biometric!!.enabled)
    }

    @Test
    fun aCancelledPromptKeepsBiometricsOffAndTheSheetClosed() = runTest(dispatcher) {
        val repository = FakePreferencesRepository()
        val gateway = FakeSettingsGateway().apply {
            onReenroll = { Outcome.Failure(SecurityActionFailure.Cancelled) }
        }
        val vm = rig(repository, gateway)
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetBiometricEnabled(true))
        vm.type("123456")
        vm.onIntent(SettingsIntent.Submit)
        advanceUntilIdle()
        assertNull(vm.state.value.biometricSheet)
        assertEquals(SecurityActionFailure.Cancelled, vm.state.value.biometricFailure)
        assertFalse(vm.state.value.biometric!!.enabled)
        assertEquals(emptyList(), repository.calls, "a cancelled prompt writes nothing")
    }

    @Test
    fun aWrongPinKeepsTheSheetOpenAndClearsTheDigits() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway().apply {
            onReenroll = { Outcome.Failure(SecurityActionFailure.WrongPin) }
        }
        val vm = rig(FakePreferencesRepository(), gateway)
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetBiometricEnabled(true))
        vm.type("123456")
        vm.onIntent(SettingsIntent.Submit)
        advanceUntilIdle()
        val sheet = checkNotNull(vm.state.value.biometricSheet)
        assertEquals(SecurityActionFailure.WrongPin, sheet.failure)
        assertEquals(0, sheet.enteredCount)
    }

    @Test
    fun aPenaltyCountsDownAndReopensTheKeypadWhenItEnds() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway().apply {
            onReenroll = { Outcome.Failure(SecurityActionFailure.Throttled(3.seconds)) }
        }
        val vm = rig(FakePreferencesRepository(), gateway)
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetBiometricEnabled(true))
        vm.type("123456")
        vm.onIntent(SettingsIntent.Submit)
        runCurrent()
        assertEquals(3, vm.state.value.biometricSheet?.retrySeconds)
        assertFalse(vm.state.value.biometricSheet!!.inputEnabled)

        advanceTimeBy(3_100)
        advanceUntilIdle()
        assertNull(vm.state.value.biometricSheet?.retrySeconds)
        assertTrue(vm.state.value.biometricSheet!!.inputEnabled)
    }

    @Test
    fun turningOffNeedsNoPinAndDisablesAtOnce() = runTest(dispatcher) {
        val repository = FakePreferencesRepository(UserPreferences(biometricEnabled = true))
        val gateway = FakeSettingsGateway().apply {
            facts = BiometricFacts(BiometricSlotFact.Valid, BiometricCapability.Available)
            onDisable = {
                facts = BiometricFacts(BiometricSlotFact.Absent, BiometricCapability.Available)
                repository.setBiometricEnabled(false)
                Outcome.Success(Unit)
            }
        }
        val vm = rig(repository, gateway)
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetBiometricEnabled(false))
        advanceUntilIdle()
        assertEquals(1, gateway.disables)
        assertNull(vm.state.value.biometricSheet)
        assertFalse(vm.state.value.biometric!!.enabled)
    }

    @Test
    fun aSwitchThatCannotBeEnabledDoesNothing() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway().apply {
            facts = BiometricFacts(BiometricSlotFact.Absent, BiometricCapability.NotEnrolled)
        }
        val vm = rig(FakePreferencesRepository(), gateway)
        advanceUntilIdle()
        assertEquals(BiometricAvailability.NotEnrolled, vm.state.value.biometric!!.availability)

        vm.onIntent(SettingsIntent.SetBiometricEnabled(true))
        assertNull(vm.state.value.biometricSheet)
        assertEquals(emptyList(), gateway.reenrolled)
    }

    @Test
    fun aPrivacyEventDropsTheDigitsAndTheSheet() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway()
        val vm = rig(FakePreferencesRepository(), gateway)
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetBiometricEnabled(true))
        vm.type("1234")
        vm.onIntent(SettingsIntent.PrivacyInvalidated)
        assertNull(vm.state.value.biometricSheet)
        assertEquals(emptyList(), gateway.reenrolled)
    }

    @Test
    fun theDraftRegistryReachesTheSheetAsThePrivacyEvent() = runTest(dispatcher) {
        val drafts = SettingsDraftRegistry()
        val vm = rig(FakePreferencesRepository(), FakeSettingsGateway(), drafts)
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetBiometricEnabled(true))
        drafts.invalidateAll()
        assertNull(vm.state.value.biometricSheet)
    }

    @Test
    fun invalidatedEnrollmentOffersEnablingAgain() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway().apply {
            facts = BiometricFacts(BiometricSlotFact.Invalid, BiometricCapability.Available)
        }
        val vm = rig(FakePreferencesRepository(UserPreferences(biometricEnabled = true)), gateway)
        advanceUntilIdle()
        val settings = vm.state.value.biometric!!
        assertEquals(BiometricAvailability.Invalidated, settings.availability)
        assertFalse(settings.enabled)
        assertTrue(settings.canEnable)
    }
}
