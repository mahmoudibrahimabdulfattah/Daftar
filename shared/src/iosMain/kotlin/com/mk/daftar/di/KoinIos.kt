package com.mk.daftar.di

import com.mk.daftar.lifecycle.VaultLifecycleCoordinator
import org.koin.mp.KoinPlatform

/**
 * Starts Koin and the late-unlock guard. Called once from the Swift app initializer, before any scene exists. A
 * second call is ignored, so no controller recreation can restart the graph.
 */
fun doInitKoin() {
    if (KoinPlatform.getKoinOrNull() != null) return
    initKoin()
    KoinPlatform.getKoin().get<VaultLifecycleCoordinator>().start()
}

/** The coordinator the Swift scene observers call. Valid only after [doInitKoin]. */
fun vaultLifecycleCoordinator(): VaultLifecycleCoordinator = KoinPlatform.getKoin().get()
