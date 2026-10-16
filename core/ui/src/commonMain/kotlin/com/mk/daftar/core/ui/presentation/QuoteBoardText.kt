package com.mk.daftar.core.ui.presentation

import androidx.compose.runtime.Composable
import com.mk.daftar.core.ui.resources.Res
import com.mk.daftar.core.ui.resources.quotes_marker_cached
import com.mk.daftar.core.ui.resources.quotes_marker_clock
import com.mk.daftar.core.ui.resources.quotes_marker_live
import com.mk.daftar.core.ui.resources.quotes_marker_manual
import com.mk.daftar.core.ui.resources.quotes_marker_manual_clock
import com.mk.daftar.core.ui.resources.quotes_marker_manual_stale
import com.mk.daftar.core.ui.resources.quotes_marker_stale
import com.mk.daftar.core.ui.resources.quotes_unavailable
import com.mk.daftar.core.ui.resources.quotes_waiting
import org.jetbrains.compose.resources.stringResource

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

/** Said once when no price at all has arrived yet. */
@Composable
fun quoteWaitingText(): String = stringResource(Res.string.quotes_waiting)
