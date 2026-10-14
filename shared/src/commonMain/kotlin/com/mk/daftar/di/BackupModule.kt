package com.mk.daftar.di

import com.mk.daftar.core.backup.BackupCodec
import com.mk.daftar.core.backup.BackupDecryptor
import com.mk.daftar.core.backup.BackupEncryptor
import com.mk.daftar.core.backup.BackupExportController
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.VaultSecurity
import com.mk.daftar.core.security.createPasswordCrypto
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.dsl.module

/** The version written into each backup's encrypted payload. */
const val DAFTAR_APP_VERSION: String = "1.0"

/**
 * Encrypted export bindings: the password-crypto facade, the codec and the process-scoped export controller. The
 * controller outlives every screen and every vault generation, so a picker that locks the vault cannot strand its
 * write. The platform document services come from [platformBackupModule].
 */
val backupModule: Module = module {
    includes(platformBackupModule)
    single { createPasswordCrypto() }
    // Exports made while the vault has a recovery key also open with that key (version-two container).
    single {
        val security = get<VaultSecurity>()
        BackupCodec(get()) { (security.recoveryPublicKey() as? Outcome.Success)?.data }
    }
    single<BackupEncryptor> { get<BackupCodec>() }
    single<BackupDecryptor> { get<BackupCodec>() }
    single {
        BackupExportController(
            source = get(),
            encryptor = get(),
            staging = get(),
            documents = get(),
            appVersion = DAFTAR_APP_VERSION,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            clock = Clock.System,
            timeZone = TimeZone.currentSystemDefault(),
        )
    }
}

/** The platform's staging area and document services: Android storage picker, iOS document picker. */
internal expect val platformBackupModule: Module
