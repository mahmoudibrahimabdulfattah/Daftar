package com.mk.daftar.di

import com.mk.daftar.core.domain.update.RemoteConfigSource
import com.mk.daftar.update.NoUpdateRemoteConfigSource
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS and desktop never prompt for an update, so their source reports no policy. */
internal val noUpdatePlatformModule: Module = module {
    single<RemoteConfigSource> { NoUpdateRemoteConfigSource }
}
