package com.mk.daftar.core.data.di

import com.mk.daftar.core.data.restore.DesktopGenerationFiles
import com.mk.daftar.core.data.restore.desktopDatabaseFile
import com.mk.daftar.core.data.session.DaftarDatabaseBuilderProvider
import com.mk.daftar.core.database.daftarDatabaseBuilder
import com.mk.daftar.core.security.GenerationFiles
import org.koin.core.module.Module
import org.koin.dsl.module

/** The database of each vault generation lives in the app's private directory, owner-only and excluded from backup. */
internal actual val dataPlatformModule: Module = module {
    single<DaftarDatabaseBuilderProvider> {
        DaftarDatabaseBuilderProvider { generation -> daftarDatabaseBuilder(desktopDatabaseFile(generation)) }
    }
    single<GenerationFiles> { DesktopGenerationFiles() }
}
