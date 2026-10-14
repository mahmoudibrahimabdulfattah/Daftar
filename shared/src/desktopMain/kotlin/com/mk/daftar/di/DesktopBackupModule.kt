package com.mk.daftar.di

import com.mk.daftar.core.backup.BackupDocuments
import com.mk.daftar.core.backup.BackupStaging
import com.mk.daftar.core.backup.RestoreFilePicker
import com.mk.daftar.platform.backup.DesktopBackupDocuments
import com.mk.daftar.platform.backup.DesktopBackupStaging
import com.mk.daftar.platform.backup.DesktopRestoreFilePicker
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformBackupModule: Module = module {
    single<BackupStaging> { DesktopBackupStaging() }
    single<BackupDocuments> { DesktopBackupDocuments() }
    single<RestoreFilePicker> { DesktopRestoreFilePicker() }
}
