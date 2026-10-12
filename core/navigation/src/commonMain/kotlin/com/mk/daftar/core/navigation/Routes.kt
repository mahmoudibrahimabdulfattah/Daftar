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

/** Gold holdings, reached from Home and Assets. A placeholder until the gold feature phase replaces its entry. */
@Serializable
data object GoldRoute : NavKey

/** Cash and wallet balances, reached from Home and Assets. A placeholder until the cash feature phase. */
@Serializable
data object CashRoute : NavKey

/** Merchant quotes and their sources. A neutral placeholder entry until the prices phase replaces it. */
@Serializable
data object PricesRoute : NavKey

/** The zakat log inside Journal: the list of recorded payments. */
@Serializable
data object ZakatRoute : NavKey

/** A new zakat payment. */
@Serializable
data object ZakatAddRoute : NavKey

/** One recorded zakat payment, edited or deleted. */
@Serializable
data class ZakatEditRoute(val paymentId: String) : NavKey

/** The personal notes list inside Journal. */
@Serializable
data object NotesRoute : NavKey

/** A new personal note. */
@Serializable
data object NoteAddRoute : NavKey

/** One personal note, edited or deleted. */
@Serializable
data class NoteEditRoute(val noteId: String) : NavKey

/** The four top-level destinations, in display order. Each owns an independent back stack rooted at its key. */
enum class TopLevelDestination(val route: NavKey) {
    HOME(HomeRoute),
    ASSETS(AssetsRoute),
    JOURNAL(JournalRoute),
    SETTINGS(SettingsRoute),
}
