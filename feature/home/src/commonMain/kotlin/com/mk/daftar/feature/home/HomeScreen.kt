package com.mk.daftar.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarAllocationBar
import com.mk.daftar.core.designsystem.component.DaftarAllocationSegment
import com.mk.daftar.core.designsystem.component.DaftarAmount
import com.mk.daftar.core.designsystem.component.DaftarBalanceSummary
import com.mk.daftar.core.designsystem.component.DaftarEmptyState
import com.mk.daftar.core.designsystem.component.DaftarIconButton
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarQuote
import com.mk.daftar.core.designsystem.component.DaftarQuoteStrip
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarStatusTag
import com.mk.daftar.core.designsystem.component.DaftarStatusTone
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTileTone
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.usecase.FirstRunGuideState
import com.mk.daftar.core.domain.valuation.AssetCategory
import com.mk.daftar.core.model.FxBasis
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.navigation.TopLevelDestination
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.CountNoun
import com.mk.daftar.core.ui.presentation.bankAndAccountLabel
import com.mk.daftar.core.ui.presentation.countLabel
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.RootReselectScroll
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.feature.home.resources.Res
import com.mk.daftar.feature.home.resources.badge_cached
import com.mk.daftar.feature.home.resources.badge_clock
import com.mk.daftar.feature.home.resources.badge_live
import com.mk.daftar.feature.home.resources.badge_manual
import com.mk.daftar.feature.home.resources.badge_stale
import com.mk.daftar.feature.home.resources.home_allocation
import com.mk.daftar.feature.home.resources.home_attribution
import com.mk.daftar.feature.home.resources.home_available_subtotal
import com.mk.daftar.feature.home.resources.home_banks
import com.mk.daftar.feature.home.resources.home_brand
import com.mk.daftar.feature.home.resources.home_cash
import com.mk.daftar.feature.home.resources.home_failed
import com.mk.daftar.feature.home.resources.home_gold
import com.mk.daftar.feature.home.resources.home_greeting
import com.mk.daftar.feature.home.resources.home_hidden_note
import com.mk.daftar.feature.home.resources.home_hide_values
import com.mk.daftar.feature.home.resources.home_incomplete
import com.mk.daftar.feature.home.resources.home_investments
import com.mk.daftar.feature.home.resources.home_masked_value
import com.mk.daftar.feature.home.resources.home_missing_karat
import com.mk.daftar.feature.home.resources.home_missing_usd
import com.mk.daftar.feature.home.resources.home_month_1
import com.mk.daftar.feature.home.resources.home_month_10
import com.mk.daftar.feature.home.resources.home_month_11
import com.mk.daftar.feature.home.resources.home_month_12
import com.mk.daftar.feature.home.resources.home_month_2
import com.mk.daftar.feature.home.resources.home_month_3
import com.mk.daftar.feature.home.resources.home_month_4
import com.mk.daftar.feature.home.resources.home_month_5
import com.mk.daftar.feature.home.resources.home_month_6
import com.mk.daftar.feature.home.resources.home_month_7
import com.mk.daftar.feature.home.resources.home_month_8
import com.mk.daftar.feature.home.resources.home_month_9
import com.mk.daftar.feature.home.resources.home_quote_caption
import com.mk.daftar.feature.home.resources.home_quote_details
import com.mk.daftar.feature.home.resources.home_quote_refreshing
import com.mk.daftar.feature.home.resources.home_refresh
import com.mk.daftar.feature.home.resources.home_refresh_failed
import com.mk.daftar.feature.home.resources.home_show_values
import com.mk.daftar.feature.home.resources.home_stamp
import com.mk.daftar.feature.home.resources.home_state_hidden
import com.mk.daftar.feature.home.resources.home_state_visible
import com.mk.daftar.feature.home.resources.home_total
import com.mk.daftar.feature.home.resources.home_total_basis
import com.mk.daftar.feature.home.resources.home_total_previous
import com.mk.daftar.feature.home.resources.home_unavailable
import com.mk.daftar.feature.home.resources.home_unit_egp
import com.mk.daftar.feature.home.resources.home_unit_short
import com.mk.daftar.feature.home.resources.home_usd_basis_bank
import com.mk.daftar.feature.home.resources.home_usd_basis_estimate
import com.mk.daftar.feature.home.resources.home_usd_basis_manual
import com.mk.daftar.feature.home.resources.home_zakat_prepare
import com.mk.daftar.feature.home.resources.home_zakat_review
import com.mk.daftar.feature.home.resources.p25_empty_action
import com.mk.daftar.feature.home.resources.p25_empty_body
import com.mk.daftar.feature.home.resources.p25_empty_title
import com.mk.daftar.feature.home.resources.p25_guide_bank_action
import com.mk.daftar.feature.home.resources.p25_guide_body
import com.mk.daftar.feature.home.resources.p25_guide_cash_action
import com.mk.daftar.feature.home.resources.p25_guide_dismiss
import com.mk.daftar.feature.home.resources.p25_guide_gold_body
import com.mk.daftar.feature.home.resources.p25_guide_gold_title
import com.mk.daftar.feature.home.resources.p25_guide_investment_body
import com.mk.daftar.feature.home.resources.p25_guide_investment_title
import com.mk.daftar.feature.home.resources.p25_guide_money_body
import com.mk.daftar.feature.home.resources.p25_guide_money_title
import com.mk.daftar.feature.home.resources.p25_guide_title
import com.mk.daftar.feature.home.resources.zakat_due_count
import com.mk.daftar.feature.home.resources.zakat_reminder
import com.mk.daftar.feature.home.resources.zakat_year_label
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The Home screen: the view model's state, with the navigator performing its effects. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val viewModel: HomeViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is HomeEffect.Open -> navigator.goTo(effect.route)
        }
    }
    HomeContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/**
 * The approved Home layout: the greeting with the privacy eye, the total, the allocation strip, the three category
 * rows, then the quote strip. It renders [state] only, so the screenshot host can show every state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(state: HomeUiState, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val refreshLabel = stringResource(Res.string.home_refresh)
    val hideLabel = stringResource(if (state.valuesHidden) Res.string.home_show_values else Res.string.home_hide_values)
    val visibilityState = stringResource(
        if (state.valuesHidden) Res.string.home_state_hidden else Res.string.home_state_visible,
    )
    val scroll = rememberScrollState()
    var moneyChoiceOpen by remember { mutableStateOf(false) }
    RootReselectScroll(TopLevelDestination.HOME) { scroll.animateScrollTo(0) }
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The privacy eye and the price refresh stay pinned in the compact bar, eye first, so neither scrolls away.
            DaftarTopBar(
                title = stringResource(Res.string.home_brand),
                actions = {
                    DaftarIconButton(
                        onClick = { onIntent(HomeIntent.ToggleValues) },
                        icon = if (state.valuesHidden) DaftarIcons.EyeOff else DaftarIcons.Eye,
                        contentDescription = hideLabel,
                        modifier = Modifier.semantics { stateDescription = visibilityState },
                    )
                    DaftarIconButton(
                        onClick = { onIntent(HomeIntent.Refresh) },
                        icon = DaftarIcons.Refresh,
                        contentDescription = refreshLabel,
                        enabled = !state.refreshing,
                    )
                },
            )
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { onIntent(HomeIntent.Refresh) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = DaftarSpacing.md)
                        .padding(bottom = DaftarSpacing.xl),
                ) {
                    Greeting()
                    when (val load = state.load) {
                        HomeLoad.Loading -> DaftarSkeleton()

                        is HomeLoad.Failed -> DaftarNotePanel(tone = DaftarNoteTone.Error) {
                            Text(stringResource(Res.string.home_failed))
                        }

                        is HomeLoad.Ready -> ReadyBody(
                            figures = load.figures,
                            state = state,
                            onIntent = onIntent,
                            onMoneyChoice = { moneyChoiceOpen = true },
                        )
                    }
                }
            }
        }
    }
    if (moneyChoiceOpen) {
        ModalBottomSheet(onDismissRequest = { moneyChoiceOpen = false }) {
            MoneyChoice(
                onChoose = { entry ->
                    moneyChoiceOpen = false
                    onIntent(HomeIntent.OpenFirstRunEntry(entry))
                },
            )
        }
    }
}

@Composable
private fun Greeting() {
    Text(
        stringResource(Res.string.home_greeting),
        modifier = Modifier.fillMaxWidth().padding(top = DaftarSpacing.sm),
        style = DaftarTheme.typography.supportingBody,
        color = DaftarTheme.colors.muted,
    )
}

@Composable
private fun ReadyBody(
    figures: HomeFigures,
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    onMoneyChoice: () -> Unit,
) {
    if (figures.isEmpty) {
        when (state.firstRun) {
            FirstRunGuideState.Pending -> FirstRunGuide(onIntent = onIntent, onMoneyChoice = onMoneyChoice)

            // Without records the guide is gone for good: the plain empty state takes its place.
            FirstRunGuideState.Dismissed, is FirstRunGuideState.HasRecords -> DaftarEmptyState(
                title = stringResource(Res.string.p25_empty_title),
                message = stringResource(Res.string.p25_empty_body),
                actionLabel = stringResource(Res.string.p25_empty_action),
                onAction = { onIntent(HomeIntent.AddFirstAsset) },
                icon = DaftarIcons.Book,
            )

            // Reads that have not settled show nothing, so the guide never flashes over a vault with records.
            FirstRunGuideState.Unknown -> Unit
        }
    } else {
        Total(figures = figures, hidden = state.valuesHidden, refreshing = state.refreshing)
        Categories(figures = figures, hidden = state.valuesHidden, onIntent = onIntent)
    }
    ZakatSection(due = state.zakatDue, onIntent = onIntent)
    QuoteSection(
        quotes = figures.quotes,
        refreshing = state.refreshing,
        refreshFailed = state.refreshFailed,
        attributionUrl = figures.fxAttributionUrl,
        onIntent = onIntent,
    )
    if (state.valuesHidden) {
        Spacer(Modifier.height(DaftarSpacing.md))
        DaftarNotePanel(tone = DaftarNoteTone.Context) {
            Text(stringResource(Res.string.home_hidden_note))
        }
    }
}

/**
 * The first-run guide for an empty vault: one restrained panel with three optional entry points and a visible way to
 * hide it. Each entry opens an existing add flow; nothing is required and nothing is counted down.
 */
