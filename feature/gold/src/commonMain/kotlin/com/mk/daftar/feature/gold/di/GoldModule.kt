package com.mk.daftar.feature.gold.di

import com.mk.daftar.core.navigation.GoldAddRoute
import com.mk.daftar.core.navigation.GoldEditRoute
import com.mk.daftar.core.navigation.GoldRoute
import com.mk.daftar.feature.gold.editor.GoldEditorTarget
import com.mk.daftar.feature.gold.editor.GoldEditorViewModel
import com.mk.daftar.feature.gold.list.GoldListViewModel
import com.mk.daftar.feature.gold.ui.GoldEditorScreen
import com.mk.daftar.feature.gold.ui.GoldListScreen
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * The Gold feature: the holdings list with its category valuation, and the add and edit editor. It replaces the Assets
 * placeholder under [GoldRoute]. Its use cases and privacy state come from the application graph; nothing here reaches
 * the data layer.
 */
@OptIn(KoinExperimentalAPI::class)
val goldModule: Module = module {
    viewModel {
        GoldListViewModel(
            observeHoldings = get(),
            observePortfolio = get(),
            observeQuotes = get(),
            visibility = get(),
            timeZone = TimeZone.currentSystemDefault(),
        )
    }
    viewModel { params ->
        GoldEditorViewModel(
            target = params.get<GoldEditorTarget>(),
            observeHolding = get(),
            observeHoldings = get(),
            observeQuotes = get(),
            createHolding = get(),
            updateHolding = get(),
            deleteHolding = get(),
            visibility = get(),
        )
    }

    navigation<GoldRoute> { GoldListScreen() }
    navigation<GoldAddRoute> { GoldEditorScreen(target = GoldEditorTarget.New) }
    navigation<GoldEditRoute> { route -> GoldEditorScreen(target = GoldEditorTarget.Existing(route.holdingId)) }
}
