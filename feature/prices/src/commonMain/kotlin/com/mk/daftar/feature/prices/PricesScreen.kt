package com.mk.daftar.feature.prices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarAmount
import com.mk.daftar.core.designsystem.component.DaftarIconButton
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarStatusTag
import com.mk.daftar.core.designsystem.component.DaftarStatusTone
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.valuation.QuoteOrigin
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.model.FxBasis
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.navigation.ManualQuoteRoute
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.pageScroll
import com.mk.daftar.feature.prices.resources.Res
import com.mk.daftar.feature.prices.resources.fx_basis_bank
import com.mk.daftar.feature.prices.resources.fx_basis_bank_explanation
import com.mk.daftar.feature.prices.resources.fx_basis_estimate
import com.mk.daftar.feature.prices.resources.fx_basis_fallback
import com.mk.daftar.feature.prices.resources.fx_basis_manual
import com.mk.daftar.feature.prices.resources.fx_time_checked
import com.mk.daftar.feature.prices.resources.fx_time_published_checked
import com.mk.daftar.feature.prices.resources.prices_attribution
import com.mk.daftar.feature.prices.resources.prices_badge_cached
import com.mk.daftar.feature.prices.resources.prices_badge_clock
import com.mk.daftar.feature.prices.resources.prices_badge_live
import com.mk.daftar.feature.prices.resources.prices_badge_manual
import com.mk.daftar.feature.prices.resources.prices_badge_stale
import com.mk.daftar.feature.prices.resources.prices_caption_fallback
import com.mk.daftar.feature.prices.resources.prices_caption_manual
import com.mk.daftar.feature.prices.resources.prices_caption_remote
import com.mk.daftar.feature.prices.resources.prices_col_buy
import com.mk.daftar.feature.prices.resources.prices_col_karat
import com.mk.daftar.feature.prices.resources.prices_col_sell
import com.mk.daftar.feature.prices.resources.prices_date
import com.mk.daftar.feature.prices.resources.prices_details_title
import com.mk.daftar.feature.prices.resources.prices_enter_karat
import com.mk.daftar.feature.prices.resources.prices_enter_usd
import com.mk.daftar.feature.prices.resources.prices_form_bar
import com.mk.daftar.feature.prices.resources.prices_form_jewelry
import com.mk.daftar.feature.prices.resources.prices_heading
import com.mk.daftar.feature.prices.resources.prices_hidden
import com.mk.daftar.feature.prices.resources.prices_load_failed
import com.mk.daftar.feature.prices.resources.prices_manual_entry
import com.mk.daftar.feature.prices.resources.prices_missing_available
import com.mk.daftar.feature.prices.resources.prices_missing_gold
import com.mk.daftar.feature.prices.resources.prices_missing_karat
import com.mk.daftar.feature.prices.resources.prices_missing_message
import com.mk.daftar.feature.prices.resources.prices_missing_title
import com.mk.daftar.feature.prices.resources.prices_missing_usd
import com.mk.daftar.feature.prices.resources.prices_missing_usd_count
import com.mk.daftar.feature.prices.resources.prices_month_1
import com.mk.daftar.feature.prices.resources.prices_month_10
import com.mk.daftar.feature.prices.resources.prices_month_11
import com.mk.daftar.feature.prices.resources.prices_month_12
import com.mk.daftar.feature.prices.resources.prices_month_2
import com.mk.daftar.feature.prices.resources.prices_month_3
import com.mk.daftar.feature.prices.resources.prices_month_4
import com.mk.daftar.feature.prices.resources.prices_month_5
import com.mk.daftar.feature.prices.resources.prices_month_6
import com.mk.daftar.feature.prices.resources.prices_month_7
import com.mk.daftar.feature.prices.resources.prices_month_8
import com.mk.daftar.feature.prices.resources.prices_month_9
import com.mk.daftar.feature.prices.resources.prices_notice_failed
import com.mk.daftar.feature.prices.resources.prices_notice_invalid
import com.mk.daftar.feature.prices.resources.prices_notice_missing
import com.mk.daftar.feature.prices.resources.prices_notice_offline
import com.mk.daftar.feature.prices.resources.prices_notice_partial
import com.mk.daftar.feature.prices.resources.prices_notice_rate_limited
import com.mk.daftar.feature.prices.resources.prices_provider_banque_misr
import com.mk.daftar.feature.prices.resources.prices_provider_exchange
import com.mk.daftar.feature.prices.resources.prices_provider_fawaz
import com.mk.daftar.feature.prices.resources.prices_provider_isagha
import com.mk.daftar.feature.prices.resources.prices_provider_manual
import com.mk.daftar.feature.prices.resources.prices_refresh
import com.mk.daftar.feature.prices.resources.prices_refreshing
import com.mk.daftar.feature.prices.resources.prices_retry
import com.mk.daftar.feature.prices.resources.prices_sell_note
import com.mk.daftar.feature.prices.resources.prices_source_date
import com.mk.daftar.feature.prices.resources.prices_source_none
import com.mk.daftar.feature.prices.resources.prices_source_price
import com.mk.daftar.feature.prices.resources.prices_source_time
import com.mk.daftar.feature.prices.resources.prices_source_unknown
import com.mk.daftar.feature.prices.resources.prices_sources_heading
import com.mk.daftar.feature.prices.resources.prices_stale_message
import com.mk.daftar.feature.prices.resources.prices_stale_title
import com.mk.daftar.feature.prices.resources.prices_stamp
import com.mk.daftar.feature.prices.resources.prices_title
import com.mk.daftar.feature.prices.resources.prices_unit
import com.mk.daftar.feature.prices.resources.prices_unit_short
import com.mk.daftar.feature.prices.resources.prices_usd_heading
import com.mk.daftar.feature.prices.resources.prices_usd_unit
import com.mk.daftar.feature.prices.resources.prices_xau_formula
import com.mk.daftar.feature.prices.resources.prices_xau_rule
import com.mk.daftar.feature.prices.resources.prices_xau_tag
import com.mk.daftar.feature.prices.resources.prices_xau_title
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The prices screen: the view model's state, with the navigator opening the manual editor. */
@Composable
fun PricesScreen(modifier: Modifier = Modifier, details: Boolean = false) {
    val viewModel: PricesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is PricesEffect.Open -> navigator.goTo(effect.route)
        }
    }
    PricesContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.goBack() },
        modifier = modifier,
        details = details,
    )
}

