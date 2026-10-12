package com.mk.daftar.feature.gold

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
private const val SCREENSHOT_FOLDER = "Pictures/DaftarGoldScreenshots"

/**
 * Renders every gold frame in Arabic and English, light and dark, and saves PNGs to MediaStore. The host has no
 * FLAG_SECURE, so the capture works; the application keeps it.
 */
@RunWith(AndroidJUnit4::class)
class GoldScreenshotTest {

    @Test
    fun capturesEveryGoldFrameInBothLanguagesAndThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val written = mutableListOf<String>()
        for (language in listOf("ar", "en")) {
            // Each language is a fresh launch, so the host's configuration is set before any resource is read.
            GoldScreenshotHostActivity.language = language
            ActivityScenario.launch(GoldScreenshotHostActivity::class.java).use { scenario ->
                for (dark in listOf(false, true)) {
                    for (name in goldFrameNames) {
                        scenario.onActivity { activity ->
                            activity.frame = GoldFrame(name, dark) { GoldFrameContent(name) }
                        }
                        // Compose draws on later frames: wait for the new state to be laid out and drawn before capture.
                        repeat(3) {
                            instrumentation.waitForIdleSync()
                            Thread.sleep(120)
                        }
                        // The display itself, so the capture shows what the user would see, not a cached draw.
                        val screen: Bitmap = instrumentation.uiAutomation.takeScreenshot()
                        val theme = if (dark) "dark" else "light"
                        val fileName = "gold-$language-$theme-$name.png"
                        savePng(resolver, screen, fileName)
                        written += fileName
                    }
                }
            }
        }
        check(written.size == goldFrameNames.size * 4) {
            "expected ${goldFrameNames.size * 4} screenshots, wrote ${written.size}"
        }
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
