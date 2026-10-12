package com.mk.daftar.core.data.session

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.storage.toStorageError
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.VaultRead
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The result of one unlock or create attempt, after the session has decided what to do with it. */
internal sealed interface EngineOpen<out E> {
    data class Opened(val generation: Long) : EngineOpen<Nothing>

    /** A session is already open. The attempt was not made. */
    data object AlreadyOpen : EngineOpen<Nothing>

    /** The credential was refused or the vault refused the attempt. Nothing was opened. */
    data class AttemptFailed<out E>(val error: E) : EngineOpen<E>

    /** A lock happened while the attempt was in flight. The late result was discarded. */
    data object Superseded : EngineOpen<Nothing>

    /** Opening the database failed. Everything partially opened has been closed. */
    data class OpenFailed(val cause: Throwable) : EngineOpen<Nothing>
}

/** The result of one guarded database operation. */
internal sealed interface EngineOp<out T> {
    data class Done<out T>(val value: T) : EngineOp<T>

    /** No session was open when the operation started. */
    data object Locked : EngineOp<Nothing>

    /** The session ended while the operation ran. Whether a write committed is unknown until the vault reopens. */
    data object Ended : EngineOp<Nothing>

    data class Failed(val cause: Throwable) : EngineOp<Nothing>
}

/**
 * The vault session's state machine, generic over the key lease [L] and the database [DB] so the races can be tested
 * on the host with fakes. Production binds [L] to the security lease and [DB] to Room.
 *
 * Generations: [generation] advances on every lock and every unlock attempt. An attempt publishes only if the
 * generation it started with is still current, so a lock during authentication, during Room opening or during a
 * publish wins. Teardown runs under [lifecycle], which also serializes publication, so a new unlock can never close a
 * database that still owns the previous lease.
 *
 * Lock order: [authentication] then [lifecycle] then [gate]. No path takes them in another order.
 */
