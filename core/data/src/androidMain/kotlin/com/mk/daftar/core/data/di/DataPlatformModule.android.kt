package com.mk.daftar.core.data.di

import com.mk.daftar.core.data.session.DaftarDatabaseBuilderProvider
import com.mk.daftar.core.database.daftarDatabaseBuilder
import com.mk.daftar.core.database.daftarDatabaseFile
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** The database lives under `noBackupFilesDir`; debug and release builds keep separate files. */
internal actual val dataPlatformModule: Module = module {
    single<DaftarDatabaseBuilderProvider> {
        val context = androidContext()
        DaftarDatabaseBuilderProvider { daftarDatabaseBuilder(context, daftarDatabaseFile(context)) }
    }
}
