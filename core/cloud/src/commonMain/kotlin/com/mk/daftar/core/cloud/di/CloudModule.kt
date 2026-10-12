package com.mk.daftar.core.cloud.di

import com.mk.daftar.core.backup.BackupEncryptor
import com.mk.daftar.core.backup.BackupSnapshotSource
import com.mk.daftar.core.cloud.CloudBackupRepositoryImpl
import com.mk.daftar.core.cloud.CloudBackupService
import com.mk.daftar.core.cloud.CloudRandom
import com.mk.daftar.core.cloud.CloudStore
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.cloud.CloudAccountGateway
import com.mk.daftar.core.domain.cloud.CloudBackupRepository
import com.mk.daftar.core.security.PasswordCrypto
import kotlin.time.Clock
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The cloud backup bindings. [appVersion] is written into each encrypted payload, as for the local export. The account
 * gateway and the platform store come from the platform modules, so the graph fails loudly when either is missing.
 */
fun cloudModule(appVersion: String): Module = module {
    includes(platformCloudModule)
    single<CloudRandom> {
        val crypto: PasswordCrypto = get()
        CloudRandom { size -> (crypto.randomBytes(size) as? Outcome.Success)?.data }
    }
    single { CloudBackupService(store = get<CloudStore>(), random = get()) }
    single<CloudBackupRepository> {
        CloudBackupRepositoryImpl(
            gateway = get<CloudAccountGateway>(),
            service = get(),
            snapshots = get<BackupSnapshotSource>(),
            encryptor = get<BackupEncryptor>(),
            appVersion = appVersion,
            clock = Clock.System,
        )
    }
}

/** The Firestore transport for this platform. */
internal expect val platformCloudModule: Module
