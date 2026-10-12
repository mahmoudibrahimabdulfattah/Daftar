package com.mk.daftar.feature.assets

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/** Where the captures land on the device. MediaStore keeps them after the test APK is uninstalled. */
private const val SCREENSHOT_FOLDER = "Pictures/DaftarAssetsScreenshots"

/** Renders the Assets overview states in Arabic and English, light and dark, and saves PNGs to MediaStore. */
@RunWith(AndroidJUnit4::class)
class AssetsScreenshotTest {

    private val states: List<Pair<String, AssetsUiState>> = listOf(
        "ready" to AssetsUiState(load = AssetsLoad.Ready(readyAssets())),
        "masked" to AssetsUiState(load = AssetsLoad.Ready(readyAssets()), valuesHidden = true),
        "incomplete" to AssetsUiState(load = AssetsLoad.Ready(incompleteAssets())),
        "empty" to AssetsUiState(load = AssetsLoad.Ready(emptyAssets())),
    )

    @Test
    fun capturesEveryAssetsStateInBothLanguagesAndThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val written = mutableListOf<String>()
        for (language in listOf("ar", "en")) {
            // Each language is a fresh launch, so the host's configuration is set before any resource is read.
            AssetsScreenshotHostActivity.language = language
            ActivityScenario.launch(AssetsScreenshotHostActivity::class.java).use { scenario ->
                for (dark in listOf(false, true)) {
                    for ((name, state) in states) {
                        scenario.onActivity { it.fake = AssetsScreenshotHostActivity.Frame(state, dark) }
                        repeat(3) {
                            instrumentation.waitForIdleSync()
                            Thread.sleep(120)
                        }
                        val screen: Bitmap = instrumentation.uiAutomation.takeScreenshot()
                        val theme = if (dark) "dark" else "light"
                        val name2 = "assets-$language-$theme-$name.png"
                        savePng(resolver, screen, name2)
                        written += name2
                    }
                }
            }
        }
        check(written.size == states.size * 4) { "expected ${states.size * 4} screenshots, wrote ${written.size}" }
    }

    private fun savePng(resolver: ContentResolver, bitmap: Bitmap, name: String) {
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
