package com.mk.daftar.core.datastore.di

import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The preferences live under `noBackupFilesDir`: private to the app, device-only, and never backed up or transferred.
 * The directory is created on first use.
 */
internal actual val preferencesDirectoryModule: Module = module {
    single(PreferencesDirectory) {
        File(androidContext().noBackupFilesDir, "datastore").apply { mkdirs() }.absolutePath
    }
}
