package com.mk.daftar.feature.home.di

import com.mk.daftar.core.navigation.HomeRoute
import com.mk.daftar.feature.home.HomeScreen
import com.mk.daftar.feature.home.HomeViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * This feature's Navigation 3 entries and view model, registered for the shell's entry provider. The Prices route is
 * owned by the prices feature, which Home only opens by its key.
 */
@OptIn(KoinExperimentalAPI::class)
val homeModule: Module = module {
    viewModelOf(::HomeViewModel)
    navigation<HomeRoute> { HomeScreen() }
}
