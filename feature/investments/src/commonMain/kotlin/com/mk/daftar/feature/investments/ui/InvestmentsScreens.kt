package com.mk.daftar.feature.investments.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.designsystem.component.DaftarAmount
import com.mk.daftar.core.designsystem.component.DaftarEmptyState
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarSkeletonSize
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.balance_hidden
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.resources.btn_edit
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.bank.BankDirectory
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.model.broker.BrokerDirectoryData
import com.mk.daftar.core.navigation.InvestmentDetailRoute
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.navigation.Navigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.CountNoun
import com.mk.daftar.core.ui.presentation.countLabel
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.DesktopPageColumn
import com.mk.daftar.core.ui.shell.DesktopWidths
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.desktopPage
import com.mk.daftar.feature.investments.InvestmentDetailIntent
import com.mk.daftar.feature.investments.InvestmentDetailUiState
import com.mk.daftar.feature.investments.InvestmentDetailViewModel
import com.mk.daftar.feature.investments.InvestmentEffect
import com.mk.daftar.feature.investments.InvestmentListIntent
import com.mk.daftar.feature.investments.InvestmentListUiState
import com.mk.daftar.feature.investments.InvestmentListViewModel
import com.mk.daftar.feature.investments.InvestmentPhase
import com.mk.daftar.feature.investments.InvestmentRow
import com.mk.daftar.feature.investments.resources.Res
import com.mk.daftar.feature.investments.resources.investments_add
import com.mk.daftar.feature.investments.resources.investments_as_of
import com.mk.daftar.feature.investments.resources.investments_convert_action
import com.mk.daftar.feature.investments.resources.investments_delete_action
import com.mk.daftar.feature.investments.resources.investments_delete_keep
import com.mk.daftar.feature.investments.resources.investments_delete_message
import com.mk.daftar.feature.investments.resources.investments_delete_title
import com.mk.daftar.feature.investments.resources.investments_detail_title
import com.mk.daftar.feature.investments.resources.investments_empty_action
import com.mk.daftar.feature.investments.resources.investments_empty_message
import com.mk.daftar.feature.investments.resources.investments_empty_title
import com.mk.daftar.feature.investments.resources.investments_load_failed
import com.mk.daftar.feature.investments.resources.investments_manual
import com.mk.daftar.feature.investments.resources.investments_missing_message
import com.mk.daftar.feature.investments.resources.investments_missing_title
import com.mk.daftar.feature.investments.resources.investments_note
import com.mk.daftar.feature.investments.resources.investments_provider_other
import com.mk.daftar.feature.investments.resources.investments_row_subtitle
import com.mk.daftar.feature.investments.resources.investments_save_failed
import com.mk.daftar.feature.investments.resources.investments_title
import com.mk.daftar.feature.investments.resources.investments_total
import com.mk.daftar.feature.investments.resources.investments_unit_egp
import com.mk.daftar.feature.investments.resources.investments_update_value
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Opens a screen from the investments feature: an account's detail shows beside the list, anything else is pushed. */
internal fun Navigator.open(route: NavKey) {
    if (route is InvestmentDetailRoute) showDetail(route) else goTo(route)
}

/** The Investments list: the total, each account with its broker and value date, and the two ways to add. */
@Composable
fun InvestmentsListScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: InvestmentListViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect is InvestmentEffect.Navigate) navigator.open(effect.route)
    }
    InvestmentsListContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The list as it is drawn. Amounts leave both rendering and semantics while values are hidden. */
