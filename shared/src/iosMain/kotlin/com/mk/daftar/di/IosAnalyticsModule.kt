package com.mk.daftar.di

import com.mk.daftar.diagnostics.AppAnalytics
import com.mk.daftar.diagnostics.NoOpAppAnalytics
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformAnalyticsModule: Module = module {
    single<AppAnalytics> { NoOpAppAnalytics }
}
