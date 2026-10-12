package com.mk.daftar.di

import com.mk.daftar.core.backup.BackupDocuments
import com.mk.daftar.core.backup.BackupStaging
import com.mk.daftar.platform.backup.IosBackupDocuments
import com.mk.daftar.platform.backup.IosBackupStaging
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformBackupModule: Module = module {
    single<BackupStaging> { IosBackupStaging() }
    single<BackupDocuments> { IosBackupDocuments() }
}
