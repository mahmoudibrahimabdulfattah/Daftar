package com.mk.daftar.feature.banks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarAmount
import com.mk.daftar.core.designsystem.component.DaftarBalanceSummary
import com.mk.daftar.core.designsystem.component.DaftarCurrencyBadge
import com.mk.daftar.core.designsystem.component.DaftarEmptyState
import com.mk.daftar.core.designsystem.component.DaftarIconButton
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
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
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.DesktopWidths
import com.mk.daftar.core.ui.shell.LocalSimultaneousPanes
import com.mk.daftar.core.ui.shell.PriceRefreshAction
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.desktopPage
import com.mk.daftar.core.ui.shell.pageScroll
import com.mk.daftar.feature.banks.AccountRow
import com.mk.daftar.feature.banks.BankDetailIntent
import com.mk.daftar.feature.banks.BankDetailUiState
import com.mk.daftar.feature.banks.BankDetailViewModel
import com.mk.daftar.feature.banks.BankPhase
import com.mk.daftar.feature.banks.resources.Res
import com.mk.daftar.feature.banks.resources.banks_account_balance
import com.mk.daftar.feature.banks.resources.banks_account_value
import com.mk.daftar.feature.banks.resources.banks_add_account
import com.mk.daftar.feature.banks.resources.banks_available_subtotal
import com.mk.daftar.feature.banks.resources.banks_conversion_note
import com.mk.daftar.feature.banks.resources.banks_detail_empty_message
import com.mk.daftar.feature.banks.resources.banks_detail_empty_title
import com.mk.daftar.feature.banks.resources.banks_edit_bank
import com.mk.daftar.feature.banks.resources.banks_egp_unit
import com.mk.daftar.feature.banks.resources.banks_failed_message
import com.mk.daftar.feature.banks.resources.banks_failed_title
import com.mk.daftar.feature.banks.resources.banks_missing_message
import com.mk.daftar.feature.banks.resources.banks_missing_title
import com.mk.daftar.feature.banks.resources.banks_title
import com.mk.daftar.feature.banks.resources.banks_unavailable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One bank's detail. Back is offered only when the list is not beside it: in a two-pane window the list stays visible,
 * so the detail has nothing to return to.
 */
@Composable
fun BankDetailScreen(bankId: String, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: BankDetailViewModel = koinViewModel(
        key = "bank-detail-$bankId",
        parameters = { parametersOf(bankId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect -> handleBankEffect(navigator, effect) }
    BankDetailContent(
        state = state,
        showsBack = !LocalSimultaneousPanes.current,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

/** The bank detail as it is drawn. Account balances are removed from rendering and semantics while values are hidden. */
@Composable
fun BankDetailContent(
    state: BankDetailUiState,
    showsBack: Boolean,
    onBack: () -> Unit,
    onIntent: (BankDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val hiddenText = stringResource(DesignRes.string.balance_hidden)
    val unavailable = stringResource(Res.string.banks_unavailable)
    val egpUnit = stringResource(Res.string.banks_egp_unit)
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = if (state.name.isEmpty()) stringResource(Res.string.banks_title) else state.name,
                onBack = if (showsBack) onBack else null,
                backLabel = stringResource(DesignRes.string.btn_back),
                actions = {
                    VisibilityToggle(hidden = state.hidden) { onIntent(BankDetailIntent.ToggleValues) }
                    PriceRefreshAction()
                    if (state.phase is BankPhase.Ready || state.phase == BankPhase.Empty) {
                        DaftarIconButton(
                            onClick = { onIntent(BankDetailIntent.EditBank) },
                            icon = DaftarIcons.Edit,
                            contentDescription = stringResource(Res.string.banks_edit_bank),
                        )
                    }
                },
            )
            Column(
                modifier = Modifier
                    .desktopPage(DesktopWidths.Page)
                    .weight(1f)
                    .fillMaxWidth()
                    .pageScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            ) {
                when (state.phase) {
                    BankPhase.Loading -> DaftarSkeleton(size = DaftarSkeletonSize.Big)

                    BankPhase.Locked -> Unit

                    BankPhase.Missing -> DaftarResultState(
                        title = stringResource(Res.string.banks_missing_title),
                        message = stringResource(Res.string.banks_missing_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    is BankPhase.Failed -> DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
                        Text(stringResource(Res.string.banks_failed_title))
                        Text(stringResource(Res.string.banks_failed_message))
                    }

                    BankPhase.Ready, BankPhase.Empty -> {
                        state.pendingRoute?.let {
                            RevealPrompt(
                                onReveal = { onIntent(BankDetailIntent.RevealAndOpen) },
                                onCancel = { onIntent(BankDetailIntent.CancelReveal) },
                            )
                        }
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
                        }
                        if (state.phase == BankPhase.Empty) {
                            DaftarEmptyState(
                                title = stringResource(Res.string.banks_detail_empty_title),
                                message = stringResource(Res.string.banks_detail_empty_message),
                                actionLabel = stringResource(Res.string.banks_add_account),
                                onAction = { onIntent(BankDetailIntent.AddAccount) },
                                icon = DaftarIcons.Wallet,
                            )
                        } else {
                            state.accounts.forEach { account ->
                                AccountLedgerRow(
                                    account = account,
                                    hidden = state.hidden,
                                    hiddenText = hiddenText,
                                    unavailable = unavailable,
                                    egpUnit = egpUnit,
                                    onClick = { onIntent(BankDetailIntent.OpenAccount(account.id)) },
                                )
                            }
                            if (state.accounts.any { it.currency == Currency.USD }) {
                                DaftarNotePanel(tone = DaftarNoteTone.Context) {
                                    Text(stringResource(Res.string.banks_conversion_note))
                                }
                            }
                        }
                        OutlineButton(
                            onClick = { onIntent(BankDetailIntent.AddAccount) },
                            fullWidth = true,
                            leadingIcon = DaftarIcons.Plus,
                        ) {
                            Text(
                                text = stringResource(Res.string.banks_add_account),
                                style = typography.buttonLabel,
                                color = colors.ink,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountLedgerRow(
    account: AccountRow,
    hidden: Boolean,
    hiddenText: String,
    unavailable: String,
    egpUnit: String,
    onClick: () -> Unit,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    DaftarLedgerRow(
        title = account.name,
        subtitle = stringResource(Res.string.banks_account_balance),
        leading = {
            val glyph = currencyGlyphOf(account.currency)
            DaftarCurrencyBadge(glyph = glyph.text, arabicGlyph = glyph.arabic)
        },
        onClick = onClick,
        value = {
            Column(horizontalAlignment = Alignment.End) {
                DaftarAmount(
                    amount = if (hidden) null else account.balanceText,
                    unit = account.currency.name,
                    masked = hidden,
                    maskedDescription = hiddenText,
                    style = typography.ledgerValue,
                    color = colors.ink,
                )
                if (account.currency == Currency.USD) {
                    DaftarAmount(
                        amount = if (hidden || !account.valued) null else account.egpText,
                        unit = egpUnit,
                        masked = hidden,
                        maskedDescription = hiddenText,
                        unavailableDescription = unavailable,
                        style = typography.fineText,
                        color = colors.muted,
                    )
                }
            }
        },
    )
}
