package com.mk.daftar.feature.prices.di

import com.mk.daftar.core.domain.usecase.DeleteManualQuoteUseCase
import com.mk.daftar.core.domain.usecase.ObserveQuoteSnapshotUseCase
import com.mk.daftar.core.domain.usecase.RemoveOverrideUseCase
import com.mk.daftar.core.domain.usecase.SetManualQuoteUseCase
import com.mk.daftar.core.navigation.ManualQuoteRoute
import com.mk.daftar.core.navigation.PriceDetailsRoute
import com.mk.daftar.core.navigation.PricesRoute
import com.mk.daftar.feature.prices.ManualQuoteScreen
import com.mk.daftar.feature.prices.ManualQuoteViewModel
import com.mk.daftar.feature.prices.PricesScreen
import com.mk.daftar.feature.prices.PricesViewModel
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * The prices feature's Navigation 3 entries and view models, registered for the shell's entry provider. The manual
 * editor's view model takes its route, so each quote key has its own editor state.
 */
@OptIn(KoinExperimentalAPI::class)
val pricesModule: Module = module {
    viewModelOf(::PricesViewModel)
    viewModel { params ->
        val route: ManualQuoteRoute = params.get()
        ManualQuoteViewModel(
            route = route,
            observeSnapshot = get<ObserveQuoteSnapshotUseCase>(),
            setManual = get<SetManualQuoteUseCase>(),
            removeOverride = get<RemoveOverrideUseCase>(),
            deleteManual = get<DeleteManualQuoteUseCase>(),
            clock = get<Clock>(),
        )
    }
    navigation<PricesRoute> { PricesScreen() }
    navigation<PriceDetailsRoute> { PricesScreen(details = true) }
    navigation<ManualQuoteRoute> { route -> ManualQuoteScreen(route = route) }
}
