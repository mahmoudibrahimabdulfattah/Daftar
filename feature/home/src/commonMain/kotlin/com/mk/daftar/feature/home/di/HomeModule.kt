package com.mk.daftar.feature.home.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.daftar.core.navigation.HomeRoute
import com.mk.daftar.core.navigation.PricesRoute
import com.mk.daftar.core.ui.shell.PlaceholderScreen
import com.mk.daftar.feature.home.HomeScreen
import com.mk.daftar.feature.home.HomeViewModel
import com.mk.daftar.feature.home.resources.Res
import com.mk.daftar.feature.home.resources.prices_message
import com.mk.daftar.feature.home.resources.prices_title
import org.jetbrains.compose.resources.stringResource
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * This feature's Navigation 3 entries and view model, registered for the shell's entry provider. The Prices entry is
 * a neutral placeholder: the prices phase replaces it without changing the route key or its callers.
 */
@OptIn(KoinExperimentalAPI::class)
val homeModule: Module = module {
    viewModelOf(::HomeViewModel)
    navigation<HomeRoute> { HomeScreen() }
    navigation<PricesRoute> { PricesPlaceholder() }
}

@Composable
private fun PricesPlaceholder() {
    PlaceholderScreen(
        title = stringResource(Res.string.prices_title),
        message = stringResource(Res.string.prices_message),
        modifier = Modifier,
    )
}
