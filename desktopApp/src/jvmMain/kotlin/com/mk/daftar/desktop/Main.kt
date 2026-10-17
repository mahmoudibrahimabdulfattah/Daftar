package com.mk.daftar.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mk.daftar.App
import com.mk.daftar.core.account.desktop.DesktopCloudConfiguration
import com.mk.daftar.core.account.di.desktopCloudConfigurationModule
import com.mk.daftar.core.common.desktop.DesktopAppDirectories
import com.mk.daftar.core.common.desktop.DesktopOs
import com.mk.daftar.core.designsystem.component.DaftarFocusVisibility
import com.mk.daftar.core.security.DesktopSecurityConfiguration
import com.mk.daftar.core.security.desktopSecurityConfigurationModule
import com.mk.daftar.core.ui.shell.DesktopEscape
import com.mk.daftar.core.ui.shell.DesktopPageKeys
import com.mk.daftar.di.DesktopBuildConfiguration
import com.mk.daftar.di.desktopBuildConfigurationModule
import com.mk.daftar.di.initKoin
import com.mk.daftar.lifecycle.VaultLifecycleCoordinator
import com.mk.daftar.platform.DesktopWindowHost
import java.awt.AWTEvent
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.util.Locale
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The desktop entry point. The private directory and the key policy are fixed before dependency injection starts,
 * from the build flavor compiled into this binary; nothing at run time can switch a release to debug key handling.
 */
fun main() {
    val debug = DesktopBuildFlavor.DEBUG
    DesktopAppDirectories.configure(if (debug) "Daftar Debug" else "Daftar")
    initKoin {
        modules(
            desktopSecurityConfigurationModule(
                if (debug) {
                    DesktopSecurityConfiguration.AllowSoftwareForDebug
                } else {
                    DesktopSecurityConfiguration.RequireHardware
                },
            ),
            desktopCloudModules(),
            desktopBuildConfigurationModule(
                DesktopBuildConfiguration(
                    versionName = DesktopBuildFlavor.VERSION,
                    buildNumber = DesktopBuildFlavor.VERSION,
                    debug = debug,
                ),
            ),
        )
    }
    val vaultLifecycle: VaultLifecycleCoordinator = GlobalContext.get().get()
    vaultLifecycle.start()
    DesktopSystemEvents.install(vaultLifecycle)

    application {
        val geometry = WindowGeometry.restore()
        Window(
            onCloseRequest = {
                WindowGeometry.save(geometry)
                exitApplication()
            },
            state = geometry,
            title = if (Locale.getDefault().language == "ar") "دفتر" else "Daftar",
            // Reaches the window only when no focused control used the key, so popups and fields go first.
            onKeyEvent = { event ->
                event.type == KeyEventType.KeyDown &&
                    if (event.key == Key.Escape) DesktopEscape.press() else DesktopPageKeys.press(event.key)
            },
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key in FOCUS_KEYS) DaftarFocusVisibility.keyboard = true
                val lockShortcut = event.type == KeyEventType.KeyDown && event.key == Key.L &&
                    if (DesktopOs.current == DesktopOs.MAC) event.isMetaPressed else event.isCtrlPressed
                if (lockShortcut) vaultLifecycle.lockForInactivity()
                lockShortcut
            },
        ) {
            DisposableEffect(window) {
                window.minimumSize = Dimension(MIN_WIDTH_DP, MIN_HEIGHT_DP)
                DesktopWindowHost.attach(window)
                WindowPrivacy.protect(window)
                val lifecycle = WindowLifecycle.attach(window, vaultLifecycle)
                onDispose { lifecycle.detach() }
            }
            DisposableEffect(window) {
                // A click hides focus rings until the keyboard moves focus again.
                val pointer = AWTEventListener { event ->
                    if (event.id == MouseEvent.MOUSE_PRESSED) DaftarFocusVisibility.keyboard = false
                }
                Toolkit.getDefaultToolkit().addAWTEventListener(pointer, AWTEvent.MOUSE_EVENT_MASK)
                onDispose { Toolkit.getDefaultToolkit().removeAWTEventListener(pointer) }
            }
            val covered by vaultLifecycle.covered.collectAsState()
            App(
                showsDebugBanner = debug,
                developerTools = debug,
                covered = covered,
                desktopLayout = true,
            )
        }
    }
}

/** The keys that move focus. */
private val FOCUS_KEYS = setOf(Key.Tab, Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)

/** The cloud configuration compiled into this build, when it has one. */
private fun desktopCloudModules(): Module {
    val apiKey = DesktopBuildFlavor.CLOUD_API_KEY
    val projectId = DesktopBuildFlavor.CLOUD_PROJECT_ID
    val clientId = DesktopBuildFlavor.CLOUD_OAUTH_CLIENT_ID
    val clientSecret = DesktopBuildFlavor.CLOUD_OAUTH_CLIENT_SECRET
    if (apiKey == null || projectId == null || clientId == null || clientSecret == null) return module { }
    return desktopCloudConfigurationModule(DesktopCloudConfiguration(apiKey, projectId, clientId, clientSecret))
}

/** The smallest client area the layouts are designed for (design phase 28). */
internal const val MIN_WIDTH_DP = 720
internal const val MIN_HEIGHT_DP = 600

/** The first-launch client area. */
internal val DEFAULT_WIDTH = 1200.dp
internal val DEFAULT_HEIGHT = 800.dp
