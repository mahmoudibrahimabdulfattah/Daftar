package com.mk.daftar.core.network.di

import com.mk.daftar.core.common.AppDispatchers
import com.mk.daftar.core.network.FxNetworkDataSource
import com.mk.daftar.core.network.GoldNetworkDataSource
import com.mk.daftar.core.network.IsaghaGoldDataSource
import com.mk.daftar.core.network.daftarHttpClient
import com.mk.daftar.core.network.marketFxChain
import io.ktor.client.HttpClient
import kotlin.time.Clock
import org.koin.dsl.module

/**
 * Network bindings. Declares the shared client and the two remote data sources. It needs a [Clock] and
 * [AppDispatchers] from the application graph, and it fetches nothing on its own. Startup wiring lives in `shared`.
 */
val networkModule = module {
    single { AppDispatchers() }
    single<HttpClient> { daftarHttpClient() }
    single<GoldNetworkDataSource> { IsaghaGoldDataSource(get(), get<Clock>(), get()) }
    single<FxNetworkDataSource> { marketFxChain(get(), get<Clock>(), get()) }
}
