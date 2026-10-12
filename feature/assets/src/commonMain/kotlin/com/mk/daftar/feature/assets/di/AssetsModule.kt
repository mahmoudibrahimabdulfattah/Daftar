package com.mk.daftar.feature.assets.di

import com.mk.daftar.core.navigation.AssetsRoute
import com.mk.daftar.feature.assets.AssetsOverviewViewModel
import com.mk.daftar.feature.assets.AssetsScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * Assets' entries: the overview only. Gold, banks (with their list-detail pair) and cash are registered by their own
 * feature modules under their route keys.
 */
@OptIn(KoinExperimentalAPI::class)
val assetsModule: Module = module {
    viewModelOf(::AssetsOverviewViewModel)
    navigation<AssetsRoute> { AssetsScreen() }
}
