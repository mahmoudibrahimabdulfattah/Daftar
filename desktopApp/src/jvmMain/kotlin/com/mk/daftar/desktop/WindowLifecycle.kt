package com.mk.daftar.desktop

import com.mk.daftar.lifecycle.VaultLifecycleCoordinator
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * Turns the window's state into the vault's privacy state. Minimizing covers the content first and then locks, like an
 * app going to the background on a phone. Restoring lifts the cover once the lock has finished, onto the lock screen.
 * Every activation also re-applies screen-capture exclusion, so dialogs opened later are excluded too.
 */
internal class WindowLifecycle private constructor(
    private val window: Window,
    private val coordinator: VaultLifecycleCoordinator,
) : WindowAdapter() {

    override fun windowIconified(event: WindowEvent) {
        coordinator.obscure(SURFACE)
        coordinator.enteredBackground()
    }

    override fun windowDeiconified(event: WindowEvent) {
        coordinator.reveal(SURFACE)
    }

    override fun windowActivated(event: WindowEvent) {
        WindowPrivacy.protect(window)
    }

    fun detach() {
        window.removeWindowListener(this)
    }

    companion object {
        /** The surface name the coordinator tracks for the main window. */
        const val SURFACE = "desktop-window"

        fun attach(window: Window, coordinator: VaultLifecycleCoordinator): WindowLifecycle =
            WindowLifecycle(window, coordinator).also { window.addWindowListener(it) }
    }
}
