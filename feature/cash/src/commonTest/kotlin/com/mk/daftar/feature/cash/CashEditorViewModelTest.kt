package com.mk.daftar.feature.cash

import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.CreateCashBalanceUseCase
import com.mk.daftar.core.domain.usecase.DeleteCashBalanceUseCase
import com.mk.daftar.core.domain.usecase.ObserveCashBalanceUseCase
import com.mk.daftar.core.domain.usecase.ObserveCashBalancesUseCase
import com.mk.daftar.core.domain.usecase.UpdateCashBalanceUseCase
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.cash.testing.FakeCashRepository
import com.mk.daftar.feature.cash.testing.balance
import com.mk.daftar.feature.cash.testing.egp
import com.mk.daftar.feature.cash.testing.usd
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CashEditorViewModelTest {

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
        val repository: FakeCashRepository,
        val visibility: ValuesVisibility,
        val viewModel: CashEditorViewModel,
    )

    private fun rig(target: CashEditorTarget, repository: FakeCashRepository = FakeCashRepository()): Rig {
        val visibility = ValuesVisibility()
        val viewModel = CashEditorViewModel(
            target = target,
            observeBalance = ObserveCashBalanceUseCase(repository),
            observeBalances = ObserveCashBalancesUseCase(repository),
            createBalance = CreateCashBalanceUseCase(repository),
            updateBalance = UpdateCashBalanceUseCase(repository),
            deleteBalance = DeleteCashBalanceUseCase(repository),
            visibility = visibility,
        )
        return Rig(repository, visibility, viewModel)
    }

    private fun TestScope.settle() = advanceUntilIdle()

    private fun CashEditorViewModel.fill(name: String, balance: String, note: String = "") {
        onIntent(CashEditorIntent.NameChanged(name))
        onIntent(CashEditorIntent.BalanceChanged(balance))
        onIntent(CashEditorIntent.NoteChanged(note))
    }

    private fun CashEditorViewModel.save() = onIntent(CashEditorIntent.Save)

    @Test
    fun aNewBalanceStartsAsCashInEgyptianPoundsWithoutErrorsOrDirtyState() = runTest(dispatcher) {
        val vm = rig(CashEditorTarget.New).viewModel
        settle()

        val state = vm.state.value
        assertEquals(CashPhase.Ready, state.phase)
        assertTrue(state.isNew)
        assertEquals(CashKind.CASH, state.draft.kind)
        assertEquals(Currency.EGP, state.draft.currency)
        assertFalse(state.dirty)
        assertFalse(state.attempted, "nothing is flagged before a save attempt")
    }

    @Test
    fun arabicIndicInputIsStoredAsExactTrimmedValues() = runTest(dispatcher) {
        val repository = FakeCashRepository()
        val vm = rig(CashEditorTarget.New, repository).viewModel
        settle()

        vm.onIntent(CashEditorIntent.KindChanged(CashKind.WALLET))
        vm.fill(name = "  Thundr  ", balance = "٧٬٥٠٠٫٥٠", note = "  رصيد  ")
        vm.save()
        settle()

        val input = repository.createdInputs.single()
        assertEquals("Thundr", input.name)
        assertEquals(CashKind.WALLET, input.kind)
        assertEquals("7500.5", input.balance.amount.toPlainString())
        assertEquals(Currency.EGP, input.balance.currency)
        assertEquals("رصيد", input.note)
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun zeroIsAValidBalance() = runTest(dispatcher) {
        val repository = FakeCashRepository()
        val vm = rig(CashEditorTarget.New, repository).viewModel
        settle()

        vm.fill(name = "Empty wallet", balance = "0")
        vm.save()
        settle()

        assertEquals("0", repository.createdInputs.single().balance.amount.toPlainString())
    }

    @Test
    fun negativeSignedExponentAndOverPreciseBalancesAreRejectedWithoutSubmitting() = runTest(dispatcher) {
        val repository = FakeCashRepository()
        val vm = rig(CashEditorTarget.New, repository).viewModel
        settle()
        val rejected = listOf("", "-5", "-0.01", "+5", "1e3", "1.234", "1.", "abc", "1,00")

        for (balance in rejected) {
            vm.onIntent(CashEditorIntent.BalanceChanged(balance))
            assertTrue(vm.state.value.errors.balance != null, "$balance is rejected")
        }
        vm.onIntent(CashEditorIntent.NameChanged("Cash"))
        vm.onIntent(CashEditorIntent.BalanceChanged("-5"))
        vm.save()
        settle()

        assertTrue(repository.createdInputs.isEmpty())
        assertTrue(vm.state.value.attempted)
    }

    @Test
    fun validBalancesIncludingTwoDecimalsAreAccepted() = runTest(dispatcher) {
        val vm = rig(CashEditorTarget.New).viewModel
        settle()
        for (balance in listOf("0", "1", "1.5", "1.25", "1,000", "١٢٥٫٥٠", "0.00")) {
            vm.onIntent(CashEditorIntent.BalanceChanged(balance))
            assertNull(vm.state.value.errors.balance, "$balance is accepted")
        }
    }

    @Test
    fun nameLimitsAreTrimmedNonblankAndAtMost120CodePoints() = runTest(dispatcher) {
        val vm = rig(CashEditorTarget.New).viewModel
        settle()

        vm.onIntent(CashEditorIntent.NameChanged("   "))
        assertEquals(CashNameError.Required, vm.state.value.errors.name)

        vm.onIntent(CashEditorIntent.NameChanged("a".repeat(120)))
        assertNull(vm.state.value.errors.name)

        vm.onIntent(CashEditorIntent.NameChanged("a".repeat(121)))
        assertEquals(CashNameError.TooLong, vm.state.value.errors.name)

        // A surrogate pair is one code point, so 120 emoji are still within the limit.
        vm.onIntent(CashEditorIntent.NameChanged("😀".repeat(120)))
        assertNull(vm.state.value.errors.name)
    }

    @Test
    fun notesAreOptionalAndLimitedTo4000CodePoints() = runTest(dispatcher) {
        val vm = rig(CashEditorTarget.New).viewModel
        settle()

        vm.onIntent(CashEditorIntent.NoteChanged("   "))
        assertNull(vm.state.value.errors.note)
        assertNull(vm.state.value.draft.values().note, "a blank note normalizes to no note")

        vm.onIntent(CashEditorIntent.NoteChanged("n".repeat(4_000)))
        assertNull(vm.state.value.errors.note)

        vm.onIntent(CashEditorIntent.NoteChanged("n".repeat(4_001)))
        assertEquals(CashNoteError.TooLong, vm.state.value.errors.note)
    }

    @Test
    fun aDuplicateSaveWhileWritingIsIgnored() = runTest(dispatcher) {
        val repository = FakeCashRepository()
        repository.createGate = CompletableDeferred()
        val vm = rig(CashEditorTarget.New, repository).viewModel
        settle()
        vm.fill(name = "Cash", balance = "10")

        vm.save()
        settle()
        assertEquals(CashBusy.Saving, vm.state.value.busy)
        vm.save()
        settle()

        assertEquals(1, repository.createdInputs.size)
        repository.createGate!!.complete(Unit)
        settle()
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun aFailedSaveKeepsTheDraftAndOffersRetry() = runTest(dispatcher) {
        val repository = FakeCashRepository()
        repository.writeFailure = StorageError.Io
        val vm = rig(CashEditorTarget.New, repository).viewModel
        settle()
        vm.fill(name = "Wallet", balance = "7500", note = "keep me")

        vm.save()
        settle()

        val state = vm.state.value
        assertEquals(CashFailure(CashAction.Save, StorageError.Io), state.failure)
        assertFalse(state.completed)
        assertEquals("Wallet", state.draft.name)
        assertEquals("7500", state.draft.balanceText)
        assertEquals("keep me", state.draft.noteText)

        repository.writeFailure = null
        vm.onIntent(CashEditorIntent.Save)
        settle()
        assertTrue(vm.state.value.completed)
        assertEquals(2, repository.createdInputs.size)
    }

    @Test
    fun aFullDiskIsReportedAsAStorageFailureThatKeepsTheDraft() = runTest(dispatcher) {
        val repository = FakeCashRepository()
        repository.writeFailure = StorageError.DiskFull
        val vm = rig(CashEditorTarget.New, repository).viewModel
        settle()
        vm.fill(name = "Cash", balance = "1")

        vm.save()
        settle()

        assertEquals(StorageError.DiskFull, vm.state.value.failure?.error)
        assertEquals("Cash", vm.state.value.draft.name)
    }

    @Test
    fun aSessionEndingDuringSaveIsUncertainAndNotRetried() = runTest(dispatcher) {
        val repository = FakeCashRepository()
        repository.writeFailure = StorageError.SessionEnded
        val vm = rig(CashEditorTarget.New, repository).viewModel
        settle()
        vm.fill(name = "Cash", balance = "1")

        vm.save()
        settle()

        val failure = vm.state.value.failure
        assertTrue(failure!!.uncertain, "the outcome is unknown, so retry is not offered")
    }

    @Test
    fun editingAClearedRecordFollowsTheStoredValue() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()
        assertEquals("2400", vm.state.value.draft.balanceText)

        repository.externalUpdate("c1", "Home", egp("2500"))
        settle()

        assertEquals("2500", vm.state.value.draft.balanceText)
        assertFalse(vm.state.value.conflict)
    }

    @Test
    fun aChangeUnderADirtyDraftIsAConflictThatKeepsTheDraft() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()
        vm.onIntent(CashEditorIntent.BalanceChanged("3000"))

        repository.externalUpdate("c1", "Home", egp("2500"))
        settle()

        val state = vm.state.value
        assertTrue(state.conflict)
        assertEquals("3000", state.draft.balanceText, "the draft stays as typed")
    }

    @Test
    fun saveAfterAConflictIsBlockedUntilTheUserReloadsAndDiscards() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()
        vm.onIntent(CashEditorIntent.BalanceChanged("3000"))
        repository.externalUpdate("c1", "Home", egp("2500"))
        settle()

        vm.save()
        settle()
        assertTrue(repository.updateRevisions.isEmpty(), "a conflicted draft is never written")

        vm.onIntent(CashEditorIntent.RequestReload)
        assertEquals(CashConfirmation.Reload, vm.state.value.confirmation)
        vm.onIntent(CashEditorIntent.ConfirmReload)
        settle()

        val state = vm.state.value
        assertFalse(state.conflict)
        assertEquals("2500", state.draft.balanceText, "the reload replaces the draft only after confirmation")
    }

    @Test
    fun aConflictReportedByTheWriteKeepsTheDraftWithoutAFailureNote() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()
        vm.onIntent(CashEditorIntent.BalanceChanged("3000"))
        // The store rejects the write because the revision it was given is no longer current.
        repository.writeFailure = StorageError.Conflict

        vm.save()
        settle()

        val state = vm.state.value
        assertTrue(state.conflict)
        assertNull(state.failure)
        assertEquals("3000", state.draft.balanceText)
        assertFalse(state.completed)
    }

    @Test
    fun aMissingRecordIsAMissingStateNotANewRecord() = runTest(dispatcher) {
        val repository = FakeCashRepository()
        val vm = rig(CashEditorTarget.Existing("gone"), repository).viewModel
        settle()

        assertEquals(CashPhase.Missing, vm.state.value.phase)
        assertFalse(vm.state.value.isNew)
    }

    @Test
    fun aDeletedRecordWhileEditingBecomesMissing() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()

        repository.externalDelete("c1")
        settle()

        assertEquals(CashPhase.Missing, vm.state.value.phase)
    }

    @Test
    fun deletingAStoredBalanceNeedsConfirmationAndCompletesOnSuccess() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()

        vm.onIntent(CashEditorIntent.RequestDelete)
        assertEquals(CashConfirmation.Delete, vm.state.value.confirmation)
        assertTrue(repository.deleteRevisions.isEmpty(), "nothing is deleted before confirmation")

        vm.onIntent(CashEditorIntent.ConfirmDelete)
        settle()

        assertEquals(listOf(1L), repository.deleteRevisions)
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun cancellingTheDeleteConfirmationKeepsTheBalance() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()

        vm.onIntent(CashEditorIntent.RequestDelete)
        vm.onIntent(CashEditorIntent.DismissConfirmation)

        assertNull(vm.state.value.confirmation)
        assertTrue(repository.deleteRevisions.isEmpty())
    }

    @Test
    fun aFailedDeleteKeepsTheRecordAndOffersAnotherTry() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        repository.writeFailure = StorageError.Io
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()

        vm.onIntent(CashEditorIntent.RequestDelete)
        vm.onIntent(CashEditorIntent.ConfirmDelete)
        settle()

        assertEquals(CashAction.Delete, vm.state.value.failure?.action)
        assertFalse(vm.state.value.completed)
        assertEquals(1, repository.revisionOf("c1"))
    }

    @Test
    fun aNewBalanceCannotBeDeleted() = runTest(dispatcher) {
        val vm = rig(CashEditorTarget.New).viewModel
        settle()

        vm.onIntent(CashEditorIntent.RequestDelete)

        assertNull(vm.state.value.confirmation)
    }

    @Test
    fun leavingWithoutEditsProceedsAtOnce() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()
        var discarded: Boolean? = null

        vm.onLeaveRequested { discarded = it }

        assertEquals(false, discarded)
        assertNull(vm.state.value.confirmation)
    }

    @Test
    fun leavingWithUnsavedEditsAsksBeforeDiscardingThem() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()
        vm.onIntent(CashEditorIntent.BalanceChanged("9999"))
        var discarded: Boolean? = null

        vm.onLeaveRequested { discarded = it }
        assertEquals(CashConfirmation.Discard, vm.state.value.confirmation)
        assertNull(discarded, "the leave waits for the user")

        vm.onIntent(CashEditorIntent.DismissConfirmation)
        assertNull(discarded, "keeping edits cancels the leave")
        assertEquals("9999", vm.state.value.draft.balanceText)

        vm.onLeaveRequested { discarded = it }
        vm.onIntent(CashEditorIntent.ConfirmDiscard)

        assertEquals(true, discarded)
        assertEquals("2400", vm.state.value.draft.balanceText, "the discard restores the stored values")
    }

    @Test
    fun aLockDropsDraftsConfirmationsAndPendingLeavesWithoutAsking() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()
        vm.onIntent(CashEditorIntent.BalanceChanged("9999"))
        var discarded: Boolean? = null
        vm.onLeaveRequested { discarded = it }

        repository.setLocked(true)
        settle()

        val state = vm.state.value
        assertEquals(CashPhase.Locked, state.phase)
        assertNull(state.confirmation)
        assertEquals(CashDraft(), state.draft)
        assertNull(discarded, "a lock never proceeds with a leave on the user's behalf")
    }

    @Test
    fun aWriteThatFinishesAfterALockReportsNothing() = runTest(dispatcher) {
        val repository = FakeCashRepository()
        repository.createGate = CompletableDeferred()
        val vm = rig(CashEditorTarget.New, repository).viewModel
        settle()
        vm.fill(name = "Cash", balance = "10")
        vm.save()
        settle()

        repository.setLocked(true)
        settle()
        repository.createGate!!.complete(Unit)
        settle()

        assertFalse(vm.state.value.completed)
        assertEquals(CashPhase.Locked, vm.state.value.phase)
    }

    @Test
    fun maskedEditingConcealsTheFormUntilTheUserRevealsIt() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        val rig = rig(CashEditorTarget.Existing("c1"), repository)
        settle()
        assertFalse(rig.viewModel.state.value.concealed)

        rig.visibility.toggle()
        settle()
        assertTrue(rig.viewModel.state.value.concealed)

        rig.viewModel.onIntent(CashEditorIntent.BalanceChanged("1"))
        assertEquals("2400", rig.viewModel.state.value.draft.balanceText, "a concealed form takes no edits")

        rig.viewModel.onIntent(CashEditorIntent.RevealForm)
        settle()
        assertFalse(rig.viewModel.state.value.concealed)
        assertTrue(rig.visibility.visible.value)
    }

    @Test
    fun aConcealedFormDoesNotSave() = runTest(dispatcher) {
        val repository = FakeCashRepository()
        val rig = rig(CashEditorTarget.New, repository)
        settle()
        rig.viewModel.fill(name = "Cash", balance = "10")
        rig.visibility.toggle()
        settle()

        rig.viewModel.save()
        settle()

        assertTrue(repository.createdInputs.isEmpty())
    }

    @Test
    fun aRelabelledBalanceNeedsAcknowledgementBeforeItIsSaved() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Thundr", money = egp("7500"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()

        vm.onIntent(CashEditorIntent.CurrencyChanged(Currency.USD))
        assertTrue(vm.state.value.currencyChanged)
        vm.save()
        settle()
        assertTrue(repository.updateRevisions.isEmpty(), "the relabelling waits for acknowledgement")

        vm.onIntent(CashEditorIntent.CurrencyAcknowledged(true))
        vm.save()
        settle()

        assertEquals(1, repository.updateRevisions.size)
        assertEquals(usd("7500").currency, repository.updatedInputs.single().balance.currency)
        assertEquals("7500", repository.updatedInputs.single().balance.amount.toPlainString())
    }

    @Test
    fun changingTheCurrencyAgainResetsAcknowledgement() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Thundr", money = egp("7500"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()
        vm.onIntent(CashEditorIntent.CurrencyChanged(Currency.USD))
        vm.onIntent(CashEditorIntent.CurrencyAcknowledged(true))

        vm.onIntent(CashEditorIntent.CurrencyChanged(Currency.EGP))
        vm.onIntent(CashEditorIntent.CurrencyChanged(Currency.USD))

        assertFalse(vm.state.value.currencyAcknowledged)
        assertTrue(vm.state.value.needsCurrencyAcknowledgement)
    }

    @Test
    fun aNewBalanceInUsdNeedsNoRelabelAcknowledgement() = runTest(dispatcher) {
        val repository = FakeCashRepository()
        val vm = rig(CashEditorTarget.New, repository).viewModel
        settle()

        vm.onIntent(CashEditorIntent.CurrencyChanged(Currency.USD))
        vm.fill(name = "USD cash", balance = "3000")
        vm.save()
        settle()

        assertFalse(vm.state.value.currencyChanged)
        assertEquals(usd("3000"), repository.createdInputs.single().balance)
    }

    @Test
    fun aKindChangeOnlyReclassifiesTheBalance() = runTest(dispatcher) {
        val repository = FakeCashRepository(listOf(balance("c1", "Home", money = egp("2400"))))
        val vm = rig(CashEditorTarget.Existing("c1"), repository).viewModel
        settle()

        vm.onIntent(CashEditorIntent.KindChanged(CashKind.WALLET))
        assertFalse(vm.state.value.currencyChanged)
        vm.save()
        settle()

        assertEquals(1, repository.updateRevisions.size)
        assertEquals(CashKind.WALLET, repository.updatedInputs.single().kind)
        assertEquals(egp("2400"), repository.updatedInputs.single().balance)
    }
}
