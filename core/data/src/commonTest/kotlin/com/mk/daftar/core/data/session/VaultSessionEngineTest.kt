@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.core.data.session

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.VaultRead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A fake key lease: closed when its key is released. */
private class FakeLease(val name: String) {
    var closed = false
}

/** A fake database: closed when Room would close its pools. */
private class FakeDatabase(val name: String) {
    var closed = false
}

/**
 * The race harness. The events list records the order of teardown steps, and the gates let a test park an operation
 * at a chosen point. The fake security mirrors the real one: it wipes its current lease on revoke, and a publish
 * started before a revoke is discarded.
 */
private class Harness(
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    isUnavailable: (Throwable) -> Boolean = { false },
) {
    val events = mutableListOf<String>()
    val opened = mutableListOf<FakeDatabase>()
    var openGate: CompletableDeferred<Unit>? = null
    var closeGate: CompletableDeferred<Unit>? = null
    var openFailures = 0
    private var securityEpoch = 0
    var securityLease: FakeLease? = null
    var publishedWhileDatabaseOpen = false
    var openDatabases = 0

    val engine = VaultSessionEngine<FakeLease, FakeDatabase>(
        openDatabase = { lease ->
            events += "open:${lease.name}"
            openGate?.await()
            if (openFailures > 0) {
                openFailures--
                throw IllegalStateException("transient open failure")
            }
            FakeDatabase(lease.name).also {
                opened += it
                openDatabases++
            }
        },
        closeDatabase = { database ->
            closeGate?.await()
            events += "close:${database.name}"
            database.closed = true
            openDatabases--
        },
        releaseLease = { lease ->
            events += "release:${lease.name}"
            lease.closed = true
        },
        revokeAuthentication = {
            events += "revoke"
            securityEpoch++
            securityLease?.closed = true
            securityLease = null
        },
        isUnavailable = isUnavailable,
        sessionDispatcher = dispatcher,
    )

    /** A fake credential check. It publishes its lease only if no revoke happened while it was verifying. */
    suspend fun attempt(name: String, verifying: CompletableDeferred<Unit>? = null): Outcome<FakeLease, String> {
        val startEpoch = securityEpoch
        verifying?.await()
        if (securityEpoch != startEpoch) return Outcome.Failure("superseded in security")
        if (openDatabases > 0) publishedWhileDatabaseOpen = true
        val lease = FakeLease(name)
        securityLease = lease
        return Outcome.Success(lease)
    }
}

class VaultSessionEngineTest {

    private fun TestScope.harness(isUnavailable: (Throwable) -> Boolean = { false }) =
        Harness(StandardTestDispatcher(testScheduler), isUnavailable)

    @Test
    fun unlockPublishesAnOpenSessionAndObserversReadIt() = runTest {
        val h = harness()
        val result = h.engine.unlock { h.attempt("a") }
        assertIs<EngineOpen.Opened>(result)
        assertEquals(VaultSessionState.Open(result.generation), h.engine.state.value)

        val seen = mutableListOf<VaultRead<Int>>()
        val job = launch { h.engine.observe { flow { emit(7) } }.collect { seen += it } }
        runCurrent()
        assertEquals(VaultRead.Ready(result.generation, 7), seen.last())
        job.cancel()
    }

    @Test
    fun observersSeeLockedWhileNoSessionIsOpen() = runTest {
        val h = harness()
        val seen = mutableListOf<VaultRead<Int>>()
        val job = launch { h.engine.observe { flow { emit(1) } }.collect { seen += it } }
        runCurrent()
        assertEquals(listOf<VaultRead<Int>>(VaultRead.Locked), seen)
        job.cancel()
    }

    @Test
    fun lockClosesTheDatabaseBeforeTheSecurityLayerIsRevoked() = runTest {
        val h = harness()
        h.engine.unlock { h.attempt("a") }
        h.engine.lock()

        // The first revoke happens before the open, when nothing is held. The teardown's revoke follows the close.
        val close = h.events.indexOf("close:a")
        val revoke = h.events.lastIndexOf("revoke")
        val release = h.events.indexOf("release:a")
        assertTrue(close >= 0 && revoke > close && release > revoke, "order was ${h.events}")
        assertTrue(h.opened.single().closed)
        assertEquals(VaultSessionState.Locked, h.engine.state.value)
    }

    @Test
    fun lockDuringAuthenticationSupersedesItAndPublishesNothing() = runTest {
        val h = harness()
        val verifying = CompletableDeferred<Unit>()
        val unlock = async { h.engine.unlock { h.attempt("a", verifying) } }
        runCurrent()

        h.engine.lock()
        verifying.complete(Unit)
        assertEquals(EngineOpen.Superseded, unlock.await())
        assertEquals(VaultSessionState.Locked, h.engine.state.value)
        assertTrue(h.opened.isEmpty())
        assertEquals(null, h.securityLease)
    }

