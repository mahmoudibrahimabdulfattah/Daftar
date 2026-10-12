package com.mk.daftar.core.ui.shell

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Where the top-level destinations sit. The bottom bar is for narrow windows only; the rail is at logical start. */
enum class ShellNavigation { BOTTOM, RAIL }

/** Below this width the bottom bar carries the destinations; from it on, the start-side rail does. */
val RailBreakpoint: Dp = 600.dp

/** From this width the bank list and its detail sit side by side when both fit. */
val SimultaneousPanesWidth: Dp = 840.dp

/** The navigation for a window of [width]. It measures the window, not the device type or orientation. */
fun shellNavigationFor(width: Dp): ShellNavigation = if (width <
    RailBreakpoint
) {
    ShellNavigation.BOTTOM
} else {
    ShellNavigation.RAIL
}

/** Whether a list and its detail may share the window. Scene strategies decide the actual panes. */
fun simultaneousPanesFor(width: Dp): Boolean = width >= SimultaneousPanesWidth

/** One top-level destination as the shell shows it. The label is already localized. */
data class ShellDestination(val label: String, val icon: ImageVector)

/** The navigation the shell chose, for screens that adapt their own chrome, such as a detail's back arrow. */
val LocalShellNavigation = staticCompositionLocalOf { ShellNavigation.BOTTOM }

/** True while a list and a detail can be shown at once; the detail then needs no back arrow. */
val LocalSimultaneousPanes = staticCompositionLocalOf { false }