@Composable
private fun FirstRunGuide(onIntent: (HomeIntent) -> Unit, onMoneyChoice: () -> Unit) {
    DaftarNotePanel(modifier = Modifier.fillMaxWidth(), tone = DaftarNoteTone.Context) {
        Text(
            text = stringResource(Res.string.p25_guide_title),
            style = DaftarTheme.typography.sectionHeading,
        )
        Spacer(Modifier.height(DaftarSpacing.xs))
        Text(
            text = stringResource(Res.string.p25_guide_body),
            style = DaftarTheme.typography.noteBody,
        )
        Spacer(Modifier.height(DaftarSpacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
            DaftarLedgerRow(
                title = stringResource(Res.string.p25_guide_gold_title),
                subtitle = stringResource(Res.string.p25_guide_gold_body),
                icon = DaftarIcons.GoldBar,
                tone = DaftarTileTone.Gold,
                onClick = { onIntent(HomeIntent.OpenFirstRunEntry(FirstRunEntry.GOLD)) },
                modifier = Modifier.fillMaxWidth(),
            )
            DaftarLedgerRow(
                title = stringResource(Res.string.p25_guide_money_title),
                subtitle = stringResource(Res.string.p25_guide_money_body),
                icon = DaftarIcons.Wallet,
                tone = DaftarTileTone.Bank,
                onClick = onMoneyChoice,
                modifier = Modifier.fillMaxWidth(),
            )
            DaftarLedgerRow(
                title = stringResource(Res.string.p25_guide_investment_title),
                subtitle = stringResource(Res.string.p25_guide_investment_body),
                icon = DaftarIcons.Investments,
                tone = DaftarTileTone.Investments,
                onClick = { onIntent(HomeIntent.OpenFirstRunEntry(FirstRunEntry.INVESTMENT)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DaftarTextButton(
            onClick = { onIntent(HomeIntent.DismissFirstRunGuide) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.p25_guide_dismiss))
        }
    }
}

/** The bank-or-cash choice: two actions of at least 48dp, each opening its own add flow. */
@Composable
private fun MoneyChoice(onChoose: (FirstRunEntry) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DaftarSpacing.md)
            .padding(bottom = DaftarSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
    ) {
        Text(
            text = stringResource(Res.string.p25_guide_money_title),
            style = DaftarTheme.typography.sectionHeading,
            color = DaftarTheme.colors.ink,
        )
        PrimaryButton(
            onClick = { onChoose(FirstRunEntry.BANK) },
            fullWidth = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = DaftarSpacing.buttonMinHeight),
        ) {
            Text(stringResource(Res.string.p25_guide_bank_action))
        }
        OutlineButton(
            onClick = { onChoose(FirstRunEntry.CASH) },
            fullWidth = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = DaftarSpacing.buttonMinHeight),
        ) {
            Text(stringResource(Res.string.p25_guide_cash_action))
        }
    }
}

@Composable
private fun Total(figures: HomeFigures, hidden: Boolean, refreshing: Boolean) {
    val colors = DaftarTheme.colors
    val allocation = figures.allocation
    val segments = allocation?.let { parts ->
        val whole = parts.sumOf { it.exact.toPlainString().toDouble() }.toFloat()
        parts.map { part ->
            DaftarAllocationSegment(
                weight = part.exact.toPlainString().toFloat() / whole,
                color = categoryColor(part.category),
            )
        }
    }
    // The bar draws nothing while hidden, and its description carries no value then.
    val summary = allocation?.let { parts ->
        if (hidden) {
            ""
        } else {
            stringResource(
                Res.string.home_allocation,
                percentText(parts.percentOf(AssetCategory.GOLD)),
                percentText(parts.percentOf(AssetCategory.BANKS)),
                percentText(parts.percentOf(AssetCategory.INVESTMENTS)),
                percentText(parts.percentOf(AssetCategory.CASH)),
            )
        }
    }
    DaftarBalanceSummary(
        label = stringResource(if (refreshing) Res.string.home_total_previous else Res.string.home_total),
        amount = figures.total?.moneyText(),
        // The unit sits on its own line under the amount, as in the approved design, so it never wraps mid-figure.
        unit = "",
        provenance = {
            Text(
                stringResource(Res.string.home_unit_egp),
                style = DaftarTheme.typography.fineText,
                color = colors.muted,
            )
        },
        masked = hidden,
        maskedDescription = stringResource(Res.string.home_masked_value),
        unavailableDescription = stringResource(Res.string.home_unavailable),
        allocation = if (segments != null && summary != null) {
            { DaftarAllocationBar(segments = segments, summary = summary, masked = hidden) }
        } else {
            null
        },
        footer = if (figures.incomplete) {
            { IncompleteFooter(figures = figures, hidden = hidden) }
        } else {
            {
                Text(
                    stringResource(Res.string.home_total_basis),
                    style = DaftarTheme.typography.fineText,
                    color = colors.muted,
                )
            }
        },
    )
}

@Composable
private fun ColumnScope.IncompleteFooter(figures: HomeFigures, hidden: Boolean) {
    val colors = DaftarTheme.colors
    Text(
        stringResource(Res.string.home_incomplete),
        style = DaftarTheme.typography.fineText,
        color = colors.warning,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(Res.string.home_available_subtotal),
            style = DaftarTheme.typography.fineText,
            color = colors.muted,
        )
        DaftarAmount(
            amount = figures.subtotal.moneyText(),
            unit = stringResource(Res.string.home_unit_short),
            masked = hidden,
            maskedDescription = stringResource(Res.string.home_masked_value),
            style = DaftarTheme.typography.ledgerValue,
        )
    }
    figures.missingQuotes.forEach { key ->
        DaftarNotePanel(tone = DaftarNoteTone.Warning) {
            Text(missingQuoteText(key))
        }
    }
}