@Composable
fun InvestmentsListContent(
    state: InvestmentListUiState,
    onIntent: (InvestmentListIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hiddenText = stringResource(DesignRes.string.balance_hidden)
    ShellScreen(modifier = modifier) {
        DesktopPageColumn(
            cap = DesktopWidths.Page,
            topBar = {
                DaftarTopBar(
                    title = stringResource(Res.string.investments_title),
                    onBack = onBack,
                    backLabel = stringResource(DesignRes.string.btn_back),
                    actions = {
                        VisibilityToggle(hidden = state.hidden) { onIntent(InvestmentListIntent.ToggleHidden) }
                    },
                )
            },
        ) {
            state.pendingRoute?.let {
                RevealPrompt(
                    onReveal = { onIntent(InvestmentListIntent.RevealAndOpen) },
                    onCancel = { onIntent(InvestmentListIntent.CancelReveal) },
                )
            }
            when (state.phase) {
                InvestmentPhase.Loading -> DaftarSkeleton(size = DaftarSkeletonSize.Big)

                InvestmentPhase.Locked, InvestmentPhase.Missing -> Unit

                is InvestmentPhase.Failed -> DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
                    Text(stringResource(Res.string.investments_load_failed))
                }

                InvestmentPhase.Empty -> {
                    DaftarEmptyState(
                        title = stringResource(Res.string.investments_empty_title),
                        message = stringResource(Res.string.investments_empty_message),
                        actionLabel = stringResource(Res.string.investments_empty_action),
                        onAction = { onIntent(InvestmentListIntent.OpenAdd) },
                        icon = DaftarIcons.Investments,
                    )
                    OutlineButton(
                        onClick = { onIntent(InvestmentListIntent.OpenConvert) },
                        fullWidth = true,
                    ) {
                        Text(stringResource(Res.string.investments_convert_action))
                    }
                }

                InvestmentPhase.Ready -> {
                    InvestmentTotal(state = state)
                    state.rows.forEach { row ->
                        InvestmentRowItem(
                            row = row,
                            hidden = state.hidden,
                            hiddenText = hiddenText,
                            onOpen = { onIntent(InvestmentListIntent.OpenAccount(row.id)) },
                        )
                    }
                    PrimaryButton(
                        onClick = { onIntent(InvestmentListIntent.OpenAdd) },
                        fullWidth = true,
                        leadingIcon = DaftarIcons.Plus,
                    ) {
                        Text(stringResource(Res.string.investments_add))
                    }
                    OutlineButton(
                        onClick = { onIntent(InvestmentListIntent.OpenConvert) },
                        fullWidth = true,
                    ) {
                        Text(stringResource(Res.string.investments_convert_action))
                    }
                }
            }
        }
    }
}

/** The total of every account, with the count of accounts (never holdings). Nothing is shown in place of a total while hidden. */
@Composable
private fun InvestmentTotal(state: InvestmentListUiState) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val count = state.rows.size
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xxs)) {
        Text(text = stringResource(Res.string.investments_total), style = typography.fineText, color = colors.muted)
        DaftarAmount(
            amount = if (state.hidden) null else state.total.moneyText(),
            unit = stringResource(Res.string.investments_unit_egp),
            masked = state.hidden,
            maskedDescription = stringResource(DesignRes.string.balance_hidden),
            style = typography.heroAmount,
            color = colors.ink,
        )
        Text(
            text = countLabel(count, CountNoun.INVESTMENT_ACCOUNTS),
            style = typography.fineText,
            color = colors.muted,
        )
    }
}

/** One account: its broker tile, name, broker and value date, the amount, and a quiet review line when it is due. */
@Composable
private fun InvestmentRowItem(row: InvestmentRow, hidden: Boolean, hiddenText: String, onOpen: () -> Unit) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val dateText = cairoDateText(row.valueAsOf)
    val broker = brokerNameText(row.providerId?.value)
    Column(modifier = Modifier.fillMaxWidth()) {
        DaftarLedgerRow(
            title = row.name,
            subtitle = stringResource(Res.string.investments_row_subtitle, broker, dateText),
            leading = { BrokerTile(row.providerId?.value) },
            onClick = onOpen,
            value = {
                DaftarAmount(
                    amount = if (hidden) null else row.amount.moneyText(),
                    unit = stringResource(Res.string.investments_unit_egp),
                    masked = hidden,
                    maskedDescription = hiddenText,
                    style = typography.ledgerValue,
                    color = colors.ink,
                )
            },
        )
        ValueDateHintLine(hint = row.hint, modifier = Modifier.padding(start = 48.dp))
    }
}

/** The broker's name in the app's language, or Other broker for an action and for an ID this build does not know. */
@Composable
internal fun brokerNameText(providerId: String?): String {
    val arabic = LocalLayoutDirection.current == LayoutDirection.Rtl
    val institution = providerId?.let { id -> BrokerDirectoryData.institutions.firstOrNull { it.id.value == id } }
    return institution?.let { BankDirectory.localizedName(it, if (arabic) AppLanguage.AR else AppLanguage.EN) }
        ?: stringResource(Res.string.investments_provider_other)
}

