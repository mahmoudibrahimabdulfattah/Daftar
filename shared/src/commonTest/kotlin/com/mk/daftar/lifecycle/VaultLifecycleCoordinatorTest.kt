@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.lifecycle

import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A session whose lock can be held open, to model a teardown that is still cleaning up. */
private class FakeSession(initial: VaultSessionState = VaultSessionState.Locked) : VaultSession {
    val current = MutableStateFlow(initial)
    override val state: StateFlow<VaultSessionState> = current

    var lockCalls = 0
    var cleanup: CompletableDeferred<Unit>? = null
    var onLock: () -> Unit = {}

    override suspend fun lock() {
        lockCalls++
        onLock()
        cleanup?.await()
        current.value = VaultSessionState.Locked
    }
}

private class Rig(scheduler: TestCoroutineScheduler, initial: VaultSessionState = VaultSessionState.Locked) {
    val dispatcher = StandardTestDispatcher(scheduler)
    val scope = CoroutineScope(SupervisorJob() + dispatcher)
    val session = FakeSession(initial)

    /** The cover value observed at each lock call. Ordering: the cover must be up before the lock starts. */
    val coverAtLock = mutableListOf<Boolean>()
    val coordinator = VaultLifecycleCoordinator(session, scope, dispatcher)

    init {
        session.onLock = { coverAtLock += coordinator.isCovered }
    }

    fun close() = scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
}

class VaultLifecycleCoordinatorTest {

    @Test
    fun theCoverGoesUpBeforeTheLockStarts() = runTest {
        val rig = Rig(testScheduler)
        rig.coordinator.obscure("activity")
        rig.coordinator.enteredBackground()
        runCurrent()
        assertEquals(listOf(true), rig.coverAtLock)
        rig.close()
    }

    @Test
    fun aBackgroundWithoutAPriorObscureStillCoversBeforeLocking() = runTest {
        val rig = Rig(testScheduler)
        rig.coordinator.stopped(changingConfigurations = false)
        runCurrent()
        assertEquals(listOf(true), rig.coverAtLock)
        rig.close()
    }

    @Test
    fun theLockStartsImmediatelyFromTheApplicationScope() = runTest {
        val rig = Rig(testScheduler)
        rig.coordinator.enteredBackground()
        // No dispatch has run yet, so the lock has already begun on the caller's thread.
        assertEquals(1, rig.session.lockCalls)
        rig.close()
    }

    @Test
    fun repeatedCallbacksWhileALockIsInFlightShareOneLock() = runTest {
        val rig = Rig(testScheduler)
        rig.session.cleanup = CompletableDeferred()
        rig.coordinator.obscure("activity")
        val first = rig.coordinator.stopped(changingConfigurations = false)
        val second = rig.coordinator.enteredBackground()
        val third = rig.coordinator.stopped(changingConfigurations = false)
        runCurrent()

        assertEquals(1, rig.session.lockCalls)
        assertSame(first, second)
        assertSame(first, third)
        rig.session.cleanup!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(first!!.isCompleted)
        rig.close()
    }

    @Test
    fun aBackgroundAfterCleanupLocksAgain() = runTest {
        val rig = Rig(testScheduler, VaultSessionState.Open(1))
        rig.coordinator.enteredBackground()
        advanceUntilIdle()
        rig.session.current.value = VaultSessionState.Open(2)
        rig.coordinator.reveal("activity")
        rig.coordinator.enteredBackground()
        advanceUntilIdle()
        assertEquals(2, rig.session.lockCalls)
        assertEquals(VaultSessionState.Locked, rig.session.state.value)
        rig.close()
    }

    @Test
    fun aConfigurationChangeKeepsTheSessionAndLocksNothing() = runTest {
        val rig = Rig(testScheduler, VaultSessionState.Open(1))
        rig.coordinator.obscure("activity")
        assertNull(rig.coordinator.stopped(changingConfigurations = true))
        advanceUntilIdle()
        assertEquals(0, rig.session.lockCalls)
        assertTrue(rig.coordinator.isCovered, "the cover stays up across the recreation")

        assertNull(rig.coordinator.reveal("activity"))
        advanceUntilIdle()
        assertFalse(rig.coordinator.isCovered)
        assertEquals(VaultSessionState.Open(1), rig.session.state.value)
        rig.close()
    }

    @Test
    fun aStopThatIsNotAConfigurationChangeLocksImmediately() = runTest {
        val rig = Rig(testScheduler, VaultSessionState.Open(1))
        rig.coordinator.obscure("activity")
        assertTrue(rig.coordinator.stopped(changingConfigurations = false) != null)
        runCurrent()
        assertEquals(1, rig.session.lockCalls)
        rig.close()
    }

    @Test
    fun theProcessStopFallbackIsIdempotentAfterTheActivityStop() = runTest {
        val rig = Rig(testScheduler, VaultSessionState.Open(1))
        rig.coordinator.obscure("activity")
        rig.coordinator.stopped(changingConfigurations = false)
        advanceUntilIdle()
        rig.coordinator.enteredBackground()
        advanceUntilIdle()
        assertEquals(VaultSessionState.Locked, rig.session.state.value)
        assertTrue(rig.coordinator.isCovered, "a backgrounded vault stays covered until the app returns")
        rig.close()
    }

