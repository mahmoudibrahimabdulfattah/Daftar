package com.mk.daftar.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mk.daftar.App

/**
 * The launcher. Every window of this activity is secure: FLAG_SECURE is set before content, is never toggled per
 * screen, and dialogs created from it inherit it from the window.
 */
class MainActivity : ComponentActivity() {

    private val vaultLifecycle get() = (application as DaftarApplication).vaultLifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        setContent {
            val covered by vaultLifecycle.covered.collectAsState()
            App(showsDebugBanner = BuildVariant.showsDebugBanner, covered = covered)
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

    private companion object {
        const val SURFACE = "main-activity"
    }
}
