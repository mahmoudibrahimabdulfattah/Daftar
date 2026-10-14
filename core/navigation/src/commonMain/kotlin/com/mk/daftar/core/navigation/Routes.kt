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

/** Theme, language and the inactivity timeout, reached from Settings. Choices apply at once and persist. */
@Serializable
data object PreferencesRoute : NavKey

/** Changing the Daftar PIN. Its stages are internal state of the screen, never route arguments. */
@Serializable
data object ChangePinRoute : NavKey

/** Creating or replacing the recovery key. */
@Serializable
data object RecoveryKeyRoute : NavKey

/** The app's version, the bundled font licenses and their source links. */
@Serializable
data object AboutRoute : NavKey

/** The bank list inside Assets. It is the list pane when the window is wide enough for two panes. */
@Serializable
data object BanksRoute : NavKey

/** One bank, shown in the detail pane beside [BanksRoute] or as its own page on narrow windows. */
@Serializable
data class BankDetailRoute(val bankId: String) : NavKey

/** A new bank, from the bank list. Its name is the only field. */
@Serializable
data object BankAddRoute : NavKey

/** One bank's name, edited or deleted. */
@Serializable
data class BankEditRoute(val bankId: String) : NavKey

/** A new account inside [bankId]. Currency and balance are entered on the form. */
@Serializable
data class AccountAddRoute(val bankId: String) : NavKey

/** One account, edited, moved to another bank, or deleted. */
@Serializable
data class AccountEditRoute(val accountId: String) : NavKey

/** Gold holdings, reached from Home and Assets. */
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

/** Investment accounts, the broker balances inside Assets. The list pane when the window is wide enough for two panes. */
@Serializable
data object InvestmentsRoute : NavKey

/** One investment account: its current value, its value date and the actions on it. Identifiers only. */
@Serializable
data class InvestmentDetailRoute(val investmentId: String) : NavKey

/** A new investment account: the broker is chosen first, then the form. */
@Serializable
data object InvestmentAddRoute : NavKey

/** One investment account, edited or deleted. */
@Serializable
data class InvestmentEditRoute(val investmentId: String) : NavKey

/**
 * Converts one EGP cash entry into an investment account by explicit choice. [sourceId] preselects the entry, such as
 * from the cash entry's own action; null starts at the entry picker.
 */
@Serializable
data class ConvertCashRoute(val sourceId: String? = null) : NavKey

/** Merchant quotes, their sources and the global estimate. Reached from Home. */
@Serializable
data object PricesRoute : NavKey

/** The zakat log inside Journal: the list of recorded payments. */
@Serializable
data object ZakatRoute : NavKey

/**
 * A new zakat payment. [prefillAmount] is an editable draft from the calculator, passed only while values are visible
 * and dropped with every other route on lock. [forHijriYear] preselects the year; null lets the editor choose.
 */
@Serializable
data class ZakatAddRoute(val prefillAmount: String? = null, val forHijriYear: Int? = null) : NavKey

/** The zakat calculator: the next Ramadan and today's planning estimate. Reached from Journal and Home. */
@Serializable
data object ZakatCalculatorRoute : NavKey

/** Yearly status: every tracked year with its declaration. Reached when several years await confirmation. */
@Serializable
data object ZakatHistoryRoute : NavKey

/** One declared year, keyed by its Hijri year of Ramadan: its declaration, payments and date. */
@Serializable
data class ZakatYearRoute(val hijriYear: Int) : NavKey

/** Adds a past or advance year to tracking, by its Hijri year. */
@Serializable
data object ZakatYearAddRoute : NavKey

/** The Ramadan date of one declared year, with its -1, 0 or +1 day adjustment. */
@Serializable
data class ZakatDateRoute(val hijriYear: Int) : NavKey

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

/** The encrypted export: one password-protected copy of the vault. Reached from Settings and never a top-level tab. */
@Serializable
data object BackupExportRoute : NavKey

/** Cloud backups: sign-in, the three stored versions and deletion. Reached from Settings and never a top-level tab. */
@Serializable
data object CloudRoute : NavKey

/** The cloud upload form: sign-in when needed, then the backup password and one encrypted upload. */
@Serializable
data object CloudUploadRoute : NavKey

/** The four top-level destinations, in display order. Each owns an independent back stack rooted at its key. */
enum class TopLevelDestination(val route: NavKey) {
    HOME(HomeRoute),
    ASSETS(AssetsRoute),
    JOURNAL(JournalRoute),
    SETTINGS(SettingsRoute),
}
