package com.mk.daftar.di

import com.mk.daftar.core.domain.update.RemoteConfigSource
import com.mk.daftar.update.AndroidUpdateBuild
import com.mk.daftar.update.FirebaseRemoteConfigSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Firebase Remote Config, with the build variant supplied by the application. Without it the build is a release build. */
internal actual val platformUpdateModule: Module = module {
    single<RemoteConfigSource> {
        FirebaseRemoteConfigSource(
            context = androidContext(),
            debugBuild = getOrNull<AndroidUpdateBuild>()?.debugBuild == true,
        )
    }
}
