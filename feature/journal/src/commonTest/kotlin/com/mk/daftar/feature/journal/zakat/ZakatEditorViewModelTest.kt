package com.mk.daftar.feature.journal.zakat

import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.CreateZakatPaymentUseCase
import com.mk.daftar.core.domain.usecase.DeleteZakatPaymentUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatPaymentUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatPaymentsUseCase
import com.mk.daftar.core.domain.usecase.UpdateZakatPaymentUseCase
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.journal.JournalAction
import com.mk.daftar.feature.journal.JournalBusy
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.conceal
import com.mk.daftar.feature.journal.isHidden
import com.mk.daftar.feature.journal.testing.FakeZakatRepository
import com.mk.daftar.feature.journal.testing.FixedClock
import com.mk.daftar.feature.journal.testing.NOW
import com.mk.daftar.feature.journal.testing.TODAY
import com.mk.daftar.feature.journal.testing.payment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class ZakatEditorViewModelTest {

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
        val repository: FakeZakatRepository,
        val visibility: ValuesVisibility,
        val viewModel: ZakatEditorViewModel,
    )

    private fun rig(target: ZakatEditorTarget, repository: FakeZakatRepository = FakeZakatRepository()): Rig {
        val visibility = ValuesVisibility()
        val viewModel = ZakatEditorViewModel(
            target = target,
            observePayment = ObserveZakatPaymentUseCase(repository),
            observePayments = ObserveZakatPaymentsUseCase(repository),
            createPayment = CreateZakatPaymentUseCase(repository),
            updatePayment = UpdateZakatPaymentUseCase(repository),
            deletePayment = DeleteZakatPaymentUseCase(repository),
            visibility = visibility,
            clock = FixedClock(NOW),
            timeZone = TimeZone.UTC,
        )
        return Rig(repository, visibility, viewModel)
    }

    private fun TestScope.settle() = advanceUntilIdle()

    private fun TestScope.newPayment(rig: Rig): ZakatEditorViewModel = rig.viewModel.also { settle() }

    private fun ZakatEditorViewModel.fill(date: String, amount: String, note: String = "") {
        onIntent(ZakatEditorIntent.DateChanged(date))
        onIntent(ZakatEditorIntent.AmountChanged(amount))
        onIntent(ZakatEditorIntent.NoteChanged(note))
    }

    @Test
    fun aNewPaymentStartsOnTodayWithoutErrorsOrDirtyState() = runTest(dispatcher) {
        val vm = newPayment(rig(ZakatEditorTarget.New))

        val state = vm.state.value
        assertEquals(JournalPhase.Ready, state.phase)
        assertEquals("2026-10-12", state.draft.dateText)
        assertFalse(state.dirty)
        assertNull(state.errors.date)
        assertNull(state.errors.note)
        assertFalse(state.attempted, "the amount is required, but nothing is flagged before an attempt")
    }

    @Test
    fun arabicIndicDigitsAndGroupingAreAcceptedAndStoredAsExactEgp() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        val vm = newPayment(rig(ZakatEditorTarget.New, repository))

        vm.fill(date = "٢٠٢٦-٠٣-٢٠", amount = "١٠٬٠٠٠٫٥٠", note = "  دفعة رمضان  ")
        vm.onIntent(ZakatEditorIntent.Save)
        settle()

        val input = repository.createdInputs.single()
        assertEquals(LocalDate(2026, 3, 20), input.paidOn)
        assertEquals("10000.5", input.amount.amount.toPlainString())
        assertEquals("دفعة رمضان", input.note)
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun amountsThatAreNotPositiveEntriesAreRejectedWithoutSubmitting() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        val vm = newPayment(rig(ZakatEditorTarget.New, repository))
        val rejected = listOf("", "0", "0.00", "-5", "+5", "1e3", "1.234", "1.", "abc", "1,00")

        for (amount in rejected) {
            vm.onIntent(ZakatEditorIntent.AmountChanged(amount))
            assertTrue(vm.state.value.errors.amount != null, "$amount is rejected")
        }
        vm.onIntent(ZakatEditorIntent.AmountChanged("1,00"))
        vm.onIntent(ZakatEditorIntent.Save)
        settle()

        assertTrue(repository.createdInputs.isEmpty())
        assertTrue(vm.state.value.attempted)
    }

    @Test
    fun validEntriesIncludingTwoDecimalsAreAccepted() = runTest(dispatcher) {
        val vm = newPayment(rig(ZakatEditorTarget.New))
        for (amount in listOf("1", "1.5", "1.25", "1,000", "١٢٥٫٥٠")) {
            vm.onIntent(ZakatEditorIntent.AmountChanged(amount))
            assertEquals(null, vm.state.value.errors.amount, "$amount is accepted")
        }
    }

    @Test
    fun intermediateTypingIsNotFlaggedBeforeAnAttempt() = runTest(dispatcher) {
        val vm = newPayment(rig(ZakatEditorTarget.New))
        vm.onIntent(ZakatEditorIntent.AmountChanged("1."))

        assertFalse(vm.state.value.attempted)
        assertTrue(vm.state.value.errors.amount != null)
        vm.onIntent(ZakatEditorIntent.Save)
        assertTrue(vm.state.value.attempted)
    }

    @Test
    fun aFutureDateIsRejectedAndTodayIsAccepted() = runTest(dispatcher) {
        val vm = newPayment(rig(ZakatEditorTarget.New))

        vm.onIntent(ZakatEditorIntent.DateChanged("2026-10-13"))
        assertEquals(ZakatDateError.Future, vm.state.value.errors.date)
        vm.onIntent(ZakatEditorIntent.DateChanged("2026-10-12"))
        assertNull(vm.state.value.errors.date)
    }

    @Test
    fun leapDaysAreRealOnlyInLeapYears() = runTest(dispatcher) {
        val vm = newPayment(rig(ZakatEditorTarget.New))

        vm.onIntent(ZakatEditorIntent.DateChanged("2024-02-29"))
        assertNull(vm.state.value.errors.date)
        vm.onIntent(ZakatEditorIntent.DateChanged("2026-02-29"))
        assertEquals(ZakatDateError.Invalid, vm.state.value.errors.date)
        vm.onIntent(ZakatEditorIntent.DateChanged("2026-13-01"))
        assertEquals(ZakatDateError.Invalid, vm.state.value.errors.date)
        vm.onIntent(ZakatEditorIntent.DateChanged("20-03-2026"))
        assertEquals(ZakatDateError.Invalid, vm.state.value.errors.date)
        vm.onIntent(ZakatEditorIntent.DateChanged(""))
        assertEquals(ZakatDateError.Required, vm.state.value.errors.date)
    }

    @Test
    fun aNoteOverTheLimitIsRejectedByCodePoints() = runTest(dispatcher) {
        val vm = newPayment(rig(ZakatEditorTarget.New))
        // Each emoji is one code point but two UTF-16 units: 4,000 of them are exactly the limit.
        vm.onIntent(ZakatEditorIntent.NoteChanged("😀".repeat(4_000)))
        assertNull(vm.state.value.errors.note)
        vm.onIntent(ZakatEditorIntent.NoteChanged("😀".repeat(4_001)))
        assertEquals(ZakatNoteError.TooLong, vm.state.value.errors.note)
    }

    @Test
    fun aSaveInFlightIgnoresEverySecondSubmission() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate
        val vm = newPayment(rig(ZakatEditorTarget.New, repository))
        vm.fill(date = "2026-03-20", amount = "10000")

        vm.onIntent(ZakatEditorIntent.Save)
        settle()
        assertEquals(JournalBusy.Saving, vm.state.value.busy)
        vm.onIntent(ZakatEditorIntent.Save)
        vm.onIntent(ZakatEditorIntent.AmountChanged("1"))
        settle()
        gate.complete(Unit)
        settle()

        assertEquals(1, repository.createdInputs.size)
        assertEquals("10000", vm.state.value.draft.amountText)
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun aConfirmedSaveEmitsCompletionOnlyAfterSuccess() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        val vm = newPayment(rig(ZakatEditorTarget.New, repository))
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { vm.effects.collect { effects += it } }
        vm.fill(date = "2026-03-20", amount = "10000")

        vm.onIntent(ZakatEditorIntent.Save)
        settle()

        assertEquals(listOf<JournalEffect>(JournalEffect.Completed), effects)
        collector.cancel()
    }

    @Test
    fun aStorageFailureKeepsTheDraftAndRetryCompletes() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        repository.writeFailure = StorageError.DiskFull
        val vm = newPayment(rig(ZakatEditorTarget.New, repository))
        vm.fill(date = "2026-03-20", amount = "10000", note = "رمضان")

        vm.onIntent(ZakatEditorIntent.Save)
        settle()

        val failure = assertNotNull(vm.state.value.failure)
        assertEquals(JournalAction.Save, failure.action)
        assertEquals(StorageError.DiskFull, failure.error)
        assertFalse(failure.uncertain)
        assertEquals("10000", vm.state.value.draft.amountText)
        assertEquals("رمضان", vm.state.value.draft.noteText)

        repository.writeFailure = null
        vm.onIntent(ZakatEditorIntent.Save)
        settle()
        assertTrue(vm.state.value.completed)
        assertNull(vm.state.value.failure)
        assertEquals(2, repository.createdInputs.size)
    }

    @Test
    fun aSessionEndedSaveIsUncertainAndNeverRepeatedAutomatically() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        repository.writeFailure = StorageError.SessionEnded
        val vm = newPayment(rig(ZakatEditorTarget.New, repository))
        vm.fill(date = "2026-03-20", amount = "10000")

        vm.onIntent(ZakatEditorIntent.Save)
        settle()

        assertTrue(assertNotNull(vm.state.value.failure).uncertain)
        assertEquals(1, repository.createdInputs.size)
        assertFalse(vm.state.value.completed)
    }

    @Test
    fun aLaterEmissionCannotOverwriteADirtyDraftAndBecomesAConflict() = runTest(dispatcher) {
        val repository = FakeZakatRepository(listOf(payment("p1", LocalDate(2026, 3, 20), "10000")))
        val vm = rig(ZakatEditorTarget.Existing("p1"), repository).viewModel
        settle()
        vm.onIntent(ZakatEditorIntent.AmountChanged("12000"))

        repository.externalUpdate("p1", LocalDate(2026, 3, 20), "7000")
        settle()

        assertEquals("12000", vm.state.value.draft.amountText)
        assertTrue(vm.state.value.conflict)
        vm.onIntent(ZakatEditorIntent.Save)
        settle()
        assertTrue(repository.updateRevisions.isEmpty(), "a conflicted editor never writes")
    }

    @Test
    fun aCleanEditorFollowsLaterEmissions() = runTest(dispatcher) {
        val repository = FakeZakatRepository(listOf(payment("p1", LocalDate(2026, 3, 20), "10000")))
        val vm = rig(ZakatEditorTarget.Existing("p1"), repository).viewModel
        settle()

        repository.externalUpdate("p1", LocalDate(2026, 3, 21), "7000")
        settle()

        assertEquals("7000", vm.state.value.draft.amountText)
        assertEquals("2026-03-21", vm.state.value.draft.dateText)
        assertFalse(vm.state.value.conflict)
        assertFalse(vm.state.value.dirty)
    }

    @Test
    fun reloadingAConflictNeedsAnExplicitDiscardAndThenUsesTheNewRevision() = runTest(dispatcher) {
        val repository = FakeZakatRepository(listOf(payment("p1", LocalDate(2026, 3, 20), "10000")))
        val vm = rig(ZakatEditorTarget.Existing("p1"), repository).viewModel
        settle()
        vm.onIntent(ZakatEditorIntent.AmountChanged("12000"))
        repository.externalUpdate("p1", LocalDate(2026, 3, 20), "7000")
        settle()

        vm.onIntent(ZakatEditorIntent.RequestReload)
        assertEquals(ZakatConfirmation.Reload, vm.state.value.confirmation)
        vm.onIntent(ZakatEditorIntent.DismissConfirmation)
        assertTrue(vm.state.value.conflict)
        assertEquals("12000", vm.state.value.draft.amountText)

        vm.onIntent(ZakatEditorIntent.RequestReload)
        vm.onIntent(ZakatEditorIntent.ConfirmReload)
        settle()
        assertFalse(vm.state.value.conflict)
        assertEquals("7000", vm.state.value.draft.amountText)

        vm.onIntent(ZakatEditorIntent.AmountChanged("8000"))
        vm.onIntent(ZakatEditorIntent.Save)
        settle()
        assertEquals(listOf(2L), repository.updateRevisions)
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun aConflictReportedByTheWriteKeepsTheDraftAndTheLoadedRevision() = runTest(dispatcher) {
        val repository = FakeZakatRepository(listOf(payment("p1", LocalDate(2026, 3, 20), "10000")))
        val vm = rig(ZakatEditorTarget.Existing("p1"), repository).viewModel
        settle()
        vm.onIntent(ZakatEditorIntent.AmountChanged("10500"))
        repository.writeFailure = StorageError.Conflict

        vm.onIntent(ZakatEditorIntent.Save)
        settle()

        assertTrue(vm.state.value.conflict)
        assertNull(vm.state.value.failure)
        assertEquals("10500", vm.state.value.draft.amountText)
        assertEquals(listOf(1L), repository.updateRevisions)
    }

    @Test
    fun aRecordThatDisappearsBecomesAMissingRecord() = runTest(dispatcher) {
        val repository = FakeZakatRepository(listOf(payment("p1", LocalDate(2026, 3, 20), "10000")))
        val vm = rig(ZakatEditorTarget.Existing("p1"), repository).viewModel
        settle()

        repository.externalDelete("p1")
        settle()

        assertEquals(JournalPhase.Missing, vm.state.value.phase)
    }

    @Test
    fun deleteUsesTheLoadedRevisionAndCompletesOnSuccess() = runTest(dispatcher) {
        val repository = FakeZakatRepository(listOf(payment("p1", LocalDate(2026, 3, 20), "10000")))
        val vm = rig(ZakatEditorTarget.Existing("p1"), repository).viewModel
        settle()

        vm.onIntent(ZakatEditorIntent.RequestDelete)
        assertEquals(ZakatConfirmation.Delete, vm.state.value.confirmation)
        vm.onIntent(ZakatEditorIntent.ConfirmDelete)
        settle()

        assertEquals(listOf(1L), repository.deleteRevisions)
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun aFailedDeleteKeepsTheRecordAndOffersTheDeleteAgain() = runTest(dispatcher) {
        val repository = FakeZakatRepository(listOf(payment("p1", LocalDate(2026, 3, 20), "10000")))
        repository.writeFailure = StorageError.Io
        val vm = rig(ZakatEditorTarget.Existing("p1"), repository).viewModel
        settle()

        vm.onIntent(ZakatEditorIntent.RequestDelete)
        vm.onIntent(ZakatEditorIntent.ConfirmDelete)
        settle()

        assertEquals(JournalAction.Delete, assertNotNull(vm.state.value.failure).action)
        assertFalse(vm.state.value.completed)
        assertEquals("10000", vm.state.value.draft.amountText)
    }

    @Test
    fun leavingWithUnsavedEditsAsksAndDiscardingProceedsWithTheDraftReset() = runTest(dispatcher) {
        val vm = newPayment(rig(ZakatEditorTarget.New))
        vm.onIntent(ZakatEditorIntent.AmountChanged("500"))
        var proceeded: Boolean? = null

        vm.onLeaveRequested { discarded -> proceeded = discarded }
        assertNull(proceeded)
        assertEquals(ZakatConfirmation.Discard, vm.state.value.confirmation)

        vm.onIntent(ZakatEditorIntent.ConfirmDiscard)
        assertEquals(true, proceeded)
        assertEquals("", vm.state.value.draft.amountText)
        assertFalse(vm.state.value.dirty)
    }

    @Test
    fun keepingEditingCancelsTheLeave() = runTest(dispatcher) {
        val vm = newPayment(rig(ZakatEditorTarget.New))
        vm.onIntent(ZakatEditorIntent.AmountChanged("500"))
        var proceeded = false

        vm.onLeaveRequested { proceeded = true }
        vm.onIntent(ZakatEditorIntent.DismissConfirmation)

        assertFalse(proceeded)
        assertEquals("500", vm.state.value.draft.amountText)
    }

    @Test
    fun aCleanEditorLeavesAtOnceAndNoLeaveHappensWhileAWriteRuns() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate
        val vm = newPayment(rig(ZakatEditorTarget.New, repository))
        var clean: Boolean? = null
        vm.onLeaveRequested { clean = it }
        assertEquals(false, clean)

        vm.fill(date = "2026-03-20", amount = "10000")
        vm.onIntent(ZakatEditorIntent.Save)
        settle()
        var ignored = false
        vm.onLeaveRequested { ignored = true }
        assertFalse(ignored)
        assertNull(vm.state.value.confirmation)
        gate.complete(Unit)
        settle()
    }

    @Test
    fun aLockDropsTheDraftAndAnyPendingLeaveWithoutAsking() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        val vm = newPayment(rig(ZakatEditorTarget.New, repository))
        vm.onIntent(ZakatEditorIntent.AmountChanged("500"))
        vm.onLeaveRequested { }
        assertEquals(ZakatConfirmation.Discard, vm.state.value.confirmation)

        repository.setLocked(true)
        settle()

        assertEquals(JournalPhase.Locked, vm.state.value.phase)
        assertEquals("", vm.state.value.draft.amountText)
        assertNull(vm.state.value.confirmation)
        var proceeded: Boolean? = null
        vm.onLeaveRequested { proceeded = it }
        assertEquals(false, proceeded)
    }

    @Test
    fun aWriteThatFinishesAfterALockReportsNothing() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate
        val vm = newPayment(rig(ZakatEditorTarget.New, repository))
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { vm.effects.collect { effects += it } }
        vm.fill(date = "2026-03-20", amount = "10000")
        vm.onIntent(ZakatEditorIntent.Save)
        settle()

        repository.setLocked(true)
        settle()
        gate.complete(Unit)
        settle()

        assertEquals(JournalPhase.Locked, vm.state.value.phase)
        assertFalse(vm.state.value.completed)
        assertNull(vm.state.value.busy)
        assertTrue(effects.isEmpty(), "a locked editor never reports a completion")
        collector.cancel()
    }

    @Test
    fun maskingConcealsTheFormWithoutLosingTheDraftAndWritesWaitForAReveal() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        val r = rig(ZakatEditorTarget.New, repository)
        val vm = newPayment(r)
        vm.fill(date = "2026-03-20", amount = "10000")

        r.visibility.conceal()
        settle()
        assertTrue(vm.state.value.concealed)
        vm.onIntent(ZakatEditorIntent.Save)
        settle()
        assertTrue(repository.createdInputs.isEmpty())
        assertEquals("10000", vm.state.value.draft.amountText)

        vm.onIntent(ZakatEditorIntent.RevealForm)
        settle()
        assertFalse(vm.state.value.concealed)
        assertFalse(r.visibility.isHidden())
        assertEquals("10000", vm.state.value.draft.amountText)
    }

    @Test
    fun aMissingPaymentOpensAsMissingWithoutCreatingAnything() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        val vm = rig(ZakatEditorTarget.Existing("nope"), repository).viewModel
        settle()

        assertEquals(JournalPhase.Missing, vm.state.value.phase)
        assertTrue(repository.createdInputs.isEmpty())
    }
}
