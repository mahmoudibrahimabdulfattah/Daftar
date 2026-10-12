package com.mk.daftar.feature.assets

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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarAmount
import com.mk.daftar.core.designsystem.component.DaftarIconButton
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTileTone
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.valuation.AssetCategory
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.CountNoun
import com.mk.daftar.core.ui.presentation.bankAndAccountLabel
import com.mk.daftar.core.ui.presentation.countLabel
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.feature.assets.resources.Res
import com.mk.daftar.feature.assets.resources.assets_banks
import com.mk.daftar.feature.assets.resources.assets_cash
import com.mk.daftar.feature.assets.resources.assets_failed
import com.mk.daftar.feature.assets.resources.assets_fx_attribution
import com.mk.daftar.feature.assets.resources.assets_gold
import com.mk.daftar.feature.assets.resources.assets_hidden_note
import com.mk.daftar.feature.assets.resources.assets_hide_values
import com.mk.daftar.feature.assets.resources.assets_incomplete
import com.mk.daftar.feature.assets.resources.assets_masked_value
import com.mk.daftar.feature.assets.resources.assets_no_records
import com.mk.daftar.feature.assets.resources.assets_note_manual
import com.mk.daftar.feature.assets.resources.assets_show_values
import com.mk.daftar.feature.assets.resources.assets_state_hidden
import com.mk.daftar.feature.assets.resources.assets_state_visible
import com.mk.daftar.feature.assets.resources.assets_subtitle
import com.mk.daftar.feature.assets.resources.assets_title
import com.mk.daftar.feature.assets.resources.unit_egp_short
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The Assets overview. Its rows open their categories through the navigator. */
@Composable
fun AssetsScreen(modifier: Modifier = Modifier) {
    val viewModel: AssetsOverviewViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is AssetsEffect.Open -> navigator.goTo(effect.route)
        }
    }
    AssetsContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/** The approved overview layout. It renders [state] only, so the screenshot host can show every state. */
@Composable
fun AssetsContent(state: AssetsUiState, onIntent: (AssetsIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = DaftarTheme.colors
    val hidden = state.valuesHidden
    val label = stringResource(if (hidden) Res.string.assets_show_values else Res.string.assets_hide_values)
    val stateLabel = stringResource(if (hidden) Res.string.assets_state_hidden else Res.string.assets_state_visible)
    ShellScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DaftarSpacing.md)
                .padding(bottom = DaftarSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            DaftarTopBar(
                title = stringResource(Res.string.assets_title),
                actions = {
                    DaftarIconButton(
                        onClick = { onIntent(AssetsIntent.ToggleValues) },
                        icon = if (hidden) DaftarIcons.EyeOff else DaftarIcons.Eye,
                        contentDescription = label,
                        modifier = Modifier.semantics { stateDescription = stateLabel },
                    )
                },
            )
            Text(
                text = stringResource(Res.string.assets_subtitle),
                style = DaftarTheme.typography.supportingBody,
                color = colors.muted,
            )
            when (val load = state.load) {
                AssetsLoad.Loading -> DaftarSkeleton()

                is AssetsLoad.Failed -> DaftarNotePanel(tone = DaftarNoteTone.Error) {
                    Text(stringResource(Res.string.assets_failed))
                }

                is AssetsLoad.Ready -> AssetsRows(figures = load.figures, hidden = hidden, onIntent = onIntent)
            }
            if (hidden) {
                DaftarNotePanel(tone = DaftarNoteTone.Context) {
                    Text(stringResource(Res.string.assets_hidden_note))
                }
            }
            DaftarNotePanel(tone = DaftarNoteTone.Context) {
                Text(stringResource(Res.string.assets_note_manual))
            }
        }
    }
}

@Composable
private fun AssetsRows(figures: AssetsFigures, hidden: Boolean, onIntent: (AssetsIntent) -> Unit) {
    val masked = stringResource(Res.string.assets_masked_value)
    val noRecords = stringResource(Res.string.assets_no_records)
    val unit = stringResource(Res.string.unit_egp_short)
    if (figures.incomplete) {
        DaftarNotePanel(tone = DaftarNoteTone.Warning) {
            Text(stringResource(Res.string.assets_incomplete))
        }
    }
    AssetRow(
        title = stringResource(Res.string.assets_gold),
        subtitle = countLabel(figures.gold.count, CountNoun.HOLDINGS),
        icon = DaftarIcons.Gold,
        tone = DaftarTileTone.Gold,
        figures = figures.gold,
        hidden = hidden,
        masked = masked,
        noRecords = noRecords,
        unit = unit,
        onClick = { onIntent(AssetsIntent.OpenCategory(AssetCategory.GOLD)) },
    )
    AssetRow(
        title = stringResource(Res.string.assets_banks),
        subtitle = bankAndAccountLabel(banks = figures.bankCount, accounts = figures.banks.count),
        icon = DaftarIcons.Bank,
        tone = DaftarTileTone.Default,
        figures = figures.banks,
        hidden = hidden,
        masked = masked,
        noRecords = noRecords,
        unit = unit,
        onClick = { onIntent(AssetsIntent.OpenCategory(AssetCategory.BANKS)) },
    )
    AssetRow(
        title = stringResource(Res.string.assets_cash),
        subtitle = countLabel(figures.cash.count, CountNoun.BALANCES),
        icon = DaftarIcons.Wallet,
        tone = DaftarTileTone.Default,
        figures = figures.cash,
        hidden = hidden,
        masked = masked,
        noRecords = noRecords,
        unit = unit,
        onClick = { onIntent(AssetsIntent.OpenCategory(AssetCategory.CASH)) },
    )
    figures.fxAttributionUrl?.let { url ->
        val uriHandler = LocalUriHandler.current
        DaftarTextButton(onClick = { uriHandler.openUri(url) }) {
            Text(stringResource(Res.string.assets_fx_attribution), style = DaftarTheme.typography.caption)
        }
    }
}

@Composable
private fun AssetRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: DaftarTileTone,
    figures: AssetsCategoryFigures,
    hidden: Boolean,
    masked: String,
    noRecords: String,
    unit: String,
    onClick: () -> Unit,
) {
    // A category with no records has no value to show; it is not a zero total.
    val amount = if (figures.count == 0) null else figures.total?.moneyText()
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
                maskedDescription = masked,
                unavailableDescription = noRecords,
                style = DaftarTheme.typography.ledgerValue,
            )
        },
    )
}
