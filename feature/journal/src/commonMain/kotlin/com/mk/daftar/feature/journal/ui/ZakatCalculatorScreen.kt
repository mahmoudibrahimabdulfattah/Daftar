package com.mk.daftar.feature.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarAmount
import com.mk.daftar.core.designsystem.component.DaftarFooterAction
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarSkeletonSize
import com.mk.daftar.core.designsystem.component.DaftarStatusTag
import com.mk.daftar.core.designsystem.component.DaftarStatusTone
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTileTone
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.balance_hidden
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.valuation.AssetCategory
import com.mk.daftar.core.domain.zakat.ZakatCategoryBasis
import com.mk.daftar.core.domain.zakat.ZakatEstimate
import com.mk.daftar.core.domain.zakat.ZakatQuoteSource
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.DesktopWidths
import com.mk.daftar.core.ui.shell.PriceRefreshAction
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.desktopPage
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.resources.Res
import com.mk.daftar.feature.journal.resources.journal_load_failed_message
import com.mk.daftar.feature.journal.resources.journal_load_failed_title
import com.mk.daftar.feature.journal.resources.journal_months
import com.mk.daftar.feature.journal.resources.journal_unit_short
import com.mk.daftar.feature.journal.resources.zakat_adjust_ramadan
import com.mk.daftar.feature.journal.resources.zakat_adjusted_date
import com.mk.daftar.feature.journal.resources.zakat_asset_usd
import com.mk.daftar.feature.journal.resources.zakat_basis_available
import com.mk.daftar.feature.journal.resources.zakat_basis_details
import com.mk.daftar.feature.journal.resources.zakat_basis_total
import com.mk.daftar.feature.journal.resources.zakat_basis_total_help
import com.mk.daftar.feature.journal.resources.zakat_calc_heading
import com.mk.daftar.feature.journal.resources.zakat_calc_title
import com.mk.daftar.feature.journal.resources.zakat_category_banks
import com.mk.daftar.feature.journal.resources.zakat_category_banks_help
import com.mk.daftar.feature.journal.resources.zakat_category_cash
import com.mk.daftar.feature.journal.resources.zakat_category_cash_help
import com.mk.daftar.feature.journal.resources.zakat_category_gold
import com.mk.daftar.feature.journal.resources.zakat_category_gold_help
import com.mk.daftar.feature.journal.resources.zakat_category_investments
import com.mk.daftar.feature.journal.resources.zakat_category_investments_help
import com.mk.daftar.feature.journal.resources.zakat_clock_ahead
import com.mk.daftar.feature.journal.resources.zakat_countdown
import com.mk.daftar.feature.journal.resources.zakat_date_caveat
import com.mk.daftar.feature.journal.resources.zakat_date_line
import com.mk.daftar.feature.journal.resources.zakat_disclaimer
import com.mk.daftar.feature.journal.resources.zakat_estimate_help
import com.mk.daftar.feature.journal.resources.zakat_estimate_label
import com.mk.daftar.feature.journal.resources.zakat_estimate_loading
import com.mk.daftar.feature.journal.resources.zakat_estimate_unavailable
import com.mk.daftar.feature.journal.resources.zakat_estimated_date
import com.mk.daftar.feature.journal.resources.zakat_investments_disclosure
import com.mk.daftar.feature.journal.resources.zakat_missing_quote
import com.mk.daftar.feature.journal.resources.zakat_next_ramadan
import com.mk.daftar.feature.journal.resources.zakat_next_unavailable
import com.mk.daftar.feature.journal.resources.zakat_no_assets
import com.mk.daftar.feature.journal.resources.zakat_provenance_cached
import com.mk.daftar.feature.journal.resources.zakat_provenance_live
import com.mk.daftar.feature.journal.resources.zakat_provenance_manual
import com.mk.daftar.feature.journal.resources.zakat_provenance_mixed
import com.mk.daftar.feature.journal.resources.zakat_provenance_stale
import com.mk.daftar.feature.journal.resources.zakat_ramadan_year
import com.mk.daftar.feature.journal.resources.zakat_record_with_estimate
import com.mk.daftar.feature.journal.resources.zakat_show_values_to_record
import com.mk.daftar.feature.journal.resources.zakat_stale_line
import com.mk.daftar.feature.journal.resources.zakat_time_entered
import com.mk.daftar.feature.journal.resources.zakat_time_oldest
import com.mk.daftar.feature.journal.resources.zakat_time_stamp
import com.mk.daftar.feature.journal.resources.zakat_today
import com.mk.daftar.feature.journal.resources.zakat_yearly_status_link
import com.mk.daftar.feature.journal.resources.zakat_zero_holdings
import com.mk.daftar.feature.journal.zakat.NextRamadanUi
import com.mk.daftar.feature.journal.zakat.ZakatCalculatorIntent
import com.mk.daftar.feature.journal.zakat.ZakatCalculatorUiState
import com.mk.daftar.feature.journal.zakat.ZakatCalculatorViewModel
import kotlin.time.Instant
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The zakat calculator: the next Ramadan and today's planning estimate, always from the live valuation. */
@Composable
fun ZakatCalculatorScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: ZakatCalculatorViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect is JournalEffect.Navigate) navigator.goTo(effect.route)
    }
    ZakatCalculatorContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The calculator as it is drawn. Estimates, totals and category values are masked while values are hidden. */
