package com.mk.daftar.feature.banks

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

/** One captured frame: a name for the file, the theme it is drawn in, and what it draws. */
class BanksFrame(val name: String, val dark: Boolean, val content: @Composable () -> Unit)

/**
 * Test-only host for the bank screens. It draws the stateless screen content with fixed state, without a vault, a
 * repository or FLAG_SECURE, so the screenshot tests can capture each state. It is declared only in this library's
 * device-test manifest.
 */
class BanksScreenshotHostActivity : ComponentActivity() {

    companion object {
        /** The language this launch renders in. The test sets it before launching the host. */
        @Volatile
        var language: String = "en"
    }

    var frame by mutableStateOf<BanksFrame?>(null)

    /**
     * Applies the language before any resource is read. Compose resources resolve through the activity's context, so a
     * configuration context in this language makes the values-ar resources load, not the device locale.
     */
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
