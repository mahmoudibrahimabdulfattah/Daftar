package com.mk.daftar.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.storage.StorageError
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Non-sensitive settings in one Preferences DataStore. Nothing here identifies a balance, record, quote, PIN or key.
 *
 * Reads are total: an unknown stored value becomes its default. A corrupt file is replaced by defaults and reported
 * through [PreferencesRead.Ready.recoveredFromCorruption]. An unreadable file is reported as a failure, never as empty
 * preferences. Writes are atomic `updateData` transactions whose failures are returned to the caller.
 */
class DaftarPreferencesDataSource internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val recovered: MutableStateFlow<Boolean>,
) {
    /** Current preferences. Emits again on every change. */
    fun observe(): Flow<PreferencesRead> = dataStore.data
        // The recovery flag is set by the corruption handler before the replacement data is emitted, so reading it
        // at emission time reports the recovery for exactly the data it describes.
        .map<Preferences, PreferencesRead> { prefs ->
            PreferencesRead.Ready(prefs.toUserPreferences(), recoveredFromCorruption = recovered.value)
        }
        .catch { error ->
            if (error is IOException || error is okio.IOException) {
                emit(PreferencesRead.Failed(StorageError.Io))
            } else {
                throw error
            }
        }

    suspend fun setThemeMode(mode: ThemeMode): Outcome<Unit, StorageError> = write { it[THEME] = mode.name }

    suspend fun setLanguage(language: AppLanguage): Outcome<Unit, StorageError> = write { it[LANGUAGE] = language.name }

    suspend fun setInactivityTimeout(timeout: InactivityTimeout): Outcome<Unit, StorageError> =
        write { it[INACTIVITY_TIMEOUT] = timeout.seconds }

    suspend fun setBiometricEnabled(enabled: Boolean): Outcome<Unit, StorageError> =
        write { it[BIOMETRIC_ENABLED] = enabled }

    private suspend fun write(change: (MutablePreferences) -> Unit): Outcome<Unit, StorageError> = try {
        dataStore.updateData { prefs -> prefs.toMutablePreferences().also(change).toPreferences() }
        Outcome.Success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        // The failure is reported, never swallowed. No exception text is kept, since it may name the file.
        Outcome.Failure(StorageError.Io)
    }
}

private val THEME = stringPreferencesKey("theme_mode")
private val LANGUAGE = stringPreferencesKey("language")
private val INACTIVITY_TIMEOUT = intPreferencesKey("inactivity_timeout_seconds")
private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")

/** Maps stored values to domain settings. Unknown or out-of-range values fall back to the defaults. */
internal fun Preferences.toUserPreferences(): UserPreferences {
    val defaults = UserPreferences()
    return UserPreferences(
        themeMode = this[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: defaults.themeMode,
        language = this[LANGUAGE]?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
            ?: defaults.language,
        inactivityTimeout = this[INACTIVITY_TIMEOUT]?.let { seconds ->
            InactivityTimeout.entries.firstOrNull { it.seconds == seconds }
        } ?: defaults.inactivityTimeout,
        biometricEnabled = this[BIOMETRIC_ENABLED] ?: defaults.biometricEnabled,
    )
}
