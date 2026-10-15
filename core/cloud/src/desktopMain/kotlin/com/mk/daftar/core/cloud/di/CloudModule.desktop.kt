package com.mk.daftar.core.cloud.di

import com.mk.daftar.core.account.desktop.DesktopCloudSessionHolder
import com.mk.daftar.core.cloud.CloudStore
import com.mk.daftar.core.cloud.RestFirestoreCloudStore
import com.mk.daftar.core.cloud.credentials
import org.koin.core.module.Module
import org.koin.dsl.module

/** Firestore over REST with the desktop Firebase session. Without a configuration every call fails as unavailable. */
internal actual val platformCloudModule: Module = module {
    single<CloudStore> { RestFirestoreCloudStore(get<DesktopCloudSessionHolder>().session?.credentials()) }
}
