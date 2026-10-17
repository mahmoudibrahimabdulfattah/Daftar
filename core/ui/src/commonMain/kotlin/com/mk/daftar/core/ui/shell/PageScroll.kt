package com.mk.daftar.core.ui.shell

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mk.daftar.core.designsystem.theme.DaftarSpacing

/** The scroll state of the page shown in one [ShellScreen], so the screen can draw its desktop scrollbar. */
class PageScrollHolder {
    var state: ScrollState? by mutableStateOf(null)
}

/** The holder of the enclosing [ShellScreen]; null outside one. */
val LocalPageScroll = staticCompositionLocalOf<PageScrollHolder?> { null }

/**
 * The page's own vertical scroll. Exactly `verticalScroll(state)`, and on desktop the enclosing [ShellScreen] also
 * shows a scrollbar for it at its logical end. Use it once per screen, on the main scrolling column; dialogs and
 * nested lists keep plain `verticalScroll`.
 */
@Composable
fun Modifier.pageScroll(state: ScrollState): Modifier {
    val holder = LocalPageScroll.current
    if (holder != null) {
        DisposableEffect(holder, state) {
            holder.state = state
            onDispose { if (holder.state === state) holder.state = null }
        }
    }
    return verticalScroll(state)
}

/**
 * Hosts one page for [pageScroll]: on desktop the page's scrollbar sits at this host's logical end, below the top
 * bar's lane. [ShellScreen] is one; pages drawn outside it wrap themselves. On phones it only lays out [content].
 */
@Composable
fun PageScrollHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val holder = remember { PageScrollHolder() }
    Box(modifier = modifier) {
        CompositionLocalProvider(LocalPageScroll provides holder) {
            content()
        }
        val scroll = holder.state
        if (scroll != null && LocalDesktopLayout.current) {
            PageScrollbar(
                state = scroll,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = DaftarSpacing.topBarMinHeight, bottom = DaftarSpacing.xs),
            )
        }
    }
}

/**
 * A scrollbar for [state] on desktop: 4dp at rest and 8dp while hovered or dragged, in `line` / `muted`, shown only
 * while the page overflows. Nothing on phones, where the system scrolling indicators apply.
 */
@Composable
expect fun PageScrollbar(state: ScrollState, modifier: Modifier = Modifier)
