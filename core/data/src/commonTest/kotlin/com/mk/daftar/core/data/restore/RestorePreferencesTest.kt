package com.mk.daftar.core.data.restore

import com.mk.daftar.core.backup.BackupPreferences
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.security.SecurityError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class RestorePreferencesTest {

    private val preferences = BackupPreferences(
        themeMode = ThemeMode.DARK,
        language = AppLanguage.EN,
        inactivityTimeout = InactivityTimeout.SECONDS_300,
    )

    @Test
    fun encodeAndDecodeAreInverse() {
        val text = RestorePreferences.encode(preferences)
        assertEquals("theme=DARK;language=EN;timeout=300", text)
        assertEquals(preferences, RestorePreferences.decode(text))
    }

    @Test
    fun decodeRefusesAnythingThatIsNotExactlyThreeKnownFields() {
        val refused = listOf(
            "",
            "theme=DARK;language=EN",
            "theme=DARK;language=EN;timeout=300;biometric=1",
            "theme=NIGHT;language=EN;timeout=300",
            "theme=DARK;language=FR;timeout=300",
            "theme=DARK;language=EN;timeout=45",
            "theme=DARK;language=EN;timeout=abc",
            "theme=DARK;language;timeout=300",
        )
        for (text in refused) assertNull(RestorePreferences.decode(text), "accepted: $text")
    }

    @Test
    fun installSwitchesBiometricsOffBeforeAnyOtherSetting() = runTest {
        val repository = RecordingPreferences()
        val installer = RestorePreferencesInstaller(repository)

        assertEquals(Outcome.Success(Unit), installer.install(RestorePreferences.encode(preferences)))

        assertEquals(
            listOf("biometric=false", "theme=DARK", "language=EN", "timeout=300"),
            repository.calls,
        )
    }

    @Test
    fun aFailedWriteIsPersistenceFailureAndStopsTheInstall() = runTest {
        val repository = RecordingPreferences(failOn = "theme")
        val result = RestorePreferencesInstaller(repository).install(RestorePreferences.encode(preferences))
        assertEquals(SecurityError.PersistenceFailure, assertIs<Outcome.Failure<SecurityError>>(result).error)
        assertEquals(listOf("biometric=false", "theme=DARK"), repository.calls)
    }

    @Test
    fun aDamagedPayloadIsCorruptStateAndWritesNothing() = runTest {
        val repository = RecordingPreferences()
        val result = RestorePreferencesInstaller(repository).install("theme=NIGHT")
        assertEquals(SecurityError.CorruptState, assertIs<Outcome.Failure<SecurityError>>(result).error)
        assertEquals(emptyList(), repository.calls)
    }
}

/** Records every write in order. [failOn] makes the named write fail. */
private class RecordingPreferences(private val failOn: String? = null) : UserPreferencesRepository {
    val calls = mutableListOf<String>()

    private fun write(name: String): Outcome<Unit, StorageError> {
        calls += name
        return if (failOn != null &&
            name.startsWith(failOn)
        ) {
            Outcome.Failure(StorageError.Io)
        } else {
            Outcome.Success(Unit)
        }
    }

    override fun observe(): Flow<PreferencesRead> = emptyFlow()

    override suspend fun setThemeMode(mode: ThemeMode): Outcome<Unit, StorageError> = write("theme=${mode.name}")

    override suspend fun setLanguage(language: AppLanguage): Outcome<Unit, StorageError> =
        write("language=${language.name}")

    override suspend fun setInactivityTimeout(timeout: InactivityTimeout): Outcome<Unit, StorageError> =
        write("timeout=${timeout.seconds}")

    override suspend fun setBiometricEnabled(enabled: Boolean): Outcome<Unit, StorageError> =
        write("biometric=$enabled")
}
