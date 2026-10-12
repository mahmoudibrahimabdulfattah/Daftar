package com.mk.daftar.core.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The navigator of the shell that is composing. Entries read it to navigate, so no screen depends on how the
 * navigator was created or which generation owns it.
 */
val LocalNavigator: ProvidableCompositionLocal<Navigator> = staticCompositionLocalOf {
    error("No Navigator is provided; compose screens inside the Daftar shell")
}
