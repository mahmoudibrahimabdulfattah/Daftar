package com.mk.daftar.desktop

import com.mk.daftar.core.common.desktop.DesktopOs
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import java.awt.Window

/**
 * Keeps the app's windows out of screenshots, screen recordings and screen sharing, the desktop counterpart of
 * Android's FLAG_SECURE. macOS: every window of the app gets `NSWindowSharingNone`. Windows: the window gets
 * `WDA_EXCLUDEFROMCAPTURE` (Windows 10 2004 and later). Calls are idempotent and repeated on every activation, so
 * windows created later (file dialogs) are covered too.
 */
internal object WindowPrivacy {
    /** True once the platform call succeeded at least once for the main window. Read by the release check. */
    @Volatile
    var excluded: Boolean = false
        private set

    fun protect(window: Window) {
        val applied = when (DesktopOs.current) {
            DesktopOs.MAC -> runCatching { MacWindows.excludeAllFromCapture() }.getOrDefault(false)
            DesktopOs.WINDOWS -> runCatching { WindowsCapture.exclude(window) }.getOrDefault(false)
            DesktopOs.OTHER -> false
        }
        if (applied) excluded = true
    }
}

/** The Objective-C runtime, for the few AppKit calls the window needs. */
@Suppress("FunctionName", "ktlint:standard:function-naming")
private interface ObjC : Library {
    fun objc_getClass(name: String): Pointer?

    fun sel_registerName(name: String): Pointer

    // objc_msgSend is not variadic on arm64: each call shape gets its own fixed-arity declaration.
    fun objc_msgSend(receiver: Pointer?, selector: Pointer): Pointer?

    fun objc_msgSend(receiver: Pointer?, selector: Pointer, argument: Long): Pointer?

    fun objc_msgSend(receiver: Pointer?, selector: Pointer, first: Pointer, second: Pointer?, third: Boolean): Pointer?

    companion object {
        val runtime: ObjC by lazy { Native.load("objc", ObjC::class.java) }
    }
}

private object MacWindows {
    fun excludeAllFromCapture(): Boolean {
        val objc = ObjC.runtime
        val application =
            objc.objc_msgSend(objc.objc_getClass("NSApplication"), objc.sel_registerName("sharedApplication"))
                ?: return false
        val windows = objc.objc_msgSend(application, objc.sel_registerName("windows")) ?: return false
        val count = Pointer.nativeValue(objc.objc_msgSend(windows, objc.sel_registerName("count")))
        val setSharingType = objc.sel_registerName("setSharingType:")
        val onMainThread = objc.sel_registerName("performSelectorOnMainThread:withObject:waitUntilDone:")
        for (index in 0 until count) {
            val window = objc.objc_msgSend(windows, objc.sel_registerName("objectAtIndex:"), index) ?: continue
            // AppKit windows must be changed on the main thread. A nil object is the integer 0, NSWindowSharingNone.
            // The call is queued, never awaited: the main thread may itself be waiting on this (AWT) thread, for
            // example for accessibility, and waiting here would freeze both until AWT's timeout.
            objc.objc_msgSend(window, onMainThread, setSharingType, null, false)
        }
        return count > 0
    }
}

@Suppress("FunctionName", "ktlint:standard:function-naming")
private interface User32Capture : Library {
    fun SetWindowDisplayAffinity(window: HWND, affinity: Int): Boolean

    companion object {
        val user32: User32Capture by lazy { Native.load("user32", User32Capture::class.java) }
    }
}

private object WindowsCapture {
    /** WDA_EXCLUDEFROMCAPTURE: the window is shown on screen but absent from every capture. */
    private const val WDA_EXCLUDEFROMCAPTURE = 0x00000011

    fun exclude(window: Window): Boolean {
        val handle = HWND(Native.getWindowPointer(window) ?: return false)
        return User32Capture.user32.SetWindowDisplayAffinity(handle, WDA_EXCLUDEFROMCAPTURE)
    }
}
