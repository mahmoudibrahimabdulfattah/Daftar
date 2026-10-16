package com.mk.daftar.feature.banks.di

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.navigation.AccountAddRoute
import com.mk.daftar.core.navigation.AccountEditRoute
import com.mk.daftar.core.navigation.BankAddRoute
import com.mk.daftar.core.navigation.BankDetailRoute
import com.mk.daftar.core.navigation.BankEditRoute
import com.mk.daftar.core.navigation.BanksRoute
import com.mk.daftar.core.ui.shell.PaneActionsBar
import com.mk.daftar.core.ui.shell.PriceRefreshAction
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.ValuesVisibilityAction
import com.mk.daftar.feature.banks.AccountEditorTarget
import com.mk.daftar.feature.banks.AccountEditorViewModel
import com.mk.daftar.feature.banks.BankDetailViewModel
import com.mk.daftar.feature.banks.BankEditorTarget
import com.mk.daftar.feature.banks.BankEditorViewModel
import com.mk.daftar.feature.banks.BanksListViewModel
import com.mk.daftar.feature.banks.resources.Res
import com.mk.daftar.feature.banks.resources.banks_select
import com.mk.daftar.feature.banks.ui.AccountEditorScreen
import com.mk.daftar.feature.banks.ui.BankDetailScreen
import com.mk.daftar.feature.banks.ui.BankEditorScreen
import com.mk.daftar.feature.banks.ui.BanksListScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * The banks feature: the bank list and its detail as one list-detail pair, and the bank and account editors as
 * standalone destinations. The list pane owns the placeholder shown beside it when no bank is selected. Use cases come
 * from the application graph; nothing here reaches the data layer.
 */
@OptIn(KoinExperimentalAPI::class, ExperimentalMaterial3AdaptiveApi::class)
val banksModule: Module = module {
    viewModelOf(::BanksListViewModel)
    viewModel { params ->
        BankDetailViewModel(
            bankId = params.get(),
            observeDetail = get(),
            observeQuotes = get(),
            visibility = get(),
        )
    }
    viewModel { params ->
        BankEditorViewModel(
            target = params.get<BankEditorTarget>(),
            observeBanks = get(),
            observeDetail = get(),
            createBank = get(),
            updateBank = get(),
            deleteBank = get(),
            visibility = get(),
        )
    }
    viewModel { params ->
        AccountEditorViewModel(
            target = params.get<AccountEditorTarget>(),
            observeAccount = get(),
            observeBanks = get(),
            createAccount = get(),
            updateAccount = get(),
            deleteAccount = get(),
            visibility = get(),
        )
    }

    navigation<BanksRoute>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = {
            SelectBankPlaceholder()
        }),
    ) {
        BanksListScreen()
    }
    navigation<BankDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        BankDetailScreen(bankId = route.bankId)
    }
    navigation<BankAddRoute> { BankEditorScreen(target = BankEditorTarget.New) }
    navigation<BankEditRoute> { route -> BankEditorScreen(target = BankEditorTarget.Existing(route.bankId)) }
    navigation<AccountAddRoute> { route -> AccountEditorScreen(target = AccountEditorTarget.New(route.bankId)) }
    navigation<AccountEditRoute> { route ->
        AccountEditorScreen(target = AccountEditorTarget.Existing(route.accountId))
    }
}

@Composable
private fun SelectBankPlaceholder() {
    ShellScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            // The pair's header: the list's bar holds the title, this one the actions at the far end.
            PaneActionsBar {
                ValuesVisibilityAction()
                PriceRefreshAction()
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.banks_select),
                    style = DaftarTheme.typography.supportingBody,
                    color = DaftarTheme.colors.muted,
                )
            }
        }
    }
}
