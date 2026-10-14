@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.settings

import com.mk.daftar.core.common.Outcome
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Creating a recovery key: PIN first, the key shown once, then its last group typed back before it counts as saved. */
class RecoveryKeyViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.rig(gateway: FakeSettingsGateway, drafts: SettingsDraftRegistry = SettingsDraftRegistry()) =
        RecoveryKeyViewModel(gateway = gateway, clock = testClock(), drafts = drafts)

    private fun RecoveryKeyViewModel.type(digits: String) {
        digits.forEach { onIntent(RecoveryKeyIntent.Digit(it.code)) }
    }

    @Test
    fun theKeyIsShownAfterThePinAndCheckedByItsLastGroup() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway()
        val vm = rig(gateway)
        advanceUntilIdle()
        assertEquals(false, vm.state.value.active)

        vm.onIntent(RecoveryKeyIntent.Start)
        vm.type("123456")
        vm.onIntent(RecoveryKeyIntent.Submit)
        advanceUntilIdle()
        assertEquals(listOf("123456"), gateway.recoveryPins)
        val shown = assertIs<RecoveryKeyStage.Show>(vm.state.value.stage)
        assertEquals("AAAA-BBBB-CCCC-DDDD-EEEE-FFFF-GGGG-HJKM", shown.key)

        vm.onIntent(RecoveryKeyIntent.Saved)
        vm.onIntent(RecoveryKeyIntent.CheckGroup("hjkn"))
        assertTrue(vm.state.value.checkMismatch)
        vm.onIntent(RecoveryKeyIntent.CheckGroup("hjkm"))
        assertEquals(RecoveryKeyStage.Done, vm.state.value.stage)
    }

    @Test
    fun aWrongPinShowsTheFailureAndNoKey() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway()
        gateway.onCreateRecovery = { Outcome.Failure(SecurityActionFailure.WrongPin) }
        val vm = rig(gateway)
        vm.onIntent(RecoveryKeyIntent.Start)
        vm.type("000000")
        vm.onIntent(RecoveryKeyIntent.Submit)
        advanceUntilIdle()
        assertEquals(RecoveryKeyStage.Verify, vm.state.value.stage)
        assertEquals(SecurityActionFailure.WrongPin, vm.state.value.failure)
    }

    @Test
    fun aPrivacyEventDropsTheShownKey() = runTest(dispatcher) {
        val gateway = FakeSettingsGateway()
        val drafts = SettingsDraftRegistry()
        val vm = rig(gateway, drafts)
        vm.onIntent(RecoveryKeyIntent.Start)
        vm.type("123456")
        vm.onIntent(RecoveryKeyIntent.Submit)
        advanceUntilIdle()

        vm.onIntent(RecoveryKeyIntent.PrivacyInvalidated)
        assertEquals(RecoveryKeyStage.Intro, vm.state.value.stage)
        assertEquals(true, vm.state.value.active)
    }
}
