package com.mk.daftar.platform

import java.awt.Frame
import java.util.Locale

/**
 * The one vault window, registered by the desktop launcher. Native dialogs (Save, Open) are parented to it, so they
 * stay in front of the app and close with it.
 */
object DesktopWindowHost {
    @Volatile
    var frame: Frame? = null
        internal set

    /** Registers [frame] as the vault window. Called once by the launcher when the window exists. */
    fun attach(frame: Frame) {
        this.frame = frame
    }

    /** True when the app's current language is Arabic. Native dialog titles follow the app, not the OS. */
    internal val arabic: Boolean get() = Locale.getDefault().language == "ar"
}
