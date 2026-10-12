@file:OptIn(ExperimentalCoroutinesApi::class)

package com.mk.daftar.core.domain.usecase

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.GoldHoldingInput
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.testing.FakeBankRepository
import com.mk.daftar.core.domain.testing.FakeGoldRepository
import com.mk.daftar.core.domain.testing.FakePriceRepository
import com.mk.daftar.core.domain.testing.buy
import com.mk.daftar.core.domain.testing.dec
import com.mk.daftar.core.domain.testing.sell
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldWeight
import com.mk.daftar.core.model.QuoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

class RecordUseCasesTest {

    private val input = GoldHoldingInput(
        karat = GoldKarat.K21,
        form = GoldForm.BAR,
        weight = GoldWeight(dec("10.5")),
        note = null,
    )

    @Test
    fun updatePassesIdRevisionAndInputThroughUnchanged() = runTest {
        val repository = FakeGoldRepository()
        val id = GoldHoldingId("bars")

        val outcome = UpdateGoldHoldingUseCase(repository)(id, expectedRevision = 7L, input = input)

        assertEquals(listOf(Triple(id, 7L, input)), repository.updates)
        // The repository's Conflict is reported as is, not remapped.
        assertEquals(Outcome.Failure(StorageError.Conflict), outcome)
    }

    @Test
    fun deleteBankKeepsTheBankHasAccountsFailure() = runTest {
        val repository = FakeBankRepository()
        val id = BankId("bank")

        val outcome = DeleteBankUseCase(repository)(id, expectedRevision = 3L)

        assertEquals(listOf(id to 3L), repository.deletes)
        assertEquals(Outcome.Failure(StorageError.BankHasAccounts), outcome)
    }

    @Test
    fun refreshReturnsTheRepositoryOutcomeUnchanged() = runTest {
        val repository = FakePriceRepository()

        val outcome = RefreshPricesUseCase(repository)()

        assertEquals(Outcome.Failure(StorageError.Io), outcome)
    }

    @Test
    fun createOnlyManualQuotePassesANullRevision() = runTest {
        val repository = FakePriceRepository()
        val quote = ManualQuoteInput(
            key = sell(GoldKarat.K21),
            value = Decimal.parseCanonical("6000"),
            note = null,
        )

        val outcome = SetManualQuoteUseCase(repository)(quote, expectedRevision = null)

        assertEquals(listOf<Long?>(null), repository.manualRevisions)
        assertEquals(Outcome.Success(Unit), outcome)
    }

    @Test
    fun removingAnOverrideAndDeletingAManualQuoteUseTheirOwnRevisions() = runTest {
        val repository = FakePriceRepository()

        RemoveOverrideUseCase(repository)(sell(GoldKarat.K21), expectedRevision = 2L)
        val deleted = DeleteManualQuoteUseCase(repository)(sell(GoldKarat.K18), expectedRevision = 4L)

        assertEquals(listOf<Pair<QuoteKey, Long>>(sell(GoldKarat.K21) to 2L), repository.removed)
        assertEquals(listOf<Pair<QuoteKey, Long>>(sell(GoldKarat.K18) to 4L), repository.deleted)
        assertEquals(Outcome.Failure(StorageError.Conflict), deleted)
    }

    @Test
    fun buySidePricesCannotReachTheManualUseCase() {
        // Validation lives in the input type, so the use case never sees a buy-side manual price.
        assertFailsWith<IllegalArgumentException> {
            ManualQuoteInput(key = buy(GoldKarat.K21), value = Decimal.parseCanonical("6000"), note = null)
        }
    }

    @Test
    fun weightsKeepTheUserPrecisionRule() {
        assertFailsWith<IllegalArgumentException> { GoldWeight(dec("1.234")) }
    }
}
