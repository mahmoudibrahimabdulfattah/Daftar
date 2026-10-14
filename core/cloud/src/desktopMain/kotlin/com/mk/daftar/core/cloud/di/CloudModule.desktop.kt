package com.mk.daftar.core.cloud.di

import com.mk.daftar.core.cloud.CloudStore
import com.mk.daftar.core.cloud.CloudStoreException
import com.mk.daftar.core.cloud.CloudStoreFailure
import org.koin.core.module.Module
import org.koin.dsl.module

/** Cloud backups are Android-only: every store operation fails as unavailable on desktop. */
internal actual val platformCloudModule: Module = module {
    single<CloudStore> { UnavailableCloudStore }
}

private object UnavailableCloudStore : CloudStore {
    override suspend fun <T> runTransaction(uid: String, body: (com.mk.daftar.core.cloud.CloudTransaction) -> T): T =
        throw CloudStoreException(CloudStoreFailure.UNAVAILABLE)
}
