package com.mk.daftar.core.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import com.mk.daftar.core.designsystem.component.DaftarNavigationBar
import com.mk.daftar.core.designsystem.component.DaftarNavigationItem
import com.mk.daftar.core.designsystem.component.DaftarNavigationRail
import com.mk.daftar.core.designsystem.component.DaftarNavigationRailItem
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/**
 * The adaptive frame around the content. Below [RailBreakpoint] the design system's bottom bar sits at the bottom edge
 * and hides while the keyboard is up; from [RailBreakpoint] its rail sits at the logical start. Only this frame owns the
 * navigation's system-bar padding and the keyboard inset. [content] keeps one place in the tree whichever navigation
 * is shown, so its state survives resizing across the breakpoint.
 */
@Composable
fun DaftarAdaptiveShell(
    destinations: List<ShellDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val navigation = shellNavigationFor(maxWidth)
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        CompositionLocalProvider(
            LocalShellNavigation provides navigation,
            LocalSimultaneousPanes provides simultaneousPanesFor(maxWidth),
        ) {
            ShellFrame(
                navigation = navigation,
                navigationVisible = navigation == ShellNavigation.RAIL || !keyboardVisible,
                navigationContent = {
                    ShellNavigationContent(navigation, destinations, selectedIndex, onSelect)
                },
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime),
                content = content,
            )
        }
    }
}

@Composable
private fun ShellFrame(
    navigation: ShellNavigation,
    navigationVisible: Boolean,
    navigationContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.fillMaxSize()) { content() }
            if (navigationVisible) navigationContent()
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val mainMeasurable = measurables[0]
        val navMeasurable = measurables.getOrNull(1)
        when (navigation) {
            ShellNavigation.BOTTOM -> {
                val nav = navMeasurable?.measure(Constraints(maxWidth = width))
                val navHeight = nav?.height ?: 0
                val main = mainMeasurable.measure(Constraints.fixed(width, (height - navHeight).coerceAtLeast(0)))
                layout(width, height) {
                    main.place(0, 0)
                    nav?.place(0, height - navHeight)
                }
            }

            ShellNavigation.RAIL -> {
                val nav = navMeasurable?.measure(Constraints(maxHeight = height))
                val navWidth = nav?.width ?: 0
                val main = mainMeasurable.measure(Constraints.fixed((width - navWidth).coerceAtLeast(0), height))
                val rtl = layoutDirection == LayoutDirection.Rtl
                layout(width, height) {
                    main.place(if (rtl) 0 else navWidth, 0)
                    nav?.place(if (rtl) width - navWidth else 0, 0)
                }
            }
        }
    }
}

@Composable
private fun ShellNavigationContent(
    navigation: ShellNavigation,
    destinations: List<ShellDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    when (navigation) {
        ShellNavigation.BOTTOM -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarTheme.colors.surface)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        ) {
            DaftarNavigationBar {
                destinations.forEachIndexed { index, destination ->
                    DaftarNavigationItem(
                        label = destination.label,
                        icon = destination.icon,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                    )
                }
            }
        }

        ShellNavigation.RAIL -> Box(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Start,
                    ),
                ),
        ) {
            DaftarNavigationRail {
                destinations.forEachIndexed { index, destination ->
                    DaftarNavigationRailItem(
                        label = destination.label,
                        icon = destination.icon,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

/**
 * The inset owner of one screen. Its top edge clears the status bar and display cut-outs, and in the rail layout its
 * bottom clears the gesture bar, because the rail does not reach the bottom of the content. The bottom bar already
 * sits above that inset, so it is never applied twice. Lists still take their own content padding.
 */
@Composable
fun ShellScreen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val rail = LocalShellNavigation.current == ShellNavigation.RAIL
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DaftarTheme.colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .then(
                if (rail) {
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}
