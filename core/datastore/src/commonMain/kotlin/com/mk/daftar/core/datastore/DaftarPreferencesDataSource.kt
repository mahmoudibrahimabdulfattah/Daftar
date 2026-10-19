package com.mk.daftar.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.update.SoftUpdateSnooze
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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

    /**
     * Whether the current lock episode already spent its automatic biometric request. The flag holds no secret. A read
     * that cannot complete reports true, so a broken store never starts an automatic prompt.
     */
    suspend fun autoBiometricConsumed(): Boolean = try {
        dataStore.data.first()[AUTO_BIOMETRIC_CONSUMED] ?: false
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        true
    }

    suspend fun setAutoBiometricConsumed(consumed: Boolean): Outcome<Unit, StorageError> =
        write { it[AUTO_BIOMETRIC_CONSUMED] = consumed }

    /** Whether the owner last chose to hide values. A flag only; null when the store cannot be read. */
    suspend fun valuesHidden(): Boolean? = try {
        dataStore.data.first()[VALUES_HIDDEN] ?: false
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        null
    }

    suspend fun setValuesHidden(hidden: Boolean): Outcome<Unit, StorageError> = write { it[VALUES_HIDDEN] = hidden }

    /** Whether the first-run guide was dismissed or completed for this vault. Null when the store cannot be read. */
    fun observeFirstRunGuideDone(): Flow<Boolean?> = dataStore.data
        .map<Preferences, Boolean?> { prefs -> prefs[FIRST_RUN_GUIDE_DONE] ?: false }
        .catch { error ->
            if (error is IOException || error is okio.IOException) {
                emit(null)
            } else {
                throw error
            }
        }

    suspend fun setFirstRunGuideDone(done: Boolean): Outcome<Unit, StorageError> =
        write { it[FIRST_RUN_GUIDE_DONE] = done }

    /**
     * The postponed soft update, or null when none is stored or the store cannot be read. A null read fails open, so an
     * unreadable store never hides an update that is due.
     */
    suspend fun softUpdateSnooze(): SoftUpdateSnooze? = try {
        val prefs = dataStore.data.first()
        val build = prefs[SNOOZED_UPDATE_BUILD]
        val until = prefs[SNOOZED_UPDATE_UNTIL_MILLIS]
        if (build != null && until != null) SoftUpdateSnooze(build, Instant.fromEpochMilliseconds(until)) else null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        null
    }

    suspend fun setSoftUpdateSnooze(snooze: SoftUpdateSnooze): Outcome<Unit, StorageError> = write {
        it[SNOOZED_UPDATE_BUILD] = snooze.build
        it[SNOOZED_UPDATE_UNTIL_MILLIS] = snooze.until.toEpochMilliseconds()
    }

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
private val AUTO_BIOMETRIC_CONSUMED = booleanPreferencesKey("biometric_auto_attempt_consumed")
private val VALUES_HIDDEN = booleanPreferencesKey("values_hidden")
private val FIRST_RUN_GUIDE_DONE = booleanPreferencesKey("first_run_guide_done")
private val SNOOZED_UPDATE_BUILD = longPreferencesKey("soft_update_snoozed_build")
private val SNOOZED_UPDATE_UNTIL_MILLIS = longPreferencesKey("soft_update_snoozed_until_millis")

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
