package com.mk.daftar.feature.gold.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarAmount
import com.mk.daftar.core.designsystem.component.DaftarEmptyState
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarSkeletonSize
import com.mk.daftar.core.designsystem.component.DaftarStatusTag
import com.mk.daftar.core.designsystem.component.DaftarStatusTone
import com.mk.daftar.core.designsystem.component.DaftarTileTone
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.balance_hidden
import com.mk.daftar.core.designsystem.resources.balance_unavailable
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.resources.status_cached
import com.mk.daftar.core.designsystem.resources.status_live
import com.mk.daftar.core.designsystem.resources.status_manual
import com.mk.daftar.core.designsystem.resources.status_stale
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.valuation.QuoteFreshness
import com.mk.daftar.core.domain.valuation.QuoteOrigin
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.feature.gold.GoldEffect
import com.mk.daftar.feature.gold.GoldPhase
import com.mk.daftar.feature.gold.list.GoldHoldingRow
import com.mk.daftar.feature.gold.list.GoldListIntent
import com.mk.daftar.feature.gold.list.GoldListUiState
import com.mk.daftar.feature.gold.list.GoldListViewModel
import com.mk.daftar.feature.gold.list.GoldQuoteLine
import com.mk.daftar.feature.gold.list.GoldSummary
import com.mk.daftar.feature.gold.resources.Res
import com.mk.daftar.feature.gold.resources.gold_add
import com.mk.daftar.feature.gold.resources.gold_bars
import com.mk.daftar.feature.gold.resources.gold_clock_warning
import com.mk.daftar.feature.gold.resources.gold_empty_message
import com.mk.daftar.feature.gold.resources.gold_empty_title
import com.mk.daftar.feature.gold.resources.gold_incomplete
import com.mk.daftar.feature.gold.resources.gold_jewelry
import com.mk.daftar.feature.gold.resources.gold_karat_label
import com.mk.daftar.feature.gold.resources.gold_load_failed_message
import com.mk.daftar.feature.gold.resources.gold_load_failed_title
import com.mk.daftar.feature.gold.resources.gold_manual_warning
import com.mk.daftar.feature.gold.resources.gold_months
import com.mk.daftar.feature.gold.resources.gold_quote_heading
import com.mk.daftar.feature.gold.resources.gold_quote_line
import com.mk.daftar.feature.gold.resources.gold_quote_none
import com.mk.daftar.feature.gold.resources.gold_recorded_at
import com.mk.daftar.feature.gold.resources.gold_row_title
import com.mk.daftar.feature.gold.resources.gold_row_weight
import com.mk.daftar.feature.gold.resources.gold_row_weight_note
import com.mk.daftar.feature.gold.resources.gold_stale_warning
import com.mk.daftar.feature.gold.resources.gold_status_clock
import com.mk.daftar.feature.gold.resources.gold_subtotal
import com.mk.daftar.feature.gold.resources.gold_title
import com.mk.daftar.feature.gold.resources.gold_unit_egp
import com.mk.daftar.feature.gold.resources.gold_value_label
import com.mk.daftar.feature.gold.resources.gold_value_unit
import com.mk.daftar.feature.gold.resources.gold_values_hidden
import com.mk.daftar.feature.gold.resources.gold_weight_notice
import com.mk.daftar.feature.gold.resources.gold_weight_notice_hidden
import com.mk.daftar.feature.gold.resources.gold_weight_notice_single
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The gold category and its holdings. Amounts, weights and notes are removed from rendering and semantics while hidden. */
@Composable
fun GoldListScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: GoldListViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect is GoldEffect.Navigate) navigator.goTo(effect.route)
    }
    GoldListContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The gold list as it is drawn. Every sensitive value passes through the masked path while values are hidden. */
