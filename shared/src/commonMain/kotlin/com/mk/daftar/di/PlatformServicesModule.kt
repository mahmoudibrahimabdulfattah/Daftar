package com.mk.daftar.di

import com.mk.daftar.core.common.MonotonicTime
import com.mk.daftar.core.common.SystemMonotonicTime
import com.mk.daftar.core.data.session.VaultAuthenticator
import com.mk.daftar.core.datastore.DaftarPreferencesDataSource
import com.mk.daftar.core.domain.refresh.PriceRefreshController
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.security.BiometricPrompter
import com.mk.daftar.core.security.VaultSecurity
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.core.ui.presentation.ValuesVisibilityStore
import com.mk.daftar.feature.auth.AuthDraftRegistry
import com.mk.daftar.feature.auth.AuthGateway
import com.mk.daftar.feature.backup.restore.RestoreDraftRegistry
import com.mk.daftar.feature.settings.SettingsDraftRegistry
import com.mk.daftar.feature.settings.SettingsSecurityGateway
import com.mk.daftar.gate.VaultAuthGateway
import com.mk.daftar.gate.VaultSettingsSecurityGateway
import com.mk.daftar.lifecycle.InactivityMonitor
import com.mk.daftar.lifecycle.VaultLifecycleCoordinator
import com.mk.daftar.refresh.SessionPriceRefresh
import com.mk.daftar.refresh.ZakatTrackingRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Application-level services that sit between the platform and the vault. Resolving them opens nothing: the
 * coordinator only observes the session and locks it when a platform asks, and the auth gateway only forwards.
 */
val platformServicesModule: Module = module {
    single<MonotonicTime> { SystemMonotonicTime }
    single<AuthGateway> {
        VaultAuthGateway(
            security = get<VaultSecurity>(),
            authenticator = get<VaultAuthenticator>(),
            prompter = get<BiometricPrompter>(),
            preferences = get<DaftarPreferencesDataSource>(),
        )
    }
    single<SettingsSecurityGateway> {
        VaultSettingsSecurityGateway(
            security = get<VaultSecurity>(),
            authenticator = get<VaultAuthenticator>(),
            session = get<VaultSession>(),
            prompter = get<BiometricPrompter>(),
        )
    }
    single {
        VaultLifecycleCoordinator(
            session = get<VaultSession>(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            mainDispatcher = Dispatchers.Main,
            // Auth drafts are dropped on the synchronous privacy signal, even when the vault is already locked.
            onPrivacyObscured = {
                get<AuthDraftRegistry>().invalidateAll()
                get<RestoreDraftRegistry>().invalidateAll()
                get<SettingsDraftRegistry>().invalidateAll()
            },
        )
    }
    // The privacy eye: the owner's last choice is remembered across locks and launches.
    single {
        val preferences = get<DaftarPreferencesDataSource>()
        ValuesVisibility(
            store = object : ValuesVisibilityStore {
                override suspend fun hidden(): Boolean? = preferences.valuesHidden()

                override suspend fun setHidden(hidden: Boolean) {
                    preferences.setValuesHidden(hidden)
                }
            },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single<PriceRefreshController> {
        SessionPriceRefresh(
            session = get<VaultSession>(),
            refreshPrices = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
    }
    single {
        ZakatTrackingRefresh(
            session = get<VaultSession>(),
            ensure = get(),
            days = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
    }
    single {
        InactivityMonitor(
            session = get<VaultSession>(),
            preferences = get<UserPreferencesRepository>(),
            coordinator = get(),
            clock = get<MonotonicTime>(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
    }
}
