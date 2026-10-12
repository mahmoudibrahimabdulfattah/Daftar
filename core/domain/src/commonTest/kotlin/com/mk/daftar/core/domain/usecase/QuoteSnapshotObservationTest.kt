package com.mk.daftar.core.domain.usecase

import com.mk.daftar.core.domain.refresh.rejections
import com.mk.daftar.core.domain.repository.RefreshRejection
import com.mk.daftar.core.domain.repository.RefreshReport
import com.mk.daftar.core.domain.repository.RefreshStepResult
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.testing.FakePriceRepository
import com.mk.daftar.core.domain.testing.FakeSession
import com.mk.daftar.core.domain.testing.NOW
import com.mk.daftar.core.domain.testing.dec
import com.mk.daftar.core.domain.testing.designQuotes
import com.mk.daftar.core.domain.testing.manual
import com.mk.daftar.core.domain.testing.sell
import com.mk.daftar.core.domain.valuation.selectRemoteQuotes
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class QuoteSnapshotObservationTest {

    @Test
    fun theSnapshotOfTheOpenGenerationIsEmitted() = runTest {
        val session = FakeSession(VaultSessionState.Open(1))
        val prices = FakePriceRepository(MutableStateFlow(VaultRead.Ready(1, designQuotes())))
        val read = ObserveQuoteSnapshotUseCase(session, prices)().first()
        assertEquals(VaultRead.Ready(1, designQuotes()), read)
    }

    @Test
    fun aReadFromAnotherGenerationIsLoadingNotAnOldValue() = runTest {
        val session = FakeSession(VaultSessionState.Open(2))
        val prices = FakePriceRepository(MutableStateFlow(VaultRead.Ready(1, designQuotes())))
        assertEquals(VaultRead.Loading, ObserveQuoteSnapshotUseCase(session, prices)().first())
    }

    @Test
    fun aLockedSessionIsLockedAndAFailedReadStaysFailed() = runTest {
        val session = FakeSession(VaultSessionState.Locked)
        val prices = FakePriceRepository(MutableStateFlow(VaultRead.Failed(StorageError.Io)))
        assertEquals(VaultRead.Locked, ObserveQuoteSnapshotUseCase(session, prices)().first())
        session.current.value = VaultSessionState.Open(1)
        assertEquals(VaultRead.Failed(StorageError.Io), ObserveQuoteSnapshotUseCase(session, prices)().first())
    }

    @Test
    fun theRemoteOnlySelectionIgnoresAPinnedOverride() {
        val snapshot = designQuotes().copy(manual = listOf(manual(sell(GoldKarat.K21), "6100", NOW, pinned = true)))
        val remote = selectRemoteQuotes(snapshot, NOW)[sell(GoldKarat.K21)]
        assertEquals(dec("6185"), remote?.value)
        assertEquals(QuoteProvider.ISAGHA, remote?.provenance?.provider)
    }

    @Test
    fun withNoRemotePriceTheRemoteOnlySelectionHasNoEntry() {
        val snapshot = designQuotes(includeEighteen = false).copy(
            manual = listOf(manual(sell(GoldKarat.K18), "5200", NOW)),
        )
        assertNull(selectRemoteQuotes(snapshot, NOW)[sell(GoldKarat.K18)])
    }

    @Test
    fun onlyRealFailuresAreRejectionsAndAnOlderStoredValueIsNot() {
        val report = RefreshReport(
            gold = mapOf(
                GoldKarat.K24 to RefreshStepResult.Kept(RefreshRejection.Unreachable),
                GoldKarat.K21 to RefreshStepResult.Kept(RefreshRejection.OlderThanStored),
                GoldKarat.K18 to RefreshStepResult.Accepted(emptyList()),
            ),
            fx = mapOf(QuoteProvider.EXCHANGE_RATE_API to RefreshStepResult.Kept(RefreshRejection.RateLimited)),
        )
        assertEquals(setOf(RefreshRejection.Unreachable, RefreshRejection.RateLimited), report.rejections())
    }
}
