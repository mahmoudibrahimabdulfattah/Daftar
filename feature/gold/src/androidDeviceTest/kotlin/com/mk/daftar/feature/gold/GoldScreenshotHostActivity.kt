package com.mk.daftar.feature.gold

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import java.util.Locale

/** One captured frame: the file name, the theme it is drawn in, and what it draws. */
class GoldFrame(val name: String, val dark: Boolean, val content: @Composable () -> Unit)

/**
 * Test-only host for the gold screens. It draws the stateless screen content with fixed state, without a vault, a
 * repository or FLAG_SECURE, so the screenshot tests can capture each state. It is declared only in this library's
 * device-test manifest.
 */
class GoldScreenshotHostActivity : ComponentActivity() {

    companion object {
        /** The language this launch renders in. The test sets it before launching the host. */
        @Volatile
        var language: String = "en"
    }

    var frame by mutableStateOf<GoldFrame?>(null)

    /** Applies the language before any resource is read, so the values-ar resources load rather than the device locale. */
    override fun attachBaseContext(base: Context) {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        super.attachBaseContext(base.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val current = frame ?: return@setContent
            val arabic = language == "ar"
            DaftarTheme(darkTheme = current.dark, arabic = arabic) {
                val direction = if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    current.content()
                }
            }
        }
    }
}
