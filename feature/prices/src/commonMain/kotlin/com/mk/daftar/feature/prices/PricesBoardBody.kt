package com.mk.daftar.feature.prices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.component.DaftarIcon
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.model.FxBasis
import com.mk.daftar.core.ui.presentation.QuoteBoardSummary
import com.mk.daftar.core.ui.presentation.QuoteMarker
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.presentation.quoteBoardSummaryOf
import com.mk.daftar.core.ui.presentation.quoteMarkerText
import com.mk.daftar.core.ui.presentation.quoteSummaryText
import com.mk.daftar.core.ui.presentation.quoteUnavailableText
import com.mk.daftar.feature.prices.resources.Res
import com.mk.daftar.feature.prices.resources.prices_col_buy
import com.mk.daftar.feature.prices.resources.prices_col_karat
import com.mk.daftar.feature.prices.resources.prices_col_sell
import com.mk.daftar.feature.prices.resources.prices_details_link
import com.mk.daftar.feature.prices.resources.prices_gold_heading
import com.mk.daftar.feature.prices.resources.prices_gold_unit
import com.mk.daftar.feature.prices.resources.prices_usd_bank
import com.mk.daftar.feature.prices.resources.prices_usd_estimate
import com.mk.daftar.feature.prices.resources.prices_usd_manual
import com.mk.daftar.feature.prices.resources.prices_usd_title
import com.mk.daftar.feature.prices.resources.prices_usd_unit
import com.mk.daftar.feature.prices.resources.prices_usd_unknown
import com.mk.daftar.feature.prices.resources.prices_valuation_note
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The seven shown prices in reading order: each karat's sell then buy, then USD. */
private fun PricesBoard.shownQuotes(): List<SelectedQuote?> = karats.flatMap { listOf(it.sell, it.buy) } + usd

/**
 * The prices at a glance: one gold table, one dollar row, one freshness paragraph for the whole board, and a single way
 * into Sources and details. Per-quote providers, times, links and manual entry live on the details page.
 */
@Composable
internal fun PricesBoardBody(board: PricesBoard, state: PricesUiState, onIntent: (PricesIntent) -> Unit) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val summary = quoteBoardSummaryOf(board.shownQuotes())
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            stringResource(Res.string.prices_gold_heading),
            style = typography.sectionHeading,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(DaftarSpacing.xxs))
        Text(stringResource(Res.string.prices_gold_unit), style = typography.fineText, color = colors.muted)
        Spacer(Modifier.height(DaftarSpacing.sm))
        GoldTable(board = board, summary = summary)
        Spacer(Modifier.height(DaftarSpacing.xs))
        Text(stringResource(Res.string.prices_valuation_note), style = typography.fineText, color = colors.muted)
        Spacer(Modifier.height(DaftarSpacing.xl))
        UsdRow(usd = board.usd, marker = summary.markers.last())
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            quoteSummaryText(summary, summary.oldest?.let { stampOf(it) }),
            style = typography.fineText,
            color = colors.muted,
        )
        state.notice?.let { notice ->
            Spacer(Modifier.height(DaftarSpacing.xs))
            Text(stringResource(notice.messageResource()), style = typography.supportingBody, color = colors.ink)
        }
        Spacer(Modifier.height(DaftarSpacing.xs))
        DetailsEntry(onClick = { onIntent(PricesIntent.OpenDetails) })
    }
}

