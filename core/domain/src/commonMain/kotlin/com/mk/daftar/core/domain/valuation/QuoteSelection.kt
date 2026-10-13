package com.mk.daftar.core.domain.valuation

import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxBasis
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** A quote reference older than this is stale. Exactly this age is not stale. */
val QUOTE_STALE_AFTER: Duration = 24.hours

/** Where the selected value came from. */
enum class QuoteOrigin {
    /** A manual value the user pinned. It overrides remote quotes until removed. */
    PINNED_MANUAL,

    /** A cached or live remote quote. */
    REMOTE,

    /** A manual value the user kept as a last resort. */
    UNPINNED_MANUAL,
}

/**
 * The age badge. Age is measured from the last successful acquisition or edit: [SelectedQuote.referenceAt], which is
 * `fetchedAt` for remote quotes and `recordedAt` for manual ones. Provider source times never drive this badge.
 */
enum class QuoteFreshness {
    FRESH,

    /** The reference is more than [QUOTE_STALE_AFTER] old. */
    STALE,

    /** The reference is after the current time, so its age cannot be trusted. Never shown as fresh. */
    CLOCK_UNCERTAIN,
}

/** A warning attached to a valued asset's quote. Warnings never make a total incomplete. */
enum class QuoteWarning {
    PINNED_MANUAL,
    UNPINNED_MANUAL,
    STALE,
    CLOCK_UNCERTAIN,
}

data class SelectedQuoteWarning(val key: QuoteKey, val warning: QuoteWarning)

/**
 * The quote chosen for one [QuoteKey]. [value] is EGP per gram for [QuoteKey.Gold] and EGP per USD for
 * [QuoteKey.UsdEgp]. Full provenance, attribution and next-update metadata are preserved from the source record.
 */
data class SelectedQuote(
    val key: QuoteKey,
    val value: Decimal,
    val origin: QuoteOrigin,
    val provenance: QuoteProvenance,
    /** The instant the badge measures from: `fetchedAt` for remote quotes, `recordedAt` for manual quotes. */
    val referenceAt: Instant,
    val freshness: QuoteFreshness,
    val nextUpdateAt: Instant?,
    val attributionUrl: String?,
    /** The basis of a USD to EGP quote. Null for gold, whose basis is its merchant side. */
    val fxBasis: FxBasis? = null,
) {
    /** Warnings to show next to any value that depends on this quote. */
    val warnings: List<QuoteWarning>
        get() = buildList {
            when (origin) {
                QuoteOrigin.PINNED_MANUAL -> add(QuoteWarning.PINNED_MANUAL)
                QuoteOrigin.UNPINNED_MANUAL -> add(QuoteWarning.UNPINNED_MANUAL)
                QuoteOrigin.REMOTE -> Unit
            }
            when (freshness) {
                QuoteFreshness.FRESH -> Unit
                QuoteFreshness.STALE -> add(QuoteWarning.STALE)
                QuoteFreshness.CLOCK_UNCERTAIN -> add(QuoteWarning.CLOCK_UNCERTAIN)
            }
        }
}

/** At most one selected quote per key. A key without an entry has no usable quote: it is missing. */
data class SelectedQuotes(val quotes: Map<QuoteKey, SelectedQuote>) {
    operator fun get(key: QuoteKey): SelectedQuote? = quotes[key]

    companion object {
        val EMPTY: SelectedQuotes = SelectedQuotes(emptyMap())
    }
}

/** The gold sell-side key for one karat. Buy-side prices never value holdings. */
internal fun goldSellKey(karat: GoldKarat): QuoteKey.Gold = QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER)

/**
 * Chooses one quote per key from [snapshot], judging freshness against [now]. Neither reads a clock nor performs I/O.
 *
 * Per key, in order: a pinned manual quote; otherwise the best eligible remote quote (LIVE or CACHED only); otherwise
 * an unpinned manual quote; otherwise the key is absent. ESTIMATE quotes are never eligible.
 */
fun selectQuotes(snapshot: QuoteSnapshot, now: Instant): SelectedQuotes {
    val keys = (
        snapshot.remote.map { it.quote.key() } +
            snapshot.manual.map { it.value.key() }
        ).distinct()
    val selected = keys.mapNotNull { key -> selectFor(key, snapshot, now)?.let { key to it } }.toMap()
    return SelectedQuotes(selected)
}

/**
 * The remote quotes alone: what every key would select if no manual value existed. This is the source value beneath a
 * pinned override, so the UI can show it next to the override without a second selection policy.
 */
fun selectRemoteQuotes(snapshot: QuoteSnapshot, now: Instant): SelectedQuotes =
    selectQuotes(snapshot.copy(manual = emptyList()), now)

