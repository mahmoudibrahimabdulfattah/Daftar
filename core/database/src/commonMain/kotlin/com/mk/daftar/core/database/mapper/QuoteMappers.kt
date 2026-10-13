package com.mk.daftar.core.database.mapper

import com.mk.daftar.core.database.entity.ManualQuoteEntity
import com.mk.daftar.core.database.entity.RemoteQuoteEntity
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.domain.repository.isManuallyEntered
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxBasis
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.FxRate
import com.mk.daftar.core.model.GoldPrice
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.time.Instant

private const val REMOTE = "remote_quotes"
private const val MANUAL = "manual_quotes"
private const val NOTE_MAX_CODE_POINTS = 4_000

/**
 * Remote rows read back as [QuoteKind.CACHED] and never pinned, whatever kind they were written from. The provider must
 * match the key: ISAGHA for gold, one of the FX providers for USD to EGP.
 */
fun RemoteQuoteEntity.toRecord(): RemoteQuoteRecord {
    val key = QuoteKeys.decode(quoteKey) ?: corrupt(REMOTE, "quote_key")
    val providerKind = enumOf<QuoteProvider>(REMOTE, "provider", provider)
    if (!providerMatches(key, providerKind)) corrupt(REMOTE, "provider")
    val amount = canonicalDecimal(REMOTE, "value", value)
    val quote = rowGuard(REMOTE) {
        val provenance = QuoteProvenance(
            provider = providerKind,
            sourceUrl = sourceUrl,
            sourceTime = sourceTimeOf(),
            fetchedAt = instantOf(REMOTE, "fetched", fetchedSeconds, fetchedNanos),
            recordedAt = instantOf(REMOTE, "recorded", recordedSeconds, recordedNanos),
            kind = QuoteKind.CACHED,
            manualPinned = false,
            note = note,
        )
        quoteOf(key, amount, provenance, basisOf(key))
    }
    return RemoteQuoteRecord(
        quote = quote,
        nextUpdateAt = optionalInstantOf(REMOTE, "next_update", nextUpdateSeconds, nextUpdateNanos),
        attributionUrl = attributionUrl,
    )
}

/** Writes a fresh or cached remote quote. Estimates and manual quotes never reach remote storage. */
fun PriceQuote.toRemoteEntity(nextUpdateAt: Instant?, attributionUrl: String?): RemoteQuoteEntity {
    val provenance = this.provenance
    require(provenance.kind == QuoteKind.LIVE || provenance.kind == QuoteKind.CACHED) {
        "only remote quotes are cached"
    }
    val key = keyOf(this)
    require(providerMatches(key, provenance.provider)) { "provider does not match the quote key" }
    val fetchedAt = requireNotNull(provenance.fetchedAt) { "remote quotes need a fetch time" }
    val sourceUrl = requireNotNull(provenance.sourceUrl) { "remote quotes need a source URL" }
    val time = provenance.sourceTime.columns()
    return RemoteQuoteEntity(
        quoteKey = QuoteKeys.encode(key),
        provider = provenance.provider.name,
        value = valueOf(this).toPlainString(),
        sourceUrl = sourceUrl,
        sourceTimeKind = time.kind,
        sourceSeconds = time.seconds,
        sourceNanos = time.nanos,
        sourceDate = time.date,
        fetchedSeconds = fetchedAt.epochSeconds,
        fetchedNanos = fetchedAt.nanosecondsOfSecond,
        recordedSeconds = provenance.recordedAt.epochSeconds,
        recordedNanos = provenance.recordedAt.nanosecondsOfSecond,
        note = provenance.note,
        nextUpdateSeconds = nextUpdateAt?.epochSeconds,
        nextUpdateNanos = nextUpdateAt?.nanosecondsOfSecond,
        attributionUrl = attributionUrl,
        basis = (this as? FxQuote)?.basis?.name,
    )
}

/** A manual quote read back with its pinned state and revision. */
fun ManualQuoteEntity.toStored(): Stored<PriceQuote> {
    val key = QuoteKeys.decode(quoteKey) ?: corrupt(MANUAL, "quote_key")
    if (!key.isManuallyEntered()) corrupt(MANUAL, "quote_key")
    val amount = canonicalDecimal(MANUAL, "value", value)
    val entered = instantOf(MANUAL, "entered", enteredSeconds, enteredNanos)
    val revision = checkRevision(MANUAL, revision)
    val quote = rowGuard(MANUAL) {
        val provenance = QuoteProvenance(
            provider = QuoteProvider.USER,
            sourceUrl = null,
            sourceTime = SourceTime.Timestamp(entered),
            fetchedAt = null,
            recordedAt = entered,
            kind = QuoteKind.MANUAL,
            manualPinned = pinned,
            note = note,
        )
        quoteOf(key, amount, provenance)
    }
    return Stored(quote, revision)
}

