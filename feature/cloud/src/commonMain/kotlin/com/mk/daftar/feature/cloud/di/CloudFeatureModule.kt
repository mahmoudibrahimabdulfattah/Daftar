package com.mk.daftar.feature.cloud.di

import com.mk.daftar.core.navigation.CloudRoute
import com.mk.daftar.feature.cloud.CloudMode
import com.mk.daftar.feature.cloud.CloudRouteScreen
import com.mk.daftar.feature.cloud.CloudViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * The cloud backup screens. The Settings route is a Navigation 3 entry; the locked recovery surface opens the same screen
 * in [CloudMode.RECOVERY] directly. The repository comes from the shell's cloud bindings.
 */
@OptIn(KoinExperimentalAPI::class)
val cloudFeatureModule: Module = module {
    viewModel { params -> CloudViewModel(repository = get(), mode = params.get()) }
    navigation<CloudRoute> { CloudRouteScreen() }
}
