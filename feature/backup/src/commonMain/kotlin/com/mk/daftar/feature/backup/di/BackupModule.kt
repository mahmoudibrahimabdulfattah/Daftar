package com.mk.daftar.feature.backup.di

import com.mk.daftar.core.navigation.BackupExportRoute
import com.mk.daftar.feature.backup.ExportScreen
import com.mk.daftar.feature.backup.ExportViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/** The export feature's Navigation 3 entry and view model. The controller and its ports are bound by the shell. */
@OptIn(KoinExperimentalAPI::class)
val backupFeatureModule: Module = module {
    viewModelOf(::ExportViewModel)
    navigation<BackupExportRoute> { ExportScreen() }
}
