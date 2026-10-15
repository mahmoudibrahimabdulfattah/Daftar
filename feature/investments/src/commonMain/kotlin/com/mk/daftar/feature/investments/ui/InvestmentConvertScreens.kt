package com.mk.daftar.feature.investments.ui

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarAmount
import com.mk.daftar.core.designsystem.component.DaftarDateField
import com.mk.daftar.core.designsystem.component.DaftarEmptyState
import com.mk.daftar.core.designsystem.component.DaftarFooterAction
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
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
import com.mk.daftar.core.designsystem.resources.btn_saving
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.bank.BankDirectory
import com.mk.daftar.core.domain.broker.BrokerDirectory
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.bank.BankInstitution
import com.mk.daftar.core.navigation.CashRoute
import com.mk.daftar.core.navigation.InvestmentsRoute
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.core.ui.shell.DesktopWidths
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.desktopPage
import com.mk.daftar.feature.investments.ConvertIntent
import com.mk.daftar.feature.investments.ConvertOutcome
import com.mk.daftar.feature.investments.ConvertSourceRow
import com.mk.daftar.feature.investments.ConvertStep
import com.mk.daftar.feature.investments.ConvertUiState
import com.mk.daftar.feature.investments.InvestmentConvertViewModel
import com.mk.daftar.feature.investments.InvestmentEffect
import com.mk.daftar.feature.investments.InvestmentPhase
import com.mk.daftar.feature.investments.ValueDateChoice
import com.mk.daftar.feature.investments.resources.Res
import com.mk.daftar.feature.investments.resources.investments_already_converted
import com.mk.daftar.feature.investments.resources.investments_as_of
import com.mk.daftar.feature.investments.resources.investments_as_of_now
import com.mk.daftar.feature.investments.resources.investments_cancel
import com.mk.daftar.feature.investments.resources.investments_choose_datetime
import com.mk.daftar.feature.investments.resources.investments_convert_action
import com.mk.daftar.feature.investments.resources.investments_convert_action_confirm
import com.mk.daftar.feature.investments.resources.investments_convert_as_of
import com.mk.daftar.feature.investments.resources.investments_convert_conflict
import com.mk.daftar.feature.investments.resources.investments_convert_duplicate
import com.mk.daftar.feature.investments.resources.investments_convert_explanation
import com.mk.daftar.feature.investments.resources.investments_convert_from
import com.mk.daftar.feature.investments.resources.investments_convert_gone
import com.mk.daftar.feature.investments.resources.investments_convert_no_source
import com.mk.daftar.feature.investments.resources.investments_convert_review_latest
import com.mk.daftar.feature.investments.resources.investments_convert_source
import com.mk.daftar.feature.investments.resources.investments_convert_title
import com.mk.daftar.feature.investments.resources.investments_convert_total
import com.mk.daftar.feature.investments.resources.investments_convert_unsupported
import com.mk.daftar.feature.investments.resources.investments_convert_usd_disabled
import com.mk.daftar.feature.investments.resources.investments_converted
import com.mk.daftar.feature.investments.resources.investments_error_date
import com.mk.daftar.feature.investments.resources.investments_load_failed
import com.mk.daftar.feature.investments.resources.investments_provider_other
import com.mk.daftar.feature.investments.resources.investments_provider_title
import com.mk.daftar.feature.investments.resources.investments_retry
import com.mk.daftar.feature.investments.resources.investments_save_failed
import com.mk.daftar.feature.investments.resources.investments_set_to_now
import com.mk.daftar.feature.investments.resources.investments_unit_egp
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Converts one EGP cash entry by explicit choice. [presetSourceId] comes from the cash entry's own action and starts at the
 * broker choice; otherwise the screen starts at the entry picker.
 */
@Composable
fun InvestmentConvertScreen(presetSourceId: CashBalanceId?, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: InvestmentConvertViewModel = koinViewModel(
        key = "investment-convert-${presetSourceId?.value.orEmpty()}",
        parameters = { parametersOf(presetSourceId?.value.orEmpty()) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect == InvestmentEffect.Completed) {
            navigator.returnTo { it == InvestmentsRoute || it is CashRoute }
        }
    }
    InvestmentConvertContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        modifier = modifier,
    )
}

