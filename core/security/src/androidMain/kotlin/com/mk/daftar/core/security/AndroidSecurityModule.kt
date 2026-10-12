package com.mk.daftar.core.security

import android.content.Context
import android.content.pm.ApplicationInfo
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * The Android key policy, chosen by the app's build variant before shared dependency injection starts. Production
 * code uses [RequireHardware]; the choice is never read from preferences, environment, remote configuration or UI.
 */
class AndroidSecurityConfiguration private constructor(internal val permitsSoftwareKeys: Boolean) {
    companion object {
        /** Software-backed Keystore keys are refused. This is the default when no configuration is bound. */
        val RequireHardware: AndroidSecurityConfiguration = AndroidSecurityConfiguration(permitsSoftwareKeys = false)

        /**
         * Accepts software-backed Keystore keys, which emulators report. Honored only when the installed application
         * carries [ApplicationInfo.FLAG_DEBUGGABLE]; otherwise the policy stays [RequireHardware].
         */
        val AllowSoftwareForDebug: AndroidSecurityConfiguration =
            AndroidSecurityConfiguration(permitsSoftwareKeys = true)
    }
}

/** Binds the Android key policy for the security module. Bind exactly one configuration per process. */
fun androidSecurityConfigurationModule(configuration: AndroidSecurityConfiguration): Module = module {
    single { configuration }
}

internal actual val platformSecurityModule: Module = module {
    single<PlatformVaultStorageFactory> { AndroidVaultStorageFactory(get(), keystorePolicy(get())) }
}

/** The policy both the envelope and the backoff protector use, resolved against the installed application. */
private fun Scope.keystorePolicy(context: Context): KeystorePolicy = resolveKeystorePolicy(
    configuration = getOrNull<AndroidSecurityConfiguration>() ?: AndroidSecurityConfiguration.RequireHardware,
    debuggable = context.isDebuggable(),
)

/**
 * Software-backed keys only when the configuration asks for them and the application is debuggable. Every other
 * combination, including a release application with a permissive configuration, requires hardware.
 */
internal fun resolveKeystorePolicy(configuration: AndroidSecurityConfiguration, debuggable: Boolean): KeystorePolicy =
    if (configuration.permitsSoftwareKeys && debuggable) {
        KeystorePolicy.AllowSoftwareForDebug
    } else {
        KeystorePolicy.RequireHardware
    }

internal fun Context.isDebuggable(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

/** File names inside a generation's security directory. The sealed state is never backed up or transferred. */
internal const val PIN_ENVELOPE_FILE: String = "pin-envelope.v1.bin"
internal const val BACKOFF_FILE: String = "backoff.v1.bin"
internal const val BIOMETRIC_SLOT_FILE: String = "biometric-slot.v1.bin"
