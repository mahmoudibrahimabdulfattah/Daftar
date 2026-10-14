package com.mk.daftar.desktop

import com.mk.daftar.lifecycle.VaultLifecycleCoordinator
import java.awt.Desktop
import java.awt.desktop.AppHiddenEvent
import java.awt.desktop.AppHiddenListener
import java.awt.desktop.ScreenSleepEvent
import java.awt.desktop.ScreenSleepListener
import java.awt.desktop.SystemSleepEvent
import java.awt.desktop.SystemSleepListener
import java.awt.desktop.UserSessionEvent
import java.awt.desktop.UserSessionListener
import javax.swing.SwingUtilities

/**
 * System events that must lock the vault: the app being hidden (macOS Cmd+H), the display or the system going to sleep,
 * and the user session becoming inactive (screen lock, fast user switching). Each one covers the content, then locks;
 * the matching return reveals the lock screen. Platforms that do not deliver an event simply never call it, and the
 * inactivity timer still applies.
 */
internal object DesktopSystemEvents {
    private const val SURFACE = "desktop-system"

    fun install(coordinator: VaultLifecycleCoordinator) {
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        fun away() = SwingUtilities.invokeLater {
            coordinator.obscure(SURFACE)
            coordinator.enteredBackground()
        }
        fun back() = SwingUtilities.invokeLater { coordinator.reveal(SURFACE) }
        runCatching {
            desktop.addAppEventListener(
                object : AppHiddenListener {
                    override fun appHidden(event: AppHiddenEvent) = away()

                    override fun appUnhidden(event: AppHiddenEvent) = back()
                },
            )
        }
        runCatching {
            desktop.addAppEventListener(
                object : ScreenSleepListener {
                    override fun screenAboutToSleep(event: ScreenSleepEvent) = away()

                    override fun screenAwoke(event: ScreenSleepEvent) = back()
                },
            )
        }
        runCatching {
            desktop.addAppEventListener(
                object : SystemSleepListener {
                    override fun systemAboutToSleep(event: SystemSleepEvent) = away()

                    override fun systemAwoke(event: SystemSleepEvent) = back()
                },
            )
        }
        runCatching {
            desktop.addAppEventListener(
                object : UserSessionListener {
                    override fun userSessionDeactivated(event: UserSessionEvent) = away()

                    override fun userSessionActivated(event: UserSessionEvent) = back()
                },
            )
        }
    }
}