@Composable
fun ZakatCalculatorContent(
    state: ZakatCalculatorUiState,
    onIntent: (ZakatCalculatorIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = DaftarTheme.typography
    val colors = DaftarTheme.colors
    val months = stringArrayResource(Res.array.journal_months)
    val hiddenText = stringResource(DesignRes.string.balance_hidden)
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(Res.string.zakat_calc_title),
                onBack = onBack,
                backLabel = stringResource(DesignRes.string.btn_back),
                actions = {
                    VisibilityToggle(hidden = state.hidden) { onIntent(ZakatCalculatorIntent.ToggleHidden) }
                    PriceRefreshAction()
                },
            )
            Column(
                modifier = Modifier
                    .desktopPage(DesktopWidths.Reading)
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.zakat_calc_heading),
                    style = typography.screenHeading,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                when (state.phase) {
                    JournalPhase.Loading -> DaftarSkeleton(size = DaftarSkeletonSize.Big)

                    JournalPhase.Locked, JournalPhase.Empty, JournalPhase.Missing -> Unit

                    is JournalPhase.Failed -> DaftarResultState(
                        title = stringResource(Res.string.journal_load_failed_title),
                        message = stringResource(Res.string.journal_load_failed_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    JournalPhase.Ready -> CalculatorBody(
                        state = state,
                        onIntent = onIntent,
                        months = months,
                        hiddenText = hiddenText,
                    )
                }
            }
            if (state.phase == JournalPhase.Ready) {
                CalculatorFooter(state = state, onIntent = onIntent)
            }
        }
    }
}

@Composable
private fun CalculatorBody(
    state: ZakatCalculatorUiState,
    onIntent: (ZakatCalculatorIntent) -> Unit,
    months: List<String>,
    hiddenText: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        val estimate = state.estimate
        NextRamadanSection(
            next = state.nextRamadan,
            unavailable = state.nextUnavailable,
            months = months,
            onIntent = onIntent,
        )
        if (estimate == null) {
            Text(
                text = stringResource(Res.string.zakat_estimate_loading),
                style = DaftarTheme.typography.fineText,
                color = DaftarTheme.colors.muted,
            )
            DaftarSkeleton()
            return
        }
        EstimateSection(estimate = estimate, hidden = state.hidden, hiddenText = hiddenText, months = months)
        BasisSection(estimate = estimate, hidden = state.hidden, hiddenText = hiddenText, months = months)
        Text(
            text = stringResource(Res.string.zakat_estimate_help),
            style = DaftarTheme.typography.fineText,
            color = DaftarTheme.colors.muted,
        )
        DaftarNotePanel(tone = DaftarNoteTone.Context) {
            Text(stringResource(Res.string.zakat_disclaimer))
        }
        OutlineButton(
            onClick = { onIntent(ZakatCalculatorIntent.OpenYearlyStatus) },
            fullWidth = true,
        ) {
            Text(stringResource(Res.string.zakat_yearly_status_link))
        }
    }
}