/**
 * The approved prices layout: the merchant table with each quote's provider, freshness and time, the status of the
 * last refresh, the sources, USD to EGP, the missing and stale states, and the separate XAU estimate. It renders
 * [state] only, so the screenshot host can show every state.
 */
@Composable
fun PricesContent(
    state: PricesUiState,
    onIntent: (PricesIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    details: Boolean = false,
) {
    val refreshLabel = stringResource(Res.string.prices_refresh)
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(if (details) Res.string.prices_details_title else Res.string.prices_title),
                onBack = onBack,
                backLabel = stringResource(DesignRes.string.btn_back),
                actions = if (details) {
                    {}
                } else {
                    {
                        DaftarIconButton(
                            onClick = { onIntent(PricesIntent.Refresh) },
                            icon = DaftarIcons.Refresh,
                            contentDescription = refreshLabel,
                            enabled = !state.refreshing,
                        )
                    }
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pageScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md)
                    .padding(bottom = DaftarSpacing.xl),
            ) {
                Column(
                    modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth(),
                ) {
                    when (val load = state.load) {
                        PricesLoad.Loading, PricesLoad.Locked -> DaftarSkeleton()

                        is PricesLoad.Failed -> DaftarNotePanel(tone = DaftarNoteTone.Error) {
                            Text(stringResource(Res.string.prices_load_failed))
                        }

                        is PricesLoad.Ready -> if (details) {
                            PricesBody(board = load.board, state = state, onIntent = onIntent)
                        } else {
                            PricesBoardBody(board = load.board, state = state, onIntent = onIntent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PricesBody(board: PricesBoard, state: PricesUiState, onIntent: (PricesIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val colors = DaftarTheme.colors
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            stringResource(Res.string.prices_heading),
            style = DaftarTheme.typography.screenHeading,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(Res.string.prices_unit), style = DaftarTheme.typography.fineText, color = colors.muted)
        Spacer(Modifier.height(DaftarSpacing.sm))
        RefreshStatus(refreshing = state.refreshing, notice = state.notice)
        if (board.missing.isNotEmpty()) {
            Spacer(Modifier.height(DaftarSpacing.sm))
            MissingPanel(board = board, valuesHidden = state.valuesHidden, onIntent = onIntent)
        }
        if (board.stale.isNotEmpty()) {
            Spacer(Modifier.height(DaftarSpacing.sm))
            StalePanel(onIntent = onIntent, staleKey = board.stale.first())
        }
        Spacer(Modifier.height(DaftarSpacing.md))
        MerchantTable(rows = board.karats)
        Spacer(Modifier.height(DaftarSpacing.sm))
        DaftarNotePanel(tone = DaftarNoteTone.Context) {
            Text(stringResource(Res.string.prices_sell_note))
        }
        Spacer(Modifier.height(DaftarSpacing.lg))
        SourcesSection(board = board)
        Spacer(Modifier.height(DaftarSpacing.lg))
        UsdSection(usd = board.usd, source = board.usdSource)
        Spacer(Modifier.height(DaftarSpacing.lg))
        OutlineButton(
            onClick = { onIntent(PricesIntent.OpenManual(ManualQuoteRoute(null))) },
            fullWidth = true,
        ) {
            Text(stringResource(Res.string.prices_manual_entry))
        }
    }
}

/** The status of the last refresh. A refresh never changes the prices or their times on screen. */
@Composable
private fun RefreshStatus(refreshing: Boolean, notice: RefreshNotice?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (refreshing) {
            DaftarStatusTag(label = stringResource(Res.string.prices_refreshing))
            Spacer(Modifier.height(DaftarSpacing.xs))
        }
        if (notice != null) {
            DaftarNotePanel(
                tone = if (notice ==
                    RefreshNotice.FAILED
                ) {
                    DaftarNoteTone.Error
                } else {
                    DaftarNoteTone.Warning
                },
            ) {
                Text(stringResource(notice.messageResource()))
            }
        }
    }
}

@Composable
private fun MissingPanel(board: PricesBoard, valuesHidden: Boolean, onIntent: (PricesIntent) -> Unit) {
    val colors = DaftarTheme.colors
    val details = board.details
    val first = board.missing.first()
    DaftarNotePanel(tone = DaftarNoteTone.Warning) {
        Text(
            stringResource(Res.string.prices_missing_title),
            style = DaftarTheme.typography.smallHeading,
            color = colors.ink,
        )
        Text(stringResource(Res.string.prices_missing_message))
        board.missing.forEach { key ->
            Text(stringResource(missingQuoteResource(key)))
        }
        if (details != null) {
            Spacer(Modifier.height(DaftarSpacing.xxs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(Res.string.prices_missing_available), style = DaftarTheme.typography.fineText)
                DaftarAmount(
                    amount = details.availableSubtotal.moneyText(),
                    unit = stringResource(Res.string.prices_unit_short),
                    masked = valuesHidden,
                    maskedDescription = stringResource(Res.string.prices_hidden),
                    style = DaftarTheme.typography.ledgerValue,
                )
            }
            details.gold.forEach { group ->
                Text(
                    stringResource(
                        Res.string.prices_missing_gold,
                        "${group.karat.carats}K",
                        stringResource(
                            if (group.form ==
                                GoldForm.BAR
                            ) {
                                Res.string.prices_form_bar
                            } else {
                                Res.string.prices_form_jewelry
                            },
                        ),
                        if (valuesHidden) stringResource(Res.string.prices_hidden) else group.grams.moneyText(),
                    ),
                    style = DaftarTheme.typography.fineText,
                )
            }
            if (details.usdBalances > 0) {
                Text(
                    stringResource(Res.string.prices_missing_usd_count, details.usdBalances),
                    style = DaftarTheme.typography.fineText,
                )
            }
        }
        Spacer(Modifier.height(DaftarSpacing.xs))
        PrimaryButton(
            onClick = { onIntent(PricesIntent.OpenManual(ManualQuoteRoute(quoteKeyToken(first)))) },
            fullWidth = true,
        ) {
            Text(stringResource(missingEnterResource(first)))
        }
        DaftarTextButton(onClick = { onIntent(PricesIntent.Refresh) }, fullWidth = true) {
            Text(stringResource(Res.string.prices_retry))
        }
    }
}

@Composable
private fun StalePanel(staleKey: QuoteKey, onIntent: (PricesIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DaftarNotePanel(tone = DaftarNoteTone.Warning) {
            Text(
                stringResource(Res.string.prices_stale_title),
                style = DaftarTheme.typography.smallHeading,
                color = DaftarTheme.colors.ink,
            )
            Text(stringResource(Res.string.prices_stale_message))
        }
        Spacer(Modifier.height(DaftarSpacing.xs))
        PrimaryButton(onClick = { onIntent(PricesIntent.Refresh) }, fullWidth = true) {
            Text(stringResource(Res.string.prices_retry))
        }
        OutlineButton(
            onClick = { onIntent(PricesIntent.OpenManual(ManualQuoteRoute(quoteKeyToken(staleKey)))) },
            fullWidth = true,
        ) {
            Text(stringResource(Res.string.prices_manual_entry))
        }
    }
}

/**
 * The merchant table. The sell column is the effective value and is what holdings are valued at. A pinned override
 * shows its badge, with the remote price beneath it. Buy prices are remote and informational.
 */
@Composable
private fun MerchantTable(rows: List<KaratPriceRow>) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = DaftarSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.prices_col_karat),
                style = typography.caption,
                color = colors.muted,
                modifier = Modifier.weight(KARAT_WEIGHT),
            )
            Text(
                stringResource(Res.string.prices_col_sell),
                style = typography.caption,
                color = colors.muted,
                modifier = Modifier.weight(SELL_WEIGHT),
            )
            Text(
                stringResource(Res.string.prices_col_buy),
                style = typography.caption,
                color = colors.muted,
                modifier = Modifier.weight(BUY_WEIGHT),
            )
        }
        HorizontalDivider(color = colors.line, thickness = 1.dp)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = DaftarSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "${row.karat.carats}K",
                    style = typography.ledgerTitle,
                    color = colors.ink,
                    modifier = Modifier.weight(KARAT_WEIGHT).padding(top = DaftarSpacing.xxs),
                )
                Column(modifier = Modifier.weight(SELL_WEIGHT)) {
                    QuoteCell(quote = row.sell, emphasized = true)
                    row.sourceSell?.let { source ->
                        Spacer(Modifier.height(DaftarSpacing.xxs))
                        SourceBeneath(source)
                    }
                }
                Column(modifier = Modifier.weight(BUY_WEIGHT)) {
                    QuoteCell(quote = row.buy, emphasized = false)
                }
            }
            HorizontalDivider(color = colors.line, thickness = 1.dp)
        }
    }
}

