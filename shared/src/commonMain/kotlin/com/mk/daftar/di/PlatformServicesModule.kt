package com.mk.daftar.di

import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.lifecycle.VaultLifecycleCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Application-level services that sit between the platform and the vault. Resolving them opens nothing: the
 * coordinator only observes the session and locks it when a platform asks.
 */
val platformServicesModule: Module = module {
    single {
        VaultLifecycleCoordinator(
            session = get<VaultSession>(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            mainDispatcher = Dispatchers.Main,
        )
    }
}
