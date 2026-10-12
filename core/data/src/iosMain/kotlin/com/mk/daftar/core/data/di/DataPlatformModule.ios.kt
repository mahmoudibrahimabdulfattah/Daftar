package com.mk.daftar.core.data.di

import com.mk.daftar.core.data.session.DaftarDatabaseBuilderProvider
import com.mk.daftar.core.database.daftarDatabaseBuilder
import com.mk.daftar.core.database.daftarDatabasePath
import org.koin.core.module.Module
import org.koin.dsl.module

/** The database lives in Application Support, excluded from backup, with file protection. */
internal actual val dataPlatformModule: Module = module {
    single<DaftarDatabaseBuilderProvider> {
        DaftarDatabaseBuilderProvider { daftarDatabaseBuilder(daftarDatabasePath()) }
    }
}
