@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.refresh

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.refresh.RefreshOutcome
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.domain.repository.PriceRepository
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RefreshRejection
import com.mk.daftar.core.domain.repository.RefreshReport
import com.mk.daftar.core.domain.repository.RefreshStepResult
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.RefreshPricesUseCase
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class ScriptedSession : VaultSession {
    val current = MutableStateFlow<VaultSessionState>(VaultSessionState.Locked)
    override val state: StateFlow<VaultSessionState> = current

    override suspend fun lock() {
        current.value = VaultSessionState.Locked
    }
}

/** Each refresh waits for its own answer, so a test decides when, and whether, a fetch completes. */
private class ScriptedPrices : PriceRepository {
    val answers = mutableListOf<CompletableDeferred<Outcome<RefreshReport, StorageError>>>()
    var calls = 0

    override suspend fun refresh(): Outcome<RefreshReport, StorageError> {
        calls++
        val answer = CompletableDeferred<Outcome<RefreshReport, StorageError>>()
        answers += answer
        return answer.await()
    }

    override fun observeQuotes(): Flow<VaultRead<QuoteSnapshot>> = flowOf(VaultRead.Locked)

    override suspend fun setManual(input: ManualQuoteInput, expectedRevision: Long?): Outcome<Unit, StorageError> =
        error("not used")

