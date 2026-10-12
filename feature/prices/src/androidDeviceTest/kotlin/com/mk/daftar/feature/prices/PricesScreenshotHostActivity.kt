package com.mk.daftar.feature.prices

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
 * Test-only host for the prices screens. It renders the stateless prices and editor content with fake state, without a
 * vault, a repository or FLAG_SECURE, so the screenshot tests can capture every state. It is declared only in this
 * library's device-test manifest.
 */
class PricesScreenshotHostActivity : ComponentActivity() {

    /** The language and font scale this launch renders in. The test sets them before launching the host. */
    companion object {
        @Volatile
        var language: String = "en"

        @Volatile
        var fontScale: Float = 1f
    }

    /** One rendered screen: the prices table or the manual editor. */
    sealed interface Screen {
        data class Prices(val state: PricesUiState) : Screen

        data class Manual(val state: ManualQuoteUiState) : Screen
    }

    data class Frame(val screen: Screen = Screen.Prices(PricesUiState()), val dark: Boolean = false)

    /** The frame the test is capturing. Set on the UI thread. */
    var fake by mutableStateOf(Frame())

    /** Applies the language and font scale before any resource is read, as the other hosts do. */
    override fun attachBaseContext(base: Context) {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.fontScale = fontScale
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
                    when (val screen = frame.screen) {
                        is Screen.Prices -> PricesContent(
                            state = screen.state,
                            onIntent = {},
                            onBack = {},
                            modifier = Modifier,
                        )

                        is Screen.Manual -> ManualQuoteContent(
                            state = screen.state,
                            onIntent = {},
                            modifier = Modifier,
                        )
                    }
                }
            }
        }
    }
}
