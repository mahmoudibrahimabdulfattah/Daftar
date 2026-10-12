package com.mk.daftar.feature.backup

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupExportState
import org.junit.Test
import org.junit.runner.RunWith

/** Where the captures land on the device. MediaStore keeps them after the test APK is uninstalled. */
private const val SCREENSHOT_FOLDER = "Pictures/DaftarBackupScreenshots"

private const val TYPED_PASSWORD = "correct horse battery"

/**
 * Renders every export step in Arabic and English, light and dark, and writes PNGs through MediaStore. Two large-text
 * captures cover the Arabic password form at the largest common scale: the IME-sized, scrollable form is the one that
 * must keep its errors on screen.
 */
@RunWith(AndroidJUnit4::class)
class BackupScreenshotTest {

    private val steps: List<Pair<String, ExportUiState>> = listOf(
        "intro" to ExportUiState(step = ExportStep.INTRO),
        "password" to ExportUiState(step = ExportStep.PASSWORD, password = TYPED_PASSWORD, confirm = TYPED_PASSWORD),
        "confirm_mismatch" to ExportUiState(
            step = ExportStep.PASSWORD,
            password = TYPED_PASSWORD,
            confirm = "correct horse batter",
            submitAttempted = true,
        ),
        "short_password" to ExportUiState(
            step = ExportStep.PASSWORD,
            password = "short",
            confirm = "short",
            submitAttempted = true,
        ),
        "exporting" to ExportUiState(step = ExportStep.PASSWORD, operation = BackupExportState.Preparing),
        "success" to ExportUiState(
            step = ExportStep.PASSWORD,
            operation = BackupExportState.Complete("daftar-2026-10-12.dftar"),
        ),
        "failure" to ExportUiState(
            step = ExportStep.PASSWORD,
            operation = BackupExportState.Failed(BackupError.WriteFailed),
        ),
    )

    @Test
    fun capturesEveryExportStepInBothLanguagesAndThemes() {
        val written = capture(language = "ar", fontScale = 1f, names = null)
        written.addAll(capture(language = "en", fontScale = 1f, names = null))
        // The largest common text scale, for the password form where wrapping and the keyboard matter most.
        written.addAll(capture(language = "ar", fontScale = 1.6f, names = setOf("password", "confirm_mismatch")))
        val expected = steps.size * 4 + 2 * 2
        check(written.size == expected) { "expected $expected screenshots, wrote ${written.size}" }
    }

    /** Captures the frames for [language] at [fontScale], in light and dark. [names] limits the set when given. */
    private fun capture(language: String, fontScale: Float, names: Set<String>?): MutableList<String> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val written = mutableListOf<String>()
        BackupScreenshotHostActivity.language = language
        BackupScreenshotHostActivity.fontScale = fontScale
        ActivityScenario.launch(BackupScreenshotHostActivity::class.java).use { scenario ->
            for (dark in listOf(false, true)) {
                for ((name, state) in steps.filter { names == null || it.first in names }) {
                    scenario.onActivity { it.fake = BackupScreenshotHostActivity.Frame(state, dark) }
                    // Compose draws on later frames: wait for the new state to be laid out and drawn before capture.
                    repeat(3) {
                        instrumentation.waitForIdleSync()
                        Thread.sleep(120)
                    }
                    val screenshot: Bitmap = instrumentation.uiAutomation.takeScreenshot()
                    val theme = if (dark) "dark" else "light"
                    val fileName = if (fontScale == 1f) {
                        "$language-$theme-$name.png"
                    } else {
                        "$language-large-$theme-$name.png"
                    }
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
