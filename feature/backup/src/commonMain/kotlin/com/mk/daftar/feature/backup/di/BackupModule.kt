package com.mk.daftar.feature.backup.di

import com.mk.daftar.core.backup.RestoreFilePicker
import com.mk.daftar.core.navigation.BackupExportRoute
import com.mk.daftar.core.navigation.CloudUploadRoute
import com.mk.daftar.feature.backup.ExportScreen
import com.mk.daftar.feature.backup.ExportViewModel
import com.mk.daftar.feature.backup.cloud.CloudUploadScreen
import com.mk.daftar.feature.backup.cloud.CloudUploadViewModel
import com.mk.daftar.feature.backup.restore.HandoffRestoreFilePicker
import com.mk.daftar.feature.backup.restore.RestoreDraftRegistry
import com.mk.daftar.feature.backup.restore.RestoreHandoff
import com.mk.daftar.feature.backup.restore.RestoreViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * The export and restore features. The export entry is a Navigation 3 route; the restore wizard is hosted by the locked
 * surface, outside the private shell, so it has no route. The controller and its ports are bound by the shell.
 */
@OptIn(KoinExperimentalAPI::class)
val backupFeatureModule: Module = module {
    viewModelOf(::ExportViewModel)
    navigation<BackupExportRoute> { ExportScreen() }
    viewModelOf(::CloudUploadViewModel)
    navigation<CloudUploadRoute> { CloudUploadScreen() }
    single { RestoreDraftRegistry() }
    single { RestoreHandoff() }
    single { HandoffRestoreFilePicker(handoff = get(), delegate = get<RestoreFilePicker>()) }
    viewModel {
        RestoreViewModel(picker = get<HandoffRestoreFilePicker>(), decryptor = get(), engine = get(), drafts = get())
    }
}