/** The conversion flow as it is drawn. Amounts are masked while values are hidden, and Confirm needs a visible review. */
@Composable
fun InvestmentConvertContent(
    state: ConvertUiState,
    onIntent: (ConvertIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arabic = LocalLayoutDirection.current == LayoutDirection.Rtl
    val language = if (arabic) AppLanguage.AR else AppLanguage.EN
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(
                    when (state.step) {
                        ConvertStep.Source -> Res.string.investments_convert_action
                        ConvertStep.Provider -> Res.string.investments_provider_title
                        ConvertStep.Review -> Res.string.investments_convert_title
                        ConvertStep.Result -> Res.string.investments_convert_action
                    },
                ),
                onBack = if (state.step == ConvertStep.Provider) {
                    { onIntent(ConvertIntent.BackToSources) }
                } else if (state.step == ConvertStep.Review) {
                    { onIntent(ConvertIntent.BackToProviders) }
                } else {
                    onBack
                },
                backLabel = stringResource(DesignRes.string.btn_back),
            )
            Column(
                modifier = Modifier
                    .desktopPage(DesktopWidths.Editor)
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            ) {
                when (state.phase) {
                    InvestmentPhase.Loading -> DaftarSkeleton()

                    InvestmentPhase.Locked -> Unit

                    is InvestmentPhase.Failed -> DaftarNotePanel(
                        tone = DaftarNoteTone.Error,
                        icon = DaftarIcons.Alert,
                    ) {
                        Text(stringResource(Res.string.investments_load_failed))
                    }

                    InvestmentPhase.Missing -> Unit

                    InvestmentPhase.Empty -> DaftarNotePanel(tone = DaftarNoteTone.Context) {
                        Text(stringResource(Res.string.investments_convert_no_source))
                    }

                    InvestmentPhase.Ready -> when {
                        state.outcome != null -> ConvertResultState(outcome = state.outcome, onFinish = {
                            onIntent(ConvertIntent.Finish)
                        })

                        state.step == ConvertStep.Source -> SourcePicker(state = state, onIntent = onIntent)

                        state.step == ConvertStep.Provider -> ProviderPicker(language = language, onIntent = onIntent)

                        state.concealed -> RevealPrompt(
                            onReveal = { onIntent(ConvertIntent.RevealForm) },
                            onCancel = onBack,
                        )

                        else -> ConvertReview(state = state, onIntent = onIntent, onCancel = onBack)
                    }
                }
            }
        }
    }
}

/** The cash entries that can convert. A USD or out-of-bound entry is shown disabled, with its reason, and is never changed. */
@Composable
private fun SourcePicker(state: ConvertUiState, onIntent: (ConvertIntent) -> Unit) {
    val hiddenText = stringResource(DesignRes.string.balance_hidden)
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
        Text(
            text = stringResource(Res.string.investments_convert_source),
            style = DaftarTheme.typography.fineText,
            color = DaftarTheme.colors.muted,
        )
        state.sources.forEach { row ->
            SourceRow(row = row, hidden = state.hidden, hiddenText = hiddenText, onIntent = onIntent)
        }
    }
}

@Composable
private fun SourceRow(row: ConvertSourceRow, hidden: Boolean, hiddenText: String, onIntent: (ConvertIntent) -> Unit) {
    val typography = DaftarTheme.typography
    val unit = if (row.currency == Currency.EGP) stringResource(Res.string.investments_unit_egp) else row.currency.name
    DaftarLedgerRow(
        title = row.name,
        subtitle = if (row.eligible) row.note else stringResource(Res.string.investments_convert_usd_disabled),
        icon = DaftarIcons.Wallet,
        tone = DaftarTileTone.Cash,
        onClick = if (row.eligible) ({ onIntent(ConvertIntent.SelectSource(row.id)) }) else null,
        value = {
            DaftarAmount(
                amount = if (hidden) null else row.amount.moneyText(),
                unit = unit,
                masked = hidden,
                maskedDescription = hiddenText,
                style = typography.ledgerValue,
                color = DaftarTheme.colors.ink,
            )
        },
    )
}

/** The broker choice for the entry. Nothing is preselected; the review follows an explicit tap. */
@Composable
private fun ProviderPicker(language: AppLanguage, onIntent: (ConvertIntent) -> Unit) {
    val brokers = BrokerDirectory.search("", language)
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        brokers.forEach { institution ->
            BrokerChoice(institution = institution, language = language, onIntent = onIntent)
        }
        OutlineButton(
            onClick = { onIntent(ConvertIntent.ChooseProvider(providerId = null)) },
            fullWidth = true,
        ) {
            Text(stringResource(Res.string.investments_provider_other))
        }
    }
}

