package com.mk.daftar.core.ui.presentation

import com.mk.daftar.core.domain.valuation.QuoteFreshness
import com.mk.daftar.core.domain.valuation.QuoteOrigin
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.model.SourceTime
import kotlin.time.Instant

/**
 * When one asset's prices (gold, or the dollar) were last updated by their source and last checked by Daftar. Gold and
 * the dollar come from different sources on different schedules, so each gets its own statement: a bank that has not
 * published since yesterday must not make a gold price checked today look old, or the other way round.
 */
sealed interface AssetFreshness {
    /**
     * A price from a remote source. [source] is the source's own time for it; [checkedAt] is the last successful fetch,
     * which a later failure never changes. [failed] is true when the latest refresh could not update this asset.
     */
    data class Remote(val source: SourceTime, val checkedAt: Instant, val failed: Boolean = false) : AssetFreshness

    /** Every shown price of the asset was entered by hand; [enteredAt] is the oldest entry. */
    data class Manual(val enteredAt: Instant) : AssetFreshness

    /** A price time lies in the future of this device's clock, so no time is shown as if it were known. */
    data object TimeUncertain : AssetFreshness
}

/**
 * The freshness of one asset's shown [quotes]. The oldest remote price speaks for the group; prices entered by hand
 * among remote ones keep their own markers. Null when none of them is available.
 */
fun assetFreshnessOf(quotes: List<SelectedQuote?>, failed: Boolean): AssetFreshness? {
    val available = quotes.filterNotNull()
    if (available.isEmpty()) return null
    if (available.any { it.freshness == QuoteFreshness.CLOCK_UNCERTAIN }) return AssetFreshness.TimeUncertain
    val remote = available.filter { it.origin == QuoteOrigin.REMOTE }
    if (remote.isEmpty()) return AssetFreshness.Manual(available.minOf { it.provenance.recordedAt })
    val oldest = remote.minBy { it.provenance.fetchedAt ?: it.referenceAt }
    return AssetFreshness.Remote(
        source = oldest.provenance.sourceTime,
        checkedAt = oldest.provenance.fetchedAt ?: oldest.referenceAt,
        failed = failed,
    )
}
