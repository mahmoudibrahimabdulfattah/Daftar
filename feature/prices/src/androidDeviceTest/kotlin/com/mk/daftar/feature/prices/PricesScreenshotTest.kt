package com.mk.daftar.feature.prices

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.refresh.RefreshOutcome
import com.mk.daftar.core.domain.repository.RefreshRejection
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteKey
import org.junit.Test
import org.junit.runner.RunWith

/** Where the captures land on the device. MediaStore keeps them after the test APK is uninstalled. */
private const val SCREENSHOT_FOLDER = "Pictures/DaftarPricesScreenshots"

/**
 * Renders every prices and editor state in Arabic and English, light and dark, and writes PNGs through MediaStore. Two
 * large-text captures cover the Arabic layout at the largest common scale. The host has no FLAG_SECURE, so the capture
 * works; the application keeps FLAG_SECURE.
 */
@RunWith(AndroidJUnit4::class)
class PricesScreenshotTest {

    private val prices: List<Pair<String, PricesUiState>> = listOf(
        "live" to pricesStateFor(liveSnapshot()),
        "cached" to pricesStateFor(cachedSnapshot()),
        "pinned_manual" to pricesStateFor(pinnedSnapshot()),
        "stale" to pricesStateFor(staleSnapshot()),
        "missing" to pricesStateFor(missingSnapshot()),
        "missing_masked" to pricesStateFor(missingSnapshot(), hidden = true),
        "last_resort" to pricesStateFor(lastResortSnapshot()),
        "refreshing" to pricesStateFor(liveSnapshot(), refresh = PriceRefreshStatus.Refreshing(1)),
        "refresh_offline" to pricesStateFor(
            cachedSnapshot(),
            refresh = PriceRefreshStatus.Finished(1, RefreshOutcome.Failed, setOf(RefreshRejection.Unreachable)),
        ),
        "refresh_rate_limited" to pricesStateFor(
            cachedSnapshot(),
            refresh = PriceRefreshStatus.Finished(1, RefreshOutcome.Failed, setOf(RefreshRejection.RateLimited)),
        ),
        "refresh_partial" to pricesStateFor(
            liveSnapshot(),
            refresh = PriceRefreshStatus.Finished(1, RefreshOutcome.PartiallyFailed, setOf(RefreshRejection.Missing)),
        ),
        "loading" to PricesUiState(load = PricesLoad.Loading),
        "storage_error" to PricesUiState(load = PricesLoad.Failed(com.mk.daftar.core.domain.storage.StorageError.Io)),
    )

    private val editors: List<Pair<String, ManualQuoteUiState>> = listOf(
        "editor_form" to editorFor(liveSnapshot(), sellKey21(), draft = "6185.5", note = "Local goldsmith"),
        "editor_pinned" to editorFor(manualSnapshot(), sellKey21(), draft = "6100", dirty = false),
        "editor_invalid" to editorFor(
            liveSnapshot(),
            key = sellKey(GoldKarat.K18),
            draft = "6,185",
            validation = ManualValueProblem.GROUPING,
        ),
        "editor_conflict" to editorFor(manualSnapshot(), sellKey21(), draft = "6200", conflict = true),
        "editor_saving" to editorFor(liveSnapshot(), sellKey21(), draft = "6200", busy = true),
        "editor_choose" to editorFor(liveSnapshot(), key = null, draft = "", dirty = false, choosing = true),
        "editor_no_source" to editorFor(lastResortSnapshot(), sellKey(GoldKarat.K18), draft = "5200", dirty = false),
    )

    private fun sellKey21(): QuoteKey = sellKey(GoldKarat.K21)

    private fun frames(): List<Pair<String, PricesScreenshotHostActivity.Screen>> =
        prices.map { (name, state) -> name to PricesScreenshotHostActivity.Screen.Prices(state) } +
            editors.map { (name, state) -> name to PricesScreenshotHostActivity.Screen.Manual(state) }

    @Test
    fun capturesEveryPricesAndEditorStateInBothLanguagesAndThemes() {
        val written = capture(language = "ar", fontScale = 1f, names = null)
        written.addAll(capture(language = "en", fontScale = 1f, names = null))
        // The largest common text scale, for the two screens where wrapping matters most.
        written.addAll(capture(language = "ar", fontScale = 1.6f, names = setOf("live", "editor_form")))
        val expected = (prices.size + editors.size) * 4 + 2 * 2
        check(written.size == expected) { "expected $expected screenshots, wrote ${written.size}" }
    }

    /** Captures the frames for [language] at [fontScale], in light and dark. [names] limits the set when given. */
    private fun capture(language: String, fontScale: Float, names: Set<String>?): MutableList<String> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val written = mutableListOf<String>()
        PricesScreenshotHostActivity.language = language
        PricesScreenshotHostActivity.fontScale = fontScale
        ActivityScenario.launch(PricesScreenshotHostActivity::class.java).use { scenario ->
            for (dark in listOf(false, true)) {
                for ((name, screen) in frames().filter { names == null || it.first in names }) {
                    scenario.onActivity { it.fake = PricesScreenshotHostActivity.Frame(screen, dark) }
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