@Composable
private fun BrokerChoice(institution: BankInstitution, language: AppLanguage, onIntent: (ConvertIntent) -> Unit) {
    val other = if (language == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR
    DaftarLedgerRow(
        title = BankDirectory.localizedName(institution, language),
        subtitle = BankDirectory.localizedName(institution, other),
        leading = { BrokerTile(institution.id.value) },
        onClick = { onIntent(ConvertIntent.ChooseProvider(providerId = institution.id)) },
    )
}

/**
 * The review: the entry's name and note, the amount, the broker, the value date, and the statement that the total does
 * not change. Confirm stays disabled until the amount is visible, and a changed entry must be reviewed again first.
 */
@Composable
private fun ConvertReview(state: ConvertUiState, onIntent: (ConvertIntent) -> Unit, onCancel: () -> Unit) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val hiddenText = stringResource(DesignRes.string.balance_hidden)
    val source = state.source ?: return
    val now = Clock.System.now()
    var pickingDate by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Text(
            text = stringResource(Res.string.investments_convert_explanation),
            style = typography.supportingBody,
            color = colors.ink,
        )
        Text(
            text = stringResource(Res.string.investments_convert_from),
            style = typography.fineText,
            color = colors.muted,
        )
        DaftarLedgerRow(
            title = source.name,
            subtitle = source.note,
            icon = DaftarIcons.Wallet,
            tone = DaftarTileTone.Cash,
            value = {
                DaftarAmount(
                    amount = if (state.hidden) null else source.amount.moneyText(),
                    unit = stringResource(Res.string.investments_unit_egp),
                    masked = state.hidden,
                    maskedDescription = hiddenText,
                    style = typography.ledgerValue,
                    color = colors.ink,
                )
            },
        )
        DaftarLedgerRow(
            title = brokerNameText(state.providerId?.value),
            leading = { BrokerTile(state.providerId?.value) },
        )
        DaftarStatusTag(label = stringResource(Res.string.investments_convert_total), tone = DaftarStatusTone.Positive)
        DaftarDateField(
            label = stringResource(Res.string.investments_as_of),
            selectedText = when (val choice = state.asOf) {
                ValueDateChoice.Now -> stringResource(Res.string.investments_as_of_now)
                is ValueDateChoice.At -> cairoDateText(choice.instant)
            },
            onClick = { pickingDate = true },
            helper = stringResource(Res.string.investments_convert_as_of),
            invalid = state.dateInvalid,
            enabled = !state.busy && !state.concealed,
        )
        if (state.dateInvalid) {
            Text(
                text = stringResource(Res.string.investments_error_date),
                style = typography.fineText,
                color = colors.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
            DaftarTextButton(onClick = { onIntent(ConvertIntent.SetToNow) }, enabled = !state.busy) {
                Text(stringResource(Res.string.investments_set_to_now))
            }
            DaftarTextButton(onClick = { pickingDate = true }, enabled = !state.busy) {
                Text(stringResource(Res.string.investments_choose_datetime))
            }
        }
        DaftarNotePanel(tone = DaftarNoteTone.Context) {
            Text(text = stringResource(Res.string.investments_convert_duplicate), style = typography.fineText)
        }
        if (state.conflict) {
            DaftarNotePanel(
                tone = DaftarNoteTone.Warning,
                icon = DaftarIcons.Alert,
                action = {
                    DaftarTextButton(onClick = { onIntent(ConvertIntent.ReviewLatest) }, enabled = !state.busy) {
                        Text(stringResource(Res.string.investments_convert_review_latest))
                    }
                },
            ) {
                Text(stringResource(Res.string.investments_convert_conflict))
            }
        }
        state.failure?.let { error ->
            DaftarNotePanel(
                tone = DaftarNoteTone.Error,
                icon = DaftarIcons.Alert,
                action = if (error == StorageError.SessionEnded) {
                    null
                } else {
                    {
                        DaftarTextButton(onClick = { onIntent(ConvertIntent.Confirm) }) {
                            Text(stringResource(Res.string.investments_retry))
                        }
                    }
                },
            ) {
                Text(stringResource(Res.string.investments_save_failed))
            }
        }
    }
    DaftarFooterAction {
        PrimaryButton(
            onClick = { onIntent(ConvertIntent.Confirm) },
            fullWidth = true,
            enabled = !state.busy && !state.conflict && !state.concealed && !state.dateInvalid,
            loading = state.busy,
        ) {
            Text(
                stringResource(
                    if (state.busy) DesignRes.string.btn_saving else Res.string.investments_convert_action_confirm,
                ),
            )
        }
        OutlineButton(onClick = onCancel, fullWidth = true, enabled = !state.busy) {
            Text(stringResource(Res.string.investments_cancel))
        }
    }
    if (pickingDate) {
        val initial = (state.asOf as? ValueDateChoice.At)?.instant ?: now
        CairoDateTimePicker(
            initial = initial,
            now = now,
            onPick = { picked ->
                pickingDate = false
                onIntent(ConvertIntent.DateTimeChosen(picked))
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/** The outcome after Confirm or a source that has gone. Each one is stated plainly, and none offers to retry. */
@Composable
private fun ConvertResultState(outcome: ConvertOutcome, onFinish: () -> Unit) {
    val title = when (outcome) {
        ConvertOutcome.Converted -> Res.string.investments_converted
        ConvertOutcome.AlreadyConverted -> Res.string.investments_already_converted
        ConvertOutcome.SourceUnavailable -> Res.string.investments_convert_gone
        ConvertOutcome.Unsupported -> Res.string.investments_convert_unsupported
    }
    DaftarResultState(
        title = stringResource(title),
        message = "",
        primaryLabel = stringResource(DesignRes.string.btn_back),
        onPrimary = onFinish,
        tone = if (outcome == ConvertOutcome.Converted || outcome == ConvertOutcome.AlreadyConverted) {
            DaftarResultTone.Success
        } else {
            DaftarResultTone.Error
        },
    )
}
