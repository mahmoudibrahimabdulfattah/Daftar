package com.mk.daftar.core.security

import android.content.Context
import java.io.File
import org.koin.core.module.Module
import org.koin.dsl.module

/** Keystore aliases for the PIN envelope and the backoff record. They are separate keys. */
private const val PIN_ENVELOPE_ALIAS = "com.mk.daftar.security.pin-envelope.v1"
private const val BACKOFF_ALIAS = "com.mk.daftar.security.backoff.v1"

internal actual val platformSecurityModule: Module = module {
    single<PlatformEnvelopeProtector> {
        AndroidKeystoreProtector(PIN_ENVELOPE_ALIAS, KeystorePolicy.RequireHardware)
    }
    single<SecurityRecordStore> {
        FileRecordStore(securityDirectory(get()).resolve(PIN_ENVELOPE_FILE))
    }
    single<BackoffStore> {
        FileBackoffStore(
            file = securityDirectory(get()).resolve(BACKOFF_FILE),
            protector = AndroidKeystoreProtector(BACKOFF_ALIAS, KeystorePolicy.RequireHardware),
        )
    }
}

/** Under `noBackupFilesDir`, so the sealed state is never backed up or transferred. */
internal fun securityDirectory(context: Context): File = File(context.noBackupFilesDir, "daftar-security")

private const val PIN_ENVELOPE_FILE = "pin-envelope.v1.bin"
private const val BACKOFF_FILE = "backoff.v1.bin"
