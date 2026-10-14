package com.mk.daftar.core.backup

import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.HIJRI_MAX_OVERRIDE_DAYS
import com.mk.daftar.core.model.HIJRI_MAX_YEAR
import com.mk.daftar.core.model.HIJRI_MIN_YEAR
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteProvider

/**
 * Cross-record rules a decoded snapshot must meet before it can become a vault. Every model constructor already checked
 * its own fields; these rules are about the collection as a whole. A violation is corrupt data, never a partial import.
 */
internal fun BackupSnapshot.isConsistent(): Boolean {
    if (goldHoldings.map { it.id }.hasDuplicates()) return false
    if (banks.map { it.id }.hasDuplicates()) return false
    if (bankAccounts.map { it.id }.hasDuplicates()) return false
    if (cashBalances.map { it.id }.hasDuplicates()) return false
    if (zakatPayments.map { it.id }.hasDuplicates()) return false
    if (personalNotes.map { it.id }.hasDuplicates()) return false
    val bankIds: Set<BankId> = banks.map { it.id }.toSet()
    if (bankAccounts.any { it.bankId !in bankIds }) return false
    if (remoteQuotes.map { remoteSlot(it) }.hasDuplicates()) return false
    if (manualQuotes.map { quoteKeyOf(it) }.hasDuplicates()) return false
    return yearsAreConsistent() && investmentsAreConsistent()
}

/**
 * Investment accounts: unique IDs, portable identifiers for the record and its conversion source, and at most one
 * investment per converted source. A converted source must be gone from the cash table: an entry cannot exist both as a
 * cash entry and as the investment it became, and a restore never reconstructs a source that was intentionally deleted.
 */
private fun BackupSnapshot.investmentsAreConsistent(): Boolean {
    val accounts = investmentAccounts.map { it.value }
    if (accounts.map { it.id }.hasDuplicates()) return false
    if (accounts.any { !it.id.value.isPortableIdentifier() }) return false
    val sources = accounts.mapNotNull { it.convertedFromCashId }
    if (sources.hasDuplicates()) return false
    if (sources.any { !it.value.isPortableIdentifier() }) return false
    val cashIds = cashBalances.map { it.id }.toSet()
    return sources.none { it in cashIds }
}

/** One to 64 ASCII letters, digits or hyphens. The generator's canonical IDs are UUIDs and always fit. */
private fun String.isPortableIdentifier(): Boolean = length in 1..MAX_PORTABLE_ID_LENGTH &&
    all { ch -> ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' }

private const val MAX_PORTABLE_ID_LENGTH = 64

/**
 * Declarations and their links: unique supported years, valid date adjustments, a mark time exactly for paid years,
 * a payment year in range, and a tracking start that exists whenever a declaration does.
 */
private fun BackupSnapshot.yearsAreConsistent(): Boolean {
    if (zakatYears.map { it.hijriYear }.hasDuplicates()) return false
    if (zakatYears.any { it.hijriYear !in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR }) return false
    if (zakatYears.any { it.overrideDays !in -HIJRI_MAX_OVERRIDE_DAYS..HIJRI_MAX_OVERRIDE_DAYS }) return false
    if (zakatYears.any { it.paid != (it.markedAt != null) }) return false
    if (zakatYears.any { it.userAdjusted != (it.overrideDays != 0) }) return false
    if (zakatTrackingStartYear == null && zakatYears.isNotEmpty()) return false
    zakatTrackingStartYear?.let { if (it !in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR) return false }
    return zakatPayments.none { payment -> payment.forHijriYear?.let { it !in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR } == true }
}

private fun <T> List<T>.hasDuplicates(): Boolean = size != toSet().size

/** A remote quote is unique per key and provider: the two FX providers may both be cached for USD to EGP. */
private fun remoteSlot(record: RemoteQuoteRecord): Pair<QuoteKey, QuoteProvider> =
    quoteKeyOf(record.quote) to record.quote.provenance.provider

private fun quoteKeyOf(quote: PriceQuote): QuoteKey = when (quote) {
    is GoldQuote -> quote.key
    is FxQuote -> quote.key
}
