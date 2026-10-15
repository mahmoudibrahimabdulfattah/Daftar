package com.mk.daftar.core.security

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The desktop key policy, chosen by the launcher from its build type before dependency injection starts. Production
 * code uses [RequireHardware]; the choice is never read from preferences, environment, remote configuration or UI.
 */
class DesktopSecurityConfiguration private constructor(internal val permitsSoftwareKeys: Boolean) {
    companion object {
        /** Only the Secure Enclave (macOS) or a TPM (Windows) may seal the vault. The default when nothing is bound. */
        val RequireHardware: DesktopSecurityConfiguration = DesktopSecurityConfiguration(permitsSoftwareKeys = false)

        /**
         * Debug builds only: when no hardware protector is available, a key file in the app's private directory seals
         * the vault. It gives no hardware guarantee, and the app shows its persistent debug banner.
         */
        val AllowSoftwareForDebug: DesktopSecurityConfiguration =
            DesktopSecurityConfiguration(permitsSoftwareKeys = true)
    }
}

/** Binds the desktop key policy for the security module. Bind exactly one configuration per process. */
fun desktopSecurityConfigurationModule(configuration: DesktopSecurityConfiguration): Module = module {
    single { configuration }
}

internal actual val platformSecurityModule: Module = module {
    single<PlatformVaultStorageFactory> {
        DesktopVaultStorageFactory(
            permitsSoftwareKeys = (
                getOrNull<DesktopSecurityConfiguration>() ?: DesktopSecurityConfiguration.RequireHardware
                ).permitsSoftwareKeys,
        )
    }
}

/** File names inside a generation's security directory. */
internal const val PIN_ENVELOPE_FILE: String = "pin-envelope.v1.bin"
internal const val BACKOFF_FILE: String = "backoff.v1.bin"
internal const val BIOMETRIC_SLOT_FILE: String = "biometric-slot.v1.bin"
internal const val RECOVERY_SLOT_FILE: String = "recovery-slot.v1.bin"
