package com.mk.daftar.shell

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.nav_assets
import com.mk.daftar.core.designsystem.resources.nav_home
import com.mk.daftar.core.designsystem.resources.nav_journal
import com.mk.daftar.core.designsystem.resources.nav_settings
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.navigation.LocalNavigatorOrNull
import com.mk.daftar.core.navigation.Navigator
import com.mk.daftar.core.navigation.TopLevelDestination
import com.mk.daftar.core.ui.motion.rememberReducedMotion
import com.mk.daftar.core.ui.shell.DaftarAdaptiveShell
import com.mk.daftar.core.ui.shell.DesktopEscape
import com.mk.daftar.core.ui.shell.DesktopWidths
import com.mk.daftar.core.ui.shell.LocalDesktopLayout
import com.mk.daftar.core.ui.shell.ShellDestination
import com.mk.daftar.core.ui.shell.windowWidth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI

/** The duration of a screen change. Reduced motion makes it instant. */
private const val TRANSITION_MILLIS = 180

/**
 * The private shell of one open vault generation: the adaptive frame, four independent stacks, and the entries each
 * feature registers in Koin. Each stack is decorated on its own, so a tab keeps its screens and scroll position while
 * another tab is shown. The registry is the retained one, so state survives configuration changes but never reaches
 * platform saved state.
 */
@OptIn(KoinExperimentalAPI::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DaftarAppShell(navigator: Navigator, retention: ShellRetention, modifier: Modifier = Modifier) {
    val registry = remember(retention) { retention.newRegistry() }
    DisposableEffect(registry) {
        onDispose { retention.savedStates = registry.performSave() }
    }
    val provider = koinEntryProvider<NavKey>()
    val defaultDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    // Desktop (design phase 28): a narrower list (wider at the large width) and a 24dp gap. Phones and tablets keep
    // the platform defaults.
    val directive = if (LocalDesktopLayout.current) {
        val list = if (windowWidth() >= DesktopWidths.Large) DesktopWidths.ListPaneLarge else DesktopWidths.ListPane
        defaultDirective.copy(defaultPanePreferredWidth = list, horizontalPartitionSpacerSize = DesktopWidths.Gutter)
    } else {
        defaultDirective
    }
    val listDetail = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    val reducedMotion = rememberReducedMotion()
    // Esc on desktop goes back within the shown tab, with the draft guard; at a tab's root it does nothing.
    DisposableEffect(navigator) {
        val onEscape = {
            val nested = navigator.stackOf(navigator.topLevel).size > 1
            if (nested) navigator.requestLeave(LeaveRequest.Back)
            nested
        }
        DesktopEscape.install(onEscape)
        onDispose { DesktopEscape.uninstall(onEscape) }
    }
    val duration = if (reducedMotion) 0 else TRANSITION_MILLIS

    CompositionLocalProvider(
        LocalSaveableStateRegistry provides registry,
        LocalNavigator provides navigator,
        LocalNavigatorOrNull provides navigator,
    ) {
        val home = decoratedEntries(navigator, TopLevelDestination.HOME, provider)
        val assets = decoratedEntries(navigator, TopLevelDestination.ASSETS, provider)
        val journal = decoratedEntries(navigator, TopLevelDestination.JOURNAL, provider)
        val settings = decoratedEntries(navigator, TopLevelDestination.SETTINGS, provider)
        val visible = when (navigator.topLevel) {
            TopLevelDestination.HOME -> home
            TopLevelDestination.ASSETS -> home + assets
            TopLevelDestination.JOURNAL -> home + journal
            TopLevelDestination.SETTINGS -> home + settings
        }
        DaftarAdaptiveShell(
            destinations = shellDestinations(),
            selectedIndex = navigator.topLevel.ordinal,
            onSelect = { index -> navigator.reselect(TopLevelDestination.entries[index]) },
            modifier = modifier,
        ) {
            NavDisplay(
                entries = visible,
                onBack = { navigator.requestLeave(LeaveRequest.Back) },
                sceneStrategies = listOf(listDetail),
                transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                popTransitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
            )
        }
    }
}

@Composable
private fun decoratedEntries(
    navigator: Navigator,
    destination: TopLevelDestination,
    provider: (NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> = rememberDecoratedNavEntries(
    backStack = navigator.stackOf(destination),
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    entryProvider = provider,
)

@Composable
private fun shellDestinations(): List<ShellDestination> = listOf(
    ShellDestination(stringResource(DesignRes.string.nav_home), DaftarIcons.Home),
    ShellDestination(stringResource(DesignRes.string.nav_assets), DaftarIcons.Gold),
    ShellDestination(stringResource(DesignRes.string.nav_journal), DaftarIcons.Note),
    ShellDestination(stringResource(DesignRes.string.nav_settings), DaftarIcons.Settings),
)
