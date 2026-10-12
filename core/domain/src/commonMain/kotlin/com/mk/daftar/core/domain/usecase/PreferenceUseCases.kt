package com.mk.daftar.core.domain.usecase

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.storage.StorageError
import kotlinx.coroutines.flow.Flow

// Preference use cases delegate to the repository unchanged. Each write is one repository call, so a failure reaches the
// caller as the repository reported it and the previously stored value stays in effect.

class ObservePreferencesUseCase(private val repository: UserPreferencesRepository) {
    operator fun invoke(): Flow<PreferencesRead> = repository.observe()
}

class SetThemeModeUseCase(private val repository: UserPreferencesRepository) {
    suspend operator fun invoke(mode: ThemeMode): Outcome<Unit, StorageError> = repository.setThemeMode(mode)
}

class SetLanguageUseCase(private val repository: UserPreferencesRepository) {
    suspend operator fun invoke(language: AppLanguage): Outcome<Unit, StorageError> = repository.setLanguage(language)
}

class SetInactivityTimeoutUseCase(private val repository: UserPreferencesRepository) {
    suspend operator fun invoke(timeout: InactivityTimeout): Outcome<Unit, StorageError> =
        repository.setInactivityTimeout(timeout)
}