private fun selectFor(key: QuoteKey, snapshot: QuoteSnapshot, now: Instant): SelectedQuote? {
    val manualForKey = snapshot.manual.filter { it.value.key() == key && it.value.provenance.kind == QuoteKind.MANUAL }
    manualForKey.firstOrNull { it.value.provenance.manualPinned }?.let { pinned ->
        return manualSelection(pinned.value, QuoteOrigin.PINNED_MANUAL, now)
    }
    remoteCandidate(key, snapshot, now)?.let { return it }
    manualForKey.firstOrNull()?.let { unpinned ->
        return manualSelection(unpinned.value, QuoteOrigin.UNPINNED_MANUAL, now)
    }
    return null
}

private data class RemoteCandidate(val selection: SelectedQuote, val tier: Int, val providerRank: Int)

private fun remoteCandidate(key: QuoteKey, snapshot: QuoteSnapshot, now: Instant): SelectedQuote? {
    val candidates = snapshot.remote.mapNotNull { record ->
        val quote = record.quote
        if (quote.key() != key) return@mapNotNull null
        val provenance = quote.provenance
        if (provenance.kind != QuoteKind.LIVE && provenance.kind != QuoteKind.CACHED) return@mapNotNull null
        val fetchedAt = provenance.fetchedAt ?: return@mapNotNull null
        val reference = referenceOf(key, provenance)
        val freshness = freshnessOf(reference = reference, now = now)
        RemoteCandidate(
            selection = SelectedQuote(
                key = key,
                value = quote.valueOf(),
                origin = QuoteOrigin.REMOTE,
                provenance = provenance,
                referenceAt = reference,
                freshness = freshness,
                nextUpdateAt = record.nextUpdateAt,
                attributionUrl = record.attributionUrl,
                fxBasis = (quote as? FxQuote)?.basis,
            ),
            tier = when (freshness) {
                QuoteFreshness.FRESH -> 0
                QuoteFreshness.CLOCK_UNCERTAIN -> 1
                QuoteFreshness.STALE -> 2
            },
            providerRank = providerRank(provenance.provider),
        )
    }
    return candidates.minWithOrNull(REMOTE_ORDER)?.selection
}

/**
 * Nonstale before clock-uncertain before stale; newest reference within a tier; exact ties prefer the provider order
 * of [providerRank].
 */
private val REMOTE_ORDER: Comparator<RemoteCandidate> = Comparator { a, b ->
    val byTier = a.tier.compareTo(b.tier)
    if (byTier != 0) return@Comparator byTier
    val byAge = b.selection.referenceAt.compareTo(a.selection.referenceAt)
    if (byAge != 0) return@Comparator byAge
    a.providerRank.compareTo(b.providerRank)
}

/**
 * Gold is priced by ISAGHA alone, so its rank never competes. FX prefers Banque Misr, then open.er-api, then the
 * fawazahmed0 fallback, which is the order of the refresh chain.
 */
private fun providerRank(provider: QuoteProvider): Int = when (provider) {
    QuoteProvider.BANQUE_MISR -> 0
    QuoteProvider.ISAGHA -> 0
    QuoteProvider.EXCHANGE_RATE_API -> 1
    QuoteProvider.FAWAZ_CURRENCY_API -> 2
    QuoteProvider.USER -> 3
}

/**
 * The instant a remote quote is judged by. Gold keeps its fetch time. An FX quote is judged by the time it describes,
 * so an older source is never refreshed by a later fetch. A quote without a source time falls back to its fetch time,
 * which is the "checked" meaning.
 */
private fun referenceOf(key: QuoteKey, provenance: QuoteProvenance): Instant {
    val fetchedAt = checkNotNull(provenance.fetchedAt) { "remote quotes always carry a fetch time" }
    if (key is QuoteKey.Gold) return fetchedAt
    return (provenance.sourceTime as? SourceTime.Timestamp)?.value ?: fetchedAt
}

private fun manualSelection(quote: PriceQuote, origin: QuoteOrigin, now: Instant): SelectedQuote {
    val recordedAt = quote.provenance.recordedAt
    val key = quote.key()
    return SelectedQuote(
        key = key,
        value = quote.valueOf(),
        origin = origin,
        provenance = quote.provenance,
        referenceAt = recordedAt,
        freshness = freshnessOf(reference = recordedAt, now = now),
        nextUpdateAt = null,
        attributionUrl = null,
        fxBasis = (quote as? FxQuote)?.basis,
    )
}

/** Age is `now − reference`. A reference after `now` is clock-uncertain, and exactly 24 hours is not stale. */
internal fun freshnessOf(reference: Instant, now: Instant): QuoteFreshness = when {
    reference > now -> QuoteFreshness.CLOCK_UNCERTAIN
    now - reference > QUOTE_STALE_AFTER -> QuoteFreshness.STALE
    else -> QuoteFreshness.FRESH
}

private fun PriceQuote.key(): QuoteKey = when (this) {
    is GoldQuote -> key
    is FxQuote -> key
}

private fun PriceQuote.valueOf(): Decimal = when (this) {
    is GoldQuote -> price.egpPerGram
    is FxQuote -> rate.egpPerUsd
}
