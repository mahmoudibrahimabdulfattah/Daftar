package com.mk.daftar.di

import com.mk.daftar.diagnostics.AppAnalytics
import com.mk.daftar.diagnostics.FirebaseAppAnalytics
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformAnalyticsModule: Module = module {
    single<AppAnalytics> { FirebaseAppAnalytics(androidContext()) }
}
