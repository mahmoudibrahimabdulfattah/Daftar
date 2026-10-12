package com.mk.daftar.core.network

import com.mk.daftar.core.network.di.networkModule
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Clock
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class NetworkModuleTest {

    @Test
    fun networkModuleResolvesEveryRemoteBoundaryWithoutFetching() {
        val app = koinApplication {
            modules(
                networkModule,
                module { single<Clock> { FixedClock(FETCHED_AT) } },
            )
        }
        try {
            val koin = app.koin
            assertIs<HttpClient>(koin.get<HttpClient>())
            assertIs<IsaghaGoldDataSource>(koin.get<GoldNetworkDataSource>())
            assertIs<FallbackFxDataSource>(koin.get<FxNetworkDataSource>())
        } finally {
            stopKoin()
        }
    }
}
