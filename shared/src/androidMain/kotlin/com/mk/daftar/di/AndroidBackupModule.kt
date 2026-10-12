package com.mk.daftar.di

import com.mk.daftar.core.backup.BackupDocuments
import com.mk.daftar.core.backup.BackupStaging
import com.mk.daftar.platform.backup.AndroidBackupDocuments
import com.mk.daftar.platform.backup.AndroidBackupStaging
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformBackupModule: Module = module {
    single<BackupStaging> { AndroidBackupStaging(androidContext()) }
    single<BackupDocuments> { AndroidBackupDocuments(androidContext()) }
}
