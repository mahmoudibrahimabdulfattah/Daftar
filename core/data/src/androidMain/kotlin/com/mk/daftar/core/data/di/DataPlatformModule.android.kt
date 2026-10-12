package com.mk.daftar.core.data.di

import android.content.Context
import com.mk.daftar.core.data.restore.AndroidGenerationFiles
import com.mk.daftar.core.data.restore.androidDatabaseFile
import com.mk.daftar.core.data.session.DaftarDatabaseBuilderProvider
import com.mk.daftar.core.database.daftarDatabaseBuilder
import com.mk.daftar.core.security.GenerationFiles
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The database of each vault generation lives under `noBackupFilesDir`; debug and release builds keep separate files.
 * The legacy generation keeps the first release's file.
 */
internal actual val dataPlatformModule: Module = module {
    single<DaftarDatabaseBuilderProvider> {
        val context: Context = androidContext()
        DaftarDatabaseBuilderProvider { generation ->
            daftarDatabaseBuilder(context, androidDatabaseFile(context, generation))
        }
    }
    single<GenerationFiles> { AndroidGenerationFiles(androidContext()) }
}
