package com.mk.daftar.core.domain.usecase

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.storage.StorageError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class PreferenceUseCasesTest {

    private class RecordingPreferences : UserPreferencesRepository {
        val state = MutableStateFlow<PreferencesRead>(PreferencesRead.Ready(UserPreferences(), false))
        val writes = mutableListOf<String>()
        var failure: StorageError? = null

        override fun observe(): Flow<PreferencesRead> = state

        override suspend fun setThemeMode(mode: ThemeMode): Outcome<Unit, StorageError> = write("theme:$mode")

        override suspend fun setLanguage(language: AppLanguage): Outcome<Unit, StorageError> =
            write("language:$language")

        override suspend fun setInactivityTimeout(timeout: InactivityTimeout): Outcome<Unit, StorageError> =
            write("timeout:$timeout")

        override suspend fun setBiometricEnabled(enabled: Boolean): Outcome<Unit, StorageError> =
            write("biometric:$enabled")

        private fun write(label: String): Outcome<Unit, StorageError> {
            writes += label
            return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
        }
    }

    @Test
    fun eachChoiceIsOneRepositoryWriteAndItsFailureIsReturnedUnchanged() = runTest {
        val repository = RecordingPreferences()
        assertIs<Outcome.Success<Unit>>(SetThemeModeUseCase(repository)(ThemeMode.DARK))
        assertIs<Outcome.Success<Unit>>(SetLanguageUseCase(repository)(AppLanguage.EN))
        assertIs<Outcome.Success<Unit>>(SetInactivityTimeoutUseCase(repository)(InactivityTimeout.SECONDS_300))
        assertEquals(listOf("theme:DARK", "language:EN", "timeout:SECONDS_300"), repository.writes)

        repository.failure = StorageError.Io
        assertEquals(Outcome.Failure(StorageError.Io), SetThemeModeUseCase(repository)(ThemeMode.LIGHT))
    }

    @Test
    fun observeReportsTheRepositoryStateUnchanged() = runTest {
        val repository = RecordingPreferences()
        val read = ObservePreferencesUseCase(repository)().first()
        assertEquals(PreferencesRead.Ready(UserPreferences(), false), read)
    }
}
