@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.prices

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.DeleteManualQuoteUseCase
import com.mk.daftar.core.domain.usecase.ObserveQuoteSnapshotUseCase
import com.mk.daftar.core.domain.usecase.RemoveOverrideUseCase
import com.mk.daftar.core.domain.usecase.SetManualQuoteUseCase
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.navigation.ManualQuoteRoute
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class ManualQuoteViewModelTest {

    private val dispatcher = StandardTestDispatcher(TestCoroutineScheduler())

    private fun settle() = dispatcher.scheduler.runCurrent()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val sell21: QuoteKey = sellKey(GoldKarat.K21)
    private val sell18 = sellKey(GoldKarat.K18)

    private class Rig(
        val repos: PricesRig,
        val model: ManualQuoteViewModel,
        val effects: MutableList<ManualQuoteEffect>,
    )

    private fun rig(route: ManualQuoteRoute = ManualQuoteRoute(quoteKeyToken(sellKey(GoldKarat.K21)))): Rig {
        val repos = PricesRig()
        val model = ManualQuoteViewModel(
            route = route,
            observeSnapshot = ObserveQuoteSnapshotUseCase(repos.session, repos.prices),
            setManual = SetManualQuoteUseCase(repos.prices),
            removeOverride = RemoveOverrideUseCase(repos.prices),
            deleteManual = DeleteManualQuoteUseCase(repos.prices),
            clock = FixedClock,
        )
        val effects = mutableListOf<ManualQuoteEffect>()
        CoroutineScope(dispatcher).launch { model.effects.collect { effects += it } }
        return Rig(repos, model, effects)
    }

    /** Opens generation 1 with [snapshot] and lets the editor load it. */
    private fun Rig.open(snapshot: QuoteSnapshot = fullSnapshot(), generation: Long = 1L) {
        repos.openEmpty(generation)
        repos.publish(snapshot, generation)
        settle()
    }

    private fun Rig.type(text: String) {
        model.onIntent(ManualQuoteIntent.DraftValueChanged(text))
        settle()
    }

    private fun Rig.intent(intent: ManualQuoteIntent) {
        model.onIntent(intent)
        settle()
    }

    private val Rig.state: ManualQuoteUiState get() = model.state.value

    private fun pinned21(revision: Long = 3L, value: String = "6100") = fullSnapshot().copy(
        manual = listOf(manualGold(GoldKarat.K21, value, revision = revision, pinned = true)),
    )

    @Test
    fun aStoredValueLoadsIntoTheDraftWithItsRevisionAndItsSourceBeneath() {
        val rig = rig()
        rig.open(pinned21())
        assertEquals("6100", rig.state.draftValue)
        assertEquals(3L, rig.state.slot?.revision)
        assertTrue(rig.state.slot?.pinned == true)
        assertEquals(dec("6185"), rig.state.source?.value)
        assertFalse(rig.state.dirty)
    }

    @Test
    fun aFirstSaveCreatesTheSlotWithNoRevisionAndCloses() {
        val rig = rig()
        rig.open()
        rig.type("6185.5")
        rig.intent(ManualQuoteIntent.Save)
        assertEquals(1, rig.repos.prices.setCalls.size)
        val (input, expected) = rig.repos.prices.setCalls.single()
        assertEquals(sell21, input.key)
        assertEquals(dec("6185.5"), input.value)
        assertNull(expected)
        assertEquals(listOf<ManualQuoteEffect>(ManualQuoteEffect.Close), rig.effects)
    }

    @Test
    fun arabicDigitsAreReadAsTheSameValue() {
        val rig = rig()
        rig.open()
        rig.type("٦١٨٥٫٥")
        rig.intent(ManualQuoteIntent.Save)
        assertEquals(dec("6185.5"), rig.repos.prices.setCalls.single().first.value)
    }

    @Test
    fun aSaveReplacesTheRevisionOnScreen() {
        val rig = rig()
        rig.open(pinned21(revision = 3))
        rig.type("6200")
        rig.intent(ManualQuoteIntent.Save)
        assertEquals(3L, rig.repos.prices.setCalls.single().second)
    }

    @Test
    fun invalidValuesAreReportedAndNeverSaved() {
        val rig = rig()
        rig.open()
        rig.type("6,185")
        assertEquals(ManualValueProblem.GROUPING, rig.state.validation)
        rig.intent(ManualQuoteIntent.Save)
        assertTrue(rig.repos.prices.setCalls.isEmpty())

        rig.type("0")
        assertEquals(ManualValueProblem.NOT_POSITIVE, rig.state.validation)

        rig.type("")
        assertNull(rig.state.validation)
        rig.intent(ManualQuoteIntent.Save)
        assertEquals(ManualValueProblem.EMPTY, rig.state.validation)
        assertTrue(rig.repos.prices.setCalls.isEmpty())
        assertTrue(rig.effects.isEmpty())
    }

    @Test
    fun aNoteThatIsTooLongBlocksSaving() {
        val rig = rig()
        rig.open()
        rig.type("6200")
        rig.model.onIntent(ManualQuoteIntent.DraftNoteChanged("x".repeat(MAX_MANUAL_NOTE_LENGTH + 1)))
        settle()
        assertEquals(ManualValueProblem.NOTE_TOO_LONG, rig.state.validation)
        rig.intent(ManualQuoteIntent.Save)
        assertTrue(rig.repos.prices.setCalls.isEmpty())
    }

    @Test
    fun aDuplicateSubmissionIsIgnoredWhileTheSaveIsInFlight() {
        val rig = rig()
        rig.open()
        val gate = CompletableDeferred<Unit>()
        rig.repos.prices.holdWrites = gate
        rig.type("6200")
        rig.intent(ManualQuoteIntent.Save)
        rig.intent(ManualQuoteIntent.Save)
        assertEquals(1, rig.repos.prices.setCalls.size)
        assertTrue(rig.state.busy)
        gate.complete(Unit)
        settle()
        assertFalse(rig.state.busy)
        assertEquals(listOf<ManualQuoteEffect>(ManualQuoteEffect.Close), rig.effects)
    }

    @Test
    fun aConflictFromTheStoreKeepsTheDraftAndNeverOverwrites() {
        val rig = rig()
        rig.open(pinned21(revision = 3))
        rig.repos.prices.setResult = Outcome.Failure(StorageError.Conflict)
        rig.type("6200")
        rig.intent(ManualQuoteIntent.Save)
        assertTrue(rig.state.conflict)
        assertEquals("6200", rig.state.draftValue)
        assertFalse(rig.state.busy)
        assertTrue(rig.effects.isEmpty())
    }

    @Test
    fun aStorageFailureKeepsTheDraftAndSaysSo() {
        val rig = rig()
        rig.open()
        rig.repos.prices.setResult = Outcome.Failure(StorageError.DiskFull)
        rig.type("6200")
        rig.intent(ManualQuoteIntent.Save)
        assertEquals(StorageError.DiskFull, rig.state.failure)
        assertEquals("6200", rig.state.draftValue)
        assertTrue(rig.effects.isEmpty())
    }

    @Test
    fun aStoredValueChangingUnderADirtyDraftIsAConflictUntilReloaded() {
        val rig = rig()
        rig.open(pinned21(revision = 3, value = "6100"))
        rig.type("6200")
        rig.repos.publish(pinned21(revision = 4, value = "6300"))
        settle()
        assertTrue(rig.state.conflict)
        assertEquals("6200", rig.state.draftValue)

        rig.intent(ManualQuoteIntent.Save)
        assertTrue(rig.repos.prices.setCalls.isEmpty(), "saving waits for a reload")

        rig.intent(ManualQuoteIntent.Reload)
        assertFalse(rig.state.conflict)
        assertEquals("6200", rig.state.draftValue)
        assertTrue(rig.state.dirty)

        rig.intent(ManualQuoteIntent.Save)
        assertEquals(4L, rig.repos.prices.setCalls.single().second)
    }

    @Test
    fun aStoredValueChangingWhileTheDraftIsCleanIsAdoptedWithoutAConflict() {
        val rig = rig()
        rig.open(pinned21(revision = 3, value = "6100"))
        rig.repos.publish(pinned21(revision = 4, value = "6300"))
        settle()
        assertFalse(rig.state.conflict)
        assertEquals("6300", rig.state.draftValue)
        assertFalse(rig.state.dirty)
    }

    @Test
    fun returnToSourceUnpinsWithTheRevisionKeepsTheDraftAndNeverDeletes() {
        val rig = rig()
        rig.open(pinned21(revision = 3))
        rig.type("6200")
        rig.intent(ManualQuoteIntent.ReturnToSource)
        assertEquals(listOf<Pair<QuoteKey, Long>>(sell21 to 3L), rig.repos.prices.removeCalls)
        assertTrue(rig.repos.prices.deleteCalls.isEmpty())
        // The editor stays open after an unpin, and the revision it caused is not a conflict.
        assertTrue(rig.effects.isEmpty())
        rig.repos.publish(
            fullSnapshot().copy(manual = listOf(manualGold(GoldKarat.K21, "6100", revision = 4, pinned = false))),
        )
        settle()
        assertFalse(rig.state.conflict)
        assertEquals("6200", rig.state.draftValue)
        assertTrue(rig.state.dirty)
    }

    @Test
    fun returnToSourceWithNoRemotePriceLeavesTheManualValueAsTheLastResort() {
        val rig = rig()
        val withoutRemote21 = pinned21(revision = 1).copy(
            remote = fullSnapshot().remote.filterNot {
                priceQuoteKey(it.quote) ==
                    sell21
            },
        )
        rig.open(withoutRemote21)
        assertNull(rig.state.source)
        assertTrue(rig.state.slot?.pinned == true)
        rig.intent(ManualQuoteIntent.ReturnToSource)
        assertEquals(listOf<Pair<QuoteKey, Long>>(sell21 to 1L), rig.repos.prices.removeCalls)
    }

    @Test
    fun deletingNeedsConfirmationAndThenUsesTheRevision() {
        val rig = rig()
        rig.open(pinned21(revision = 2))
        rig.intent(ManualQuoteIntent.RequestDelete)
        assertTrue(rig.state.confirmDelete)
        assertTrue(rig.repos.prices.deleteCalls.isEmpty())
        rig.intent(ManualQuoteIntent.ConfirmDelete)
        assertEquals(listOf<Pair<QuoteKey, Long>>(sell21 to 2L), rig.repos.prices.deleteCalls)
        assertEquals(listOf<ManualQuoteEffect>(ManualQuoteEffect.Close), rig.effects)
    }

    @Test
    fun dismissingTheDeleteConfirmationDeletesNothing() {
        val rig = rig()
        rig.open(pinned21(revision = 2))
        rig.intent(ManualQuoteIntent.RequestDelete)
        rig.intent(ManualQuoteIntent.DismissDelete)
        assertFalse(rig.state.confirmDelete)
        assertTrue(rig.repos.prices.deleteCalls.isEmpty())
    }

    @Test
    fun backWithACleanDraftClosesAtOnce() {
        val rig = rig()
        rig.open()
        rig.intent(ManualQuoteIntent.RequestBack)
        assertEquals(listOf<ManualQuoteEffect>(ManualQuoteEffect.Close), rig.effects)
    }

    @Test
    fun backWithADirtyDraftAsksBeforeItDropsIt() {
        val rig = rig()
        rig.open()
        rig.type("6200")
        rig.intent(ManualQuoteIntent.RequestBack)
        assertTrue(rig.state.confirmDiscard)
        assertTrue(rig.effects.isEmpty())

        rig.intent(ManualQuoteIntent.KeepEditing)
        assertFalse(rig.state.confirmDiscard)
        assertEquals("6200", rig.state.draftValue)

        rig.intent(ManualQuoteIntent.RequestBack)
        rig.intent(ManualQuoteIntent.ConfirmDiscard)
        assertEquals(listOf<ManualQuoteEffect>(ManualQuoteEffect.Close), rig.effects)
    }

    @Test
    fun aLockClearsTheDraftClosesTheEditorAndCancelsTheWriteInFlight() {
        val rig = rig()
        rig.open(pinned21(revision = 3))
        val gate = CompletableDeferred<Unit>()
        rig.repos.prices.holdWrites = gate
        rig.type("6200")
        rig.intent(ManualQuoteIntent.Save)
        assertTrue(rig.state.busy)

        rig.repos.session.set(VaultSessionState.Locked)
        settle()
        assertTrue(rig.state.closed)
        assertEquals("", rig.state.draftValue)
        assertNull(rig.state.slot)

        gate.complete(Unit)
        settle()
        assertTrue(rig.effects.isEmpty(), "a write that finishes after the lock never closes or reports anything")
    }

    @Test
    fun aClosedEditorNeverShowsAValueFromAnotherGeneration() {
        val rig = rig()
        rig.open(pinned21(revision = 3))
        rig.type("6200")
        rig.repos.session.set(VaultSessionState.Locked)
        settle()
        rig.open(pinned21(revision = 7, value = "6400"), generation = 2L)
        assertTrue(rig.state.closed)
        assertEquals("", rig.state.draftValue)
        assertNull(rig.state.slot)
    }

    @Test
    fun aFreshEditorOfANewGenerationStartsEmptyOfTheOldDraft() {
        val rig = rig()
        rig.open(pinned21(revision = 3))
        rig.type("6200")
        rig.repos.session.set(VaultSessionState.Locked)
        settle()
        val next = rig()
        next.open(pinned21(revision = 7, value = "6400"), generation = 2L)
        assertEquals("6400", next.state.draftValue)
        assertEquals(7L, next.state.slot?.revision)
        assertFalse(next.state.dirty)
    }

    @Test
    fun anUnknownQuoteTokenIsAMissingQuoteAndNeverAnotherQuote() {
        val rig = rig(ManualQuoteRoute("gold-99-sell"))
        rig.open()
        assertTrue(rig.state.unknownQuote)
        assertNull(rig.state.key)
        rig.type("6200")
        rig.intent(ManualQuoteIntent.Save)
        assertTrue(rig.repos.prices.setCalls.isEmpty())
    }

    @Test
    fun aRouteWithoutAQuoteOpensTheChooserAndChoosingLoadsItsStoredValue() {
        val rig = rig(ManualQuoteRoute(null))
        rig.open(
            fullSnapshot().copy(manual = listOf(manualUsd("49.10", revision = 1, pinned = true))),
        )
        assertNull(rig.state.key)
        assertTrue(rig.state.choosing)
        rig.intent(ManualQuoteIntent.ChooseQuote(QuoteKey.UsdEgp))
        assertEquals(QuoteKey.UsdEgp, rig.state.key)
        assertFalse(rig.state.choosing)
        assertEquals("49.1", rig.state.draftValue)
        assertEquals(1L, rig.state.slot?.revision)
        assertEquals(dec("48.50"), rig.state.source?.value)
    }

    @Test
    fun aDirtyDraftCannotBeSilentlyCarriedToAnotherQuote() {
        val rig = rig()
        rig.open()
        rig.type("6200")
        rig.intent(ManualQuoteIntent.ChooseQuote(QuoteKey.UsdEgp))
        assertEquals(sell21, rig.state.key)
        assertEquals("6200", rig.state.draftValue)
    }

    @Test
    fun anUnexpectedStorageFailureOnUnpinIsReportedAndTheEditorStaysOpen() {
        val rig = rig()
        rig.open(pinned21(revision = 3))
        rig.repos.prices.removeResult = Outcome.Failure(StorageError.Io)
        rig.intent(ManualQuoteIntent.ReturnToSource)
        assertEquals(StorageError.Io, rig.state.failure)
        assertFalse(rig.state.closed)
        assertNotNull(rig.state.slot)
    }

    @Test
    fun reloadWithoutAConflictChangesNothing() {
        val rig = rig()
        rig.open(pinned21(revision = 3))
        rig.type("6200")
        rig.intent(ManualQuoteIntent.Reload)
        assertEquals("6200", rig.state.draftValue)
        assertTrue(rig.state.dirty)
    }
}