/** One price with its badge and caption. A missing price is a dash: it is never inferred from another quote. */
@Composable
private fun QuoteCell(quote: SelectedQuote?, emphasized: Boolean) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    Column(modifier = Modifier.fillMaxWidth()) {
        if (quote == null) {
            Text(MISSING_VALUE, style = typography.quoteNumber, color = colors.muted)
        } else {
            Text(
                ltr(quote.value.moneyText()),
                style = typography.quoteNumber.copy(
                    fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
                ),
                color = if (emphasized) colors.gold else colors.ink,
            )
            Spacer(Modifier.height(DaftarSpacing.xxs))
            BadgeTag(quote.priceBadge())
            Spacer(Modifier.height(DaftarSpacing.xxs))
            QuoteCaption(quote)
        }
    }
}

/** The remote price a pinned override covers, shown beneath the override with its own badge and caption. */
@Composable
private fun SourceBeneath(source: SelectedQuote) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val colors = DaftarTheme.colors
        val typography = DaftarTheme.typography
        Text(
            stringResource(Res.string.prices_source_price, ltr(source.value.moneyText())),
            style = typography.fineText,
            color = colors.muted,
        )
        BadgeTag(source.priceBadge())
        QuoteCaption(source)
    }
}

@Composable
private fun QuoteCaption(quote: SelectedQuote) {
    val text = when {
        quote.origin == QuoteOrigin.REMOTE -> stringResource(
            Res.string.prices_caption_remote,
            stringResource(providerResource(quote.provenance.provider)),
            stampOf(quote.referenceAt),
        )

        quote.origin == QuoteOrigin.UNPINNED_MANUAL -> stringResource(Res.string.prices_caption_fallback)

        else -> stringResource(Res.string.prices_caption_manual, stampOf(quote.referenceAt))
    }
    Text(text, style = DaftarTheme.typography.caption, color = DaftarTheme.colors.muted)
}