internal class VaultSessionEngine<L : Any, DB : Any>(
    private val openDatabase: suspend (L) -> DB,
    private val closeDatabase: suspend (DB) -> Unit,
    private val releaseLease: (L) -> Unit,
    private val revokeAuthentication: suspend () -> Unit,
    private val isUnavailable: (Throwable) -> Boolean,
    private val sessionDispatcher: CoroutineDispatcher,
) {
    private class Live<Lease : Any, Database : Any>(
        val generation: Long,
        val lease: Lease,
        val database: Database,
        val scope: CoroutineScope,
    )

    private val gate = Mutex()
    private val lifecycle = Mutex()
    private val authentication = Mutex()

    /** Guarded by [gate]. */
    private var generation = 0L

    /** Guarded by [gate]. The open session whose teardown has not finished yet. */
    private var current: Live<L, DB>? = null

    /** Guarded by [gate]. The generation the in-flight authentication started with, if one is running. */
    private var attemptStarted: Long? = null

    /**
     * Guarded by [gate]. The child job running the in-flight authentication. A lock cancels it, so a pending platform
     * prompt is dismissed at once and its late result is discarded.
     */
    private var attemptJob: Job? = null

    /** Mirrors [generation] for synchronous reads by running operations. Written only under [gate]. */
    private val latestGeneration = MutableStateFlow(0L)

    private val phase = MutableStateFlow<VaultSessionState>(VaultSessionState.Locked)

    /** The session visible to observers. Null whenever no data may be exposed. */
    private val visible = MutableStateFlow<Live<L, DB>?>(null)

    val state: StateFlow<VaultSessionState> = phase.asStateFlow()

    /** Revokes access at once, then tears the session down before returning. Cleanup finishes even if cancelled. */
    suspend fun lock(): Unit = withContext(NonCancellable) {
        val (target, running) = gate.withLock {
            generation++
            latestGeneration.value = generation
            visible.value = null
            phase.value = if (current == null) VaultSessionState.Locked else VaultSessionState.Closing
            generation to attemptJob.takeIf { attemptStarted != null }
        }
        running?.cancel()
        lifecycle.withLock { teardownBefore(target) }
    }

    /**
     * Runs one authentication attempt and publishes the database it opens. [attempt] must not throw for expected
     * failures; it returns the lease on success. A lock during the attempt cancels only the attempt's own job, so the
     * result is [EngineOpen.Superseded] and the caller is never cancelled.
     */
    suspend fun <E : Any> unlock(attempt: suspend () -> Outcome<L, E>): EngineOpen<E> = authentication.withLock {
        val started = beginAttempt() ?: return@withLock EngineOpen.AlreadyOpen
        try {
            val outcome = try {
                runAttempt(attempt)
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    revokeAuthentication()
                    markFailed(started, unavailable = false)
                }
                throw error
            }
            when (outcome) {
                null -> {
                    // A lock cancelled the attempt, so nothing is published and the lease is never admitted.
                    withContext(NonCancellable) {
                        revokeAuthentication()
                        markFailed(started, unavailable = false)
                    }
                    EngineOpen.Superseded
                }

                is Outcome.Failure -> {
                    markFailed(started, unavailable = false)
                    if (currentGeneration() == started) {
                        EngineOpen.AttemptFailed(outcome.error)
                    } else {
                        EngineOpen.Superseded
                    }
                }

                is Outcome.Success -> admit(started, outcome.data)
            }
        } finally {
            withContext(NonCancellable) {
                gate.withLock {
                    if (attemptStarted == started) {
                        attemptStarted = null
                        attemptJob = null
                    }
                }
            }
        }
    }

    /**
     * Runs [attempt] in its own child job, which is registered so that [lock] can cancel it. Returns null when the
     * child was cancelled while the caller stayed active; a cancelled caller rethrows.
     */
    private suspend fun <E : Any> runAttempt(attempt: suspend () -> Outcome<L, E>): Outcome<L, E>? = coroutineScope {
        val job = async { attempt() }
        gate.withLock { attemptJob = job }
        try {
            job.await()
        } catch (error: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    /** Runs [block] on the open session. Returns [EngineOp.Locked] without running it when nothing is open. */
    suspend fun <T> run(block: suspend (DB, () -> Boolean) -> T): EngineOp<T> {
        val session = gate.withLock { visible.value } ?: return EngineOp.Locked
        return try {
            val value = session.scope.async {
                block(session.database) { isCurrent(session.generation) }
            }.await()
            if (isCurrent(session.generation)) EngineOp.Done(value) else EngineOp.Ended
        } catch (error: CancellationException) {
            // The caller's own cancellation is never swallowed. A cancelled session scope is a session end.
            currentCoroutineContext().ensureActive()
            EngineOp.Ended
        } catch (error: Throwable) {
            EngineOp.Failed(error)
        }
    }

    /** Observes [read] for the open session. Emits [VaultRead.Locked] or [VaultRead.Loading] otherwise. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observe(read: (DB) -> Flow<T>): Flow<VaultRead<T>> =
        combine(phase, visible) { currentPhase, session -> currentPhase to session }
            .flatMapLatest { (currentPhase, session) ->
                when {
                    currentPhase is VaultSessionState.Open && session != null -> sessionFlow(session, read)
                    currentPhase == VaultSessionState.Opening -> flowOf(VaultRead.Loading)
                    else -> flowOf(VaultRead.Locked)
                }
            }

    private fun <T> sessionFlow(session: Live<L, DB>, read: (DB) -> Flow<T>): Flow<VaultRead<T>> = callbackFlow {
        // Collection runs inside the session scope, so teardown waits for it before the database closes.
        val job = session.scope.launch {
            try {
                read(session.database).collect { value -> send(VaultRead.Ready(session.generation, value)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                trySend(VaultRead.Failed(error.toStorageError()))
            }
        }
        awaitClose { job.cancel() }
    }

    /** True while [sessionGeneration] is the visible, open session. Synchronous, so writes can check it cheaply. */
    private fun isCurrent(sessionGeneration: Long): Boolean =
        latestGeneration.value == sessionGeneration && visible.value?.generation == sessionGeneration

    private suspend fun currentGeneration(): Long = gate.withLock { generation }

    /** Under [lifecycle]: tears down any stale session, then marks the attempt as opening. Null if already open. */
    private suspend fun beginAttempt(): Long? = lifecycle.withLock {
        if (gate.withLock { visible.value } != null) return@withLock null
        teardownBefore(currentGeneration())
        gate.withLock {
            phase.value = VaultSessionState.Opening
            attemptStarted = generation
            generation
        }
    }

    /**
     * Under [lifecycle]: closes every session older than [target] and releases its key. A session at or after [target]
     * was opened by a later unlock and survives. The database closes before the security layer wipes the key.
     */
    private suspend fun teardownBefore(target: Long): Unit = withContext(NonCancellable) {
        val session = gate.withLock { current }
        if (session != null && session.generation >= target) return@withContext
        // An authentication that started at or after [target] was requested after this lock. It must not be revoked.
        val newerAttempt = gate.withLock { attemptStarted?.let { it >= target } ?: false }
        try {
            if (session != null) {
                session.scope.cancel()
                session.scope.coroutineContext[Job]?.join()
                closeDatabase(session.database)
            }
        } finally {
            if (!newerAttempt) revokeAuthentication()
            session?.let { releaseLease(it.lease) }
            gate.withLock {
                if (current === session) current = null
                if (phase.value == VaultSessionState.Closing) phase.value = VaultSessionState.Locked
            }
        }
    }

    /** Under [lifecycle]: opens the database for a verified lease, unless a lock superseded the attempt. */
    private suspend fun <E : Any> admit(started: Long, lease: L): EngineOpen<E> {
        try {
            return lifecycle.withLock {
                if (currentGeneration() != started) {
                    discard(lease)
                    return@withLock EngineOpen.Superseded
                }
                val database = try {
                    openDatabase(lease)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    discard(lease)
                    markFailed(started, unavailable = isUnavailable(error))
                    return@withLock EngineOpen.OpenFailed(error)
                }
                publish(started, lease, database)
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                discard(lease)
                markFailed(started, unavailable = false)
            }
            throw error
        }
    }

    /** Publishes the open session if no lock arrived. The check and the publication are one gate section. */
    private suspend fun <E : Any> publish(started: Long, lease: L, database: DB): EngineOpen<E> {
        val scope = CoroutineScope(SupervisorJob() + sessionDispatcher)
        val published = gate.withLock {
            if (generation != started) {
                false
            } else {
                val session = Live(started, lease, database, scope)
                current = session
                visible.value = session
                phase.value = VaultSessionState.Open(started)
                true
            }
        }
        if (published) return EngineOpen.Opened(started)
        scope.cancel()
        withContext(NonCancellable) {
            runCatching { closeDatabase(database) }
            discard(lease)
        }
        return EngineOpen.Superseded
    }

    private suspend fun discard(lease: L): Unit = withContext(NonCancellable) {
        revokeAuthentication()
        releaseLease(lease)
    }

    private suspend fun markFailed(started: Long, unavailable: Boolean) {
        gate.withLock {
            if (generation == started) {
                phase.value = if (unavailable) VaultSessionState.Unavailable else VaultSessionState.Locked
            }
        }
    }
}
