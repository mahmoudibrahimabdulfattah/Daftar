package com.mk.daftar.core.datastore.di

import com.mk.daftar.core.datastore.DAFTAR_PREFERENCES_FILE_NAME
import com.mk.daftar.core.datastore.createDaftarPreferencesDataSource
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The device-only directory that holds the preferences file. Per platform, excluded from backup. */
internal expect val preferencesDirectoryModule: Module

/** Path of the directory, bound by [preferencesDirectoryModule]. */
internal val PreferencesDirectory = named("preferencesDirectory")

/** Non-sensitive settings only. The module never holds keys, balances or PINs. */
val datastoreModule: Module = module {
    includes(preferencesDirectoryModule)
    single {
        val directory = get<String>(PreferencesDirectory)
        createDaftarPreferencesDataSource(
            produceFile = { "$directory/$DAFTAR_PREFERENCES_FILE_NAME".toPath() },
        )
    }
}
