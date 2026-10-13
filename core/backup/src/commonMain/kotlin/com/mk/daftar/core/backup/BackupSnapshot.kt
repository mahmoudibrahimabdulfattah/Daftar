package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatYear

/**
 * The non-sensitive preferences a backup carries. The biometric flag is absent on purpose: it describes a device key,
 * which never leaves the device.
 */
data class BackupPreferences(
    val themeMode: ThemeMode,
    val language: AppLanguage,
    val inactivityTimeout: InactivityTimeout,
)

/**
 * One logical, consistent view of every record. Repositories are not part of the format: the reader that builds this
 * value owns the transaction, and the codec only sees model values that already passed their own constructors.
 */
data class BackupSnapshot(
    val preferences: BackupPreferences,
    val goldHoldings: List<GoldHolding>,
    val banks: List<Bank>,
    val bankAccounts: List<BankAccount>,
    val cashBalances: List<CashBalance>,
    val zakatPayments: List<ZakatPayment>,
    val personalNotes: List<PersonalNote>,
    /** Cached remote quotes with their provenance and attribution. */
    val remoteQuotes: List<RemoteQuoteRecord>,
    /** Manual quotes. Their pinned state lives in the provenance. */
    val manualQuotes: List<PriceQuote>,
    /** Annual declarations. Empty for a version-one payload, which predates them. */
    val zakatYears: List<ZakatYear> = emptyList(),
    /** The first tracked Hijri year, or null when tracking has not started. Null means no declarations exist. */
    val zakatTrackingStartYear: Int? = null,
) {
    /** Every record across all collections. Preferences and the tracking start are not counted. */
    val recordCount: Int
        get() = goldHoldings.size + banks.size + bankAccounts.size + cashBalances.size + zakatPayments.size +
            personalNotes.size + remoteQuotes.size + manualQuotes.size + zakatYears.size
}

/**
 * Reads one consistent snapshot of the open vault. Implemented by the data layer, which must read every table inside a
 * single transaction. Returns [BackupError.Storage] when the vault is not open or cannot be read.
 */
fun interface BackupSnapshotSource {
    suspend fun read(): Outcome<BackupSnapshot, BackupError>
}

/**
 * The canonical text of a snapshot: the same bytes the format writes for its records, with a fixed export time. Two
 * snapshots with the same records have the same canonical form, whatever order their lists were built in.
 */
fun BackupSnapshot.canonicalForm(): String = BackupJson.encodeToString(
    PayloadDocument.serializer(),
    toDocument(kotlin.time.Instant.fromEpochSeconds(0), appVersion = ""),
)
