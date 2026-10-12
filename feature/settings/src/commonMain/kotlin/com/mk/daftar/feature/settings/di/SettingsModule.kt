package com.mk.daftar.feature.settings.di

import com.mk.daftar.core.navigation.SettingsRoute
import com.mk.daftar.feature.settings.SettingsScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/** This feature's Navigation 3 entries, registered for the shell's entry provider. */
@OptIn(KoinExperimentalAPI::class)
val settingsModule: Module = module {
    navigation<SettingsRoute> { SettingsScreen() }
}