/** The next Ramadan with its civil date, countdown and the caveat that Egypt confirms the date by sighting. */
@Composable
private fun NextRamadanSection(
    next: NextRamadanUi?,
    unavailable: Boolean,
    months: List<String>,
    onIntent: (ZakatCalculatorIntent) -> Unit,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    if (next == null) {
        if (unavailable) {
            DaftarNotePanel(tone = DaftarNoteTone.Warning, icon = DaftarIcons.Alert) {
                Text(stringResource(Res.string.zakat_next_unavailable))
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Text(
            text = stringResource(Res.string.zakat_next_ramadan),
            style = typography.sectionHeading,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.zakat_ramadan_year, next.hijriYear),
            style = typography.ledgerTitle,
            color = colors.ink,
        )
        val date = next.date
        val dateLine = stringResource(Res.string.zakat_date_line, date.day, months[date.month.ordinal], date.year)
        val countdown = countdownText(next.daysRemaining)
        Text(
            text = "$dateLine · $countdown",
            style = typography.supportingBody,
            color = colors.ink,
        )
        DaftarStatusTag(
            label = stringResource(
                if (next.adjusted) Res.string.zakat_adjusted_date else Res.string.zakat_estimated_date,
            ),
            tone = if (next.adjusted) DaftarStatusTone.Positive else DaftarStatusTone.Warning,
        )
        Text(text = stringResource(Res.string.zakat_date_caveat), style = typography.fineText, color = colors.muted)
        DaftarTextButton(
            onClick = { onIntent(ZakatCalculatorIntent.AdjustNextRamadan) },
            fullWidth = true,
        ) {
            Text(stringResource(Res.string.zakat_adjust_ramadan))
        }
    }
}

/** "Today" on the date, otherwise the plural countdown in the language's own rules. Never negative. */
@Composable
private fun countdownText(days: Int): String = if (days <= 0) {
    stringResource(Res.string.zakat_today)
} else {
    pluralStringResource(Res.plurals.zakat_countdown, days, days)
}

/** The estimate, its label and the notes that qualify it: missing quotes, no assets, zero holdings, staleness and clock. */
@Composable
private fun EstimateSection(estimate: ZakatEstimate, hidden: Boolean, hiddenText: String, months: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        val colors = DaftarTheme.colors
        val typography = DaftarTheme.typography
        val unit = stringResource(Res.string.journal_unit_short)
        val unavailable = stringResource(Res.string.zakat_estimate_unavailable)
        Text(
            text = stringResource(Res.string.zakat_estimate_label),
            style = typography.fineText,
            color = colors.muted,
        )
        val amount = when (estimate) {
            is ZakatEstimate.Available -> estimate.estimateEgp.moneyText()
            is ZakatEstimate.Unavailable -> null
        }
        DaftarAmount(
            amount = if (hidden) null else amount,
            unit = unit,
            masked = hidden,
            maskedDescription = hiddenText,
            unavailableDescription = unavailable,
            color = colors.ink,
        )
        when (estimate) {
            is ZakatEstimate.Unavailable -> estimate.missingQuotes.forEach { key ->
                DaftarNotePanel(tone = DaftarNoteTone.Warning, icon = DaftarIcons.Alert) {
                    Text(stringResource(Res.string.zakat_missing_quote, assetName(key)))
                }
            }

            is ZakatEstimate.Available -> {
                val oldest = estimate.oldestQuoteAt?.let { stampText(it, months) }
                when {
                    estimate.isEmpty -> DaftarNotePanel(tone = DaftarNoteTone.Context) {
                        Text(stringResource(Res.string.zakat_no_assets))
                    }

                    estimate.totalEgp <= Decimal.ZERO -> DaftarNotePanel(tone = DaftarNoteTone.Context) {
                        Text(stringResource(Res.string.zakat_zero_holdings))
                    }

                    estimate.stale && oldest != null -> DaftarNotePanel(
                        tone = DaftarNoteTone.Warning,
                        icon = DaftarIcons.Clock,
                    ) {
                        Text(
                            stringResource(
                                Res.string.zakat_stale_line,
                                stringResource(Res.string.zakat_time_oldest, oldest),
                            ),
                        )
                    }

                    estimate.clockAhead -> DaftarNotePanel(tone = DaftarNoteTone.Warning, icon = DaftarIcons.Clock) {
                        Text(stringResource(Res.string.zakat_clock_ahead))
                    }

                    oldest != null -> Text(
                        text = stringResource(Res.string.zakat_time_oldest, oldest),
                        style = typography.fineText,
                        color = colors.muted,
                    )
                }
            }
        }
    }
}

