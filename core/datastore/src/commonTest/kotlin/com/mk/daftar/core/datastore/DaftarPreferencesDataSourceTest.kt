package com.mk.daftar.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.storage.StorageError
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path

class DaftarPreferencesDataSourceTest {

    private fun newDirectory(): Path {
        val directory = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "daftar-prefs-${Random.nextLong()}")
        FileSystem.SYSTEM.createDirectories(directory)
        return directory
    }

    private fun TestScope.source(file: Path): DaftarPreferencesDataSource = createDaftarPreferencesDataSource(
        produceFile = { file },
        scope = backgroundScope,
    )

    private fun TestScope.rawStore(file: Path) = PreferenceDataStoreFactory.createWithPath(
        scope = backgroundScope,
        produceFile = { file },
    )

    @Test
    fun aMissingFileReadsAsDefaultsWithoutRecovery() = runTest {
        val file = newDirectory() / "daftar.preferences_pb"
        val read = source(file).observe().first()
        assertEquals(PreferencesRead.Ready(UserPreferences(), recoveredFromCorruption = false), read)
    }

    @Test
    fun defaultsMatchTheSpecification() {
        val defaults = UserPreferences()
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertEquals(AppLanguage.AR, defaults.language)
        assertEquals(InactivityTimeout.SECONDS_60, defaults.inactivityTimeout)
        assertEquals(false, defaults.biometricEnabled)
    }

    @Test
    fun typedSettersPersistAndReadBack() = runTest {
        val file = newDirectory() / "daftar.preferences_pb"
        val source = source(file)
        assertEquals(Outcome.Success(Unit), source.setThemeMode(ThemeMode.DARK))
        assertEquals(Outcome.Success(Unit), source.setLanguage(AppLanguage.EN))
        assertEquals(Outcome.Success(Unit), source.setInactivityTimeout(InactivityTimeout.SECONDS_300))
        assertEquals(Outcome.Success(Unit), source.setBiometricEnabled(true))

        val read = source.observe().first()
        assertEquals(
            PreferencesRead.Ready(
                UserPreferences(
                    themeMode = ThemeMode.DARK,
                    language = AppLanguage.EN,
                    inactivityTimeout = InactivityTimeout.SECONDS_300,
                    biometricEnabled = true,
                ),
                recoveredFromCorruption = false,
            ),
            read,
        )
    }

    @Test
    fun unknownStoredValuesFallBackToDefaults() = runTest {
        val file = newDirectory() / "daftar.preferences_pb"
        val store = rawStore(file)
        store.edit { prefs ->
            prefs[stringPreferencesKey("theme_mode")] = "NEON"
            prefs[stringPreferencesKey("language")] = "FR"
            prefs[intPreferencesKey("inactivity_timeout_seconds")] = 45
            prefs[booleanPreferencesKey("biometric_enabled")] = true
        }
        val read = DaftarPreferencesDataSource(store, MutableStateFlow(false)).observe().first()
        assertEquals(
            PreferencesRead.Ready(
                UserPreferences(biometricEnabled = true),
                recoveredFromCorruption = false,
            ),
            read,
        )
    }

    @Test
    fun aCorruptFileRecoversToDefaultsAndReportsIt() = runTest {
        val directory = newDirectory()
        val file = directory / "daftar.preferences_pb"
        // A run of continuation bytes is never a valid protobuf varint, so the parser must report corruption.
        FileSystem.SYSTEM.write(file) { write(ByteArray(16) { 0xFF.toByte() }) }

        val read = source(file).observe().first()
        assertEquals(PreferencesRead.Ready(UserPreferences(), recoveredFromCorruption = true), read)
    }

    @Test
    fun anUnreadableFileIsReportedNotEmpty() = runTest {
        // A directory at the file path cannot be read as preferences, so the failure must surface.
        val directory = newDirectory()
        val path = (directory / "daftar.preferences_pb").also { FileSystem.SYSTEM.createDirectories(it) }

        val read = source(path).observe().first()
        assertEquals(PreferencesRead.Failed(StorageError.Io), read)
    }

    @Test
    fun aFailedWriteIsReturnedToTheCaller() = runTest {
        val directory = newDirectory()
        val path = (directory / "daftar.preferences_pb").also { FileSystem.SYSTEM.createDirectories(it) }

        val result = source(path).setThemeMode(ThemeMode.LIGHT)
        assertIs<Outcome.Failure<StorageError>>(result)
        assertEquals(StorageError.Io, result.error)
    }

    @Test
    fun aRecoveredFlagStaysInTheObservationState() = runTest {
        val file = newDirectory() / "daftar.preferences_pb"
        val recovered = MutableStateFlow(true)
        val store = rawStore(file)
        val source = DaftarPreferencesDataSource(store, recovered)
        assertEquals(
            PreferencesRead.Ready(UserPreferences(), recoveredFromCorruption = true),
            source.observe().first(),
        )
    }

    @Test
    fun theFileNameIsTheSpecifiedOne() {
        assertEquals("daftar.preferences_pb", DAFTAR_PREFERENCES_FILE_NAME)
    }

    @Test
    fun emptyPreferencesMapToDefaults() {
        assertEquals(UserPreferences(), emptyPreferences().toUserPreferences())
    }
}