@Composable
private fun Categories(figures: HomeFigures, hidden: Boolean, onIntent: (HomeIntent) -> Unit) {
    val unavailable = stringResource(Res.string.home_unavailable)
    val masked = stringResource(Res.string.home_masked_value)
    val unit = stringResource(Res.string.home_unit_short)
    val gold = figures.categoryOf(AssetCategory.GOLD)
    val banks = figures.categoryOf(AssetCategory.BANKS)
    val cash = figures.categoryOf(AssetCategory.CASH)
    val investments = figures.categoryOf(AssetCategory.INVESTMENTS)
    CategoryRow(
        title = stringResource(Res.string.home_gold),
        subtitle = countLabel(gold.count, CountNoun.HOLDINGS),
        icon = DaftarIcons.GoldBar,
        tone = DaftarTileTone.Gold,
        amount = gold.total?.moneyText(),
        hidden = hidden,
        maskedDescription = masked,
        unavailableDescription = unavailable,
        unit = unit,
        onClick = { onIntent(HomeIntent.OpenCategory(HomeCategory.GOLD)) },
    )
    CategoryRow(
        title = stringResource(Res.string.home_banks),
        subtitle = bankAndAccountLabel(banks = figures.bankCount, accounts = banks.count),
        icon = DaftarIcons.Bank,
        tone = DaftarTileTone.Bank,
        amount = banks.total?.moneyText(),
        hidden = hidden,
        maskedDescription = masked,
        unavailableDescription = unavailable,
        unit = unit,
        onClick = { onIntent(HomeIntent.OpenCategory(HomeCategory.BANKS)) },
    )
    CategoryRow(
        title = stringResource(Res.string.home_investments),
        subtitle = countLabel(investments.count, CountNoun.INVESTMENT_ACCOUNTS),
        icon = DaftarIcons.Investments,
        tone = DaftarTileTone.Investments,
        amount = investments.total?.moneyText(),
        hidden = hidden,
        maskedDescription = masked,
        unavailableDescription = unavailable,
        unit = unit,
        onClick = { onIntent(HomeIntent.OpenCategory(HomeCategory.INVESTMENTS)) },
    )
    CategoryRow(
        title = stringResource(Res.string.home_cash),
        subtitle = countLabel(cash.count, CountNoun.BALANCES),
        icon = DaftarIcons.Wallet,
        tone = DaftarTileTone.Cash,
        amount = cash.total?.moneyText(),
        hidden = hidden,
        maskedDescription = masked,
        unavailableDescription = unavailable,
        unit = unit,
        onClick = { onIntent(HomeIntent.OpenCategory(HomeCategory.CASH)) },
    )
}

