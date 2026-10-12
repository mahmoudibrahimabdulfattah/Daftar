package com.mk.daftar

import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.mk.daftar.core.domain.repository.ThemeMode
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController

/**
 * The iOS root. Developer entries follow the binary's debug flag: a debug framework carries them, and a release
 * framework is built without them, so no developer route is reachable from a release build.
 */
@OptIn(ExperimentalNativeApi::class)
@Suppress("FunctionName", "ktlint:standard:function-naming")
fun MainViewController(): UIViewController = ComposeUIViewController {
    val controller = LocalUIViewController.current
    App(
        developerTools = Platform.isDebugBinary,
        // The status bar and any system UI follow the app's theme; "system" hands it back to iOS.
        onThemeChange = { mode, _ -> controller.overrideUserInterfaceStyle = mode.interfaceStyle },
    )
}

private val ThemeMode.interfaceStyle: UIUserInterfaceStyle
    get() = when (this) {
        ThemeMode.SYSTEM -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
        ThemeMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
        ThemeMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
    }