    @Test
    fun lockDuringRoomOpeningWaitsForTheOpenThenDiscardsIt() = runTest {
        val h = harness()
        h.openGate = CompletableDeferred()
        val unlock = async { h.engine.unlock { h.attempt("a") } }
        runCurrent()

        val lock = async { h.engine.lock() }
        runCurrent()
        assertFalse(lock.isCompleted, "lock must wait for the open that holds the lifecycle gate")

        h.openGate!!.complete(Unit)
        assertEquals(EngineOpen.Superseded, unlock.await())
        lock.await()
        assertEquals(VaultSessionState.Locked, h.engine.state.value)
        assertTrue(h.opened.all { it.closed })
        assertTrue(h.events.indexOf("close:a") < h.events.indexOf("release:a"))
    }

    @Test
    fun lockDuringFlowEmissionCancelsCollectorsBeforeTheDatabaseCloses() = runTest {
        val h = harness()
        h.engine.unlock { h.attempt("a") }
        val collected = CompletableDeferred<Unit>()
        val flow = h.engine.observe {
            flow {
                emit(1)
                collected.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    h.events += "collector-done"
                }
            }
        }
        val job = launch { flow.collect {} }
        runCurrent()
        collected.await()

        h.engine.lock()
        val done = h.events.indexOf("collector-done")
        val close = h.events.indexOf("close:a")
        assertTrue(done >= 0 && close > done, "order was ${h.events}")
        job.cancel()
    }

    @Test
    fun lockDuringAWriteWaitsForTheWriteAndReportsTheSessionEnded() = runTest {
        val h = harness()
        h.engine.unlock { h.attempt("a") }
        val inside = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val write = async {
            h.engine.run { _, stillCurrent ->
                inside.complete(Unit)
                release.await()
                h.events += "write-done"
                stillCurrent()
            }
        }
        runCurrent()
        inside.await()

        val lock = async { h.engine.lock() }
        runCurrent()
        release.complete(Unit)
        lock.await()
        val result = write.await()
        assertEquals(EngineOp.Ended, result)
        assertTrue(h.events.indexOf("write-done") < h.events.indexOf("close:a"), "order was ${h.events}")
    }

    @Test
    fun aCancelledLockCallerStillCompletesTheTeardown() = runTest {
        val h = harness()
        h.engine.unlock { h.attempt("a") }
        h.closeGate = CompletableDeferred()
        val caller = launch(start = CoroutineStart.UNDISPATCHED) { h.engine.lock() }
        runCurrent()
        caller.cancel()

        h.closeGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(h.opened.single().closed, "cleanup must finish after the caller was cancelled")
        assertEquals(VaultSessionState.Locked, h.engine.state.value)
        assertEquals(null, h.securityLease)
    }

    @Test
    fun rapidLockAndUnlockNeverLeaksADatabaseOrALease() = runTest {
        val h = harness()
        repeat(30) { round ->
            val result = h.engine.unlock { h.attempt("r$round") }
            assertIs<EngineOpen.Opened>(result)
            h.engine.lock()
        }
        assertEquals(30, h.opened.size)
        assertTrue(h.opened.all { it.closed })
        assertEquals(0, h.openDatabases)
        assertFalse(h.publishedWhileDatabaseOpen, "a new lease was published while a database still owned one")
        assertEquals(VaultSessionState.Locked, h.engine.state.value)
    }

    @Test
    fun aSecondUnlockWhileOpenIsRefusedWithoutOpeningAgain() = runTest {
        val h = harness()
        h.engine.unlock { h.attempt("a") }
        assertEquals(EngineOpen.AlreadyOpen, h.engine.unlock { h.attempt("b") })
        assertEquals(1, h.opened.size)
    }

    @Test
    fun aFailedInitialOpenLeavesNothingOpenAndRetrySucceeds() = runTest {
        val h = harness()
        h.openFailures = 1
        val first = h.engine.unlock { h.attempt("a") }
        assertIs<EngineOpen.OpenFailed>(first)
        assertEquals(VaultSessionState.Locked, h.engine.state.value)
        assertEquals(null, h.securityLease)
        assertTrue(h.events.contains("release:a"))

        val retry = h.engine.unlock { h.attempt("b") }
        assertIs<EngineOpen.Opened>(retry)
        assertEquals(VaultSessionState.Open(retry.generation), h.engine.state.value)
    }

    @Test
    fun anUnavailableStorageFailureIsReportedAsUnavailable() = runTest {
        val h = harness(isUnavailable = { true })
        h.openFailures = 1
        h.engine.unlock { h.attempt("a") }
        assertEquals(VaultSessionState.Unavailable, h.engine.state.value)
    }

    @Test
    fun aFailedCredentialLeavesTheVaultLocked() = runTest {
        val h = harness()
        val result = h.engine.unlock { Outcome.Failure("wrong pin") }
        assertEquals(EngineOpen.AttemptFailed("wrong pin"), result)
        assertEquals(VaultSessionState.Locked, h.engine.state.value)
        assertTrue(h.opened.isEmpty())
    }

    @Test
    fun aRunOnALockedVaultDoesNotExecuteTheBlock() = runTest {
        val h = harness()
        var ran = false
        val result = h.engine.run { _, _ ->
            ran = true
            Unit
        }
        assertEquals(EngineOp.Locked, result)
        assertFalse(ran)
    }
}
