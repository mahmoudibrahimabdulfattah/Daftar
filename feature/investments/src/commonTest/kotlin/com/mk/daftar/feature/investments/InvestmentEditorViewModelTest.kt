package com.mk.daftar.feature.investments

import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.CreateInvestmentUseCase
import com.mk.daftar.core.domain.usecase.DeleteInvestmentUseCase
import com.mk.daftar.core.domain.usecase.ObserveInvestmentUseCase
import com.mk.daftar.core.domain.usecase.UpdateInvestmentUseCase
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.investments.testing.FakeInvestmentRepository
import com.mk.daftar.feature.investments.testing.NOW
import com.mk.daftar.feature.investments.testing.TestClock
import com.mk.daftar.feature.investments.testing.account
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentEditorViewModelTest {

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
        val repository: FakeInvestmentRepository,
        val visibility: ValuesVisibility,
        val clock: TestClock,
        val viewModel: InvestmentEditorViewModel,
    )

    private fun rig(
        target: InvestmentEditorTarget,
        repository: FakeInvestmentRepository = FakeInvestmentRepository(),
        clock: TestClock = TestClock(),
    ): Rig {
        val visibility = ValuesVisibility()
        val viewModel = InvestmentEditorViewModel(
            target = target,
            observeInvestment = ObserveInvestmentUseCase(repository),
            createInvestment = CreateInvestmentUseCase(repository),
            updateInvestment = UpdateInvestmentUseCase(repository),
            deleteInvestment = DeleteInvestmentUseCase(repository),
            visibility = visibility,
            clock = clock,
        )
        return Rig(repository, visibility, clock, viewModel)
    }

    private fun TestScope.settle() = advanceUntilIdle()

    private fun InvestmentEditorViewModel.chooseThndr() =
        onIntent(InvestmentEditorIntent.ProviderPicked(InstitutionId("eg.thndr"), "Thndr"))

    private fun InvestmentEditorViewModel.fill(name: String, amount: String, note: String = "") {
        onIntent(InvestmentEditorIntent.NameChanged(name))
        onIntent(InvestmentEditorIntent.AmountChanged(amount))
        onIntent(InvestmentEditorIntent.NoteChanged(note))
    }

    private fun InvestmentEditorViewModel.save() = onIntent(InvestmentEditorIntent.Save)

    private fun InvestmentEditorViewModel.collectEffects(scope: TestScope): MutableList<InvestmentEffect> {
        val effects = mutableListOf<InvestmentEffect>()
        CoroutineScope(dispatcher).launch { this@collectEffects.effects.collect { effects += it } }
        scope.settle()
        return effects
    }

    @Test
    fun aNewAccountStartsWithTheBrokerPickerAndSavesOnlyFromTheForm() = runTest(dispatcher) {
        val rig = rig(InvestmentEditorTarget.New)
        settle()
        assertTrue(rig.viewModel.state.value.picking)
        rig.viewModel.chooseThndr()
        settle()
        assertFalse(rig.viewModel.state.value.picking)
        assertTrue(rig.repository.createdInputs.isEmpty(), "selecting a broker alone never saves")
    }

    @Test
    fun selectingThndrSuggestsItsNameUntilTheUserTypesOne() = runTest(dispatcher) {
        val vm = rig(InvestmentEditorTarget.New).viewModel
        settle()
        vm.onIntent(InvestmentEditorIntent.ProviderPicked(InstitutionId("eg.thndr"), "ثاندر"))
        assertEquals("ثاندر", vm.state.value.draft.name)

        vm.onIntent(InvestmentEditorIntent.NameChanged("حسابي"))
        vm.onIntent(InvestmentEditorIntent.ProviderPicked(null, null))
        assertEquals("حسابي", vm.state.value.draft.name, "a typed name is never replaced by a suggestion")
        assertNull(vm.state.value.draft.providerId)
    }

    @Test
    fun theAmountValidationRejectsEveryOutOfBoundsOrAmbiguousValueWithoutSaving() = runTest(dispatcher) {
        val repository = FakeInvestmentRepository()
        val vm = rig(InvestmentEditorTarget.New, repository).viewModel
        settle()
        vm.chooseThndr()
        listOf("", "-0.01", "1000000000000", "1.001", "1e3").forEach { amount ->
            vm.fill("Thndr", amount)
            vm.save()
            settle()
            assertTrue(vm.state.value.errors.amount, "$amount is rejected")
        }
        assertTrue(repository.createdInputs.isEmpty())
    }

    @Test
    fun zeroAndTheLargestAmountAreAcceptedAndArabicDigitsNormalize() = runTest(dispatcher) {
        val zero = rig(InvestmentEditorTarget.New)
        settle()
        zero.viewModel.chooseThndr()
        zero.viewModel.fill("Zero", "0")
        zero.viewModel.save()
        settle()
        assertEquals("0", zero.repository.createdInputs.single().amount.toPlainString())
        assertTrue(zero.viewModel.state.value.completed, "a zero value stays editable and saves")

        val largest = rig(InvestmentEditorTarget.New)
        settle()
        largest.viewModel.chooseThndr()
        largest.viewModel.fill("Max", "999999999999.99")
        largest.viewModel.save()
        settle()
        assertEquals("999999999999.99", largest.repository.createdInputs.single().amount.toPlainString())

        val arabic = rig(InvestmentEditorTarget.New, FakeInvestmentRepository())
        settle()
        arabic.viewModel.chooseThndr()
        arabic.viewModel.fill("Arabic", "١٢٣٫٤٥")
        arabic.viewModel.save()
        settle()
        assertEquals("123.45", arabic.repository.createdInputs.single().amount.toPlainString())
    }

    @Test
    fun aNewAccountIsValuedAtTheSaveMomentUnlessTheUserChoosesADate() = runTest(dispatcher) {
        val repository = FakeInvestmentRepository()
        val rig = rig(InvestmentEditorTarget.New, repository, TestClock(NOW))
        settle()
        rig.viewModel.chooseThndr()
        rig.viewModel.fill("Thndr", "100")
        rig.clock.instant = NOW + 2.hours
        rig.viewModel.save()
        settle()
        assertEquals(NOW + 2.hours, repository.createdInputs.single().valueAsOf)
    }

    @Test
    fun changingTheAmountOnAnEditDatesTheValueNowAndAFormattingOnlyChangeDoesNot() = runTest(dispatcher) {
        val previous = NOW - 5.hours
        val repository = FakeInvestmentRepository(listOf(account("a", "100", valueAsOf = previous)))
        val rig = rig(InvestmentEditorTarget.Existing("a"), repository)
        settle()

        rig.viewModel.onIntent(InvestmentEditorIntent.AmountChanged("100.00"))
        assertEquals(ValueDateChoice.At(previous), rig.viewModel.state.value.draft.asOf)

        rig.viewModel.onIntent(InvestmentEditorIntent.AmountChanged("120"))
        assertEquals(ValueDateChoice.Now, rig.viewModel.state.value.draft.asOf)
    }

    @Test
    fun aNoteOrNameOnlyEditKeepsThePriorValueDate() = runTest(dispatcher) {
        val previous = NOW - 5.hours
        val repository = FakeInvestmentRepository(listOf(account("a", "100", valueAsOf = previous)))
        val rig = rig(InvestmentEditorTarget.Existing("a"), repository)
        settle()
        rig.viewModel.onIntent(InvestmentEditorIntent.NameChanged("Renamed"))
        rig.viewModel.onIntent(InvestmentEditorIntent.NoteChanged("A note"))
        rig.viewModel.save()
        settle()
        assertEquals(previous, repository.updatedInputs.single().valueAsOf)
    }

    @Test
    fun setToNowAdvancesTheDateOnlyWhenTheUserAskedAndSavesTheSameAmount() = runTest(dispatcher) {
        val previous = NOW - 5.hours
        val repository = FakeInvestmentRepository(listOf(account("a", "100", valueAsOf = previous)))
        val rig = rig(InvestmentEditorTarget.Existing("a"), repository, TestClock(NOW))
        settle()
        rig.viewModel.onIntent(InvestmentEditorIntent.SetToNow)
        rig.viewModel.save()
        settle()
        val saved = repository.updatedInputs.single()
        assertEquals("100", saved.amount.toPlainString())
        assertEquals(NOW, saved.valueAsOf)
    }

    @Test
    fun aChosenDateSurvivesUntilTheAmountChangesAgain() = runTest(dispatcher) {
        val rig =
            rig(
                InvestmentEditorTarget.Existing("a"),
                FakeInvestmentRepository(listOf(account("a", "100"))),
                TestClock(NOW),
            )
        settle()
        val chosen = NOW - 3.hours
        rig.viewModel.onIntent(InvestmentEditorIntent.DateTimeChosen(chosen))
        assertEquals(ValueDateChoice.At(chosen), rig.viewModel.state.value.draft.asOf)
        rig.viewModel.onIntent(InvestmentEditorIntent.NameChanged("Still the same amount"))
        assertEquals(ValueDateChoice.At(chosen), rig.viewModel.state.value.draft.asOf)
    }

    @Test
    fun aFutureOrMissingDateIsRejectedAndLeavesTheDraftAlone() = runTest(dispatcher) {
        val rig = rig(InvestmentEditorTarget.New, FakeInvestmentRepository(), TestClock(NOW))
        settle()
        rig.viewModel.onIntent(InvestmentEditorIntent.DateTimeChosen(NOW + 1.hours))
        assertTrue(rig.viewModel.state.value.dateInvalid)
        assertEquals(ValueDateChoice.Now, rig.viewModel.state.value.draft.asOf)
        rig.viewModel.onIntent(InvestmentEditorIntent.DateTimeChosen(null))
        assertTrue(rig.viewModel.state.value.dateInvalid)
        // Before 1 January 1900 in Cairo, which is still 31 December 1899 in UTC.
        rig.viewModel.onIntent(InvestmentEditorIntent.DateTimeChosen(Instant.parse("1899-12-31T12:00:00Z")))
        assertTrue(rig.viewModel.state.value.dateInvalid)
    }

    @Test
    fun updatingWritesWithTheLoadedRevisionAndCompletes() = runTest(dispatcher) {
        val repository = FakeInvestmentRepository(listOf(account("a", "100")))
        val rig = rig(InvestmentEditorTarget.Existing("a"), repository)
        settle()
        val effects = rig.viewModel.collectEffects(this)
        rig.viewModel.onIntent(InvestmentEditorIntent.AmountChanged("150"))
        rig.viewModel.save()
        settle()
        assertEquals(listOf(1L), repository.updateRevisions)
        assertEquals("150", repository.current.single().value.amount.amount.toPlainString())
        assertEquals(listOf<InvestmentEffect>(InvestmentEffect.Completed), effects)
        assertTrue(rig.viewModel.state.value.completed)
    }

    @Test
    fun deletingNeedsConfirmationAndRemovesTheRecord() = runTest(dispatcher) {
        val repository = FakeInvestmentRepository(listOf(account("a", "100")))
        val rig = rig(InvestmentEditorTarget.Existing("a"), repository)
        settle()
        rig.viewModel.onIntent(InvestmentEditorIntent.ConfirmDelete)
        settle()
        assertTrue(repository.deleteRevisions.isEmpty(), "nothing is deleted before the user confirms")

        rig.viewModel.onIntent(InvestmentEditorIntent.RequestDelete)
        rig.viewModel.onIntent(InvestmentEditorIntent.ConfirmDelete)
        settle()
        assertEquals(listOf(1L), repository.deleteRevisions)
        assertTrue(repository.current.isEmpty())
    }

    @Test
    fun aDirtyDraftAsksBeforeItLeavesAndKeepsEditsWhenTheUserStays() = runTest(dispatcher) {
        val rig = rig(InvestmentEditorTarget.Existing("a"), FakeInvestmentRepository(listOf(account("a", "100"))))
        settle()
        rig.viewModel.onIntent(InvestmentEditorIntent.NameChanged("Edited"))
        var discarded: Boolean? = null

        rig.viewModel.onLeaveRequested { discarded = it }
        assertEquals(InvestmentConfirmation.Discard, rig.viewModel.state.value.confirmation)
        assertNull(discarded)

        rig.viewModel.onIntent(InvestmentEditorIntent.DismissConfirmation)
        assertNull(discarded)
        assertEquals("Edited", rig.viewModel.state.value.draft.name)

        rig.viewModel.onLeaveRequested { discarded = it }
        rig.viewModel.onIntent(InvestmentEditorIntent.ConfirmDiscard)
        assertEquals(true, discarded)
        assertEquals("Account a", rig.viewModel.state.value.draft.name)
    }

    @Test
    fun aChangeUnderADirtyDraftIsAConflictThatNeedsAReload() = runTest(dispatcher) {
        val repository = FakeInvestmentRepository(listOf(account("a", "100")))
        val rig = rig(InvestmentEditorTarget.Existing("a"), repository)
        settle()
        rig.viewModel.onIntent(InvestmentEditorIntent.NameChanged("Mine"))
        // Another writer saves the same account first, so the loaded revision is now stale.
        repository.update(
            com.mk.daftar.core.model.InvestmentAccountId("a"),
            1,
            com.mk.daftar.core.domain.repository.InvestmentInput(
                providerId = null,
                name = "Theirs",
                amount = com.mk.daftar.core.model.Decimal.parseCanonical("200"),
                note = null,
                valueAsOf = NOW,
            ),
        )
        settle()
        assertTrue(rig.viewModel.state.value.conflict)
        assertEquals("Mine", rig.viewModel.state.value.draft.name, "the draft is never merged silently")
        val writesBeforeSave = repository.updatedInputs.size
        rig.viewModel.save()
        settle()
        assertEquals(writesBeforeSave, repository.updatedInputs.size, "a conflicted draft cannot save")
    }

    @Test
    fun aFailedSaveKeepsTheDraftAndOffersRetry() = runTest(dispatcher) {
        val repository = FakeInvestmentRepository().apply { writeFailure = StorageError.DiskFull }
        val rig = rig(InvestmentEditorTarget.New, repository)
        settle()
        rig.viewModel.chooseThndr()
        rig.viewModel.fill("Thndr", "100")
        rig.viewModel.save()
        settle()
        assertEquals(StorageError.DiskFull, rig.viewModel.state.value.failure?.error)
        assertEquals("100", rig.viewModel.state.value.draft.amountText)
    }

    @Test
    fun aHiddenFormTakesNoEditsUntilTheUserReveals() = runTest(dispatcher) {
        val rig = rig(InvestmentEditorTarget.Existing("a"), FakeInvestmentRepository(listOf(account("a", "100"))))
        settle()
        rig.visibility.toggle()
        settle()
        rig.viewModel.onIntent(InvestmentEditorIntent.AmountChanged("999"))
        assertEquals("100", rig.viewModel.state.value.draft.amountText)
        rig.viewModel.onIntent(InvestmentEditorIntent.RevealForm)
        settle()
        assertTrue(rig.visibility.visible.value)
    }

    @Test
    fun aLockDropsTheDraftWithoutAsking() = runTest(dispatcher) {
        val repository = FakeInvestmentRepository(listOf(account("a", "100")))
        val rig = rig(InvestmentEditorTarget.Existing("a"), repository)
        settle()
        rig.viewModel.onIntent(InvestmentEditorIntent.NameChanged("Edited"))
        repository.setLocked(true)
        settle()
        assertEquals(InvestmentPhase.Locked, rig.viewModel.state.value.phase)
        assertEquals(InvestmentDraft(), rig.viewModel.state.value.draft)
        assertFalse(rig.viewModel.state.value.dirty)
    }
}