@Composable
private fun BadgeTag(badge: PriceBadge) {
    val tone = when (badge) {
        PriceBadge.LIVE, PriceBadge.CACHED -> DaftarStatusTone.Positive
        else -> DaftarStatusTone.Warning
    }
    DaftarStatusTag(label = stringResource(badgeResource(badge)), tone = tone)
}

/** The source block: the iSagha page for gold, with the provider's own time labelled apart from acquisition. */
@Composable
private fun SourcesSection(board: PricesBoard) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val colors = DaftarTheme.colors
        val gold = board.karats.firstNotNullOfOrNull { row ->
            row.sell?.takeIf { it.origin == QuoteOrigin.REMOTE }
                ?: row.buy
        }
        Text(
            stringResource(Res.string.prices_sources_heading),
            style = DaftarTheme.typography.sectionHeading,
            color = colors.ink,
            modifier = Modifier.semantics {
                heading()
            },
        )
        Spacer(Modifier.height(DaftarSpacing.xs))
        if (gold == null) {
            Text(
                stringResource(Res.string.prices_source_none),
                style = DaftarTheme.typography.fineText,
                color = colors.muted,
            )
        } else {
            SourceLine(quote = gold, label = stringResource(providerResource(gold.provenance.provider)))
        }
    }
}

@Composable
private fun UsdSection(usd: SelectedQuote?, source: SelectedQuote?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val colors = DaftarTheme.colors
        Text(
            stringResource(Res.string.prices_usd_heading),
            style = DaftarTheme.typography.sectionHeading,
            color = colors.ink,
            modifier = Modifier.semantics {
                heading()
            },
        )
        Spacer(Modifier.height(DaftarSpacing.xs))
        if (usd == null) {
            Text(
                stringResource(Res.string.prices_missing_usd),
                style = DaftarTheme.typography.fineText,
                color = colors.warning,
            )
            return
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
        ) {
            Text(ltr(usd.value.moneyText()), style = DaftarTheme.typography.quoteNumber, color = colors.ink)
            Text(
                stringResource(Res.string.prices_usd_unit),
                style = DaftarTheme.typography.fineText,
                color = colors.muted,
            )
        }
        Spacer(Modifier.height(DaftarSpacing.xxs))
        BadgeTag(usd.priceBadge())
        Spacer(Modifier.height(DaftarSpacing.xxs))
        QuoteCaption(usd)
        SourceLine(quote = usd, label = stringResource(providerResource(usd.provenance.provider)))
        FxBasisBlock(usd)
        if (source != null) {
            Spacer(Modifier.height(DaftarSpacing.xs))
            SourceBeneath(source)
            SourceLine(quote = source, label = stringResource(providerResource(source.provenance.provider)))
        }
    }
}