    override suspend fun removeOverride(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> =
        error("not used")

    override suspend fun deleteManual(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> =
        error("not used")
}

private fun allAccepted(): RefreshReport = RefreshReport(gold = emptyMap(), fx = emptyMap())

/** One karat kept its cached value while the other was accepted: a partial provider failure. */
private fun partly(): RefreshReport = RefreshReport(
    gold = mapOf(
        GoldKarat.K21 to RefreshStepResult.Kept(RefreshRejection.Unreachable),
        GoldKarat.K24 to RefreshStepResult.Accepted(emptyList()),
    ),
    fx = emptyMap(),
)

class SessionPriceRefreshTest {

    private class Rig(scope: TestScope, retryDelays: List<Duration> = emptyList()) {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val session = ScriptedSession()
        val prices = ScriptedPrices()
        val refresh = SessionPriceRefresh(
            session = session,
            refreshPrices = RefreshPricesUseCase(prices),
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            retryDelays = retryDelays,
            jitter = { 0.0 },
        )

        init {
            refresh.start()
        }
    }

    @Test
    fun eachNewlyOpenedGenerationStartsExactlyOneAutomaticRefresh() = runTest {
        val rig = Rig(this)
        rig.session.current.value = VaultSessionState.Open(1)
        runCurrent()
        assertEquals(1, rig.prices.calls)
        assertEquals(PriceRefreshStatus.Refreshing(1, visible = false), rig.refresh.status.value, "automatic is quiet")

        // Re-emitting the same generation, and tab changes or collectors, never start another refresh.
        rig.session.current.value = VaultSessionState.Open(1)
        rig.refresh.start()
        runCurrent()
        assertEquals(1, rig.prices.calls)
    }

    @Test
    fun explicitRequestsWhileOneIsInFlightJoinIt() = runTest {
        val rig = Rig(this)
        rig.session.current.value = VaultSessionState.Open(1)
        runCurrent()
        rig.refresh.refresh()
        rig.refresh.refresh()
        runCurrent()
        assertEquals(1, rig.prices.calls, "coalesced taps never queue duplicate fetches")
        assertEquals(
            PriceRefreshStatus.Refreshing(1, visible = true),
            rig.refresh.status.value,
            "joining shows progress",
        )

        rig.prices.answers.single().complete(Outcome.Success(allAccepted()))
        advanceUntilIdle()
        assertEquals(PriceRefreshStatus.Finished(1, RefreshOutcome.Succeeded), rig.refresh.status.value)

        rig.refresh.refresh()
        runCurrent()
        assertEquals(2, rig.prices.calls, "a finished refresh allows the next explicit one")
    }

    @Test
    fun lockCancelsTheInFlightRefreshAndALateReportChangesNothing() = runTest {
        val rig = Rig(this)
        rig.session.current.value = VaultSessionState.Open(1)
        runCurrent()
        rig.session.current.value = VaultSessionState.Locked
        runCurrent()
        assertEquals(PriceRefreshStatus.Idle, rig.refresh.status.value)

        rig.prices.answers.single().complete(Outcome.Success(allAccepted()))
        advanceUntilIdle()
        assertEquals(PriceRefreshStatus.Idle, rig.refresh.status.value, "a cancelled refresh never reports")
    }

    @Test
    fun aNewGenerationStartsAgainAndNeverInheritsTheOldStatus() = runTest {
        val rig = Rig(this)
        rig.session.current.value = VaultSessionState.Open(1)
        runCurrent()
        rig.prices.answers.single().complete(Outcome.Success(partly()))
        advanceUntilIdle()
        assertEquals(
            PriceRefreshStatus.Finished(1, RefreshOutcome.PartiallyFailed, setOf(RefreshRejection.Unreachable)),
            rig.refresh.status.value,
        )

        rig.session.current.value = VaultSessionState.Locked
        rig.session.current.value = VaultSessionState.Open(2)
        runCurrent()
        assertEquals(2, rig.prices.calls)
        assertEquals(PriceRefreshStatus.Refreshing(2, visible = false), rig.refresh.status.value)
    }

    @Test
    fun aStorageFailureIsReportedAsAFailedRefresh() = runTest {
        val rig = Rig(this)
        rig.session.current.value = VaultSessionState.Open(1)
        runCurrent()
        rig.prices.answers.single().complete(Outcome.Failure(StorageError.DiskFull))
        advanceUntilIdle()
        assertEquals(PriceRefreshStatus.Finished(1, RefreshOutcome.Failed), rig.refresh.status.value)
    }

    @Test
    fun requestsWithoutAnOpenSessionDoNothing() = runTest {
        val rig = Rig(this)
        rig.refresh.refresh()
        runCurrent()
        assertEquals(0, rig.prices.calls)
        assertIs<PriceRefreshStatus.Idle>(rig.refresh.status.value)
    }

    @Test
    fun anUnreachableProviderIsRetriedQuietlyAndOnlyTheFinalResultIsReported() = runTest {
        val rig = Rig(this, retryDelays = listOf(5.seconds, 15.seconds))
        rig.session.current.value = VaultSessionState.Open(1)
        runCurrent()
        rig.prices.answers[0].complete(Outcome.Success(partly()))
        runCurrent()
        assertEquals(
            PriceRefreshStatus.Refreshing(1, visible = false),
            rig.refresh.status.value,
            "no failure shown yet",
        )

        advanceTimeBy(5.seconds + 1.milliseconds)
        assertEquals(2, rig.prices.calls)
        rig.prices.answers[1].complete(Outcome.Success(partly()))
        advanceTimeBy(15.seconds + 1.milliseconds)
        assertEquals(3, rig.prices.calls)
        rig.prices.answers[2].complete(Outcome.Success(partly()))
        advanceUntilIdle()

        assertEquals(3, rig.prices.calls, "retries stop after the schedule")
        assertEquals(
            PriceRefreshStatus.Finished(1, RefreshOutcome.PartiallyFailed, setOf(RefreshRejection.Unreachable)),
            rig.refresh.status.value,
        )
    }

    @Test
    fun aRetryThatSucceedsReportsSuccess() = runTest {
        val rig = Rig(this, retryDelays = listOf(5.seconds))
        rig.session.current.value = VaultSessionState.Open(1)
        runCurrent()
        rig.prices.answers[0].complete(Outcome.Success(partly()))
        advanceTimeBy(5.seconds + 1.milliseconds)
        rig.prices.answers[1].complete(Outcome.Success(allAccepted()))
        advanceUntilIdle()
        assertEquals(PriceRefreshStatus.Finished(1, RefreshOutcome.Succeeded), rig.refresh.status.value)
    }

    @Test
    fun aRateLimitOrStorageFailureIsNotRetried() = runTest {
        val rig = Rig(this, retryDelays = listOf(5.seconds))
        rig.session.current.value = VaultSessionState.Open(1)
        runCurrent()
        rig.prices.answers[0].complete(Outcome.Failure(StorageError.DiskFull))
        advanceUntilIdle()
        assertEquals(1, rig.prices.calls)
        assertEquals(PriceRefreshStatus.Finished(1, RefreshOutcome.Failed), rig.refresh.status.value)
    }

    @Test
    fun anExplicitRequestDuringAQuietWaitRunsNowAndReportsAtOnce() = runTest {
        val rig = Rig(this, retryDelays = listOf(60.seconds))
        rig.session.current.value = VaultSessionState.Open(1)
        runCurrent()
        rig.prices.answers[0].complete(Outcome.Success(partly()))
        runCurrent()

        rig.refresh.refresh()
        runCurrent()
        assertEquals(2, rig.prices.calls, "the owner does not wait for the retry timer")
        assertEquals(PriceRefreshStatus.Refreshing(1, visible = true), rig.refresh.status.value)
        rig.prices.answers[1].complete(Outcome.Success(partly()))
        runCurrent()
        assertEquals(
            PriceRefreshStatus.Finished(1, RefreshOutcome.PartiallyFailed, setOf(RefreshRejection.Unreachable)),
            rig.refresh.status.value,
            "the owner's failure is reported at once",
        )
    }

    @Test
    fun lockStopsPendingRetries() = runTest {
        val rig = Rig(this, retryDelays = listOf(5.seconds))
        rig.session.current.value = VaultSessionState.Open(1)
        runCurrent()
        rig.prices.answers[0].complete(Outcome.Success(partly()))
        runCurrent()
        rig.session.current.value = VaultSessionState.Locked
        advanceUntilIdle()
        assertEquals(1, rig.prices.calls)
        assertEquals(PriceRefreshStatus.Idle, rig.refresh.status.value)
    }
}
