package com.mk.daftar.core.data.restore

import com.mk.daftar.core.backup.BackupPreferences
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.security.PostCommitInstaller
import com.mk.daftar.core.security.SecurityError

/**
 * The imported settings as one short text value, stored in the generation pointer until they are installed. The text
 * holds only enum names and a number, so it can be parsed strictly and never carries a secret.
 */
internal object RestorePreferences {
    fun encode(preferences: BackupPreferences): String =
        "theme=${preferences.themeMode.name};language=${preferences.language.name};" +
            "timeout=${preferences.inactivityTimeout.seconds}"

    fun decode(text: String): BackupPreferences? {
        val fields = text.split(';').associate { field ->
            val parts = field.split('=', limit = 2)
            if (parts.size != 2) return null
            parts[0] to parts[1]
        }
        if (fields.size != 3) return null
        val theme = ThemeMode.entries.firstOrNull { it.name == fields["theme"] } ?: return null
        val language = AppLanguage.entries.firstOrNull { it.name == fields["language"] } ?: return null
        val timeoutSeconds = fields["timeout"]?.toIntOrNull() ?: return null
        val timeout = InactivityTimeout.entries.firstOrNull { it.seconds == timeoutSeconds } ?: return null
        return BackupPreferences(themeMode = theme, language = language, inactivityTimeout = timeout)
    }
}

/**
 * Installs the settings of a committed restore. Biometrics are switched off first: the new vault has no slot, so the
 * preference must never say otherwise. Every write is idempotent, so a repeated install after an interruption is safe.
 */
internal class RestorePreferencesInstaller(private val preferences: UserPreferencesRepository) : PostCommitInstaller {
    override suspend fun install(payload: String): Outcome<Unit, SecurityError> {
        val imported = RestorePreferences.decode(payload) ?: return Outcome.Failure(SecurityError.CorruptState)
        if (preferences.setBiometricEnabled(false) is Outcome.Failure) {
            return Outcome.Failure(SecurityError.PersistenceFailure)
        }
        if (preferences.setThemeMode(imported.themeMode) is Outcome.Failure) {
            return Outcome.Failure(SecurityError.PersistenceFailure)
        }
        if (preferences.setLanguage(imported.language) is Outcome.Failure) {
            return Outcome.Failure(SecurityError.PersistenceFailure)
        }
        if (preferences.setInactivityTimeout(imported.inactivityTimeout) is Outcome.Failure) {
            return Outcome.Failure(SecurityError.PersistenceFailure)
        }
        return Outcome.Success(Unit)
    }
}
