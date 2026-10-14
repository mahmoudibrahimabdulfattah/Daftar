package com.mk.daftar.di

import com.mk.daftar.core.security.BiometricAuthResult
import com.mk.daftar.core.security.BiometricPrompter
import com.mk.daftar.feature.settings.AppBuildInfo
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What this desktop build is, supplied by the launcher before dependency injection starts. [debug] is fixed when the
 * app is built: a packaged release always carries false.
 */
data class DesktopBuildConfiguration(val versionName: String, val buildNumber: String, val debug: Boolean)

/** Binds the launcher's build facts. Bind exactly one configuration per process. */
fun desktopBuildConfigurationModule(configuration: DesktopBuildConfiguration): Module = module {
    single { configuration }
}

/** The About screen shows what this build really is. */
internal actual val platformBuildInfoModule: Module = module {
    single {
        val build = get<DesktopBuildConfiguration>()
        AppBuildInfo(versionName = build.versionName, buildNumber = build.buildNumber)
    }
}

/** No desktop biometric adapter yet: the key store reports unavailable, so this prompter is never reached. */
internal actual val platformBiometricModule: Module = module {
    single<BiometricPrompter> { BiometricPrompter { BiometricAuthResult.Unavailable } }
}