@Composable
fun GoldListContent(
    state: GoldListUiState,
    onIntent: (GoldListIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hiddenText = stringResource(DesignRes.string.balance_hidden)
    val unavailableText = stringResource(DesignRes.string.balance_unavailable)
    ShellScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DaftarSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            DaftarTopBar(
                title = stringResource(Res.string.gold_title),
                onBack = onBack,
                backLabel = stringResource(DesignRes.string.btn_back),
                actions = { VisibilityToggle(hidden = state.hidden) { onIntent(GoldListIntent.ToggleHidden) } },
            )
            if (state.pendingRoute != null) {
                RevealPrompt(
                    onReveal = { onIntent(GoldListIntent.RevealAndOpen) },
                    onCancel = { onIntent(GoldListIntent.CancelReveal) },
                )
            }
            when (state.phase) {
                GoldPhase.Loading -> DaftarSkeleton(size = DaftarSkeletonSize.Big)

                GoldPhase.Locked, GoldPhase.Missing -> Unit

                is GoldPhase.Failed -> DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
                    Text(stringResource(Res.string.gold_load_failed_title))
                    Text(stringResource(Res.string.gold_load_failed_message))
                }

                GoldPhase.Empty -> DaftarEmptyState(
                    title = stringResource(Res.string.gold_empty_title),
                    message = stringResource(Res.string.gold_empty_message),
                    actionLabel = stringResource(Res.string.gold_add),
                    onAction = { onIntent(GoldListIntent.OpenAdd) },
                    icon = DaftarIcons.Gold,
                )

                GoldPhase.Ready -> GoldReadyContent(
                    state = state,
                    hiddenText = hiddenText,
                    unavailableText = unavailableText,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun GoldReadyContent(
    state: GoldListUiState,
    hiddenText: String,
    unavailableText: String,
    onIntent: (GoldListIntent) -> Unit,
) {
    val summary = state.summary ?: return
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        val months = stringArrayResource(Res.array.gold_months)
        val colors = DaftarTheme.colors
        val typography = DaftarTheme.typography
        Text(
            text = stringResource(Res.string.gold_value_label),
            style = typography.fineText,
            color = colors.muted,
        )
        DaftarAmount(
            amount = if (state.hidden) null else summary.totalEgp?.moneyText(),
            unit = "",
            masked = state.hidden,
            maskedDescription = hiddenText,
            unavailableDescription = unavailableText,
            style = typography.heroAmount,
            color = colors.ink,
        )
        Text(
            text = stringResource(Res.string.gold_value_unit),
            style = typography.fineText,
            color = colors.muted,
        )
        SubtotalNote(summary = summary, hidden = state.hidden)
        if (state.quotes.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.gold_quote_heading),
                style = typography.fineText,
                color = colors.muted,
            )
            state.quotes.forEach { line -> QuoteRow(line = line, months = months) }
            QuoteWarnings(lines = state.quotes)
        }
        WeightNotice(state = state, summary = summary)
        state.rows.forEach { row ->
            GoldRow(row = row, hidden = state.hidden, hiddenText = hiddenText, unavailableText = unavailableText) {
                onIntent(GoldListIntent.OpenHolding(row.id))
            }
        }
        PrimaryButton(
            onClick = { onIntent(GoldListIntent.OpenAdd) },
            fullWidth = true,
            leadingIcon = DaftarIcons.Plus,
        ) {
            Text(stringResource(Res.string.gold_add))
        }
    }
}

/** The available subtotal and the incomplete note. While hidden, the subtotal is withheld entirely. */
@Composable
private fun SubtotalNote(summary: GoldSummary, hidden: Boolean) {
    if (summary.unvaluedCount == 0) return
    val typography = DaftarTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xxs)) {
        Text(
            text = if (hidden) {
                stringResource(Res.string.gold_values_hidden)
            } else {
                stringResource(Res.string.gold_subtotal, summary.subtotalEgp.moneyText())
            },
            style = typography.fineText,
            color = DaftarTheme.colors.muted,
        )
        Text(
            text = stringResource(Res.string.gold_incomplete),
            style = typography.fineText,
            color = DaftarTheme.colors.muted,
        )
    }
}

/** One karat's merchant sell quote, with its provenance: where it came from, and when it was recorded. */
@Composable
private fun QuoteRow(line: GoldQuoteLine, months: List<String>) {
    val typography = DaftarTheme.typography
    val karat = stringResource(Res.string.gold_karat_label, line.karat.carats)
    val quote = line.quote
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xxs)) {
        if (quote == null) {
            Text(
                text = stringResource(Res.string.gold_quote_none, karat),
                style = typography.fineText,
                color = DaftarTheme.colors.muted,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.gold_quote_line, karat, quote.value.moneyText()),
                    style = typography.fineText,
                    color = DaftarTheme.colors.ink,
                )
                val (label, tone) = provenanceTag(quote)
                DaftarStatusTag(label = stringResource(label), tone = tone)
            }
            line.recordedAt?.let { recorded ->
                Text(
                    text = stringResource(
                        Res.string.gold_recorded_at,
                        recorded.date.day,
                        months[recorded.date.month.ordinal],
                        recorded.clockText(),
                    ),
                    style = typography.fineText,
                    color = DaftarTheme.colors.muted,
                )
            }
        }
    }
}

