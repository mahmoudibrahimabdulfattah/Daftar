package com.mk.daftar.core.account.di

import com.mk.daftar.core.account.desktop.DesktopCloudAccountGateway
import com.mk.daftar.core.account.desktop.DesktopCloudConfiguration
import com.mk.daftar.core.account.desktop.DesktopCloudSessionHolder
import com.mk.daftar.core.account.desktop.DesktopFirebaseSession
import com.mk.daftar.core.domain.cloud.CloudAccountGateway
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop cloud sign-in: Google in the system browser and a Firebase session in memory. Without a bound
 * [DesktopCloudConfiguration] the gateway reports unavailable and never signs in.
 */
actual val accountModule: Module = module {
    single { DesktopCloudSessionHolder(getOrNull<DesktopCloudConfiguration>()?.let(::DesktopFirebaseSession)) }
    single<CloudAccountGateway> { DesktopCloudAccountGateway(get<DesktopCloudSessionHolder>().session) }
}

/** Binds the Firebase project of this desktop build. Bind it only when the build has a configuration. */
fun desktopCloudConfigurationModule(configuration: DesktopCloudConfiguration): Module = module {
    single { configuration }
}
