package com.mk.daftar.core.domain.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.storage.StorageError
import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage { AR, EN }

/** Auto-lock delay after the app goes inactive. */
enum class InactivityTimeout(val seconds: Int) {
    SECONDS_30(30),
    SECONDS_60(60),
    SECONDS_300(300),
}

/**
 * Non-sensitive settings. They are readable while locked and never describe a usable key: [biometricEnabled] is a
 * preference only, and the vault reconciles it with protected-key availability before offering biometrics.
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.AR,
    val inactivityTimeout: InactivityTimeout = InactivityTimeout.SECONDS_60,
    val biometricEnabled: Boolean = false,
)

sealed interface PreferencesRead {
    /**
     * Preferences as stored. [recoveredFromCorruption] is true when the stored file was unreadable and defaults were
     * used instead; that state is reported, never hidden.
     */
    data class Ready(val preferences: UserPreferences, val recoveredFromCorruption: Boolean) : PreferencesRead

    data class Failed(val error: StorageError) : PreferencesRead
}

interface UserPreferencesRepository {
    fun observe(): Flow<PreferencesRead>

    suspend fun setThemeMode(mode: ThemeMode): Outcome<Unit, StorageError>

    suspend fun setLanguage(language: AppLanguage): Outcome<Unit, StorageError>

    suspend fun setInactivityTimeout(timeout: InactivityTimeout): Outcome<Unit, StorageError>

    suspend fun setBiometricEnabled(enabled: Boolean): Outcome<Unit, StorageError>
}
