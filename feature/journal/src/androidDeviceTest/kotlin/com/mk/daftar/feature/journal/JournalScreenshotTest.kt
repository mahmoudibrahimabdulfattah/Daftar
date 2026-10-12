package com.mk.daftar.feature.journal

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders every journal frame in Arabic and English, light and dark, and writes PNGs to the test app's external files
 * directory under `journal-screenshots`. The host has no FLAG_SECURE, so the capture works; the application keeps it.
 */
@RunWith(AndroidJUnit4::class)
class JournalScreenshotTest {

    @Test
    fun capturesEveryJournalFrameInBothLanguagesAndThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "journal-screenshots")
        directory.mkdirs()
        val written = mutableListOf<String>()
        for (language in listOf("ar", "en")) {
            // Each language is a fresh launch, so the host's configuration is set before any resource is read.
            JournalScreenshotHostActivity.language = language
            ActivityScenario.launch(JournalScreenshotHostActivity::class.java).use { scenario ->
                for (dark in listOf(false, true)) {
                    for (name in journalFrameNames) {
                        scenario.onActivity { activity ->
                            activity.frame = JournalFrame(name, dark) { JournalFrameContent(name) }
                        }
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
        check(written.size == journalFrameNames.size * 4) {
            "expected ${journalFrameNames.size * 4} screenshots, wrote ${written.size}"
        }
    }
}
