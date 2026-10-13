@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.refresh

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.repository.ZakatYearRepository
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.EnsureZakatTrackingUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatDayUseCase
import com.mk.daftar.core.model.ZakatYear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

private class ZakatScriptedSession : VaultSession {
    val current = MutableStateFlow<VaultSessionState>(VaultSessionState.Locked)
    override val state: StateFlow<VaultSessionState> = current

    override suspend fun lock() {
        current.value = VaultSessionState.Locked
    }
}

/** Counts the reconciliations; a test can make the next one fail. */
private class ZakatCountingTracking : ZakatYearRepository {
    var ensures = 0
    var failNext = false

    override fun observeTracking(): Flow<VaultRead<ZakatTrackingState>> = flowOf(VaultRead.Locked)

    override suspend fun ensureTracking(today: LocalDate): Outcome<ZakatTrackingState, StorageError> {
        ensures++
        if (failNext) {
            failNext = false
            return Outcome.Failure(StorageError.Io)
        }
        return Outcome.Success(ZakatTrackingState(1447, listOf(ZakatYear.unchecked(1447))))
    }

    override suspend fun setPaid(hijriYear: Int, paid: Boolean): Outcome<ZakatYear, StorageError> = error("unused")

    override suspend fun setDateAdjustment(hijriYear: Int, overrideDays: Int): Outcome<ZakatYear, StorageError> =
        error("unused")

    override suspend fun addYear(hijriYear: Int, today: LocalDate): Outcome<ZakatYear, StorageError> = error("unused")
}

private object ZakatFixedClock : Clock {
    override fun now(): Instant = Instant.parse("2026-10-13T10:00:00Z")
}

/** A Cairo day the test moves by hand. */
private class ZakatScriptedDays : ObserveZakatDayUseCase(ZakatFixedClock) {
    val day = MutableStateFlow(LocalDate(2026, 10, 13))

    override operator fun invoke(): Flow<LocalDate> = day
}

class ZakatTrackingRefreshTest {

    private class Rig(scope: TestScope) {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val session = ZakatScriptedSession()
        val tracking = ZakatCountingTracking()
        val days = ZakatScriptedDays()
        val refresh = ZakatTrackingRefresh(
            session = session,
            ensure = EnsureZakatTrackingUseCase(tracking, ZakatFixedClock),
            days = days,
            scope = CoroutineScope(StandardTestDispatcher(scope.testScheduler)),
        )
    }

    @Test
    fun eachUnlockReconcilesTrackingOnce() = runTest {
        val rig = Rig(this)
        rig.refresh.start()
        advanceUntilIdle()
        assertEquals(0, rig.tracking.ensures, "nothing runs while locked")

        rig.session.current.value = VaultSessionState.Open(1)
        advanceUntilIdle()
        assertEquals(1, rig.tracking.ensures)
    }

    @Test
    fun aChangeOfTheCairoDayWhileOpenReconcilesAgain() = runTest {
        val rig = Rig(this)
        rig.refresh.start()
        rig.session.current.value = VaultSessionState.Open(1)
        advanceUntilIdle()

        rig.days.day.value = LocalDate(2026, 10, 14)
        advanceUntilIdle()
        assertEquals(2, rig.tracking.ensures)
    }

    @Test
    fun aLockStopsReconcilingUntilTheNextUnlock() = runTest {
        val rig = Rig(this)
        rig.refresh.start()
        rig.session.current.value = VaultSessionState.Open(1)
        advanceUntilIdle()
        rig.session.current.value = VaultSessionState.Locked
        advanceUntilIdle()

        rig.days.day.value = LocalDate(2026, 10, 14)
        advanceUntilIdle()
        assertEquals(1, rig.tracking.ensures)
    }

    @Test
    fun aFailedReconciliationIsIgnoredAndTheVaultStaysOpen() = runTest {
        val rig = Rig(this)
        rig.tracking.failNext = true
        rig.refresh.start()
        rig.session.current.value = VaultSessionState.Open(1)
        advanceUntilIdle()

        assertEquals(VaultSessionState.Open(1), rig.session.current.value)
        assertEquals(1, rig.tracking.ensures)
    }
}