@Composable
private fun GoldTable(board: PricesBoard, summary: QuoteBoardSummary) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = HEADER_MIN_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
        ) {
            Header(stringResource(Res.string.prices_col_karat), TextAlign.Start, Modifier.weight(KARAT_COLUMN))
            Header(stringResource(Res.string.prices_col_sell), TextAlign.End, Modifier.weight(PRICE_COLUMN))
            Header(stringResource(Res.string.prices_col_buy), TextAlign.End, Modifier.weight(PRICE_COLUMN))
        }
        HorizontalDivider(color = colors.line, thickness = 1.dp)
        board.karats.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ROW_MIN_HEIGHT)
                    .padding(vertical = DaftarSpacing.sm),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
            ) {
                Text(
                    ltr("${row.karat.carats}K"),
                    style = typography.ledgerTitle,
                    color = colors.ink,
                    modifier = Modifier.weight(KARAT_COLUMN),
                )
                PriceCell(row.sell, summary.markers[index * 2], sell = true, modifier = Modifier.weight(PRICE_COLUMN))
                PriceCell(
                    row.buy,
                    summary.markers[index * 2 + 1],
                    sell = false,
                    modifier = Modifier.weight(PRICE_COLUMN),
                )
            }
            HorizontalDivider(color = colors.line, thickness = 1.dp)
        }
    }
}

@Composable
private fun Header(text: String, align: TextAlign, modifier: Modifier = Modifier) {
    Text(
        text,
        style = DaftarTheme.typography.caption,
        color = DaftarTheme.colors.muted,
        textAlign = align,
        modifier = modifier,
    )
}

/** One price, end-aligned. Sell prices are brass (they value the gold); a missing price is a dash, read "Unavailable". */
@Composable
private fun PriceCell(quote: SelectedQuote?, marker: QuoteMarker?, sell: Boolean, modifier: Modifier = Modifier) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val unavailable = quoteUnavailableText()
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        if (quote == null) {
            Text(
                MISSING_VALUE,
                style = typography.quoteNumber,
                color = colors.muted,
                modifier = Modifier.semantics { contentDescription = unavailable },
            )
        } else {
            Text(
                ltr(quote.value.moneyText()),
                style = typography.quoteNumber.copy(fontWeight = if (sell) FontWeight.Medium else FontWeight.Normal),
                color = if (sell) colors.gold else colors.ink,
            )
        }
        if (marker != null) {
            Spacer(Modifier.height(DaftarSpacing.xxs))
            Text(quoteMarkerText(marker), style = typography.caption, color = colors.muted)
        }
    }
}

@Composable
private fun UsdRow(usd: SelectedQuote?, marker: QuoteMarker?) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = USD_MIN_HEIGHT).padding(vertical = DaftarSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.prices_usd_title), style = typography.ledgerTitle, color = colors.ink)
                Text(stringResource(Res.string.prices_usd_unit), style = typography.fineText, color = colors.muted)
                usd?.let {
                    Text(stringResource(fxBasisShort(it.fxBasis)), style = typography.caption, color = colors.muted)
                }
            }
            PriceCell(usd, marker, sell = false)
        }
        HorizontalDivider(color = colors.line, thickness = 1.dp)
    }
}

/** The plain row into Sources and details: one 48dp target with a direction-aware chevron. */
@Composable
private fun DetailsEntry(onClick: () -> Unit) {
    val colors = DaftarTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DaftarSpacing.buttonMinHeight)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.prices_details_link),
            style = DaftarTheme.typography.ledgerTitle,
            color = colors.primary,
            modifier = Modifier.weight(1f),
        )
        DaftarIcon(DaftarIcons.Chevron, contentDescription = null, mirrorInRtl = true)
    }
}

internal fun fxBasisShort(basis: FxBasis?): StringResource = when (basis) {
    FxBasis.BANK_TRANSFER_BUY -> Res.string.prices_usd_bank
    FxBasis.MID_MARKET_ESTIMATE -> Res.string.prices_usd_estimate
    FxBasis.MANUAL -> Res.string.prices_usd_manual
    FxBasis.LEGACY_UNKNOWN, null -> Res.string.prices_usd_unknown
}

private const val KARAT_COLUMN = 1f
private const val PRICE_COLUMN = 1.6f
private val HEADER_MIN_HEIGHT = 48.dp
private val ROW_MIN_HEIGHT = 56.dp
private val USD_MIN_HEIGHT = 72.dp
