package com.mk.daftar.feature.cash.di

import com.mk.daftar.core.navigation.CashAddRoute
import com.mk.daftar.core.navigation.CashEditRoute
import com.mk.daftar.core.navigation.CashRoute
import com.mk.daftar.feature.cash.CashEditorTarget
import com.mk.daftar.feature.cash.CashEditorViewModel
import com.mk.daftar.feature.cash.CashListViewModel
import com.mk.daftar.feature.cash.ui.CashEditorScreen
import com.mk.daftar.feature.cash.ui.CashListScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * The cash and wallet feature: the list and its editor. Their use cases come from the application graph, so nothing
 * here reaches the data layer.
 */
@OptIn(KoinExperimentalAPI::class)
val cashModule: Module = module {
    viewModelOf(::CashListViewModel)
    viewModel { params ->
        CashEditorViewModel(
            target = params.get<CashEditorTarget>(),
            observeBalance = get(),
            observeBalances = get(),
            createBalance = get(),
            updateBalance = get(),
            deleteBalance = get(),
            visibility = get(),
        )
    }

    navigation<CashRoute> { CashListScreen() }
    navigation<CashAddRoute> { CashEditorScreen(target = CashEditorTarget.New) }
    navigation<CashEditRoute> { route -> CashEditorScreen(target = CashEditorTarget.Existing(route.balanceId)) }
}
