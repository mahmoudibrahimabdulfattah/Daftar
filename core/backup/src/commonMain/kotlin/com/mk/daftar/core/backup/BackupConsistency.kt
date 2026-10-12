package com.mk.daftar.core.backup

import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.GoldQuote
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
    return manualQuotes.map { quoteKeyOf(it) }.hasDuplicates().not()
}

private fun <T> List<T>.hasDuplicates(): Boolean = size != toSet().size

/** A remote quote is unique per key and provider: the two FX providers may both be cached for USD to EGP. */
private fun remoteSlot(record: RemoteQuoteRecord): Pair<QuoteKey, QuoteProvider> =
    quoteKeyOf(record.quote) to record.quote.provenance.provider

private fun quoteKeyOf(quote: PriceQuote): QuoteKey = when (quote) {
    is GoldQuote -> quote.key
    is FxQuote -> quote.key
}
