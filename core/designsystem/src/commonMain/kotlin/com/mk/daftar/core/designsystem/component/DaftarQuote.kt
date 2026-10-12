package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/** Shown in place of a missing price. */
private const val MISSING_QUOTE = "—"

/** One price in a [DaftarQuoteStrip]. A null [value] means no quote is available for it. */
data class DaftarQuote(val label: String, val value: String?)

/** One karat in a [DaftarQuoteTable]. A null price means that quote is missing. */
data class DaftarKaratQuote(val karat: String, val sell: String?, val buy: String?)

/**
 * A compact strip of current quotes with their source line. Missing quotes show an em dash and never zero.
 * While a refresh runs, the values stay on screen; [status] is where the caller places a refreshing or stale tag.
 */
@Composable
fun DaftarQuoteStrip(
    quotes: List<DaftarQuote>,
    provenance: String,
    modifier: Modifier = Modifier,
    style: Style = Style,
    status: (@Composable () -> Unit)? = null,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val styleState = remember { MutableStyleState(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .styleable(styleState, DaftarStyles.quoteStrip, style),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            quotes.forEachIndexed { index, quote ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (index == 0) 0.dp else DaftarSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(quote.label, style = typography.caption, color = colors.muted)
                    Text(
                        text = quote.value ?: MISSING_QUOTE,
                        style = typography.quoteNumber,
                        color = if (quote.value == null) colors.muted else colors.ink,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
        ) {
            Text(
                text = provenance,
                style = typography.caption,
                color = colors.muted,
                modifier = Modifier.weight(1f),
            )
            if (status != null) status()
        }
    }
}

/**
 * Sell and buy prices per karat. The sell column is the one valued against holdings: gold, medium weight.
 * Missing prices show an em dash.
 */
@Composable
fun DaftarQuoteTable(
    rows: List<DaftarKaratQuote>,
    karatLabel: String,
    sellLabel: String,
    buyLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    Column(modifier = modifier.fillMaxWidth()) {
        QuoteTableRow(
            karat = karatLabel,
            sell = sellLabel,
            buy = buyLabel,
            karatStyle = typography.caption,
            priceStyle = typography.caption,
            sellColor = colors.muted,
            buyColor = colors.muted,
        )
        HorizontalDivider(color = colors.line, thickness = 1.dp)
        rows.forEach { row ->
            QuoteTableRow(
                karat = row.karat,
                sell = row.sell ?: MISSING_QUOTE,
                buy = row.buy ?: MISSING_QUOTE,
                karatStyle = typography.ledgerTitle,
                priceStyle = typography.quoteNumber,
                sellColor = if (row.sell == null) colors.muted else colors.gold,
                buyColor = if (row.buy == null) colors.muted else colors.ink,
                sellWeight = FontWeight.Medium,
            )
            HorizontalDivider(color = colors.line, thickness = 1.dp)
        }
    }
}

@Composable
private fun QuoteTableRow(
    karat: String,
    sell: String,
    buy: String,
    karatStyle: TextStyle,
    priceStyle: TextStyle,
    sellColor: Color,
    buyColor: Color,
    sellWeight: FontWeight = FontWeight.Normal,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(karat, style = karatStyle, color = DaftarTheme.colors.ink, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.weight(1f)) {
            Text(
                sell,
                style = priceStyle.copy(fontWeight = sellWeight),
                color = sellColor,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            Text(buy, style = priceStyle, color = buyColor)
        }
    }
}
