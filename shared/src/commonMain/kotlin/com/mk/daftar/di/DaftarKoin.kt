package com.mk.daftar.di

import com.mk.daftar.core.data.di.dataModule
import com.mk.daftar.core.datastore.di.datastoreModule
import com.mk.daftar.core.network.di.networkModule
import com.mk.daftar.core.security.di.securityModule
import com.mk.daftar.feature.assets.di.assetsModule
import com.mk.daftar.feature.home.di.homeModule
import com.mk.daftar.feature.journal.di.journalModule
import com.mk.daftar.feature.settings.di.settingsModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/** Every module the app is made of, in dependency order. Tests load the same list with fakes for the platform. */
val daftarModules: List<Module> = listOf(
    securityModule,
    networkModule,
    datastoreModule,
    dataModule,
    domainModule,
    platformServicesModule,
    homeModule,
    assetsModule,
    journalModule,
    settingsModule,
)

/**
 * Starts dependency injection once for the process. Android supplies its context and key policy through
 * [appDeclaration]. Startup resolves only the lifecycle coordinator and the session; it neither opens the database
 * nor fetches a quote.
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(daftarModules)
    }
}
