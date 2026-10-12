package com.mk.daftar.feature.home

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mk.daftar.core.domain.storage.StorageError
import org.junit.Test
import org.junit.runner.RunWith

/** Where the captures land on the device. MediaStore keeps them after the test APK is uninstalled. */
private const val SCREENSHOT_FOLDER = "Pictures/DaftarHomeScreenshots"

/**
 * Renders every Home state in Arabic and English, light and dark, and writes PNGs to the test app's external files
 * directory. The host has no FLAG_SECURE, so the capture works; the application keeps FLAG_SECURE.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenshotTest {

    private val states: List<Pair<String, HomeUiState>> = listOf(
        "ready" to HomeUiState(load = HomeLoad.Ready(readyFigures())),
        "masked" to HomeUiState(load = HomeLoad.Ready(readyFigures()), valuesHidden = true),
        "empty" to HomeUiState(load = HomeLoad.Ready(emptyFigures())),
        "incomplete" to HomeUiState(load = HomeLoad.Ready(incompleteFigures())),
        "refreshing" to HomeUiState(load = HomeLoad.Ready(readyFigures()), refreshing = true),
        "stale_refresh_failed" to HomeUiState(load = HomeLoad.Ready(staleFigures()), refreshFailed = true),
        "masked_incomplete" to HomeUiState(load = HomeLoad.Ready(incompleteFigures()), valuesHidden = true),
        "storage_error" to HomeUiState(load = HomeLoad.Failed(StorageError.Io)),
        "loading" to HomeUiState(load = HomeLoad.Loading),
    )

    @Test
    fun capturesEveryHomeStateInBothLanguagesAndThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val written = mutableListOf<String>()
        for (language in listOf("ar", "en")) {
            // Each language is a fresh launch, so the host's configuration is set before any resource is read.
            HomeScreenshotHostActivity.language = language
            ActivityScenario.launch(HomeScreenshotHostActivity::class.java).use { scenario ->
                for (dark in listOf(false, true)) {
                    for ((name, state) in states) {
                        scenario.onActivity { it.fake = HomeScreenshotHostActivity.Frame(state, dark) }
                        // Compose draws on later frames: wait for the new state to be laid out and drawn before capture.
                        repeat(3) {
                            instrumentation.waitForIdleSync()
                            Thread.sleep(120)
                        }
                        val screen: Bitmap = instrumentation.uiAutomation.takeScreenshot()
                        val theme = if (dark) "dark" else "light"
                        val name = "$language-$theme-$name.png"
                        savePng(resolver, screen, name)
                        written += name
                    }
                }
            }
        }
        check(written.size == states.size * 4) { "expected ${states.size * 4} screenshots, wrote ${written.size}" }
    }

    private fun savePng(resolver: android.content.ContentResolver, bitmap: Bitmap, name: String) {
        val pending = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, SCREENSHOT_FOLDER)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri =
            checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pending)) {
                "no MediaStore row for $name"
            }
        checkNotNull(resolver.openOutputStream(uri)) {
            "no output for $name"
        }.use { bitmap.compress(CompressFormat.PNG, 100, it) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }
}