/**
 * What the USD reference measures. Banque Misr's transfer buy is named as such, a market-rate fallback is marked as an
 * estimate, and a manual rate says when it was entered. Legacy quotes of unproven basis show no basis line.
 */
@Composable
private fun FxBasisBlock(quote: SelectedQuote) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val basis = quote.fxBasis
    if (basis == null || basis == FxBasis.LEGACY_UNKNOWN) return
    val label = when (basis) {
        FxBasis.MID_MARKET_ESTIMATE -> stringResource(
            Res.string.fx_basis_estimate,
            stringResource(providerResource(quote.provenance.provider)),
        )

        FxBasis.MANUAL -> stringResource(Res.string.fx_basis_manual, stampOf(quote.referenceAt))

        else -> stringResource(Res.string.fx_basis_bank)
    }
    val published = (quote.provenance.sourceTime as? SourceTime.Timestamp)?.value
    val times = when {
        basis != FxBasis.BANK_TRANSFER_BUY -> null

        published != null -> stringResource(
            Res.string.fx_time_published_checked,
            stampOf(published),
            stampOf(quote.referenceAt),
        )

        else -> stringResource(Res.string.fx_time_checked, stampOf(quote.referenceAt))
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(DaftarSpacing.xxs))
        Text(label, style = typography.caption, color = colors.ink)
        if (basis == FxBasis.BANK_TRANSFER_BUY) {
            Text(
                stringResource(Res.string.fx_basis_bank_explanation),
                style = typography.fineText,
                color = colors.muted,
            )
        }
        if (times != null) Text(times, style = typography.fineText, color = colors.muted)
        if (basis == FxBasis.MID_MARKET_ESTIMATE) {
            Text(stringResource(Res.string.fx_basis_fallback), style = typography.fineText, color = colors.warning)
        }
    }
}

/**
 * One source's provider line: the provider's own time, labelled apart from acquisition, and the source it came from.
 * An exchange-rate value carries the required attribution wherever it is shown; a manual value links to nothing.
 */
