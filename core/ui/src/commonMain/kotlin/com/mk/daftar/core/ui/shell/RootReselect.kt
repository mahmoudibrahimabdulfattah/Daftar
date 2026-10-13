package com.mk.daftar.core.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.mk.daftar.core.navigation.LocalNavigatorOrNull
import com.mk.daftar.core.navigation.TopLevelDestination

/**
 * Scrolls a tab's root to the top when the tab is reselected at its root. The tick is read at composition, so a root
 * that is shown later, after an earlier reselect, does not scroll on its own. [scrollToTop] runs once per tick.
 */
@Composable
fun RootReselectScroll(destination: TopLevelDestination, scrollToTop: suspend () -> Unit) {
    val navigator = LocalNavigatorOrNull.current ?: return
    val tick = navigator.rootReselectTick(destination)
    val handled = remember { mutableIntStateOf(tick) }
    val latest by rememberUpdatedState(scrollToTop)
    LaunchedEffect(tick) {
        if (tick != handled.intValue) {
            handled.intValue = tick
            latest()
        }
    }
}
