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

/** Gold holdings, reached from Home and Assets: the holdings list with its category valuation. */
@Serializable
data object GoldRoute : NavKey

/** A new gold holding. */
@Serializable
data object GoldAddRoute : NavKey

/** One gold holding, edited or deleted. */
@Serializable
data class GoldEditRoute(val holdingId: String) : NavKey

/** Cash and wallet balances, reached from Home and Assets. The list of the cash feature. */
@Serializable
data object CashRoute : NavKey

/** A new cash or wallet balance. */
@Serializable
data object CashAddRoute : NavKey

/** One cash or wallet balance, edited or deleted. */
@Serializable
data class CashEditRoute(val balanceId: String) : NavKey

/** Merchant quotes, their sources and the global estimate. Reached from Home. */
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

/**
 * The manual quote editor. [quoteKey] is a stable token for one enterable quote, such as `gold-21-sell` or `usd-egp`,
 * decoded by the prices feature. Null opens the editor with the quote still to be chosen. An unknown token is shown as
 * a missing quote, never as another quote.
 */
@Serializable
data class ManualQuoteRoute(val quoteKey: String? = null) : NavKey

/** The four top-level destinations, in display order. Each owns an independent back stack rooted at its key. */
enum class TopLevelDestination(val route: NavKey) {
    HOME(HomeRoute),
    ASSETS(AssetsRoute),
    JOURNAL(JournalRoute),
    SETTINGS(SettingsRoute),
}