/** One account's detail: the value, its date and review line, its note, Update value and the secondary actions. */
@Composable
fun InvestmentDetailScreen(investmentId: String, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: InvestmentDetailViewModel = koinViewModel(
        key = "investment-detail-$investmentId",
        parameters = { parametersOf(investmentId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is InvestmentEffect.Navigate -> navigator.open(effect.route)
            InvestmentEffect.Completed -> navigator.goBack()
        }
    }
    InvestmentDetailContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The detail as it is drawn. The note and the amount are masked while values are hidden. */
@Composable
fun InvestmentDetailContent(
    state: InvestmentDetailUiState,
    onIntent: (InvestmentDetailIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val hiddenText = stringResource(DesignRes.string.balance_hidden)
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(Res.string.investments_detail_title),
                onBack = onBack,
                backLabel = stringResource(DesignRes.string.btn_back),
                actions = {
                    VisibilityToggle(hidden = state.hidden) { onIntent(InvestmentDetailIntent.ToggleHidden) }
                },
            )
            Column(
                modifier = Modifier
                    .desktopPage(DesktopWidths.Page)
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            ) {
                state.pendingRoute?.let {
                    RevealPrompt(
                        onReveal = { onIntent(InvestmentDetailIntent.RevealAndOpen) },
                        onCancel = { onIntent(InvestmentDetailIntent.CancelReveal) },
                    )
                }
                when (state.phase) {
                    InvestmentPhase.Loading -> repeat(3) { DaftarSkeleton() }

                    InvestmentPhase.Locked -> Unit

                    InvestmentPhase.Missing -> DaftarResultState(
                        title = stringResource(Res.string.investments_missing_title),
                        message = stringResource(Res.string.investments_missing_message),
                        primaryLabel = stringResource(DesignRes.string.btn_back),
                        onPrimary = onBack,
                        tone = DaftarResultTone.Error,
                    )

                    is InvestmentPhase.Failed -> DaftarNotePanel(
                        tone = DaftarNoteTone.Error,
                        icon = DaftarIcons.Alert,
                    ) {
                        Text(stringResource(Res.string.investments_load_failed))
                    }

                    InvestmentPhase.Empty, InvestmentPhase.Ready -> state.row?.let { row ->
                        val dateText = cairoDateText(row.valueAsOf)
                        Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xxs)) {
                            Text(
                                text = row.name,
                                style = typography.ledgerTitle,
                                color = colors.ink,
                            )
                            Text(
                                text = brokerNameText(row.providerId?.value),
                                style = typography.fineText,
                                color = colors.muted,
                            )
                        }
                        DaftarAmount(
                            amount = if (state.hidden) null else row.amount.moneyText(),
                            unit = stringResource(Res.string.investments_unit_egp),
                            masked = state.hidden,
                            maskedDescription = hiddenText,
                            style = typography.heroAmount,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(
                                Res.string.investments_row_subtitle,
                                stringResource(Res.string.investments_as_of),
                                dateText,
                            ),
                            style = typography.fineText,
                            color = colors.muted,
                        )
                        Text(
                            text = stringResource(Res.string.investments_manual),
                            style = typography.fineText,
                            color = colors.muted,
                        )
                        ValueDateHintLine(hint = row.hint)
                        row.note?.let { note ->
                            DaftarNotePanel(tone = DaftarNoteTone.Context) {
                                Text(text = stringResource(Res.string.investments_note), style = typography.fineText)
                                Text(text = if (state.hidden) hiddenText else note, style = typography.supportingBody)
                            }
                        }
                        PrimaryButton(
                            onClick = { onIntent(InvestmentDetailIntent.OpenEdit) },
                            fullWidth = true,
                            enabled = !state.busy,
                        ) {
                            Text(stringResource(Res.string.investments_update_value))
                        }
                        DaftarTextButton(
                            onClick = { onIntent(InvestmentDetailIntent.OpenEdit) },
                            fullWidth = true,
                            enabled = !state.busy,
                        ) {
                            Text(stringResource(DesignRes.string.btn_edit))
                        }
                        DaftarTextButton(
                            onClick = { onIntent(InvestmentDetailIntent.RequestDelete) },
                            fullWidth = true,
                            enabled = !state.busy,
                        ) {
                            Text(stringResource(Res.string.investments_delete_action))
                        }
                    }
                }
                state.failure?.let {
                    DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
                        Text(stringResource(Res.string.investments_save_failed))
                    }
                }
            }
        }
    }
    if (state.confirmDelete) {
        InvestmentConfirmDialog(
            title = stringResource(Res.string.investments_delete_title),
            message = stringResource(Res.string.investments_delete_message),
            primaryLabel = stringResource(Res.string.investments_delete_keep),
            onPrimary = { onIntent(InvestmentDetailIntent.DismissConfirmation) },
            onDismiss = { onIntent(InvestmentDetailIntent.DismissConfirmation) },
            destructiveLabel = stringResource(Res.string.investments_delete_action),
            onDestructive = { if (!state.busy) onIntent(InvestmentDetailIntent.ConfirmDelete) },
        )
    }
}