@Composable
private fun CategoryRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tone: DaftarTileTone,
    amount: String?,
    hidden: Boolean,
    maskedDescription: String,
    unavailableDescription: String,
    unit: String,
    onClick: () -> Unit,
) {
    DaftarLedgerRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        tone = tone,
        onClick = onClick,
        value = {
            DaftarAmount(
                amount = amount,
                unit = unit,
                masked = hidden,
                maskedDescription = maskedDescription,
                unavailableDescription = unavailableDescription,
                style = DaftarTheme.typography.ledgerValue,
            )
        },
    )
}

@Composable
private fun QuoteSection(
    quotes: QuoteStripFigures,
    refreshing: Boolean,
    refreshFailed: Boolean,
    attributionUrl: String?,
    onIntent: (HomeIntent) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = DaftarSpacing.lg)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
        ) {
            Text(
                stringResource(Res.string.home_quote_caption),
                style = DaftarTheme.typography.caption,
                color = DaftarTheme.colors.muted,
                modifier = Modifier.weight(1f),
            )
            if (refreshing) {
                DaftarStatusTag(label = stringResource(Res.string.home_quote_refreshing))
            } else {
                quotes.badge?.let { badge ->
                    DaftarStatusTag(
                        label = stringResource(badge.labelResource()),
                        tone = if (badge == QuoteBadge.LIVE || badge == QuoteBadge.CACHED) {
                            DaftarStatusTone.Positive
                        } else {
                            DaftarStatusTone.Warning
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(DaftarSpacing.xs))
        DaftarQuoteStrip(
            quotes = quotes.quotes.map { figure ->
                DaftarQuote(label = quoteLabel(figure.key), value = figure.value?.moneyText())
            },
            provenance = quotes.referenceAt?.let { stampText(it) } ?: "",
            status = {
                DaftarTextButton(onClick = { onIntent(HomeIntent.OpenPrices) }) {
                    Text(stringResource(Res.string.home_quote_details))
                }
            },
        )
        quotes.usdBasis?.let { basis ->
            Text(
                text = stringResource(
                    when (basis) {
                        FxBasis.BANK_TRANSFER_BUY -> Res.string.home_usd_basis_bank
                        FxBasis.MANUAL -> Res.string.home_usd_basis_manual
                        else -> Res.string.home_usd_basis_estimate
                    },
                ),
                style = DaftarTheme.typography.caption,
                color = DaftarTheme.colors.muted,
            )
        }
        if (refreshFailed) {
            Spacer(Modifier.height(DaftarSpacing.xs))
            DaftarNotePanel(tone = DaftarNoteTone.Warning) {
                Text(stringResource(Res.string.home_refresh_failed))
            }
        }
        if (attributionUrl != null) {
            DaftarTextButton(onClick = { uriHandler.openUri(attributionUrl) }) {
                Text(stringResource(Res.string.home_attribution), style = DaftarTheme.typography.caption)
            }
        }
    }
}

@Composable
private fun missingQuoteText(key: QuoteKey): String = when (key) {
    is QuoteKey.Gold -> stringResource(Res.string.home_missing_karat, quoteLabel(key))
    QuoteKey.UsdEgp -> stringResource(Res.string.home_missing_usd)
}

@Composable
private fun stampText(at: Instant): String {
    val local = at.toLocalDateTime(CAIRO)
    val month = stringResource(MONTHS[local.month.ordinal])
    val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    return stringResource(Res.string.home_stamp, local.day, month, time)
}

@Composable
private fun QuoteBadge.labelResource(): StringResource = when (this) {
    QuoteBadge.LIVE -> Res.string.badge_live
    QuoteBadge.CACHED -> Res.string.badge_cached
    QuoteBadge.MANUAL -> Res.string.badge_manual
    QuoteBadge.STALE -> Res.string.badge_stale
    QuoteBadge.CLOCK_UNCERTAIN -> Res.string.badge_clock
}

private fun quoteLabel(key: QuoteKey): String = when (key) {
    is QuoteKey.Gold -> "${key.karat.carats}K"
    QuoteKey.UsdEgp -> "USD"
}

private fun HomeFigures.categoryOf(category: AssetCategory): CategoryFigures =
    categories.first { it.category == category }

private fun List<AllocationPart>.percentOf(category: AssetCategory): Int = first { it.category == category }.percent

/** The one place a percentage gets its sign, so the allocation text never doubles or drops it. */
private fun percentText(percent: Int): String = "$percent%"

@Composable
private fun categoryColor(category: AssetCategory): Color = when (category) {
    AssetCategory.GOLD -> DaftarTheme.colors.gold
    AssetCategory.BANKS -> DaftarTheme.colors.primary
    AssetCategory.CASH -> DaftarTheme.colors.cashAccent
    AssetCategory.INVESTMENTS -> DaftarTheme.colors.investmentAccent
}

private val CAIRO: TimeZone = TimeZone.of("Africa/Cairo")

private val MONTHS: List<StringResource> = listOf(
    Res.string.home_month_1,
    Res.string.home_month_2,
    Res.string.home_month_3,
    Res.string.home_month_4,
    Res.string.home_month_5,
    Res.string.home_month_6,
    Res.string.home_month_7,
    Res.string.home_month_8,
    Res.string.home_month_9,
    Res.string.home_month_10,
    Res.string.home_month_11,
    Res.string.home_month_12,
)

/**
 * The zakat entry. Normally a neutral row to the calculator. When years await confirmation, a warning reminder and a review
 * row replace it: one year opens its declaration, several open the yearly status. No amount appears here.
 */
@Composable
private fun ZakatSection(due: List<ZakatDueYear>, onIntent: (HomeIntent) -> Unit) {
    if (due.isEmpty()) {
        DaftarLedgerRow(
            title = stringResource(Res.string.home_zakat_prepare),
            icon = DaftarIcons.Heart,
            onClick = { onIntent(HomeIntent.OpenZakatCalculator) },
        )
        return
    }
    DaftarNotePanel(tone = DaftarNoteTone.Warning, icon = DaftarIcons.Clock) {
        Text(stringResource(Res.string.zakat_reminder))
    }
    DaftarLedgerRow(
        title = stringResource(Res.string.home_zakat_review),
        subtitle = if (due.size == 1) {
            stringResource(Res.string.zakat_year_label, due.single().hijriYear, due.single().gregorianYear)
        } else {
            pluralStringResource(Res.plurals.zakat_due_count, due.size, due.size)
        },
        icon = DaftarIcons.Heart,
        onClick = { onIntent(HomeIntent.OpenZakatReview) },
    )
}
