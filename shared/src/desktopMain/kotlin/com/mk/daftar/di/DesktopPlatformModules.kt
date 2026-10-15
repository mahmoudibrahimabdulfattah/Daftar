package com.mk.daftar.di

import com.mk.daftar.core.security.BiometricAuthResult
import com.mk.daftar.core.security.BiometricPrompter
import com.mk.daftar.core.security.MacBiometricCrypto
import com.mk.daftar.feature.settings.AppBuildInfo
import com.mk.daftar.shared.resources.Res
import com.mk.daftar.shared.resources.biometric_prompt_title
import org.jetbrains.compose.resources.getString
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

/**
 * The desktop prompt. On macOS the Secure Enclave operation itself shows the system Touch ID sheet, so this adapter only
 * supplies the reason text; the operation completes or fails from the system's answer, which keeps a cancelled sheet
 * from ever counting as authentication. Without a Touch ID key store this prompter is never reached.
 */
internal actual val platformBiometricModule: Module = module {
    single<BiometricPrompter> {
        BiometricPrompter { crypto ->
            val mac = crypto as? MacBiometricCrypto ?: return@BiometricPrompter BiometricAuthResult.Unavailable
            mac.reason = getString(Res.string.biometric_prompt_title)
            BiometricAuthResult.Authenticated(crypto)
        }
    }
}
