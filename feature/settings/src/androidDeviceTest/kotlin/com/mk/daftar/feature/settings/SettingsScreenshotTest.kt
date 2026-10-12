package com.mk.daftar.feature.settings

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.feature.settings.SettingsScreenshotHostActivity.Frame
import com.mk.daftar.feature.settings.SettingsScreenshotHostActivity.Screen
import org.junit.Test
import org.junit.runner.RunWith

/** Where the captures land on the device. MediaStore keeps them after the test APK is uninstalled. */
private const val SCREENSHOT_FOLDER = "Pictures/DaftarSettingsScreenshots"

/**
 * Renders every settings page, sheet and PIN stage in Arabic and English, light and dark, and writes PNGs through
 * MediaStore. The host has no FLAG_SECURE, so the capture works; the application keeps FLAG_SECURE.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenshotTest {

    private val prefs = UserPreferences(
        themeMode = ThemeMode.SYSTEM,
        language = AppLanguage.AR,
        inactivityTimeout = InactivityTimeout.SECONDS_60,
    )

    private fun settings(
        biometric: BiometricSettings = BiometricSettings(enabled = false, availability = BiometricAvailability.Ready),
        failure: SecurityActionFailure? = null,
        sheet: BiometricPinSheet? = null,
    ) = Screen.Settings(
        SettingsUiState(preferences = prefs, biometric = biometric, biometricFailure = failure, biometricSheet = sheet),
    )

    private val frames: List<Pair<String, Screen>> = listOf(
        "settings" to settings(),
        "settings_biometric_on" to
            settings(BiometricSettings(enabled = true, availability = BiometricAvailability.Ready)),
        "settings_biometric_not_enrolled" to settings(
            BiometricSettings(enabled = false, availability = BiometricAvailability.NotEnrolled),
        ),
        "settings_biometric_unavailable" to settings(
            BiometricSettings(enabled = false, availability = BiometricAvailability.Unavailable),
        ),
        "settings_biometric_invalidated" to settings(
            BiometricSettings(enabled = false, availability = BiometricAvailability.Invalidated),
        ),
        "settings_biometric_cancelled" to settings(failure = SecurityActionFailure.Cancelled),
        "settings_biometric_pin" to settings(sheet = BiometricPinSheet(enteredCount = 6)),
        "settings_biometric_pin_wrong" to settings(
            sheet = BiometricPinSheet(enteredCount = 0, failure = SecurityActionFailure.WrongPin),
        ),
        "settings_biometric_pin_backoff" to settings(sheet = BiometricPinSheet(retrySeconds = 30)),
        "preferences" to Screen.Preferences(PreferencesUiState(preferences = prefs)),
        "preferences_theme_sheet" to
            Screen.Preferences(PreferencesUiState(preferences = prefs, sheet = PreferenceSheet.Theme)),
        "preferences_language_sheet" to Screen.Preferences(
            PreferencesUiState(preferences = prefs, sheet = PreferenceSheet.Language),
        ),
        "preferences_timeout_sheet" to Screen.Preferences(
            PreferencesUiState(preferences = prefs, sheet = PreferenceSheet.Timeout),
        ),
        "preferences_save_failed" to Screen.Preferences(PreferencesUiState(preferences = prefs, saveFailed = true)),
        "pin_verify" to Screen.ChangePin(ChangePinUiState(stage = ChangePinStage.Verify, enteredCount = 4)),
        "pin_verify_wrong" to Screen.ChangePin(
            ChangePinUiState(stage = ChangePinStage.Verify, failure = SecurityActionFailure.WrongPin),
        ),
        "pin_verify_backoff" to Screen.ChangePin(ChangePinUiState(stage = ChangePinStage.Verify, retrySeconds = 30)),
        "pin_new" to Screen.ChangePin(ChangePinUiState(stage = ChangePinStage.NewPin, enteredCount = 6)),
        "pin_confirm" to Screen.ChangePin(ChangePinUiState(stage = ChangePinStage.ConfirmPin, enteredCount = 3)),
        "pin_confirm_mismatch" to Screen.ChangePin(
            ChangePinUiState(stage = ChangePinStage.ConfirmPin, enteredCount = 0, mismatch = true),
        ),
        "pin_saving" to Screen.ChangePin(ChangePinUiState(stage = ChangePinStage.Saving, busy = true)),
        "pin_failed_previous" to
            Screen.ChangePin(ChangePinUiState(stage = ChangePinStage.Failed(previousPinStillWorks = true))),
        "pin_failed_unknown" to
            Screen.ChangePin(ChangePinUiState(stage = ChangePinStage.Failed(previousPinStillWorks = false))),
        "about" to Screen.About(HOST_INFO),
    )

    @Test
    fun capturesEverySettingsStateInBothLanguagesAndThemes() {
        val written = capture(language = "ar") + capture(language = "en")
        val expected = frames.size * 4
        check(written.size == expected) { "expected $expected screenshots, wrote ${written.size}" }
    }

    /** Captures every frame for [language], in light and dark. Each language is a fresh launch of the host. */
    private fun capture(language: String): List<String> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val written = mutableListOf<String>()
        SettingsScreenshotHostActivity.language = language
        ActivityScenario.launch(SettingsScreenshotHostActivity::class.java).use { scenario ->
            for (dark in listOf(false, true)) {
                for ((name, screen) in frames) {
                    scenario.onActivity { it.fake = Frame(screen, dark) }
                    // Compose draws on later frames: wait for the new state to be laid out and drawn before capture.
                    repeat(3) {
                        instrumentation.waitForIdleSync()
                        Thread.sleep(120)
                    }
                    val screenshot: Bitmap = instrumentation.uiAutomation.takeScreenshot()
                    val theme = if (dark) "dark" else "light"
                    val fileName = "$language-$theme-$name.png"
                    savePng(resolver, screenshot, fileName)
                    written += fileName
                }
            }
        }
        return written
    }

    private fun savePng(resolver: ContentResolver, bitmap: Bitmap, name: String) {
        val pending = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, SCREENSHOT_FOLDER)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pending)) {
            "no MediaStore row for $name"
        }
        checkNotNull(resolver.openOutputStream(uri)) { "no output for $name" }
            .use { bitmap.compress(CompressFormat.PNG, 100, it) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }
}