/** The row for a manual entry. [pinned] is true for setManual and false after removeOverride. */
fun ManualQuoteInput.toEntity(enteredAt: Instant, pinned: Boolean, revision: Long): ManualQuoteEntity {
    val trimmed = note?.trim()?.ifEmpty { null }
    require(trimmed == null || trimmed.length <= NOTE_MAX_CODE_POINTS) { "note too long" }
    return ManualQuoteEntity(
        quoteKey = QuoteKeys.encode(key),
        value = value.toPlainString(),
        enteredSeconds = enteredAt.epochSeconds,
        enteredNanos = enteredAt.nanosecondsOfSecond,
        pinned = pinned,
        note = trimmed,
        revision = revision,
    )
}

/** The quote a stored key and value describe: a gold price for a karat and side, or the USD to EGP rate. */
private fun quoteOf(key: QuoteKey, value: Decimal, provenance: QuoteProvenance, basis: FxBasis? = null): PriceQuote =
    when (key) {
        is QuoteKey.Gold -> GoldQuote(key, GoldPrice(value), provenance)
        QuoteKey.UsdEgp -> FxQuote(FxRate(value), provenance, basis ?: FxBasis.defaultFor(provenance.provider))
    }

/** The stored basis of a remote row: required for USD to EGP, absent for gold. */
private fun RemoteQuoteEntity.basisOf(key: QuoteKey): FxBasis? = when (key) {
    is QuoteKey.Gold -> if (basis == null) null else corrupt(REMOTE, "basis")
    QuoteKey.UsdEgp -> enumOf<FxBasis>(REMOTE, "basis", basis ?: corrupt(REMOTE, "basis"))
}

private fun keyOf(quote: PriceQuote): QuoteKey = when (quote) {
    is GoldQuote -> quote.key
    is FxQuote -> quote.key
}

private fun valueOf(quote: PriceQuote): Decimal = when (quote) {
    is GoldQuote -> quote.price.egpPerGram
    is FxQuote -> quote.rate.egpPerUsd
}

private fun providerMatches(key: QuoteKey, provider: QuoteProvider): Boolean = when (key) {
    is QuoteKey.Gold -> provider == QuoteProvider.ISAGHA

    QuoteKey.UsdEgp ->
        provider == QuoteProvider.BANQUE_MISR ||
            provider == QuoteProvider.EXCHANGE_RATE_API ||
            provider == QuoteProvider.FAWAZ_CURRENCY_API
}

private fun RemoteQuoteEntity.sourceTimeOf(): SourceTime = when (sourceTimeKind) {
    "TIMESTAMP" -> SourceTime.Timestamp(
        optionalInstantOf(REMOTE, "source", sourceSeconds, sourceNanos)
            ?: corrupt(REMOTE, "source_seconds"),
    )

    "DATE_ONLY" -> {
        if (sourceSeconds != null || sourceNanos != null) corrupt(REMOTE, "source_time_kind")
        SourceTime.DateOnly(localDateOf(REMOTE, "source_date", sourceDate ?: corrupt(REMOTE, "source_date")))
    }

    "UNKNOWN" -> {
        if (sourceSeconds != null || sourceNanos != null || sourceDate != null) {
            corrupt(REMOTE, "source_time_kind")
        }
        SourceTime.Unknown
    }

    else -> corrupt(REMOTE, "source_time_kind")
}

private class SourceColumns(val kind: String, val seconds: Long?, val nanos: Int?, val date: String?)

private fun SourceTime.columns(): SourceColumns = when (this) {
    is SourceTime.Timestamp -> SourceColumns("TIMESTAMP", value.epochSeconds, value.nanosecondsOfSecond, null)
    is SourceTime.DateOnly -> SourceColumns("DATE_ONLY", null, null, value.toString())
    SourceTime.Unknown -> SourceColumns("UNKNOWN", null, null, null)
}

/** The row for a manual quote read from a backup. [revision] is the first revision of a restored row. */
fun PriceQuote.toManualEntity(revision: Long): ManualQuoteEntity {
    val provenance = this.provenance
    require(provenance.kind == QuoteKind.MANUAL) { "only manual quotes have manual rows" }
    val entered = provenance.recordedAt
    return ManualQuoteEntity(
        quoteKey = QuoteKeys.encode(keyOf(this)),
        value = valueOf(this).toPlainString(),
        enteredSeconds = entered.epochSeconds,
        enteredNanos = entered.nanosecondsOfSecond,
        pinned = provenance.manualPinned,
        note = provenance.note,
        revision = revision,
    )
}
