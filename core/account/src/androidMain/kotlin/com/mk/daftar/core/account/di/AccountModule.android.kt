package com.mk.daftar.core.account.di

import com.mk.daftar.core.account.FirebaseCloudAccountGateway
import com.mk.daftar.core.domain.cloud.CloudAccountGateway
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val accountModule: Module = module {
    single<CloudAccountGateway> { FirebaseCloudAccountGateway(androidContext()) }
}
