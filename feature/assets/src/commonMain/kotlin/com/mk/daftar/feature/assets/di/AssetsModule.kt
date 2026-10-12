package com.mk.daftar.feature.assets.di

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.navigation.AssetsRoute
import com.mk.daftar.core.navigation.BankDetailRoute
import com.mk.daftar.core.navigation.BanksRoute
import com.mk.daftar.core.navigation.GoldRoute
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.navigation.TopLevelDestination
import com.mk.daftar.core.ui.shell.LocalSimultaneousPanes
import com.mk.daftar.core.ui.shell.PlaceholderScreen
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.feature.assets.AssetsOverviewViewModel
import com.mk.daftar.feature.assets.AssetsScreen
import com.mk.daftar.feature.assets.BankDetailScreen
import com.mk.daftar.feature.assets.BanksScreen
import com.mk.daftar.feature.assets.resources.Res
import com.mk.daftar.feature.assets.resources.bank_select
import com.mk.daftar.feature.assets.resources.gold_message
import com.mk.daftar.feature.assets.resources.gold_title
import org.jetbrains.compose.resources.stringResource
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * Assets' entries. The bank list and its detail are one list-detail pair: scene metadata, not a second navigator,
 * decides whether they sit side by side. Selecting another bank replaces the detail on the stack. Gold is a neutral
 * placeholder until its feature phase replaces that entry under the same route key.
 */
@OptIn(KoinExperimentalAPI::class, ExperimentalMaterial3AdaptiveApi::class)
val assetsModule: Module = module {
    viewModelOf(::AssetsOverviewViewModel)
    navigation<AssetsRoute> { AssetsScreen() }
    navigation<GoldRoute> {
        PlaceholderScreen(
            title = stringResource(Res.string.gold_title),
            message = stringResource(Res.string.gold_message),
        )
    }
    navigation<BanksRoute>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { SelectBankPlaceholder() }),
    ) {
        val navigator = LocalNavigator.current
        val selectedBankId = (navigator.stackOf(TopLevelDestination.ASSETS).lastOrNull() as? BankDetailRoute)?.bankId
        BanksScreen(
            selectedBankId = selectedBankId,
            onOpenBank = { bankId -> navigator.showDetail(BankDetailRoute(bankId)) },
        )
    }
    navigation<BankDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        val navigator = LocalNavigator.current
        BankDetailScreen(
            bankId = route.bankId,
            showsBack = !LocalSimultaneousPanes.current,
            onBack = { navigator.goBack() },
        )
    }
}

@Composable
private fun SelectBankPlaceholder() {
    ShellScreen {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(Res.string.bank_select),
                style = DaftarTheme.typography.supportingBody,
                color = DaftarTheme.colors.muted,
            )
        }
    }
}
