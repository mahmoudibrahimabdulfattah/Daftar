package com.mk.daftar.feature.settings.di

import com.mk.daftar.core.navigation.AboutRoute
import com.mk.daftar.core.navigation.ChangePinRoute
import com.mk.daftar.core.navigation.PreferencesRoute
import com.mk.daftar.core.navigation.RecoveryKeyRoute
import com.mk.daftar.core.navigation.SettingsRoute
import com.mk.daftar.feature.settings.ChangePinViewModel
import com.mk.daftar.feature.settings.PreferencesViewModel
import com.mk.daftar.feature.settings.RecoveryKeyViewModel
import com.mk.daftar.feature.settings.SettingsDraftRegistry
import com.mk.daftar.feature.settings.SettingsViewModel
import com.mk.daftar.feature.settings.ui.AboutScreen
import com.mk.daftar.feature.settings.ui.ChangePinScreen
import com.mk.daftar.feature.settings.ui.PreferencesScreen
import com.mk.daftar.feature.settings.ui.RecoveryKeyScreen
import com.mk.daftar.feature.settings.ui.SettingsScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * The Settings feature: the list, preferences, the PIN change and About. The gateway, the clock and the use cases come from
 * the application graph; nothing here reaches the data layer.
 */
@OptIn(KoinExperimentalAPI::class)
val settingsModule: Module = module {
    single { SettingsDraftRegistry() }
    viewModelOf(::SettingsViewModel)
    viewModelOf(::PreferencesViewModel)
    viewModelOf(::ChangePinViewModel)
    viewModelOf(::RecoveryKeyViewModel)

    navigation<SettingsRoute> { SettingsScreen() }
    navigation<PreferencesRoute> { PreferencesScreen() }
    navigation<ChangePinRoute> { ChangePinScreen() }
    navigation<RecoveryKeyRoute> { RecoveryKeyScreen() }
    navigation<AboutRoute> { AboutScreen() }
}
