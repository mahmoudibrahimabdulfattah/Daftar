package com.mk.daftar.feature.banks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarAmount
import com.mk.daftar.core.designsystem.component.DaftarBalanceSummary
import com.mk.daftar.core.designsystem.component.DaftarEmptyState
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarSkeletonSize
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.balance_hidden
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.navigation.BankDetailRoute
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.LocalSimultaneousPanes
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.feature.banks.BankListIntent
import com.mk.daftar.feature.banks.BankListUiState
import com.mk.daftar.feature.banks.BankPhase
import com.mk.daftar.feature.banks.BankRow
import com.mk.daftar.feature.banks.BanksListViewModel
import com.mk.daftar.feature.banks.resources.Res
import com.mk.daftar.feature.banks.resources.banks_account_value
import com.mk.daftar.feature.banks.resources.banks_accounts_count
import com.mk.daftar.feature.banks.resources.banks_add
import com.mk.daftar.feature.banks.resources.banks_available_subtotal
import com.mk.daftar.feature.banks.resources.banks_egp_unit
import com.mk.daftar.feature.banks.resources.banks_empty_message
import com.mk.daftar.feature.banks.resources.banks_empty_title
import com.mk.daftar.feature.banks.resources.banks_failed_message
import com.mk.daftar.feature.banks.resources.banks_failed_title
import com.mk.daftar.feature.banks.resources.banks_row_summary
import com.mk.daftar.feature.banks.resources.banks_stale_note
import com.mk.daftar.feature.banks.resources.banks_title
import com.mk.daftar.feature.banks.resources.banks_unavailable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The bank list pane. It reads the shell's stack for the selected bank, and opens details and the add form through effects. */
@Composable
fun BanksListScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: BanksListViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect -> handleBankEffect(navigator, effect) }
    BankListContent(
        state = state,
        selectedBankId = (navigator.stackOf(navigator.topLevel).lastOrNull() as? BankDetailRoute)?.bankId,
        showsBack = !LocalSimultaneousPanes.current,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

/**
 * The bank list as it is drawn. Amounts are removed from rendering and semantics while values are hidden. The selected row
 * is the bank whose detail is open beside it.
 */
@Composable
fun BankListContent(
    state: BankListUiState,
    selectedBankId: String?,
    showsBack: Boolean,
    onBack: () -> Unit,
    onIntent: (BankListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val hiddenText = stringResource(DesignRes.string.balance_hidden)
    val unavailable = stringResource(Res.string.banks_unavailable)
    val egpUnit = stringResource(Res.string.banks_egp_unit)
    ShellScreen(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = DaftarSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            DaftarTopBar(
                title = stringResource(Res.string.banks_title),
                onBack = if (showsBack) onBack else null,
                backLabel = stringResource(DesignRes.string.btn_back),
                actions = { VisibilityToggle(hidden = state.hidden) { onIntent(BankListIntent.ToggleValues) } },
            )
            when (state.phase) {
                BankPhase.Loading -> DaftarSkeleton(size = DaftarSkeletonSize.Big)

                BankPhase.Locked, BankPhase.Missing -> Unit

                is BankPhase.Failed -> DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
                    Text(stringResource(Res.string.banks_failed_title))
                    Text(stringResource(Res.string.banks_failed_message))
                }

                BankPhase.Empty -> DaftarEmptyState(
                    title = stringResource(Res.string.banks_empty_title),
                    message = stringResource(Res.string.banks_empty_message),
                    actionLabel = stringResource(Res.string.banks_add),
                    onAction = { onIntent(BankListIntent.AddBank) },
                    icon = DaftarIcons.Bank,
                )

                BankPhase.Ready -> {
                    state.total?.let { total ->
                        val subtotalLabel: (@Composable () -> Unit)? = if (total.complete) {
                            null
                        } else {
                            { Text(stringResource(Res.string.banks_available_subtotal)) }
                        }
                        DaftarBalanceSummary(
                            label = stringResource(Res.string.banks_account_value),
                            amount = if (state.hidden) null else total.amount.moneyText(),
                            unit = egpUnit,
                            masked = state.hidden,
                            maskedDescription = hiddenText,
                            unavailableDescription = unavailable,
                            provenance = subtotalLabel,
                        )
                        if (total.warnings.isNotEmpty() && !state.hidden) {
                            DaftarNotePanel(tone = DaftarNoteTone.Warning, icon = DaftarIcons.Alert) {
                                Text(stringResource(Res.string.banks_stale_note))
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
                    ) {
                        items(state.rows, key = { it.id }) { row ->
                            BankListRowItem(
                                row = row,
                                hidden = state.hidden,
                                selected = row.id == selectedBankId,
                                hiddenText = hiddenText,
                                unavailable = unavailable,
                                egpUnit = egpUnit,
                                onClick = { onIntent(BankListIntent.OpenBank(row.id)) },
                            )
                        }
                    }
                    OutlineButton(
                        onClick = { onIntent(BankListIntent.AddBank) },
                        fullWidth = true,
                        leadingIcon = DaftarIcons.Plus,
                    ) {
                        Text(stringResource(Res.string.banks_add), style = typography.buttonLabel, color = colors.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun BankListRowItem(
    row: BankRow,
    hidden: Boolean,
    selected: Boolean,
    hiddenText: String,
    unavailable: String,
    egpUnit: String,
    onClick: () -> Unit,
) {
    val countText = pluralStringResource(Res.plurals.banks_accounts_count, row.accountCount, row.accountCount)
    val currencies = row.currencies.joinToString(" / ") { "⁦${it.name}⁩" }
    DaftarLedgerRow(
        title = row.name,
        subtitle = if (currencies.isEmpty()) {
            countText
        } else {
            stringResource(Res.string.banks_row_summary, countText, currencies)
        },
        icon = DaftarIcons.Bank,
        selected = selected,
        onClick = onClick,
        value = {
            DaftarAmount(
                amount = if (hidden) null else row.valueEgp?.moneyText(),
                unit = egpUnit,
                masked = hidden,
                maskedDescription = hiddenText,
                unavailableDescription = unavailable,
                style = DaftarTheme.typography.ledgerValue,
                color = DaftarTheme.colors.ink,
            )
        },
    )
}
