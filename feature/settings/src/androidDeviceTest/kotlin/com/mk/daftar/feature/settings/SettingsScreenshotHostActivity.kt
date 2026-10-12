package com.mk.daftar.feature.settings

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
import com.mk.daftar.feature.settings.ui.AboutContent
import com.mk.daftar.feature.settings.ui.ChangePinContent
import com.mk.daftar.feature.settings.ui.PreferencesContent
import com.mk.daftar.feature.settings.ui.SettingsContent
import java.util.Locale

/**
 * Test-only host for the settings screens. It renders the stateless content with fake state, without a vault, a
 * repository or FLAG_SECURE, so the screenshot tests can capture every state. It is declared only in this library's
 * device-test manifest.
 */
class SettingsScreenshotHostActivity : ComponentActivity() {

    /** The language this launch renders in. The test sets it before launching the host. */
    companion object {
        @Volatile
        var language: String = "en"
    }

    /** One rendered page. */
    sealed interface Screen {
        data class Settings(val state: SettingsUiState) : Screen

        data class Preferences(val state: PreferencesUiState) : Screen

        data class ChangePin(val state: ChangePinUiState) : Screen

        data class About(val info: AppBuildInfo) : Screen
    }

    data class Frame(val screen: Screen, val dark: Boolean = false)

    /** The frame the test is capturing. Set on the UI thread. */
    var fake by mutableStateOf(Frame(Screen.Settings(SettingsUiState())))

    /** Applies the language before any resource is read, as the other hosts do. */
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
                    when (val screen = frame.screen) {
                        is Screen.Settings -> SettingsContent(
                            state = screen.state,
                            appInfo = HOST_INFO,
                            onIntent = {},
                            onChangePin = {},
                            onOpenPreferences = {},
                            onOpenAbout = {},
                            onOpenExport = {},
                            modifier = Modifier,
                        )

                        is Screen.Preferences -> PreferencesContent(
                            state = screen.state,
                            onIntent = {},
                            onBack = {},
                            modifier = Modifier,
                        )

                        is Screen.ChangePin -> ChangePinContent(
                            state = screen.state,
                            onIntent = {},
                            onBack = {},
                            modifier = Modifier,
                        )

                        is Screen.About -> AboutContent(
                            info = screen.info,
                            onBack = {},
                            onOpenSource = {},
                            modifier = Modifier,
                        )
                    }
                }
            }
        }
    }
}

/** The build the captures show. It is the build's own values, not a release claim. */
internal val HOST_INFO = AppBuildInfo(versionName = "1.0.0", buildNumber = "1")
