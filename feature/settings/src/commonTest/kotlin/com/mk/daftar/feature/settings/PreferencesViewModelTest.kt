@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.settings

import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.ObservePreferencesUseCase
import com.mk.daftar.core.domain.usecase.SetInactivityTimeoutUseCase
import com.mk.daftar.core.domain.usecase.SetLanguageUseCase
import com.mk.daftar.core.domain.usecase.SetThemeModeUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class PreferencesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun rig(repository: FakePreferencesRepository): PreferencesViewModel = PreferencesViewModel(
        observePreferences = ObservePreferencesUseCase(repository),
        setThemeMode = SetThemeModeUseCase(repository),
        setLanguage = SetLanguageUseCase(repository),
        setInactivityTimeout = SetInactivityTimeoutUseCase(repository),
    )

    @Test
    fun theStateShowsOnlyWhatTheStoreHasCommitted() = runTest(dispatcher) {
        val repository = FakePreferencesRepository()
        val hold = CompletableDeferred<Unit>()
        repository.hold = hold
        val vm = rig(repository)
        advanceUntilIdle()
        assertEquals(ThemeMode.SYSTEM, vm.state.value.shown.themeMode)

        vm.onIntent(PreferencesIntent.SelectTheme(ThemeMode.DARK))
        advanceUntilIdle()
        assertEquals(
            ThemeMode.SYSTEM,
            vm.state.value.shown.themeMode,
            "nothing is shown as applied before it is stored",
        )
        assertTrue(vm.state.value.saving)

        hold.complete(Unit)
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, vm.state.value.shown.themeMode)
        assertFalse(vm.state.value.saving)
        assertFalse(vm.state.value.saveFailed)
    }

    @Test
    fun rapidChangesCommitInTheOrderTheyWereMade() = runTest(dispatcher) {
        val repository = FakePreferencesRepository()
        val hold = CompletableDeferred<Unit>()
        repository.hold = hold
        val vm = rig(repository)
        advanceUntilIdle()

        vm.onIntent(PreferencesIntent.SelectTheme(ThemeMode.DARK))
        vm.onIntent(PreferencesIntent.SelectLanguage(AppLanguage.EN))
        vm.onIntent(PreferencesIntent.SelectTimeout(InactivityTimeout.SECONDS_300))
        advanceUntilIdle()
        assertEquals(listOf("theme:DARK"), repository.calls, "the second write waits for the first")

        hold.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("theme:DARK", "language:EN", "timeout:SECONDS_300"), repository.calls)
        assertEquals(
            UserPreferences(
                themeMode = ThemeMode.DARK,
                language = AppLanguage.EN,
                inactivityTimeout = InactivityTimeout.SECONDS_300,
            ),
            repository.current,
        )
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun aFailedWriteKeepsTheLastCommittedSettingAndReportsIt() = runTest(dispatcher) {
        val repository = FakePreferencesRepository(UserPreferences(inactivityTimeout = InactivityTimeout.SECONDS_60))
        val vm = rig(repository)
        advanceUntilIdle()

        repository.failNextWith = StorageError.Io
        vm.onIntent(PreferencesIntent.SelectTimeout(InactivityTimeout.SECONDS_30))
        advanceUntilIdle()
        assertEquals(InactivityTimeout.SECONDS_60, vm.state.value.shown.inactivityTimeout)
        assertTrue(vm.state.value.saveFailed)

        vm.onIntent(PreferencesIntent.DismissSaveFailure)
        assertFalse(vm.state.value.saveFailed)

        vm.onIntent(PreferencesIntent.SelectTimeout(InactivityTimeout.SECONDS_30))
        advanceUntilIdle()
        assertEquals(InactivityTimeout.SECONDS_30, vm.state.value.shown.inactivityTimeout)
        assertFalse(vm.state.value.saveFailed)
    }

    @Test
    fun sheetsOpenAndTheChoiceClosesThemAndPersists() = runTest(dispatcher) {
        val repository = FakePreferencesRepository()
        val vm = rig(repository)
        advanceUntilIdle()

        vm.onIntent(PreferencesIntent.OpenSheet(PreferenceSheet.Language))
        assertEquals(PreferenceSheet.Language, vm.state.value.sheet)

        vm.onIntent(PreferencesIntent.SelectLanguage(AppLanguage.EN))
        assertNull(vm.state.value.sheet)
        advanceUntilIdle()
        assertEquals(AppLanguage.EN, repository.current.language)
    }

    @Test
    fun closingASheetWithoutChoosingChangesNothing() = runTest(dispatcher) {
        val repository = FakePreferencesRepository()
        val vm = rig(repository)
        advanceUntilIdle()

        vm.onIntent(PreferencesIntent.OpenSheet(PreferenceSheet.Theme))
        vm.onIntent(PreferencesIntent.CloseSheet)
        advanceUntilIdle()
        assertNull(vm.state.value.sheet)
        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun anUnreadableStoreIsReportedAndDefaultsAreShown() = runTest(dispatcher) {
        val repository = FakePreferencesRepository()
        repository.publishRead(PreferencesRead.Failed(StorageError.Io))
        val vm = rig(repository)
        advanceUntilIdle()
        assertTrue(vm.state.value.unreadable)
        assertEquals(UserPreferences(), vm.state.value.shown)
    }
}
