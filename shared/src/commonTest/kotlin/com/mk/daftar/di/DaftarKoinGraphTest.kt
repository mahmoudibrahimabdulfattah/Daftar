package com.mk.daftar.di

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.session.DaftarDatabaseBuilderProvider
import com.mk.daftar.core.data.session.DaftarVaultSession
import com.mk.daftar.core.data.session.VaultAuthenticator
import com.mk.daftar.core.datastore.DaftarPreferencesDataSource
import com.mk.daftar.core.datastore.createDaftarPreferencesDataSource
import com.mk.daftar.core.domain.repository.BankRepository
import com.mk.daftar.core.domain.repository.CashRepository
import com.mk.daftar.core.domain.repository.GoldRepository
import com.mk.daftar.core.domain.repository.JournalRepository
import com.mk.daftar.core.domain.repository.PriceRepository
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.usecase.CreatePersonalNoteUseCase
import com.mk.daftar.core.domain.usecase.CreateZakatPaymentUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.domain.usecase.RefreshPricesUseCase
import com.mk.daftar.core.security.KeyLease
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.SecurityStatus
import com.mk.daftar.core.security.VaultSecurity
import com.mk.daftar.lifecycle.VaultLifecycleCoordinator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Resolves the production graph in isolation. The platform parts (Keystore or Keychain, the database file, the
 * preferences directory) and the network engine are replaced by probes, so the test runs on the host and the
 * simulator alike. Any call into the vault, the database or the network is counted and fails the test.
 */
class DaftarKoinGraphTest {

    @Test
    fun everyRepositoryAndTheSessionResolveWithoutOpeningAnything() {
        val probes = StartupProbes()
        val app = koinApplication {
            allowOverride(true)
            modules(daftarModules)
            modules(probes.module)
        }
        try {
            val koin = app.koin
            koin.get<GoldRepository>()
            koin.get<BankRepository>()
            koin.get<CashRepository>()
            koin.get<JournalRepository>()
            koin.get<PriceRepository>()
            koin.get<UserPreferencesRepository>()
            koin.get<VaultAuthenticator>()
            koin.get<ObservePortfolioValuationUseCase>()
            koin.get<ObserveSelectedQuotesUseCase>()
            koin.get<CreateZakatPaymentUseCase>()
            koin.get<CreatePersonalNoteUseCase>()
            koin.get<RefreshPricesUseCase>()

            val session = koin.get<VaultSession>()
            assertSame(koin.get<DaftarVaultSession>(), session, "the session is one singleton")
            assertSame(koin.get<VaultSession>(), session)

            val coordinator = koin.get<VaultLifecycleCoordinator>()
            assertSame(coordinator, koin.get<VaultLifecycleCoordinator>())

            assertEquals(VaultSessionState.Locked, session.state.value)
            assertFalse(coordinator.isCovered)
            assertEquals(0, probes.securityCalls, "startup neither creates nor unlocks a vault")
            assertEquals(0, probes.databaseBuilds, "startup opens no database")
            assertEquals(0, probes.requests, "startup fetches nothing")
        } finally {
            app.close()
        }
    }
}

/** Counts every platform touch. Each counted path fails loudly if startup ever reaches it. */
private class StartupProbes {
    var securityCalls = 0
    var databaseBuilds = 0
    var requests = 0

    val module: Module = module {
        single<VaultSecurity> { ProbedSecurity(this@StartupProbes) }
        single<DaftarDatabaseBuilderProvider> {
            DaftarDatabaseBuilderProvider {
                databaseBuilds++
                error("startup must not open the database")
            }
        }
        single<DaftarPreferencesDataSource> {
            createDaftarPreferencesDataSource(produceFile = { error("startup must not read preferences") })
        }
        single<HttpClient> {
            HttpClient(
                MockEngine {
                    requests++
                    error("startup must not send a request")
                },
            )
        }
    }
}

private class ProbedSecurity(private val probes: StartupProbes) : VaultSecurity {
    override suspend fun status(): SecurityStatus {
        probes.securityCalls++
        error("startup must not query the vault")
    }

    override suspend fun create(pin: SecretBytes): Outcome<KeyLease, SecurityError> {
        probes.securityCalls++
        error("startup must not create a vault")
    }

    override suspend fun unlock(pin: SecretBytes): Outcome<KeyLease, SecurityError> {
        probes.securityCalls++
        error("startup must not unlock the vault")
    }

    override suspend fun changePin(currentPin: SecretBytes, newPin: SecretBytes): Outcome<Unit, SecurityError> {
        probes.securityCalls++
        error("startup must not change the PIN")
    }

    override suspend fun lock() {
        probes.securityCalls++
    }
}
