package com.mk.daftar.feature.investments

import com.mk.daftar.core.domain.repository.InvestmentConversionFailure
import com.mk.daftar.core.domain.repository.InvestmentDraftIds
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.ConvertCashToInvestmentUseCase
import com.mk.daftar.core.domain.usecase.ObserveCashBalancesUseCase
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccountId
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.investments.testing.FakeCashRepository
import com.mk.daftar.feature.investments.testing.FakeInvestmentRepository
import com.mk.daftar.feature.investments.testing.NOW
import com.mk.daftar.feature.investments.testing.TestClock
import com.mk.daftar.feature.investments.testing.cashEntry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentConvertViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Rig(
        val cash: FakeCashRepository,
        val investments: FakeInvestmentRepository,
        val ids: CountingDraftIds,
        val visibility: ValuesVisibility,
        val viewModel: InvestmentConvertViewModel,
    )

    /** Mints a new draft ID each time it is asked, so a test can tell whether the flow asked more than once. */
    private class CountingDraftIds : InvestmentDraftIds {
        var calls = 0

        override fun newDraftId(): InvestmentAccountId {
            calls++
            return InvestmentAccountId("draft-$calls")
        }
    }

    private fun rig(
        entries: List<com.mk.daftar.core.model.CashBalance>,
        preset: CashBalanceId? = null,
        investments: FakeInvestmentRepository = FakeInvestmentRepository(),
    ): Rig {
        val cash = FakeCashRepository(entries)
        val ids = CountingDraftIds()
        val visibility = ValuesVisibility()
        val viewModel = InvestmentConvertViewModel(
            presetSourceId = preset,
            observeCash = ObserveCashBalancesUseCase(cash),
            convert = ConvertCashToInvestmentUseCase(investments),
            draftIds = ids,
            visibility = visibility,
            clock = TestClock(NOW),
        )
        return Rig(cash, investments, ids, visibility, viewModel)
    }

    private fun TestScope.settle() = advanceUntilIdle()

    private fun InvestmentConvertViewModel.pickEntry(id: String) =
        onIntent(ConvertIntent.SelectSource(CashBalanceId(id)))

    private fun InvestmentConvertViewModel.pickThndr() =
        onIntent(ConvertIntent.ChooseProvider(InstitutionId("eg.thndr")))

    @Test
    fun usdAndOversizedEntriesAreShownButCannotBeSelected() = runTest(dispatcher) {
        val rig = rig(
            listOf(
                cashEntry("egp", "9900"),
                cashEntry("usd", "50", currency = Currency.USD),
                cashEntry("big", "1000000000000"),
                cashEntry("zero", "0"),
            ),
        )
        settle()
        assertEquals(listOf(true, false, false, true), rig.viewModel.state.value.sources.map { it.eligible })
        rig.viewModel.pickEntry("usd")
        assertNull(rig.viewModel.state.value.sourceId)
        assertEquals(ConvertStep.Source, rig.viewModel.state.value.step)
        rig.viewModel.pickEntry("zero")
        assertEquals(ConvertStep.Provider, rig.viewModel.state.value.step, "zero EGP is eligible")
    }

    @Test
    fun theReviewStatesTheAmountAndNothingIsConvertedBeforeConfirm() = runTest(dispatcher) {
        val rig = rig(listOf(cashEntry("c1", "9900", name = "Thundr wallet")))
        settle()
        rig.viewModel.pickEntry("c1")
        assertNull(rig.viewModel.state.value.providerId, "a name never preselects a broker")
        rig.viewModel.pickThndr()
        settle()
        assertEquals(ConvertStep.Review, rig.viewModel.state.value.step)
        assertEquals("Thundr wallet", rig.viewModel.state.value.source?.name)
        assertTrue(rig.investments.conversionCalls.isEmpty())
    }

    @Test
    fun confirmingConvertsOnceWithTheSameDraftIdAndAReusedRetry() = runTest(dispatcher) {
        val investments = FakeInvestmentRepository()
        val rig = rig(listOf(cashEntry("c1", "9900")), investments = investments)
        settle()
        rig.viewModel.pickEntry("c1")
        rig.viewModel.pickThndr()
        rig.viewModel.onIntent(ConvertIntent.Confirm)
        settle()
        assertEquals(ConvertOutcome.Converted, rig.viewModel.state.value.outcome)
        assertEquals(1, rig.ids.calls, "the draft ID is minted once per draft")

        // A second confirmation after a completed conversion does nothing: the result is already on screen.
        rig.viewModel.onIntent(ConvertIntent.Confirm)
        settle()
        assertEquals(1, investments.conversionCalls.size)
    }

    @Test
    fun aRetryAfterAnAlreadyConvertedRecordReportsItWithoutCreatingAnother() = runTest(dispatcher) {
        val investments = FakeInvestmentRepository().apply {
            conversionFailure = null
        }
        val rig = rig(listOf(cashEntry("c1", "9900")), investments = investments)
        settle()
        rig.viewModel.pickEntry("c1")
        rig.viewModel.pickThndr()
        investments.conversionFailure = InvestmentConversionFailure.SourceChanged
        rig.viewModel.onIntent(ConvertIntent.Confirm)
        settle()
        assertTrue(rig.viewModel.state.value.conflict)

        investments.conversionFailure = null
        rig.viewModel.onIntent(ConvertIntent.ReviewLatest)
        rig.viewModel.onIntent(ConvertIntent.Confirm)
        settle()
        assertEquals(ConvertOutcome.Converted, rig.viewModel.state.value.outcome)
        assertEquals(1, rig.ids.calls)
    }

    @Test
    fun eachFailureIsMappedToItsOwnStateAndOnlyStorageFailuresOfferRetry() = runTest(dispatcher) {
        val failures = listOf(
            InvestmentConversionFailure.SourceUnavailable to ConvertOutcome.SourceUnavailable,
            InvestmentConversionFailure.UnsupportedCurrency to ConvertOutcome.Unsupported,
            InvestmentConversionFailure.AmountOutOfRange to ConvertOutcome.Unsupported,
        )
        failures.forEach { (failure, expected) ->
            val investments = FakeInvestmentRepository().apply { conversionFailure = failure }
            val rig = rig(listOf(cashEntry("c1", "9900")), investments = investments)
            settle()
            rig.viewModel.pickEntry("c1")
            rig.viewModel.pickThndr()
            rig.viewModel.onIntent(ConvertIntent.Confirm)
            settle()
            assertEquals(expected, rig.viewModel.state.value.outcome, "$failure")
        }

        val storage = FakeInvestmentRepository().apply {
            conversionFailure = InvestmentConversionFailure.Storage(StorageError.DiskFull)
        }
        val rig = rig(listOf(cashEntry("c1", "9900")), investments = storage)
        settle()
        rig.viewModel.pickEntry("c1")
        rig.viewModel.pickThndr()
        rig.viewModel.onIntent(ConvertIntent.Confirm)
        settle()
        assertEquals(StorageError.DiskFull, rig.viewModel.state.value.failure)
        assertNull(rig.viewModel.state.value.outcome, "a storage failure keeps the review for a retry")
    }

    @Test
    fun aSourceThatVanishesIsUnavailableAndIsNeverRecreated() = runTest(dispatcher) {
        val rig = rig(listOf(cashEntry("c1", "9900")))
        settle()
        rig.viewModel.pickEntry("c1")
        rig.viewModel.pickThndr()
        rig.cash.externalRemove("c1")
        settle()
        assertEquals(ConvertOutcome.SourceUnavailable, rig.viewModel.state.value.outcome)
        assertTrue(rig.investments.conversionCalls.isEmpty())
    }

    @Test
    fun aChangedEntryNeedsANewReviewBeforeItConverts() = runTest(dispatcher) {
        val rig = rig(listOf(cashEntry("c1", "9900")))
        settle()
        rig.viewModel.pickEntry("c1")
        rig.viewModel.pickThndr()
        rig.cash.externalUpdate("c1", "9999")
        settle()
        assertTrue(rig.viewModel.state.value.conflict)
        rig.viewModel.onIntent(ConvertIntent.Confirm)
        settle()
        assertTrue(rig.investments.conversionCalls.isEmpty(), "a changed entry cannot convert without a new review")
    }

    @Test
    fun aPresetEntryStartsAtTheBrokerChoiceAndAHiddenReviewCannotConvert() = runTest(dispatcher) {
        val rig = rig(listOf(cashEntry("c1", "9900")), preset = CashBalanceId("c1"))
        settle()
        assertEquals(ConvertStep.Provider, rig.viewModel.state.value.step)

        rig.viewModel.pickThndr()
        rig.visibility.toggle()
        settle()
        rig.viewModel.onIntent(ConvertIntent.Confirm)
        settle()
        assertTrue(rig.investments.conversionCalls.isEmpty())
    }

    @Test
    fun aLockDropsTheSelectionAndTheDraft() = runTest(dispatcher) {
        val rig = rig(listOf(cashEntry("c1", "9900")))
        settle()
        rig.viewModel.pickEntry("c1")
        rig.viewModel.pickThndr()
        rig.cash.setLocked(true)
        settle()
        assertEquals(InvestmentPhase.Locked, rig.viewModel.state.value.phase)
        assertNull(rig.viewModel.state.value.draftId)
        assertNull(rig.viewModel.state.value.sourceId)
    }
}