/** Total assets, the basis by category and its provenance. An unavailable total shows only the labelled subtotal. */
@Composable
private fun BasisSection(estimate: ZakatEstimate, hidden: Boolean, hiddenText: String, months: List<String>) {
    val unit = stringResource(Res.string.journal_unit_short)
    val unavailable = stringResource(Res.string.zakat_estimate_unavailable)
    val total = (estimate as? ZakatEstimate.Available)?.totalEgp?.moneyText()
    DaftarLedgerRow(
        title = stringResource(Res.string.zakat_basis_total),
        subtitle = stringResource(Res.string.zakat_basis_total_help),
        icon = DaftarIcons.Book,
        value = {
            DaftarAmount(
                amount = if (hidden) null else total,
                unit = unit,
                masked = hidden,
                maskedDescription = hiddenText,
                unavailableDescription = unavailable,
                style = DaftarTheme.typography.ledgerValue,
                color = DaftarTheme.colors.ink,
            )
        },
    )
    if (estimate is ZakatEstimate.Unavailable) {
        DaftarLedgerRow(
            title = stringResource(Res.string.zakat_basis_available),
            icon = DaftarIcons.Book,
            value = {
                DaftarAmount(
                    amount = if (hidden) null else estimate.availableSubtotalEgp.moneyText(),
                    unit = unit,
                    masked = hidden,
                    maskedDescription = hiddenText,
                    style = DaftarTheme.typography.ledgerValue,
                    color = DaftarTheme.colors.ink,
                )
            },
        )
    }
    if (estimate is ZakatEstimate.Available) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        DaftarTextButton(onClick = { expanded = !expanded }, fullWidth = true) {
            Text(stringResource(Res.string.zakat_basis_details))
        }
        if (expanded) {
            BasisDetails(estimate = estimate, hidden = hidden, hiddenText = hiddenText, unit = unit, months = months)
        }
    }
}

/** The expanded basis: each category's figure, the planning disclosure, and the provenance of the prices. */
@Composable
private fun BasisDetails(
    estimate: ZakatEstimate.Available,
    hidden: Boolean,
    hiddenText: String,
    unit: String,
    months: List<String>,
) {
    Column {
        estimate.categories.forEach { basis ->
            CategoryRow(basis = basis, hidden = hidden, hiddenText = hiddenText, unit = unit)
        }
        Text(
            text = stringResource(Res.string.zakat_investments_disclosure),
            style = DaftarTheme.typography.fineText,
            color = DaftarTheme.colors.muted,
        )
        Text(
            text = provenanceText(estimate, months),
            style = DaftarTheme.typography.fineText,
            color = DaftarTheme.colors.muted,
        )
    }
}

@Composable
private fun CategoryRow(basis: ZakatCategoryBasis, hidden: Boolean, hiddenText: String, unit: String) {
    DaftarLedgerRow(
        title = stringResource(categoryTitle(basis.category)),
        subtitle = stringResource(categoryHelp(basis.category)),
        icon = categoryIcon(basis.category),
        tone = when (basis.category) {
            AssetCategory.GOLD -> DaftarTileTone.Gold
            AssetCategory.BANKS -> DaftarTileTone.Bank
            AssetCategory.CASH -> DaftarTileTone.Cash
            AssetCategory.INVESTMENTS -> DaftarTileTone.Investments
        },
        value = {
            DaftarAmount(
                amount = if (hidden) null else basis.totalEgp?.moneyText(),
                unit = unit,
                masked = hidden,
                maskedDescription = hiddenText,
                style = DaftarTheme.typography.ledgerValue,
                color = DaftarTheme.colors.ink,
            )
        },
    )
}

