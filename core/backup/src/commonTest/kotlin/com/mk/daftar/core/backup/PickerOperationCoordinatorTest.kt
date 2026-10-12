@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class PickerOperationCoordinatorTest {

    private class FakeClock(var now: Long = 0L) : MonotonicClock {
        override fun millis(): Long = now
    }

    private class FakeStaging : StagedBackup {
        var discards = 0

        override suspend fun discard() {
            discards++
        }
    }

    private val lifetime = PICKER_OPERATION_LIFETIME_MILLIS

    @Test
    fun theLifetimeIsFiveMinutes() {
        assertEquals(300_000L, PICKER_OPERATION_LIFETIME_MILLIS)
    }

    @Test
    fun aFirstAnswerIsDeliveredAndConsumesTheToken() = runTest {
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, FakeClock())
        val token = coordinator.begin()

        assertTrue(coordinator.complete(token, "picked"))
        assertEquals("picked", coordinator.awaitResult(token))
        assertNull(coordinator.pendingToken)
    }

    @Test
    fun aDuplicateAnswerIsRejectedAndDoesNotReplaceTheFirst() = runTest {
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, FakeClock())
        val token = coordinator.begin()

        assertTrue(coordinator.complete(token, "first"))
        assertFalse(coordinator.complete(token, "second"))
        assertEquals("first", coordinator.awaitResult(token))
    }

    @Test
    fun aCancelledAnswerIsDeliveredAsNull() = runTest {
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, FakeClock())
        val token = coordinator.begin()

        assertTrue(coordinator.complete(token, null))
        assertNull(coordinator.awaitResult(token))
    }

    @Test
    fun aTokenFromAnEarlierOperationCannotAnswerTheLiveOne() = runTest {
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, FakeClock())
        val earlier = coordinator.begin()
        val live = coordinator.begin()

        assertFalse(coordinator.complete(earlier, "late"))
        assertTrue(coordinator.complete(live, "fresh"))
        assertEquals("fresh", coordinator.awaitResult(live))
    }

    @Test
    fun beginningANewOperationAbandonsTheOneWaitingAndDiscardsItsStaging() = runTest {
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, FakeClock())
        val staging = FakeStaging()
        val first = coordinator.begin(staging)
        coordinator.begin()
        runCurrent()

        assertEquals(1, staging.discards)
        assertFalse(coordinator.complete(first, "late"))
    }

    @Test
    fun completePendingAnswersTheOperationThatIsWaiting() = runTest {
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, FakeClock())
        assertFalse(coordinator.completePending("nobody waits"))

        val token = coordinator.begin()
        assertTrue(coordinator.completePending("picked"))
        assertEquals("picked", coordinator.awaitResult(token))
        assertFalse(coordinator.completePending("duplicate"))
    }

    @Test
    fun anAnswerThatArrivesBeforeTheWaitIsStillDelivered() = runTest {
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, FakeClock())
        val token = coordinator.begin()
        coordinator.complete(token, "early")

        assertEquals("early", coordinator.awaitResult(token))
    }

    @Test
    fun expiryOnTheTimerDiscardsStagingAndRefusesALateAnswer() = runTest {
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, FakeClock())
        val staging = FakeStaging()
        val token = coordinator.begin(staging)

        advanceTimeBy(lifetime + 1)
        runCurrent()

        assertEquals(1, staging.discards)
        assertFalse(coordinator.complete(token, "late"))
        assertNull(coordinator.awaitResult(token))
    }

    @Test
    fun anAnswerAfterTheMonotonicLifetimeIsRefusedAndDiscardsStaging() = runTest {
        val clock = FakeClock()
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, clock)
        val staging = FakeStaging()
        val token = coordinator.begin(staging)

        // The timer has not run, but the lifetime has passed on the clock, so the answer is still refused.
        clock.now = lifetime
        assertFalse(coordinator.complete(token, "late"))
        runCurrent()

        assertEquals(1, staging.discards)
        assertNull(coordinator.pendingToken)
    }

    @Test
    fun anAnswerOneMillisecondBeforeTheLifetimeIsAccepted() = runTest {
        val clock = FakeClock()
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, clock)
        val token = coordinator.begin()

        clock.now = lifetime - 1
        assertTrue(coordinator.complete(token, "in time"))
        assertEquals("in time", coordinator.awaitResult(token))
    }

    @Test
    fun anAnswerInsideTheLifetimeIsAcceptedJustBeforeExpiry() = runTest {
        val clock = FakeClock()
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, clock)
        val staging = FakeStaging()
        val token = coordinator.begin(staging)

        clock.now = lifetime - 1
        advanceTimeBy(lifetime - 1)
        runCurrent()
        assertTrue(coordinator.complete(token, "in time"))
        advanceUntilIdle()

        assertEquals("in time", coordinator.awaitResult(token))
        assertEquals(0, staging.discards)
    }

    @Test
    fun aConsumedOperationLeavesItsStagingToItsOwner() = runTest {
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, FakeClock())
        val staging = FakeStaging()
        val token = coordinator.begin(staging)

        assertTrue(coordinator.complete(token, "written"))
        runCurrent()

        assertEquals(0, staging.discards)
    }

    @Test
    fun abandoningDiscardsStagingAndRefusesALateAnswer() = runTest {
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, FakeClock())
        val staging = FakeStaging()
        val token = coordinator.begin(staging)

        coordinator.abandon(token)
        runCurrent()

        assertEquals(1, staging.discards)
        assertFalse(coordinator.complete(token, "late"))
        assertNull(coordinator.awaitResult(token))
    }

    @Test
    fun cancellingTheWaitingCoroutineAbandonsTheOperation() = runTest {
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, FakeClock())
        val staging = FakeStaging()
        val token = coordinator.begin(staging)
        val waiter = async { coordinator.awaitResult(token) }
        runCurrent()

        waiter.cancel()
        runCurrent()

        assertEquals(1, staging.discards)
        assertNull(coordinator.pendingToken)
        assertFalse(coordinator.complete(token, "late"))
    }

    @Test
    fun aPrivacyEventDoesNotCancelTheWaitBecauseItDoesNotTouchTheCoroutine() = runTest {
        val coordinator = PickerOperationCoordinator<String>(backgroundScope, FakeClock())
        val staging = FakeStaging()
        val token = coordinator.begin(staging)
        val waiter = async { coordinator.awaitResult(token) }
        runCurrent()

        // Nothing is cancelled here: the operation is still waiting and its answer is still accepted.
        assertTrue(coordinator.pendingToken === token)
        assertTrue(coordinator.complete(token, "returned"))
        assertEquals("returned", waiter.await())
        assertEquals(0, staging.discards)
    }
}
