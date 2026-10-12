package com.mk.daftar.core.data.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.datastore.DaftarPreferencesDataSource
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.storage.StorageError
import kotlinx.coroutines.flow.Flow

/** Non-sensitive settings over the preferences DataStore. Readable while the vault is locked. */
internal class UserPreferencesRecordRepository(private val source: DaftarPreferencesDataSource) :
    UserPreferencesRepository {
    override fun observe(): Flow<PreferencesRead> = source.observe()

    override suspend fun setThemeMode(mode: ThemeMode): Outcome<Unit, StorageError> = source.setThemeMode(mode)

    override suspend fun setLanguage(language: AppLanguage): Outcome<Unit, StorageError> = source.setLanguage(language)

    override suspend fun setInactivityTimeout(timeout: InactivityTimeout): Outcome<Unit, StorageError> =
        source.setInactivityTimeout(timeout)

    override suspend fun setBiometricEnabled(enabled: Boolean): Outcome<Unit, StorageError> =
        source.setBiometricEnabled(enabled)
}
