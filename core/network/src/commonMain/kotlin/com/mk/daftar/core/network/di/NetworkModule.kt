package com.mk.daftar.core.network.di

import com.mk.daftar.core.common.AppDispatchers
import com.mk.daftar.core.network.ExchangeRateApiDataSource
import com.mk.daftar.core.network.FallbackFxDataSource
import com.mk.daftar.core.network.FawazFxDataSource
import com.mk.daftar.core.network.FxNetworkDataSource
import com.mk.daftar.core.network.GoldNetworkDataSource
import com.mk.daftar.core.network.IsaghaGoldDataSource
import com.mk.daftar.core.network.daftarHttpClient
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
    single<FxNetworkDataSource> {
        FallbackFxDataSource(
            primary = ExchangeRateApiDataSource(get(), get<Clock>(), get()),
            secondary = FawazFxDataSource(get(), get<Clock>(), get()),
        )
    }
}
