package com.mk.daftar.feature.auth

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mk.daftar.feature.auth.AuthBusy
import com.mk.daftar.feature.auth.AuthError
import com.mk.daftar.feature.auth.AuthNotice
import com.mk.daftar.feature.auth.AuthPhase
import com.mk.daftar.feature.auth.AuthUiState
import com.mk.daftar.feature.auth.AuthUnavailableReason
import com.mk.daftar.feature.auth.BiometricOffer
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders every auth state in Arabic and English, light and dark, and writes PNGs to the test app's external files
 * directory. The host has no FLAG_SECURE, so the capture works; the application keeps FLAG_SECURE.
 */
@RunWith(AndroidJUnit4::class)
class AuthScreenshotTest {

    private val states: List<Pair<String, AuthUiState>> = listOf(
        "create" to AuthUiState(phase = AuthPhase.Create, enteredCount = 4),
        "confirm" to AuthUiState(phase = AuthPhase.Confirm, enteredCount = 3),
        "mismatch" to AuthUiState(phase = AuthPhase.Confirm, enteredCount = 2, error = AuthError.PinMismatch),
        "biometric_offer" to AuthUiState(phase = AuthPhase.Biometric, biometricOffer = BiometricOffer.Available),
        "biometric_not_enrolled" to
            AuthUiState(phase = AuthPhase.Biometric, biometricOffer = BiometricOffer.NotEnrolled),
        "biometric_unavailable" to AuthUiState(
            phase = AuthPhase.Biometric,
            biometricOffer = BiometricOffer.Unavailable,
        ),
        "biometric_enrolling" to AuthUiState(
            phase = AuthPhase.Biometric,
            biometricOffer = BiometricOffer.Available,
            busy = AuthBusy.Enrolling,
        ),
        "unlock_biometric_key" to AuthUiState(phase = AuthPhase.Unlock, biometricReady = true),
        "unlock_biometric_fallback" to AuthUiState(
            phase = AuthPhase.Unlock,
            enteredCount = 6,
            error = AuthError.IncorrectPin,
            biometricReady = true,
        ),
        "unlock_notice_unavailable" to AuthUiState(
            phase = AuthPhase.Unlock,
            notice = AuthNotice.BiometricsUnavailable,
        ),
        "unlock_notice_locked_out" to AuthUiState(
            phase = AuthPhase.Unlock,
            notice = AuthNotice.BiometricsLockedOut,
        ),
        "unlock_notice_not_enabled" to AuthUiState(
            phase = AuthPhase.Unlock,
            notice = AuthNotice.BiometricsNotEnabled,
        ),
        "unlock" to AuthUiState(phase = AuthPhase.Unlock, enteredCount = 3),
        "wrong_pin" to AuthUiState(phase = AuthPhase.Unlock, enteredCount = 0, error = AuthError.IncorrectPin),
        "backoff" to AuthUiState(phase = AuthPhase.Unlock, enteredCount = 0, retrySeconds = 30),
        "busy" to AuthUiState(phase = AuthPhase.Unlock, enteredCount = 6, busy = AuthBusy.Unlocking),
        "busy_create" to AuthUiState(phase = AuthPhase.Biometric, busy = AuthBusy.Creating),
        "recovery" to AuthUiState(phase = AuthPhase.Recovery),
        "unavailable_hardware" to AuthUiState(phase = AuthPhase.Unavailable(AuthUnavailableReason.UnsupportedHardware)),
        "unavailable_key" to AuthUiState(phase = AuthPhase.Unavailable(AuthUnavailableReason.DeviceKeyUnavailable)),
        "unavailable_corrupt" to AuthUiState(phase = AuthPhase.Unavailable(AuthUnavailableReason.CorruptState)),
        "unavailable_format" to AuthUiState(phase = AuthPhase.Unavailable(AuthUnavailableReason.UnsupportedFormat)),
        "unavailable_transient" to AuthUiState(phase = AuthPhase.Unavailable(AuthUnavailableReason.Transient)),
    )

    @Test
    fun capturesEveryAuthStateInBothLanguagesAndThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "auth-screenshots")
        directory.mkdirs()
        val written = mutableListOf<String>()
        for (language in listOf("ar", "en")) {
            // Each language is a fresh launch, so the host's configuration is set before any resource is read.
            AuthScreenshotHostActivity.language = language
            ActivityScenario.launch(AuthScreenshotHostActivity::class.java).use { scenario ->
                for (dark in listOf(false, true)) {
                    for ((name, state) in states) {
                        scenario.onActivity { it.fake = AuthScreenshotHostActivity.Frame(state, dark) }
                        // Compose draws on later frames: wait for the new state to be laid out and drawn before capture.
                        repeat(3) {
                            instrumentation.waitForIdleSync()
                            Thread.sleep(120)
                        }
                        // The display itself, so the capture shows what the user would see, not a cached draw.
                        val screen: Bitmap = instrumentation.uiAutomation.takeScreenshot()
                        val theme = if (dark) "dark" else "light"
                        val file = File(directory, "$language-$theme-$name.png")
                        file.outputStream().use { screen.compress(CompressFormat.PNG, 100, it) }
                        written += file.name
                    }
                }
            }
        }
        check(written.size == states.size * 4) { "expected ${states.size * 4} screenshots, wrote ${written.size}" }
    }
}
