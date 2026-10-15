package com.mk.daftar.core.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mk.daftar.core.common.desktop.DesktopOs

/** macOS "Reduce motion" (Accessibility > Display), read once per composition. Other systems report no preference. */
@Composable
actual fun rememberReducedMotion(): Boolean = remember { DesktopOs.current == DesktopOs.MAC && macReduceMotion() }

private fun macReduceMotion(): Boolean = try {
    val process = ProcessBuilder("/usr/bin/defaults", "read", "com.apple.universalaccess", "reduceMotion")
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    process.waitFor() == 0 && output == "1"
} catch (ignored: Exception) {
    false
}
