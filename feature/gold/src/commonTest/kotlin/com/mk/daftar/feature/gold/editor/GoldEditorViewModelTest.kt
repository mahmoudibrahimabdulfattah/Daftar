package com.mk.daftar.feature.gold.editor

import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.CreateGoldHoldingUseCase
import com.mk.daftar.core.domain.usecase.DeleteGoldHoldingUseCase
import com.mk.daftar.core.domain.usecase.ObserveGoldHoldingUseCase
import com.mk.daftar.core.domain.usecase.ObserveGoldHoldingsUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.domain.usecase.UpdateGoldHoldingUseCase
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.gold.GoldAction
import com.mk.daftar.feature.gold.GoldBusy
import com.mk.daftar.feature.gold.GoldEffect
import com.mk.daftar.feature.gold.GoldFailure
import com.mk.daftar.feature.gold.GoldPhase
import com.mk.daftar.feature.gold.testing.FakeGoldRepository
import com.mk.daftar.feature.gold.testing.FakePriceRepository
import com.mk.daftar.feature.gold.testing.FakeSession
import com.mk.daftar.feature.gold.testing.FixedClock
import com.mk.daftar.feature.gold.testing.NOW
import com.mk.daftar.feature.gold.testing.holding
import com.mk.daftar.feature.gold.testing.remoteGold
import com.mk.daftar.feature.gold.testing.remoteUsd
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The gold editor. Virtual time advances with runCurrent only: the quote observation's clock ticks never end on their own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoldEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher(TestCoroutineScheduler())
    private val scope = CoroutineScope(dispatcher)
    private val effects = mutableListOf<GoldEffect>()
    private var collector: Job? = null

    private fun settle() = dispatcher.scheduler.runCurrent()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        collector?.cancel()
        Dispatchers.resetMain()
    }

    private class Rig(
        val gold: FakeGoldRepository,
        val prices: FakePriceRepository,
        val visibility: ValuesVisibility,
        val vm: GoldEditorViewModel,
    )

    private fun rig(
        target: GoldEditorTarget = GoldEditorTarget.New,
        holdings: List<GoldHolding> = listOf(holding("g1", GoldKarat.K21, "80")),
        quotes: QuoteSnapshot = fixtureQuotes(),
    ): Rig {
        val session = FakeSession()
        session.open()
        val gold = FakeGoldRepository(holdings)
        val prices = FakePriceRepository(quotes)
        val clock = FixedClock(NOW)
        val visibility = ValuesVisibility()
        val vm = GoldEditorViewModel(
            target = target,
            observeHolding = ObserveGoldHoldingUseCase(gold),
            observeHoldings = ObserveGoldHoldingsUseCase(gold),
            observeQuotes = ObserveSelectedQuotesUseCase(session, prices, clock),
            createHolding = CreateGoldHoldingUseCase(gold),
            updateHolding = UpdateGoldHoldingUseCase(gold),
            deleteHolding = DeleteGoldHoldingUseCase(gold),
            visibility = visibility,
        )
        collector = scope.launch { vm.effects.collect { effects += it } }
        settle()
        return Rig(gold, prices, visibility, vm)
    }

    private fun GoldEditorViewModel.weight(text: String) = onIntent(GoldEditorIntent.WeightChanged(text))

    private fun fixtureQuotes(): QuoteSnapshot = QuoteSnapshot(
        remote = listOf(
            remoteGold(GoldKarat.K24, "7069"),
            remoteGold(GoldKarat.K21, "6185"),
            remoteGold(GoldKarat.K18, "5301"),
            remoteUsd("48.50"),
        ),
        manual = emptyList(),
    )

    @Test
    fun aNewHoldingStartsOnTheDefaultSelectionWithoutErrorsOrDirtyState() {
        val state = rig().vm.state.value
        assertEquals(GoldPhase.Ready, state.phase)
        assertTrue(state.isNew)
        assertEquals(GoldKarat.K21, state.draft.karat)
        assertEquals(GoldForm.BAR, state.draft.form)
        assertEquals("", state.draft.weightText)
        assertFalse(state.dirty)
        assertFalse(state.attempted)
        assertEquals(GoldWeightError.Invalid, state.errors.weight, "a blank weight is not yet a valid holding")
        assertFalse(state.attempted, "the screen flags errors only after a save attempt")
    }

    @Test
    fun arabicIndicWeightSavesExactlyWithTheChosenKaratAndForm() {
        val rig = rig()
        rig.vm.onIntent(GoldEditorIntent.KaratChanged(GoldKarat.K18))
        rig.vm.onIntent(GoldEditorIntent.FormChanged(GoldForm.JEWELRY))
        rig.vm.weight("٢٦٫٧٧")
        rig.vm.onIntent(GoldEditorIntent.NoteChanged("  زفاف  "))
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()

        val input = rig.gold.createdInputs.single()
        assertEquals(GoldKarat.K18, input.karat)
        assertEquals(GoldForm.JEWELRY, input.form)
        assertEquals("26.77", input.weight.grams.toPlainString())
        assertEquals("زفاف", input.note)
        assertTrue(rig.vm.state.value.completed)
        assertEquals(listOf<GoldEffect>(GoldEffect.Completed), effects)
    }

    @Test
    fun zeroNegativeSignedExponentAndOverprecisionNeverSubmit() {
        val rig = rig()
        for (text in listOf("", "0", "0.00", "-5", "+5", "1e3", "26.777", "1.", "abc", "1,00")) {
            rig.vm.weight(text)
            assertNotNull(rig.vm.state.value.errors.weight, "$text is invalid")
        }
        rig.vm.weight("26.777")
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()
        assertTrue(rig.gold.createdInputs.isEmpty())
        assertTrue(rig.vm.state.value.attempted, "the first attempt flags the invalid weight")
    }

    @Test
    fun aValidWeightWithTwoDecimalsIsAccepted() {
        val rig = rig()
        rig.vm.weight("26.77")
        assertNull(rig.vm.state.value.errors.weight)
    }

    @Test
    fun karatChangesNeverModifyTheWeight() {
        val rig = rig()
        rig.vm.weight("٢٦٫٧٧")
        rig.vm.onIntent(GoldEditorIntent.KaratChanged(GoldKarat.K24))
        assertEquals("٢٦٫٧٧", rig.vm.state.value.draft.weightText)
        assertEquals(GoldKarat.K24, rig.vm.state.value.draft.karat)
    }

    @Test
    fun aDuplicateSaveWhileTheCreateRunsIsIgnored() {
        val rig = rig()
        val gate = CompletableDeferred<Unit>()
        rig.gold.createGate = gate
        rig.vm.weight("5")
        rig.vm.onIntent(GoldEditorIntent.Save)
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()
        assertEquals(GoldBusy.Saving, rig.vm.state.value.busy)
        gate.complete(Unit)
        settle()
        assertEquals(1, rig.gold.createdInputs.size)
        assertTrue(rig.vm.state.value.completed)
    }

    @Test
    fun aFailedSaveKeepsTheDraftAndRetryCompletesIt() {
        val rig = rig()
        rig.vm.weight("٢٦٫٧٧")
        rig.gold.writeFailure = StorageError.Io
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()

        val state = rig.vm.state.value
        assertEquals(GoldFailure(GoldAction.Save, StorageError.Io), state.failure)
        assertEquals("٢٦٫٧٧", state.draft.weightText)
        assertFalse(state.completed)
        assertFalse(state.failure!!.uncertain)

        rig.gold.writeFailure = null
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()
        assertTrue(rig.vm.state.value.completed)
        assertEquals(2, rig.gold.createdInputs.size)
    }

    @Test
    fun aFullDiskIsReportedAsDiskFullAndTheDraftStays() {
        val rig = rig()
        rig.vm.weight("5")
        rig.gold.writeFailure = StorageError.DiskFull
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()
        assertEquals(StorageError.DiskFull, rig.vm.state.value.failure?.error)
        assertEquals("5", rig.vm.state.value.draft.weightText)
    }

    @Test
    fun aSessionEndDuringTheSaveIsUncertainAndNeverRepeatedAutomatically() {
        val rig = rig()
        rig.vm.weight("5")
        rig.gold.writeFailure = StorageError.SessionEnded
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()
        assertTrue(rig.vm.state.value.failure!!.uncertain)
        assertEquals(1, rig.gold.createdInputs.size, "the create is not repeated")
    }

    @Test
    fun anExistingHoldingLoadsItsValuesAsItsBaseline() {
        val rig = rig(target = GoldEditorTarget.Existing("g1"))
        val state = rig.vm.state.value
        assertEquals(GoldPhase.Ready, state.phase)
        assertFalse(state.isNew)
        assertEquals(GoldKarat.K21, state.draft.karat)
        assertEquals("80", state.draft.weightText)
        assertFalse(state.dirty)
    }

    @Test
    fun aMissingHoldingIsAMissingRecordNotANewOne() {
        val rig = rig(target = GoldEditorTarget.Existing("gone"), holdings = emptyList())
        val state = rig.vm.state.value
        assertEquals(GoldPhase.Missing, state.phase)
        assertFalse(state.isNew)
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()
        assertTrue(rig.gold.updateRevisions.isEmpty())
    }

    @Test
    fun editingSavesWithTheLoadedRevision() {
        val rig = rig(target = GoldEditorTarget.Existing("g1"))
        rig.vm.weight("90")
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()
        assertEquals(listOf(1L), rig.gold.updateRevisions)
        assertEquals("90", rig.gold.updatedInputs.single().weight.grams.toPlainString())
        assertTrue(rig.vm.state.value.completed)
    }

    @Test
    fun aChangeUnderADirtyDraftIsAConflictThatKeepsTheDraftUntilAnExplicitReload() {
        val rig = rig(target = GoldEditorTarget.Existing("g1"))
        rig.vm.weight("90")
        rig.gold.externalUpdate("g1", GoldKarat.K18, "5")
        settle()

        var state = rig.vm.state.value
        assertTrue(state.conflict)
        assertEquals("90", state.draft.weightText, "the draft is preserved, not merged")

        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()
        assertTrue(rig.gold.updateRevisions.isEmpty(), "a conflicting save never goes out")

        rig.vm.onIntent(GoldEditorIntent.ConfirmReload)
        settle()
        assertTrue(rig.vm.state.value.conflict, "reload needs its explicit confirmation first")
        rig.vm.onIntent(GoldEditorIntent.RequestReload)
        settle()
        assertEquals(GoldConfirmation.Reload, rig.vm.state.value.confirmation)
        rig.vm.onIntent(GoldEditorIntent.ConfirmReload)
        settle()

        state = rig.vm.state.value
        assertFalse(state.conflict)
        assertEquals(GoldKarat.K18, state.draft.karat)
        assertEquals("5", state.draft.weightText)
        assertTrue(rig.gold.updateRevisions.isEmpty(), "reloading never retries the write")
    }

    @Test
    fun aCleanEditorFollowsAnExternalChangeWithoutAConflict() {
        val rig = rig(target = GoldEditorTarget.Existing("g1"))
        rig.gold.externalUpdate("g1", GoldKarat.K18, "5")
        settle()
        val state = rig.vm.state.value
        assertFalse(state.conflict)
        assertEquals(GoldKarat.K18, state.draft.karat)
        assertEquals("5", state.draft.weightText)
    }

    @Test
    fun deletingNeedsItsConfirmationAndUsesTheLoadedRevision() {
        val rig = rig(target = GoldEditorTarget.Existing("g1"))
        rig.vm.onIntent(GoldEditorIntent.RequestDelete)
        settle()
        assertEquals(GoldConfirmation.Delete, rig.vm.state.value.confirmation)
        rig.vm.onIntent(GoldEditorIntent.DismissConfirmation)
        settle()
        assertNotNull(rig.gold.revisionOf("g1"), "cancelling keeps the holding")
        assertTrue(rig.gold.deleteRevisions.isEmpty())

        rig.vm.onIntent(GoldEditorIntent.RequestDelete)
        rig.vm.onIntent(GoldEditorIntent.ConfirmDelete)
        settle()
        assertEquals(listOf(1L), rig.gold.deleteRevisions)
        assertNull(rig.gold.revisionOf("g1"))
        assertTrue(rig.vm.state.value.completed)
        assertEquals(listOf<GoldEffect>(GoldEffect.Completed), effects)
    }

    @Test
    fun aFailedDeleteKeepsTheHoldingAndTheDraft() {
        val rig = rig(target = GoldEditorTarget.Existing("g1"))
        rig.gold.writeFailure = StorageError.Io
        rig.vm.onIntent(GoldEditorIntent.RequestDelete)
        rig.vm.onIntent(GoldEditorIntent.ConfirmDelete)
        settle()
        assertEquals(GoldFailure(GoldAction.Delete, StorageError.Io), rig.vm.state.value.failure)
        assertEquals("80", rig.vm.state.value.draft.weightText)
        assertNotNull(rig.gold.revisionOf("g1"))
    }

    @Test
    fun aHoldingDeletedElsewhereShowsTheMissingRecordState() {
        val rig = rig(target = GoldEditorTarget.Existing("g1"))
        rig.gold.externalDelete("g1")
        settle()
        assertEquals(GoldPhase.Missing, rig.vm.state.value.phase)
    }

    @Test
    fun leavingACleanEditorProceedsAtOnce() {
        val rig = rig(target = GoldEditorTarget.Existing("g1"))
        var discarded: Boolean? = null
        rig.vm.onLeaveRequested { discarded = it }
        assertEquals(false, discarded)
        assertNull(rig.vm.state.value.confirmation)
    }

    @Test
    fun leavingADirtyEditorAsksAndProceedsOnlyAfterDiscard() {
        val rig = rig(target = GoldEditorTarget.Existing("g1"))
        rig.vm.weight("1")
        var discarded: Boolean? = null
        rig.vm.onLeaveRequested { discarded = it }
        assertNull(discarded, "the leave waits for the discard confirmation")
        assertEquals(GoldConfirmation.Discard, rig.vm.state.value.confirmation)

        rig.vm.onIntent(GoldEditorIntent.ConfirmDiscard)
        assertEquals(true, discarded)
        assertEquals("80", rig.vm.state.value.draft.weightText, "the draft returns to the saved values")
    }

    @Test
    fun keepEditingCancelsTheLeaveAndKeepsTheDraft() {
        val rig = rig(target = GoldEditorTarget.Existing("g1"))
        rig.vm.weight("1")
        var proceeded = false
        rig.vm.onLeaveRequested { proceeded = true }
        rig.vm.onIntent(GoldEditorIntent.DismissConfirmation)
        assertFalse(proceeded)
        assertEquals("1", rig.vm.state.value.draft.weightText)
        assertNull(rig.vm.state.value.confirmation)
    }

    @Test
    fun aLockDropsTheDraftAndItsConfirmationWithoutAsking() {
        val rig = rig(target = GoldEditorTarget.Existing("g1"))
        rig.vm.weight("1")
        var proceeded = false
        rig.vm.onLeaveRequested { proceeded = true }
        rig.gold.setLocked(true)
        settle()

        val state = rig.vm.state.value
        assertEquals(GoldPhase.Locked, state.phase)
        assertNull(state.confirmation)
        assertEquals("", state.draft.weightText, "the draft is cleared")
        rig.vm.onIntent(GoldEditorIntent.ConfirmDiscard)
        assertFalse(proceeded, "a lock never completes a pending leave")
    }

    @Test
    fun aLockDuringTheSaveDropsItsOutcome() {
        val rig = rig()
        val gate = CompletableDeferred<Unit>()
        rig.gold.createGate = gate
        rig.vm.weight("5")
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()
        rig.gold.setLocked(true)
        settle()
        gate.complete(Unit)
        settle()
        val state = rig.vm.state.value
        assertEquals(GoldPhase.Locked, state.phase)
        assertFalse(state.completed)
        assertTrue(effects.isEmpty(), "a write that outlived the lock reports nothing")
    }

    @Test
    fun maskingConcealsTheFormAndSavingIsRefusedUntilAnExplicitReveal() {
        val rig = rig()
        rig.vm.weight("5")
        rig.visibility.toggle()
        settle()
        assertTrue(rig.vm.state.value.concealed)
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()
        assertTrue(rig.gold.createdInputs.isEmpty(), "nothing saves while concealed")

        rig.vm.onIntent(GoldEditorIntent.RevealForm)
        settle()
        assertFalse(rig.vm.state.value.concealed)
        assertTrue(rig.visibility.visible.value)
    }

    @Test
    fun aNoteOverTheLimitBlocksTheSave() {
        val rig = rig()
        rig.vm.weight("5")
        rig.vm.onIntent(GoldEditorIntent.NoteChanged("ا".repeat(4_001)))
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()
        assertTrue(rig.gold.createdInputs.isEmpty())
        assertNotNull(rig.vm.state.value.errors.note)
    }

    @Test
    fun thePreviewUsesTheSelectedKaratsSellQuoteAndNeverBlocksSaving() {
        val rig = rig(quotes = QuoteSnapshot(remote = listOf(remoteGold(GoldKarat.K21, "6185")), manual = emptyList()))
        rig.vm.onIntent(GoldEditorIntent.KaratChanged(GoldKarat.K21))
        rig.vm.weight("٢٦٫٧٧")
        assertEquals("165572.45", rig.vm.state.value.preview.egp?.toPlainString())

        rig.vm.onIntent(GoldEditorIntent.KaratChanged(GoldKarat.K18))
        assertNull(rig.vm.state.value.preview.quote, "no 18K sell quote: the preview is unavailable")
        rig.vm.onIntent(GoldEditorIntent.Save)
        settle()
        assertEquals(1, rig.gold.createdInputs.size, "saving is not blocked by a missing quote")
    }
}