    @Test
    fun aProcessStopDuringAConfigurationChangeDoesNotLock() = runTest {
        val rig = Rig(testScheduler, VaultSessionState.Open(1))
        rig.coordinator.obscure("activity")
        rig.coordinator.stopped(changingConfigurations = true)
        // The delayed process-level stop lands while the replacement activity is still being created.
        assertNull(rig.coordinator.processStopped())
        advanceUntilIdle()
        assertEquals(0, rig.session.lockCalls)
        assertEquals(VaultSessionState.Open(1), rig.session.state.value)

        assertNull(rig.coordinator.reveal("activity"))
        advanceUntilIdle()
        assertFalse(rig.coordinator.isCovered)
        rig.close()
    }

    @Test
    fun aProcessStopLocksWhenNoConfigurationChangeIsPending() = runTest {
        val rig = Rig(testScheduler, VaultSessionState.Open(1))
        rig.coordinator.obscure("activity")
        rig.coordinator.stopped(changingConfigurations = false)
        advanceUntilIdle()
        assertTrue(rig.coordinator.processStopped() != null)
        advanceUntilIdle()
        assertEquals(VaultSessionState.Locked, rig.session.state.value)
        rig.close()
    }

    @Test
    fun aLateUnlockWhileBackgroundedIsLockedAgain() = runTest {
        val rig = Rig(testScheduler)
        rig.coordinator.start()
        runCurrent()
        rig.coordinator.enteredBackground()
        advanceUntilIdle()
        rig.session.current.value = VaultSessionState.Open(2)
        advanceUntilIdle()
        assertEquals(2, rig.session.lockCalls)
        assertEquals(VaultSessionState.Locked, rig.session.state.value)
        rig.close()
    }

    @Test
    fun aLateUnlockDuringACleanupIsLockedToo() = runTest {
        val rig = Rig(testScheduler, VaultSessionState.Open(1))
        val cleanup = CompletableDeferred<Unit>()
        rig.session.cleanup = cleanup
        rig.coordinator.start()
        runCurrent()
        rig.coordinator.enteredBackground()
        runCurrent()
        rig.session.current.value = VaultSessionState.Open(2)
        runCurrent()
        assertEquals(2, rig.session.lockCalls, "the unlock raced the first lock and must be locked again")
        cleanup.complete(Unit)
        advanceUntilIdle()
        assertEquals(VaultSessionState.Locked, rig.session.state.value)
        rig.close()
    }

    @Test
    fun aReturnDuringCleanupKeepsTheCoverUntilTheCleanupFinishes() = runTest {
        val rig = Rig(testScheduler, VaultSessionState.Open(1))
        val cleanup = CompletableDeferred<Unit>()
        rig.session.cleanup = cleanup
        rig.coordinator.obscure("activity")
        val lock = rig.coordinator.enteredBackground()
        runCurrent()

        val pending = rig.coordinator.reveal("activity")
        assertSame(lock, pending, "the cover waits for the lock that is still cleaning up")
        assertTrue(rig.coordinator.isCovered, "the previous generation must not be visible during cleanup")

        cleanup.complete(Unit)
        advanceUntilIdle()
        assertTrue(lock.isCompleted)
        assertFalse(rig.coordinator.isCovered)
        rig.close()
    }

    @Test
    fun aReturnAfterCleanupRevealsAtOnce() = runTest {
        val rig = Rig(testScheduler, VaultSessionState.Open(1))
        rig.coordinator.obscure("activity")
        rig.coordinator.enteredBackground()
        advanceUntilIdle()
        assertTrue(rig.coordinator.isCovered)
        assertNull(rig.coordinator.reveal("activity"))
        assertFalse(rig.coordinator.isCovered)
        rig.close()
    }

    @Test
    fun aLockStillRunsWhenTheSurfaceIsGone() = runTest {
        val rig = Rig(testScheduler, VaultSessionState.Open(1))
        val cleanup = CompletableDeferred<Unit>()
        rig.session.cleanup = cleanup
        rig.coordinator.obscure("scene-gone")
        rig.coordinator.enteredBackground()
        runCurrent()
        // The surface is never revealed again, for example because the scene was discarded.
        cleanup.complete(Unit)
        advanceUntilIdle()
        assertEquals(VaultSessionState.Locked, rig.session.state.value)
        rig.close()
    }

    @Test
    fun everyConnectedSceneKeepsTheCoverUntilItReturns() = runTest {
        val rig = Rig(testScheduler, VaultSessionState.Open(1))
        rig.coordinator.obscure("scene-a")
        rig.coordinator.obscure("scene-b")
        rig.coordinator.enteredBackground()
        advanceUntilIdle()

        assertNull(rig.coordinator.reveal("scene-a"))
        assertTrue(rig.coordinator.isCovered, "scene-b is still obscured")
        assertNull(rig.coordinator.reveal("scene-b"))
        assertFalse(rig.coordinator.isCovered)
        assertEquals(VaultSessionState.Locked, rig.session.state.value)
        rig.close()
    }
}
