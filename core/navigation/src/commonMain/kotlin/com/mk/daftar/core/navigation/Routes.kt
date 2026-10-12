package com.mk.daftar.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every destination's key. Arguments carry identifiers only: never balances, names or other private values. Features
 * register a screen for their keys and navigate to other features through these keys, never by depending on them.
 */
@Serializable
data object HomeRoute : NavKey

@Serializable
data object AssetsRoute : NavKey

@Serializable
data object JournalRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

/** The bank list inside Assets. It is the list pane when the window is wide enough for two panes. */
@Serializable
data object BanksRoute : NavKey

/** One bank, shown in the detail pane beside [BanksRoute] or as its own page on narrow windows. */
@Serializable
data class BankDetailRoute(val bankId: String) : NavKey

/** The four top-level destinations, in display order. Each owns an independent back stack rooted at its key. */
enum class TopLevelDestination(val route: NavKey) {
    HOME(HomeRoute),
    ASSETS(AssetsRoute),
    JOURNAL(JournalRoute),
    SETTINGS(SettingsRoute),
}