/** The tag for a quote's origin and age. A manual or stale quote is a warning, never a silent value. */
private fun provenanceTag(quote: SelectedQuote): Pair<StringResource, DaftarStatusTone> = when {
    quote.freshness == QuoteFreshness.CLOCK_UNCERTAIN -> Res.string.gold_status_clock to DaftarStatusTone.Warning
    quote.freshness == QuoteFreshness.STALE -> DesignRes.string.status_stale to DaftarStatusTone.Warning
    quote.origin != QuoteOrigin.REMOTE -> DesignRes.string.status_manual to DaftarStatusTone.Warning
    quote.provenance.kind == QuoteKind.LIVE -> DesignRes.string.status_live to DaftarStatusTone.Positive
    else -> DesignRes.string.status_cached to DaftarStatusTone.Positive
}

@Composable
private fun QuoteWarnings(lines: List<GoldQuoteLine>) {
    val quotes = lines.mapNotNull { it.quote }
    if (quotes.any { it.origin != QuoteOrigin.REMOTE }) {
        DaftarNotePanel(tone = DaftarNoteTone.Warning, icon = DaftarIcons.Alert) {
            Text(stringResource(Res.string.gold_manual_warning))
        }
    }
    if (quotes.any { it.freshness == QuoteFreshness.STALE }) {
        DaftarNotePanel(tone = DaftarNoteTone.Warning, icon = DaftarIcons.Alert) {
            Text(stringResource(Res.string.gold_stale_warning))
        }
    }
    if (quotes.any { it.freshness == QuoteFreshness.CLOCK_UNCERTAIN }) {
        DaftarNotePanel(tone = DaftarNoteTone.Warning, icon = DaftarIcons.Alert) {
            Text(stringResource(Res.string.gold_clock_warning))
        }
    }
}

/** The total weight. Karats are never mixed into one fine-gold figure, and while hidden the weight is not read out. */
@Composable
private fun WeightNotice(state: GoldListUiState, summary: GoldSummary) {
    val distinctKarats = state.rows.map { it.karat }.distinct().size
    DaftarNotePanel(tone = DaftarNoteTone.Context) {
        Text(
            text = when {
                state.hidden -> stringResource(Res.string.gold_weight_notice_hidden)
                distinctKarats > 1 -> stringResource(Res.string.gold_weight_notice, summary.totalWeight.toPlainString())
                else -> stringResource(Res.string.gold_weight_notice_single, summary.totalWeight.toPlainString())
            },
        )
    }
}

@Composable
private fun GoldRow(
    row: GoldHoldingRow,
    hidden: Boolean,
    hiddenText: String,
    unavailableText: String,
    onOpen: () -> Unit,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    DaftarLedgerRow(
        title = stringResource(
            Res.string.gold_row_title,
            stringResource(formLabel(row.form)),
            stringResource(Res.string.gold_karat_label, row.karat.carats),
        ),
        subtitle = if (hidden) null else weightLine(row),
        icon = DaftarIcons.Gold,
        tone = DaftarTileTone.Gold,
        onClick = onOpen,
        value = {
            DaftarAmount(
                amount = if (hidden) null else row.exactEgp?.moneyText(),
                unit = stringResource(Res.string.gold_unit_egp),
                masked = hidden,
                maskedDescription = hiddenText,
                unavailableDescription = unavailableText,
                style = typography.ledgerValue,
                color = colors.ink,
            )
        },
    )
}

@Composable
private fun weightLine(row: GoldHoldingRow): String {
    val weight = row.weight.toPlainString()
    val note = row.note
    return if (note == null) {
        stringResource(Res.string.gold_row_weight, weight)
    } else {
        stringResource(Res.string.gold_row_weight_note, weight, note)
    }
}

internal fun formLabel(form: GoldForm): StringResource = when (form) {
    GoldForm.BAR -> Res.string.gold_bars
    GoldForm.JEWELRY -> Res.string.gold_jewelry
}
