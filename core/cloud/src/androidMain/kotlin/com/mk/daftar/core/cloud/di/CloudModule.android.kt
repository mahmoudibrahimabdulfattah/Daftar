package com.mk.daftar.core.cloud.di

import com.mk.daftar.core.cloud.CloudStore
import com.mk.daftar.core.cloud.FirestoreCloudStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformCloudModule: Module = module {
    single<CloudStore> { FirestoreCloudStore(androidContext()) }
}