/** The calculator's action: record with the displayed estimate, or show values first, or a disabled unavailable state. */
@Composable
private fun CalculatorFooter(state: ZakatCalculatorUiState, onIntent: (ZakatCalculatorIntent) -> Unit) {
    val estimate = state.estimate ?: return
    // A zero or empty estimate offers no payment at all: a payment amount must be positive.
    if (estimate is ZakatEstimate.Available && estimate.prefillEgp == null) return
    DaftarFooterAction {
        when {
            estimate is ZakatEstimate.Unavailable -> PrimaryButton(onClick = {}, fullWidth = true, enabled = false) {
                Text(stringResource(Res.string.zakat_estimate_unavailable))
            }

            state.hidden -> OutlineButton(
                onClick = { onIntent(ZakatCalculatorIntent.RevealForRecord) },
                fullWidth = true,
            ) {
                Text(stringResource(Res.string.zakat_show_values_to_record))
            }

            else -> PrimaryButton(
                onClick = { onIntent(ZakatCalculatorIntent.RecordWithEstimate) },
                fullWidth = true,
            ) {
                Text(stringResource(Res.string.zakat_record_with_estimate))
            }
        }
    }
}

@Composable
private fun assetName(key: QuoteKey): String = stringResource(
    when (key) {
        is QuoteKey.Gold -> Res.string.zakat_category_gold
        QuoteKey.UsdEgp -> Res.string.zakat_asset_usd
    },
)

/** A quote's time in Cairo, as the copy contract shows it: day, month name, year and HH:mm. */
@Composable
private fun stampText(at: Instant, months: List<String>): String {
    val stamp = cairoStampParts(at)
    val date = stamp.date
    return stringResource(Res.string.zakat_time_stamp, date.day, months[date.month.ordinal], date.year, stamp.time)
}

/** The source label, the stale flag and the oldest quote's time, joined in that order. */
@Composable
private fun provenanceText(estimate: ZakatEstimate.Available, months: List<String>): String {
    val source = when (estimate.quoteSource) {
        ZakatQuoteSource.LIVE -> stringResource(Res.string.zakat_provenance_live)
        ZakatQuoteSource.CACHED -> stringResource(Res.string.zakat_provenance_cached)
        ZakatQuoteSource.MANUAL -> stringResource(Res.string.zakat_provenance_manual)
        ZakatQuoteSource.MIXED -> stringResource(Res.string.zakat_provenance_mixed)
        null -> null
    }
    val oldest = estimate.oldestQuoteAt?.let { stampText(it, months) }
    val time = oldest?.let {
        if (estimate.quoteSource == ZakatQuoteSource.MANUAL) {
            stringResource(Res.string.zakat_time_entered, it)
        } else {
            stringResource(Res.string.zakat_time_oldest, it)
        }
    }
    val stale = if (estimate.stale) stringResource(Res.string.zakat_provenance_stale) else null
    return listOfNotNull(source, stale, time).joinToString(" · ")
}

private fun categoryTitle(category: AssetCategory): StringResource = when (category) {
    AssetCategory.GOLD -> Res.string.zakat_category_gold
    AssetCategory.BANKS -> Res.string.zakat_category_banks
    AssetCategory.CASH -> Res.string.zakat_category_cash
    AssetCategory.INVESTMENTS -> Res.string.zakat_category_investments
}

private fun categoryHelp(category: AssetCategory): StringResource = when (category) {
    AssetCategory.GOLD -> Res.string.zakat_category_gold_help
    AssetCategory.BANKS -> Res.string.zakat_category_banks_help
    AssetCategory.CASH -> Res.string.zakat_category_cash_help
    AssetCategory.INVESTMENTS -> Res.string.zakat_category_investments_help
}

private fun categoryIcon(category: AssetCategory): ImageVector = when (category) {
    AssetCategory.GOLD -> DaftarIcons.GoldBar
    AssetCategory.BANKS -> DaftarIcons.Bank
    AssetCategory.CASH -> DaftarIcons.Wallet
    AssetCategory.INVESTMENTS -> DaftarIcons.Investments
}
