package com.mk.daftar.di

import com.mk.daftar.core.domain.update.CheckForUpdateUseCase
import com.mk.daftar.core.domain.update.InstalledBuildProvider
import com.mk.daftar.core.domain.update.ObserveRemoteConfigUpdatesUseCase
import com.mk.daftar.core.domain.update.SnoozeSoftUpdateUseCase
import com.mk.daftar.feature.settings.AppBuildInfo
import com.mk.daftar.update.AppInstalledBuildProvider
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/** In-app update checks. The remote source is Android only; other platforms bind one that never offers an update. */
val updateModule: Module = module {
    includes(platformUpdateModule)
    single<InstalledBuildProvider> { AppInstalledBuildProvider(get<AppBuildInfo>().buildNumber) }
    factoryOf(::CheckForUpdateUseCase)
    factoryOf(::ObserveRemoteConfigUpdatesUseCase)
    factoryOf(::SnoozeSoftUpdateUseCase)
}

/** The Remote Config source of this platform. */
internal expect val platformUpdateModule: Module
