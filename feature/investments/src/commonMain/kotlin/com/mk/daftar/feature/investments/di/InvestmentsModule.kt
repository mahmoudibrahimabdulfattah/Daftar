package com.mk.daftar.feature.investments.di

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.navigation.ConvertCashRoute
import com.mk.daftar.core.navigation.InvestmentAddRoute
import com.mk.daftar.core.navigation.InvestmentDetailRoute
import com.mk.daftar.core.navigation.InvestmentEditRoute
import com.mk.daftar.core.navigation.InvestmentsRoute
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.feature.investments.InvestmentConvertViewModel
import com.mk.daftar.feature.investments.InvestmentDetailViewModel
import com.mk.daftar.feature.investments.InvestmentEditorTarget
import com.mk.daftar.feature.investments.InvestmentEditorViewModel
import com.mk.daftar.feature.investments.InvestmentListViewModel
import com.mk.daftar.feature.investments.resources.Res
import com.mk.daftar.feature.investments.resources.investments_select_account
import com.mk.daftar.feature.investments.ui.InvestmentConvertScreen
import com.mk.daftar.feature.investments.ui.InvestmentDetailScreen
import com.mk.daftar.feature.investments.ui.InvestmentEditorScreen
import com.mk.daftar.feature.investments.ui.InvestmentsListScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * The investments feature: the account list and its detail as one list-detail pair, and the editor and the cash
 * conversion beside them on wide windows. Use cases come from the application graph; nothing here reaches the data layer.
 */
@OptIn(KoinExperimentalAPI::class, ExperimentalMaterial3AdaptiveApi::class)
val investmentsModule: Module = module {
    viewModelOf(::InvestmentListViewModel)
    viewModel { params ->
        InvestmentDetailViewModel(
            investmentId = params.get(),
            observeInvestment = get(),
            deleteInvestment = get(),
            visibility = get(),
            clock = get(),
        )
    }
    viewModel { params ->
        InvestmentEditorViewModel(
            target = params.get<InvestmentEditorTarget>(),
            observeInvestment = get(),
            createInvestment = get(),
            updateInvestment = get(),
            deleteInvestment = get(),
            visibility = get(),
            clock = get(),
        )
    }
    viewModel { params ->
        // An empty text means no preselected entry; the route carries identifiers only.
        InvestmentConvertViewModel(
            presetSourceId = params.get<String>().takeIf { it.isNotEmpty() }?.let(::CashBalanceId),
            observeCash = get(),
            convert = get(),
            draftIds = get(),
            visibility = get(),
            clock = get(),
        )
    }

    navigation<InvestmentsRoute>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { SelectAccountPlaceholder() }),
    ) {
        InvestmentsListScreen()
    }
    navigation<InvestmentDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        InvestmentDetailScreen(investmentId = route.investmentId)
    }
    navigation<InvestmentAddRoute>(metadata = ListDetailSceneStrategy.detailPane()) {
        InvestmentEditorScreen(target = InvestmentEditorTarget.New)
    }
    navigation<InvestmentEditRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        InvestmentEditorScreen(target = InvestmentEditorTarget.Existing(route.investmentId))
    }
    navigation<ConvertCashRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        InvestmentConvertScreen(presetSourceId = route.sourceId?.let(::CashBalanceId))
    }
}

@Composable
private fun SelectAccountPlaceholder() {
    ShellScreen {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(Res.string.investments_select_account),
                style = DaftarTheme.typography.supportingBody,
                color = DaftarTheme.colors.muted,
            )
        }
    }
}
