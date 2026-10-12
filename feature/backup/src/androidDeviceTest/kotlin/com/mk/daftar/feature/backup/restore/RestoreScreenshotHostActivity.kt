package com.mk.daftar.feature.backup.restore

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import java.util.Locale

/**
 * Test-only host for the restore wizard. It renders the stateless restore content with fake state, without a vault or
 * a view model, so every step can be captured in both languages and themes. It lives only in this library's device-test
 * manifest.
 */
class RestoreScreenshotHostActivity : ComponentActivity() {

    companion object {
        @Volatile
        var language: String = "en"
    }

    data class Frame(val state: RestoreUiState = RestoreUiState(), val dark: Boolean = false)

    /** The frame the test is capturing. Set on the UI thread. */
    var fake by mutableStateOf(Frame())

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
            val frame = fake
            val arabic = language == "ar"
            DaftarTheme(darkTheme = frame.dark, arabic = arabic) {
                val direction = if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    RestoreContent(state = frame.state, onIntent = {}, modifier = Modifier)
                }
            }
        }
    }
}
