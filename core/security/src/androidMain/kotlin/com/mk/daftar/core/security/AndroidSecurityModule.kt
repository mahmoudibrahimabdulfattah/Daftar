package com.mk.daftar.core.security

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File
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

/** Keystore aliases for the PIN envelope and the backoff record. They are separate keys. */
private const val PIN_ENVELOPE_ALIAS = "com.mk.daftar.security.pin-envelope.v1"
private const val BACKOFF_ALIAS = "com.mk.daftar.security.backoff.v1"

internal actual val platformSecurityModule: Module = module {
    single<PlatformEnvelopeProtector> {
        AndroidKeystoreProtector(PIN_ENVELOPE_ALIAS, keystorePolicy(get()))
    }
    single<SecurityRecordStore> {
        FileRecordStore(securityDirectory(get()).resolve(PIN_ENVELOPE_FILE))
    }
    single<BackoffStore> {
        FileBackoffStore(
            file = securityDirectory(get()).resolve(BACKOFF_FILE),
            protector = AndroidKeystoreProtector(BACKOFF_ALIAS, keystorePolicy(get())),
        )
    }
    single<BiometricKeyStore> { AndroidBiometricKeyStore(get(), keystorePolicy(get())) }
    single<BiometricSlotStore> { FileBiometricSlotStore(securityDirectory(get()).resolve(BIOMETRIC_SLOT_FILE)) }
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

/** Under `noBackupFilesDir`, so the sealed state is never backed up or transferred. */
internal fun securityDirectory(context: Context): File = File(context.noBackupFilesDir, "daftar-security")

private const val PIN_ENVELOPE_FILE = "pin-envelope.v1.bin"
private const val BACKOFF_FILE = "backoff.v1.bin"
private const val BIOMETRIC_SLOT_FILE = "biometric-slot.v1.bin"
