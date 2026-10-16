package com.mk.daftar.core.ui.shell

/**
 * Esc on desktop: the window passes it here only when nothing focused used it (a popup closes itself first), and the
 * open vault's shell answers with Back, under the same unsaved-draft guard as the Back button. With no shell (locked)
 * or at a tab's root it does nothing; it never quits or unlocks.
 */
object DesktopEscape {
    private var handler: (() -> Boolean)? = null

    /** Installs [onEscape] for the shell that is showing; it returns whether it went back. */
    fun install(onEscape: () -> Boolean) {
        handler = onEscape
    }

    /** Removes [onEscape] if it is still the installed one. */
    fun uninstall(onEscape: () -> Boolean) {
        if (handler === onEscape) handler = null
    }

    /** Runs the installed handler. False when there is none or it had nowhere to go. */
    fun press(): Boolean = handler?.invoke() ?: false
}
