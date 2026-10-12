package com.mk.daftar.feature.backup.restore

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.time.Instant
import org.junit.Test
import org.junit.runner.RunWith

/** Where the captures land on the device. MediaStore keeps them after the test APK is uninstalled. */
private const val SCREENSHOT_FOLDER = "Pictures/DaftarRestoreScreenshots"

private const val TYPED_PASSWORD = "correct horse battery"

private val SUMMARY = RestoreSummary(
    exportedAt = Instant.parse("2026-10-12T09:45:00Z"),
    goldHoldings = 3,
    banks = 3,
    bankAccounts = 4,
    cashBalances = 2,
    zakatPayments = 2,
    personalNotes = 2,
    remoteQuotes = 0,
    manualQuotes = 0,
)

/**
 * Renders every restore frame in Arabic and English, light and dark, and writes PNGs through MediaStore. The frames
 * cover each wizard step and each approved error, so a reviewer can check the copy and the layout of every state.
 */
@RunWith(AndroidJUnit4::class)
class RestoreScreenshotTest {

    private val steps: List<Pair<String, RestoreUiState>> = listOf(
        "choose" to RestoreUiState(phase = RestorePhase.ChooseFile),
        "password" to RestoreUiState(phase = RestorePhase.Password, password = TYPED_PASSWORD),
        "decrypting" to RestoreUiState(phase = RestorePhase.Decrypting),
        "review" to RestoreUiState(phase = RestorePhase.Review, summary = SUMMARY),
        "pin_new" to RestoreUiState(phase = RestorePhase.NewPin(confirming = false), pinCount = 6),
        "pin_mismatch" to RestoreUiState(
            phase = RestorePhase.NewPin(confirming = true),
            pinCount = 6,
            pinMismatch = true,
        ),
        "confirm_replace" to RestoreUiState(phase = RestorePhase.ConfirmReplacement),
        "preparing" to RestoreUiState(phase = RestorePhase.Preparing),
        "restoring" to RestoreUiState(phase = RestorePhase.Committing),
        "success" to RestoreUiState(phase = RestorePhase.Complete),
        "error_unreadable" to RestoreUiState(phase = RestorePhase.Failed(RestoreProblem.Unreadable)),
        "error_newer" to RestoreUiState(phase = RestorePhase.Failed(RestoreProblem.NewerFormat)),
        "error_file" to RestoreUiState(phase = RestorePhase.Failed(RestoreProblem.FileUnavailable)),
        "error_not_replaced" to RestoreUiState(phase = RestorePhase.Failed(RestoreProblem.NotReplaced)),
        "error_recovery" to RestoreUiState(phase = RestorePhase.Failed(RestoreProblem.RecoveryRequired)),
    )

    @Test
    fun capturesEveryRestoreStepInBothLanguagesAndThemes() {
        val written = capture(language = "ar")
        written.addAll(capture(language = "en"))
        val expected = steps.size * 4
        check(written.size == expected) { "expected $expected screenshots, wrote ${written.size}" }
    }

    private fun capture(language: String): MutableList<String> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val written = mutableListOf<String>()
        RestoreScreenshotHostActivity.language = language
        ActivityScenario.launch(RestoreScreenshotHostActivity::class.java).use { scenario ->
            for (dark in listOf(false, true)) {
                for ((name, state) in steps) {
                    scenario.onActivity { it.fake = RestoreScreenshotHostActivity.Frame(state, dark) }
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

    private fun savePng(resolver: android.content.ContentResolver, bitmap: Bitmap, name: String) {
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
