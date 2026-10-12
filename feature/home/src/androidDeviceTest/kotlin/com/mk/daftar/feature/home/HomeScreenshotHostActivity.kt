package com.mk.daftar.feature.home

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
 * Test-only host for the Home screen. It renders the stateless content with fake state, without a vault, a repository
 * or FLAG_SECURE, so the screenshot tests can capture every state. It is declared only in this library's device-test
 * manifest.
 */
class HomeScreenshotHostActivity : ComponentActivity() {

    /** The language this launch renders in. The test sets it before launching the host. */
    companion object {
        @Volatile
        var language: String = "en"
    }

    /** The state and theme the test is capturing. Set on the UI thread. */
    var fake by mutableStateOf(Frame())

    data class Frame(val state: HomeUiState = HomeUiState(), val dark: Boolean = false)

    /** Applies the language before any resource is read, as the auth host does. */
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
                    HomeContent(state = frame.state, onIntent = {}, modifier = Modifier)
                }
            }
        }
    }
}
