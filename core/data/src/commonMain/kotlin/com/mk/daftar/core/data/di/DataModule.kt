package com.mk.daftar.core.data.di

import com.mk.daftar.core.data.ids.IdGenerator
import com.mk.daftar.core.data.ids.UuidV4Generator
import com.mk.daftar.core.data.repository.BankRecordRepository
import com.mk.daftar.core.data.repository.CashRecordRepository
import com.mk.daftar.core.data.repository.GoldRecordRepository
import com.mk.daftar.core.data.repository.JournalRecordRepository
import com.mk.daftar.core.data.repository.PriceRecordRepository
import com.mk.daftar.core.data.repository.UserPreferencesRecordRepository
import com.mk.daftar.core.data.session.DaftarVaultSession
import com.mk.daftar.core.data.session.VaultAuthenticator
import com.mk.daftar.core.domain.repository.BankRepository
import com.mk.daftar.core.domain.repository.CashRepository
import com.mk.daftar.core.domain.repository.GoldRepository
import com.mk.daftar.core.domain.repository.JournalRepository
import com.mk.daftar.core.domain.repository.PriceRepository
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.network.FxNetworkDataSource
import com.mk.daftar.core.network.GoldNetworkDataSource
import com.mk.daftar.core.security.VaultSecurity
import kotlin.time.Clock
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Data-layer bindings. Nothing here opens the database: the session is created eagerly, but Room opens only after a
 * successful unlock. Expects [VaultSecurity], the datastore module, and the network module from the application graph.
 * The platform part supplies the database location.
 */
val dataModule: Module = module {
    includes(dataPlatformModule)
    single<Clock> { Clock.System }
    single<IdGenerator> { UuidV4Generator() }
    single<DaftarVaultSession> {
        DaftarVaultSession(
            security = get<VaultSecurity>(),
            databases = get(),
            clock = get(),
            ids = get(),
        )
    }
    single<VaultSession> { get<DaftarVaultSession>() }
    single { VaultAuthenticator(security = get(), session = get()) }
    single<GoldRepository> { GoldRecordRepository(get<DaftarVaultSession>().host) }
    single<BankRepository> { BankRecordRepository(get<DaftarVaultSession>().host) }
    single<CashRepository> { CashRecordRepository(get<DaftarVaultSession>().host) }
    single<JournalRepository> { JournalRecordRepository(get<DaftarVaultSession>().host) }
    single<PriceRepository> {
        PriceRecordRepository(
            host = get<DaftarVaultSession>().host,
            gold = get<GoldNetworkDataSource>(),
            fx = get<FxNetworkDataSource>(),
        )
    }
    single<UserPreferencesRepository> { UserPreferencesRecordRepository(get()) }
}

/** Platform bindings: the database builder for this platform's private, device-only location. */
internal expect val dataPlatformModule: Module
