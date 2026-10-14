package com.mk.daftar.core.ui.presentation

import androidx.compose.runtime.Composable
import com.mk.daftar.core.ui.resources.Res
import com.mk.daftar.core.ui.resources.quotes_differ
import com.mk.daftar.core.ui.resources.quotes_marker_cached
import com.mk.daftar.core.ui.resources.quotes_marker_clock
import com.mk.daftar.core.ui.resources.quotes_marker_live
import com.mk.daftar.core.ui.resources.quotes_marker_manual
import com.mk.daftar.core.ui.resources.quotes_marker_manual_clock
import com.mk.daftar.core.ui.resources.quotes_marker_manual_stale
import com.mk.daftar.core.ui.resources.quotes_marker_stale
import com.mk.daftar.core.ui.resources.quotes_norm_cached
import com.mk.daftar.core.ui.resources.quotes_norm_clock
import com.mk.daftar.core.ui.resources.quotes_norm_live
import com.mk.daftar.core.ui.resources.quotes_norm_manual
import com.mk.daftar.core.ui.resources.quotes_norm_stale
import com.mk.daftar.core.ui.resources.quotes_oldest
import com.mk.daftar.core.ui.resources.quotes_some_missing
import com.mk.daftar.core.ui.resources.quotes_unavailable
import com.mk.daftar.core.ui.resources.quotes_unknown_time
import com.mk.daftar.core.ui.resources.quotes_waiting
import org.jetbrains.compose.resources.stringResource

/**
 * The one freshness paragraph of a price board: its state, the oldest update ([stamp], already localized), and whether
 * some prices differ or are missing. Waiting copy when nothing is available yet.
 */
@Composable
fun quoteSummaryText(summary: QuoteBoardSummary, stamp: String?): String {
    if (summary.empty) return stringResource(Res.string.quotes_waiting)
    val parts = buildList {
        add(
            stringResource(
                when (summary.norm) {
                    QuoteStatus.LIVE -> Res.string.quotes_norm_live
                    QuoteStatus.CACHED -> Res.string.quotes_norm_cached
                    QuoteStatus.MANUAL -> Res.string.quotes_norm_manual
                    QuoteStatus.STALE -> Res.string.quotes_norm_stale
                    QuoteStatus.CLOCK_UNCERTAIN, null -> Res.string.quotes_norm_clock
                },
            ),
        )
        // A time that cannot be verified is never shown as if it were known.
        if (summary.norm != QuoteStatus.CLOCK_UNCERTAIN) {
            add(
                if (stamp != null) {
                    stringResource(Res.string.quotes_oldest, stamp)
                } else {
                    stringResource(Res.string.quotes_unknown_time)
                },
            )
        }
        if (summary.someDiffer) add(stringResource(Res.string.quotes_differ))
        if (summary.someMissing) add(stringResource(Res.string.quotes_some_missing))
    }
    return parts.joinToString(" · ")
}

/** The marker text under a price that differs from the board. */
@Composable
fun quoteMarkerText(marker: QuoteMarker): String = stringResource(
    when (marker) {
        QuoteMarker.LIVE -> Res.string.quotes_marker_live
        QuoteMarker.CACHED -> Res.string.quotes_marker_cached
        QuoteMarker.MANUAL -> Res.string.quotes_marker_manual
        QuoteMarker.STALE -> Res.string.quotes_marker_stale
        QuoteMarker.CLOCK_UNCERTAIN -> Res.string.quotes_marker_clock
        QuoteMarker.MANUAL_STALE -> Res.string.quotes_marker_manual_stale
        QuoteMarker.MANUAL_CLOCK_UNCERTAIN -> Res.string.quotes_marker_manual_clock
    },
)

/** How a missing price is spoken: never as zero. */
@Composable
fun quoteUnavailableText(): String = stringResource(Res.string.quotes_unavailable)
