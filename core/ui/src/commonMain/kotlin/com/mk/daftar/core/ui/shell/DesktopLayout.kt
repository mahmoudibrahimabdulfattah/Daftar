package com.mk.daftar.core.ui.shell

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarSpacing

/**
 * True only in the desktop app (design phase 28). Phones and tablets never set it, so every desktop arrangement below is
 * a no-op there and their layouts stay exactly as they were.
 */
val LocalDesktopLayout = staticCompositionLocalOf { false }

/** Desktop content widths (design phase 28), measured without the rail and the outer gutters. */
object DesktopWidths {
    /** Lists and single-column pages: Home, Assets, Gold, Cash, Banks, Investments, Journal, Notes, Zakat. */
    val Page: Dp = 720.dp

    /** Reading pages: Prices, the zakat calculator, Settings. */
    val Reading: Dp = 640.dp

    /** Forms and editors. */
    val Editor: Dp = 560.dp

    /** Home's two-column group at the large width. */
    val HomeGroup: Dp = 1120.dp

    /** Home's supporting column at the large width. */
    val HomeSupporting: Dp = 360.dp

    /** The large width class, where Home and Assets use two columns. */
    val Large: Dp = 1200.dp

    /** The outer gutter of a desktop page, from the rail or the window edge to its content. */
    val Gutter: Dp = 24.dp

    /** A list beside its detail, up to [Large]. */
    val ListPane: Dp = 280.dp

    /** A list beside its detail at [Large] and wider. */
    val ListPaneLarge: Dp = 320.dp
}

/**
 * Caps a desktop page at [cap] and centers it in the space beside the rail. Below the rail breakpoint, and on every
 * phone or tablet, it changes nothing.
 */
@Composable
fun Modifier.desktopPage(cap: Dp): Modifier =
    if (LocalDesktopLayout.current && LocalShellNavigation.current == ShellNavigation.RAIL) {
        // The page pads its content by DaftarSpacing.md; the extra gutter makes the outer edge 24dp, and the cap
        // counts only the content inside that padding.
        this.fillMaxWidth()
            .padding(horizontal = DesktopWidths.Gutter - DaftarSpacing.md)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = cap + DaftarSpacing.md * 2)
    } else {
        this
    }

/**
 * A scrolling page with its top bar. On a phone or tablet the bar scrolls with the page, as it always has. On desktop
 * beside the rail the bar spans the whole content area and stays put, as on Assets and Home, and only the page below
 * it is capped at [cap] and scrolls.
 */
@Composable
fun DesktopPageColumn(
    cap: Dp,
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val arrangement = Arrangement.spacedBy(DaftarSpacing.sm)
    // The window can cross the rail breakpoint, so the bar and the page move between the two shapes with their state.
    val bar = remember(topBar) { movableContentOf(topBar) }
    val page = remember(content) { movableContentOf<ColumnScope> { scope -> scope.content() } }
    if (LocalDesktopLayout.current && LocalShellNavigation.current == ShellNavigation.RAIL) {
        Column(modifier = modifier.fillMaxSize()) {
            bar()
            Column(
                modifier = Modifier
                    .desktopPage(cap)
                    .weight(1f)
                    .pageScroll(scrollState)
                    .padding(horizontal = DaftarSpacing.md),
                verticalArrangement = arrangement,
            ) {
                page(this)
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .pageScroll(scrollState)
                .padding(horizontal = DaftarSpacing.md),
            verticalArrangement = arrangement,
        ) {
            bar()
            page(this)
        }
    }
}

/** The window's width, including the rail: the width the design's classes are defined on. */
@Composable
fun windowWidth(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
