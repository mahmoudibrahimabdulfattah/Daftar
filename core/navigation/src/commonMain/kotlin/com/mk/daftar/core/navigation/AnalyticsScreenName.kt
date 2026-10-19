package com.mk.daftar.core.navigation

import androidx.navigation3.runtime.NavKey

/**
 * The fixed name a screen is reported under. Literal names only: never `::class.simpleName` (release shrinking renames
 * classes), and never route arguments such as identifiers, amounts or years. Unknown keys report as `other`.
 */
fun analyticsScreenName(route: NavKey): String = when (route) {
    HomeRoute -> "home"
    AssetsRoute -> "assets"
    JournalRoute -> "journal"
    SettingsRoute -> "settings"
    PreferencesRoute -> "preferences"
    ChangePinRoute -> "change_pin"
    PriceDetailsRoute -> "price_details"
    RecoveryKeyRoute -> "recovery_key"
    AboutRoute -> "about"
    BanksRoute -> "banks"
    is BankDetailRoute -> "bank_detail"
    BankAddRoute -> "bank_add"
    is BankEditRoute -> "bank_edit"
    is AccountAddRoute -> "account_add"
    is AccountEditRoute -> "account_edit"
    GoldRoute -> "gold"
    GoldAddRoute -> "gold_add"
    is GoldEditRoute -> "gold_edit"
    CashRoute -> "cash"
    CashAddRoute -> "cash_add"
    is CashEditRoute -> "cash_edit"
    InvestmentsRoute -> "investments"
    is InvestmentDetailRoute -> "investment_detail"
    InvestmentAddRoute -> "investment_add"
    is InvestmentEditRoute -> "investment_edit"
    is ConvertCashRoute -> "convert_cash"
    PricesRoute -> "prices"
    ZakatRoute -> "zakat"
    is ZakatAddRoute -> "zakat_add"
    ZakatCalculatorRoute -> "zakat_calculator"
    ZakatHistoryRoute -> "zakat_history"
    is ZakatYearRoute -> "zakat_year"
    ZakatYearAddRoute -> "zakat_year_add"
    is ZakatDateRoute -> "zakat_date"
    is ZakatEditRoute -> "zakat_edit"
    NotesRoute -> "notes"
    NoteAddRoute -> "note_add"
    is NoteEditRoute -> "note_edit"
    is ManualQuoteRoute -> "manual_quote"
    BackupExportRoute -> "backup_export"
    CloudRoute -> "cloud"
    CloudUploadRoute -> "cloud_upload"
    else -> "other"
}