@Composable
private fun SourceLine(quote: SelectedQuote, label: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val uriHandler = LocalUriHandler.current
        val colors = DaftarTheme.colors
        val remote = quote.origin == QuoteOrigin.REMOTE
        Spacer(Modifier.height(DaftarSpacing.xxs))
        Text(sourceTimeText(quote.provenance.sourceTime), style = DaftarTheme.typography.fineText, color = colors.muted)
        if (!remote) return
        if (quote.provenance.provider == QuoteProvider.EXCHANGE_RATE_API) {
            val target = quote.attributionUrl ?: EXCHANGE_RATE_ATTRIBUTION_URL
            DaftarTextButton(onClick = { uriHandler.openUri(target) }) {
                Text(stringResource(Res.string.prices_attribution), style = DaftarTheme.typography.caption)
            }
        } else {
            quote.provenance.sourceUrl?.let { url ->
                DaftarTextButton(onClick = { uriHandler.openUri(url) }) {
                    Text("$label · ${url.removePrefix("https://")}", style = DaftarTheme.typography.caption)
                }
            }
        }
    }
}

@Composable
private fun sourceTimeText(time: SourceTime): String = when (time) {
    is SourceTime.Timestamp -> stringResource(Res.string.prices_source_time, stampOf(time.value))
    is SourceTime.DateOnly -> stringResource(Res.string.prices_source_date, dateOf(time.value))
    SourceTime.Unknown -> stringResource(Res.string.prices_source_unknown)
}

/** Day, month and time in Cairo, as the approved design writes them. */
@Composable
internal fun stampOf(at: Instant): String {
    val local = at.toLocalDateTime(CAIRO)
    val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    return ltr(stringResource(Res.string.prices_stamp, local.day, stringResource(MONTHS[local.month.ordinal]), time))
}

@Composable
private fun dateOf(date: LocalDate): String =
    ltr(stringResource(Res.string.prices_date, date.day, stringResource(MONTHS[date.month.ordinal]), date.year))

private fun badgeResource(badge: PriceBadge): StringResource = when (badge) {
    PriceBadge.LIVE -> Res.string.prices_badge_live
    PriceBadge.CACHED -> Res.string.prices_badge_cached
    PriceBadge.MANUAL -> Res.string.prices_badge_manual
    PriceBadge.STALE -> Res.string.prices_badge_stale
    PriceBadge.CLOCK_UNCERTAIN -> Res.string.prices_badge_clock
}

private fun providerResource(provider: QuoteProvider): StringResource = when (provider) {
    QuoteProvider.ISAGHA -> Res.string.prices_provider_isagha
    QuoteProvider.BANQUE_MISR -> Res.string.prices_provider_banque_misr
    QuoteProvider.EXCHANGE_RATE_API -> Res.string.prices_provider_exchange
    QuoteProvider.FAWAZ_CURRENCY_API -> Res.string.prices_provider_fawaz
    QuoteProvider.USER -> Res.string.prices_provider_manual
}

internal fun RefreshNotice.messageResource(): StringResource = when (this) {
    RefreshNotice.PARTIAL -> Res.string.prices_notice_partial
    RefreshNotice.RATE_LIMITED -> Res.string.prices_notice_rate_limited
    RefreshNotice.INVALID_PAYLOAD -> Res.string.prices_notice_invalid
    RefreshNotice.OFFLINE -> Res.string.prices_notice_offline
    RefreshNotice.MISSING -> Res.string.prices_notice_missing
    RefreshNotice.FAILED -> Res.string.prices_notice_failed
}

private fun missingQuoteResource(key: QuoteKey): StringResource = when (key) {
    is QuoteKey.Gold -> Res.string.prices_missing_karat
    QuoteKey.UsdEgp -> Res.string.prices_missing_usd
}

private fun missingEnterResource(key: QuoteKey): StringResource = when (key) {
    is QuoteKey.Gold -> Res.string.prices_enter_karat
    QuoteKey.UsdEgp -> Res.string.prices_enter_usd
}

/** Numbers stay left-to-right inside Arabic text, isolated from the surrounding sentence. */
internal fun ltr(text: String): String = "⁦$text⁩"

private val CAIRO: TimeZone = TimeZone.of("Africa/Cairo")

internal const val MISSING_VALUE = "—"

private val CONTENT_MAX_WIDTH = 640.dp

private const val KARAT_WEIGHT = 0.8f
private const val SELL_WEIGHT = 1.25f
private const val BUY_WEIGHT = 1f

private val MONTHS: List<StringResource> = listOf(
    Res.string.prices_month_1,
    Res.string.prices_month_2,
    Res.string.prices_month_3,
    Res.string.prices_month_4,
    Res.string.prices_month_5,
    Res.string.prices_month_6,
    Res.string.prices_month_7,
    Res.string.prices_month_8,
    Res.string.prices_month_9,
    Res.string.prices_month_10,
    Res.string.prices_month_11,
    Res.string.prices_month_12,
)
