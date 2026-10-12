package com.mk.daftar.core.domain.usecase

import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** How often age-dependent values are recomputed while collected. Recomputation is local; nothing polls the network. */
internal val AGE_REEVALUATION_INTERVAL: Duration = 1.minutes

/**
 * Runs [read] only while the session is open, for the generation it reports. Each session change cancels the previous
 * inner collection, so a lock or a new generation never leaves an old observation running. Nothing is cached here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> observeInSession(
    session: VaultSession,
    read: (generation: Long) -> Flow<VaultRead<T>>,
): Flow<VaultRead<T>> = session.state
    .flatMapLatest { state ->
        when (state) {
            is VaultSessionState.Open -> read(state.generation)
            VaultSessionState.Opening -> flowOf(VaultRead.Loading)
            VaultSessionState.Locked, VaultSessionState.Closing -> flowOf(VaultRead.Locked)
            VaultSessionState.Unavailable -> flowOf(VaultRead.Failed(StorageError.DatabaseUnavailable))
        }
    }
    .distinctUntilChanged()

/** Emits the current instant immediately on collection, then once per [AGE_REEVALUATION_INTERVAL]. */
internal fun clockTicks(clock: Clock): Flow<Instant> = flow {
    emit(clock.now())
    while (true) {
        delay(AGE_REEVALUATION_INTERVAL)
        emit(clock.now())
    }
}

/** The value when this read is `Ready` for [generation]. Anything else, including another generation, is null. */
internal fun <T> VaultRead<T>.valueOf(generation: Long): T? =
    (this as? VaultRead.Ready<T>)?.takeIf { it.generation == generation }?.value
