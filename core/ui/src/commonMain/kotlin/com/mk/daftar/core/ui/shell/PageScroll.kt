package com.mk.daftar.core.ui.shell

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The scroll state of the page shown in one [ShellScreen], so the screen can draw its desktop scrollbar. */
class PageScrollHolder {
    var state: ScrollState? by mutableStateOf(null)

    /** The visible height of the page, in pixels; a page key moves by most of it. */
    var viewport: Int = 0

    internal var scope: CoroutineScope? = null

    /**
     * Page Up / Page Down move by 90% of the visible height, Home and End to the top and bottom. False for any other
     * key, or when nothing scrolls.
     */
    fun scrollFor(key: Key): Boolean {
        val scroll = state ?: return false
        val coroutines = scope ?: return false
        val step = viewport * PAGE_FRACTION
        when (key) {
            Key.PageDown -> coroutines.launch { scroll.animateScrollBy(step) }
            Key.PageUp -> coroutines.launch { scroll.animateScrollBy(-step) }
            Key.MoveHome -> coroutines.launch { scroll.animateScrollTo(0) }
            Key.MoveEnd -> coroutines.launch { scroll.animateScrollTo(scroll.maxValue) }
            else -> return false
        }
        return true
    }
}

/**
 * Page keys on desktop. The focused page takes them first (see [pageScroll]); when nothing focused used them, the window
 * hands them here for the page under the pointer, the same page the wheel scrolls.
 */
object DesktopPageKeys {
    private var hovered: PageScrollHolder? = null

    internal fun enter(holder: PageScrollHolder) {
        hovered = holder
    }

    internal fun leave(holder: PageScrollHolder) {
        if (hovered === holder) hovered = null
    }

    /** Scrolls the hovered page for a key-down of [key]. */
    fun press(key: Key): Boolean = hovered?.scrollFor(key) ?: false
}

private const val PAGE_FRACTION = 0.9f

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
    if (holder == null || !LocalDesktopLayout.current) return verticalScroll(state)
    // Desktop: Page Up/Down, Home and End scroll this page when focus is inside it and no field used the key first.
    return onSizeChanged { holder.viewport = it.height }
        .onKeyEvent { event -> event.type == KeyEventType.KeyDown && holder.scrollFor(event.key) }
        .verticalScroll(state)
}

/**
 * Hosts one page for [pageScroll]: on desktop the page's scrollbar sits at this host's logical end, below the top
 * bar's lane. [ShellScreen] is one; pages drawn outside it wrap themselves. On phones it only lays out [content].
 */
@Composable
fun PageScrollHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val holder = remember { PageScrollHolder() }
    holder.scope = rememberCoroutineScope()
    DisposableEffect(holder) { onDispose { DesktopPageKeys.leave(holder) } }
    Box(
        // The page under the pointer takes page keys nothing focused used. Observed in the initial pass, never consumed.
        modifier = modifier.pointerInput(holder) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) {
                        DesktopPageKeys.enter(holder)
                    }
                }
            }
        },
    ) {
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
