package com.mk.daftar.android

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mk.daftar.App

/**
 * The launcher. Every window of this activity is secure: FLAG_SECURE is set before content, is never toggled per
 * screen, and dialogs created from it inherit it from the window.
 *
 * It is an AppCompat activity so that the in-app language applies on every supported API level. Recreating it for a
 * language or configuration change keeps the open vault: the lifecycle coordinator and the shell state live in the
 * application and in the view model, not in this activity.
 */
class MainActivity : AppCompatActivity() {

    private val vaultLifecycle get() = (application as DaftarApplication).vaultLifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash is the static starting theme; it never waits for the vault or the network.
        installSplashScreen()
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        setContent {
            val covered by vaultLifecycle.covered.collectAsState()
            App(
                showsDebugBanner = BuildVariant.showsDebugBanner,
                developerTools = BuildVariant.developerTools,
                covered = covered,
                onThemeChange = { _, dark -> applySystemBars(dark) },
            )
        }
    }

    override fun onPause() {
        vaultLifecycle.obscure(SURFACE)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        vaultLifecycle.reveal(SURFACE)
    }

    override fun onStop() {
        vaultLifecycle.stopped(changingConfigurations = isChangingConfigurations)
        super.onStop()
    }

    /** System bar icons follow the app's theme, which may differ from the device's. */
    private fun applySystemBars(dark: Boolean) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private companion object {
        const val SURFACE = "main-activity"
    }
}
