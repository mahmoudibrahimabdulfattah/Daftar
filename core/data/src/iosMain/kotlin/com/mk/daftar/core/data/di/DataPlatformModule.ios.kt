package com.mk.daftar.core.data.di

import com.mk.daftar.core.data.restore.IosGenerationFiles
import com.mk.daftar.core.data.restore.iosDatabasePath
import com.mk.daftar.core.data.session.DaftarDatabaseBuilderProvider
import com.mk.daftar.core.database.daftarDatabaseBuilder
import com.mk.daftar.core.security.GenerationFiles
import org.koin.core.module.Module
import org.koin.dsl.module

/** The database of each vault generation lives in Application Support, excluded from backup, with file protection. */
internal actual val dataPlatformModule: Module = module {
    single<DaftarDatabaseBuilderProvider> {
        DaftarDatabaseBuilderProvider { generation -> daftarDatabaseBuilder(iosDatabasePath(generation)) }
    }
    single<GenerationFiles> { IosGenerationFiles() }
}
