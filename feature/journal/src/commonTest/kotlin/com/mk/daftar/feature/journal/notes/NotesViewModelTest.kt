package com.mk.daftar.feature.journal.notes

import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.CreatePersonalNoteUseCase
import com.mk.daftar.core.domain.usecase.DeletePersonalNoteUseCase
import com.mk.daftar.core.domain.usecase.ObservePersonalNoteUseCase
import com.mk.daftar.core.domain.usecase.ObservePersonalNotesUseCase
import com.mk.daftar.core.domain.usecase.UpdatePersonalNoteUseCase
import com.mk.daftar.core.model.EntryLimits
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.navigation.NoteAddRoute
import com.mk.daftar.core.navigation.NoteEditRoute
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.journal.JournalBusy
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.conceal
import com.mk.daftar.feature.journal.isHidden
import com.mk.daftar.feature.journal.testing.FakeNoteRepository
import com.mk.daftar.feature.journal.testing.FixedClock
import com.mk.daftar.feature.journal.testing.note
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
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
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.notesList(
        repository: FakeNoteRepository,
        visibility: ValuesVisibility = ValuesVisibility(),
    ) = NotesListViewModel(ObservePersonalNotesUseCase(repository), visibility, TimeZone.UTC).also {
        advanceUntilIdle()
    }

    private fun TestScope.editor(
        target: NoteEditorTarget,
        repository: FakeNoteRepository,
        visibility: ValuesVisibility = ValuesVisibility(),
    ): NoteEditorViewModel = NoteEditorViewModel(
        target = target,
        observeNote = ObservePersonalNoteUseCase(repository),
        observeNotes = ObservePersonalNotesUseCase(repository),
        createNote = CreatePersonalNoteUseCase(repository),
        updateNote = UpdatePersonalNoteUseCase(repository),
        deleteNote = DeletePersonalNoteUseCase(repository),
        visibility = visibility,
    ).also { advanceUntilIdle() }

    private fun NoteEditorViewModel.fill(title: String, body: String) {
        onIntent(NoteEditorIntent.TitleChanged(title))
        onIntent(NoteEditorIntent.BodyChanged(body))
    }

    @Test
    fun theListKeepsTheRepositoryOrderAndShowsTheUpdateDay() = runTest(dispatcher) {
        val older = note("n1", "قديمة", "نص", updatedAt = Instant.parse("2026-09-28T10:00:00Z"))
        val newer = note("n2", "حديثة", "نص", updatedAt = Instant.parse("2026-10-01T10:00:00Z"))
        val vm = notesList(FakeNoteRepository(listOf(older, newer)))

        assertEquals(listOf("n2", "n1"), vm.state.value.notes.map { it.id.value })
        val day = vm.state.value.notes.first().updatedOn
        assertEquals(listOf(2026, 10, 1), listOf(day.year, day.month.ordinal + 1, day.day))
        assertEquals(JournalPhase.Ready, vm.state.value.phase)
    }

    @Test
    fun anEmptyListAndALockedListAreDistinct() = runTest(dispatcher) {
        val repository = FakeNoteRepository()
        val vm = notesList(repository)
        assertEquals(JournalPhase.Empty, vm.state.value.phase)

        repository.setLocked(true)
        advanceUntilIdle()
        assertEquals(JournalPhase.Locked, vm.state.value.phase)
    }

    @Test
    fun openingANoteWhileTitlesAreHiddenNeedsAnExplicitReveal() = runTest(dispatcher) {
        val visibility = ValuesVisibility()
        visibility.conceal()
        val vm = notesList(FakeNoteRepository(listOf(note("n1", "سلفة", "نص"))), visibility)
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { vm.effects.collect { effects += it } }

        vm.onIntent(NotesListIntent.OpenNote(NoteId("n1")))
        advanceUntilIdle()
        assertTrue(effects.isEmpty())
        assertEquals(NoteEditRoute("n1"), vm.state.value.pendingRoute)

        vm.onIntent(NotesListIntent.RevealAndOpen)
        advanceUntilIdle()
        assertFalse(visibility.isHidden())
        assertEquals(listOf<JournalEffect>(JournalEffect.Navigate(NoteEditRoute("n1"))), effects)
        collector.cancel()
    }

    @Test
    fun newNotesOpenImmediatelyWhenVisible() = runTest(dispatcher) {
        val vm = notesList(FakeNoteRepository())
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { vm.effects.collect { effects += it } }

        vm.onIntent(NotesListIntent.OpenAdd)
        advanceUntilIdle()

        assertEquals(listOf<JournalEffect>(JournalEffect.Navigate(NoteAddRoute)), effects)
        collector.cancel()
    }

    @Test
    fun titleRulesTrimAndCountCodePoints() {
        assertTrue(EntryLimits.isValidName("  سلفة  "))
        assertFalse(EntryLimits.isValidName("   "))
        assertTrue(EntryLimits.isValidName("😀".repeat(EntryLimits.NAME_CODE_POINTS)))
        assertFalse(EntryLimits.isValidName("😀".repeat(EntryLimits.NAME_CODE_POINTS + 1)))
        assertEquals(NoteTitleError.TooLong, NoteDraft(title = "a".repeat(121), body = "x").errors().title)
        assertEquals(NoteTitleError.Required, NoteDraft(title = "   ", body = "x").errors().title)
    }

    @Test
    fun bodyRulesRejectBlankAndOverlongTextButKeepLineBreaks() {
        assertEquals(NoteBodyError.Required, NoteDraft(title = "t", body = " \n\t ").errors().body)
        assertNull(NoteDraft(title = "t", body = "a".repeat(10_000)).errors().body)
        assertEquals(NoteBodyError.TooLong, NoteDraft(title = "t", body = "a".repeat(10_001)).errors().body)
        val body = "سطر ١\n  سطر ٢  \r\n"
        assertEquals(body, NoteDraft(title = "t", body = body).values().body)
    }

    @Test
    fun aCreatedNoteKeepsItsBodyLineBreaksAndTrimsOnlyTheTitle() = runTest(dispatcher) {
        val repository = FakeNoteRepository()
        val vm = editor(NoteEditorTarget.New, repository)
        vm.fill(title = "  تذكرة الفاتورة  ", body = "الرف الأول\n  الدرج الثاني\n")

        vm.onIntent(NoteEditorIntent.Save)
        advanceUntilIdle()

        val input = repository.createdInputs.single()
        assertEquals("تذكرة الفاتورة", input.title)
        assertEquals("الرف الأول\n  الدرج الثاني\n", input.body)
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun aNoteMentioningAnAmountIsStoredAsTextOnly() = runTest(dispatcher) {
        val repository = FakeNoteRepository()
        val vm = editor(NoteEditorTarget.New, repository)
        vm.fill(title = "سلفة", body = "أقرضته EGP5,000 لا تُضاف للأصول")

        vm.onIntent(NoteEditorIntent.Save)
        advanceUntilIdle()

        assertEquals("أقرضته EGP5,000 لا تُضاف للأصول", repository.createdInputs.single().body)
    }

    @Test
    fun aDuplicateSaveWhileWritingIsIgnored() = runTest(dispatcher) {
        val repository = FakeNoteRepository()
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate
        val vm = editor(NoteEditorTarget.New, repository)
        vm.fill(title = "سلفة", body = "نص")

        vm.onIntent(NoteEditorIntent.Save)
        advanceUntilIdle()
        assertEquals(JournalBusy.Saving, vm.state.value.busy)
        vm.onIntent(NoteEditorIntent.Save)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.createdInputs.size)
    }

    @Test
    fun aFailedSaveKeepsBothFieldsAndRetryCompletes() = runTest(dispatcher) {
        val repository = FakeNoteRepository()
        repository.writeFailure = StorageError.DiskFull
        val vm = editor(NoteEditorTarget.New, repository)
        vm.fill(title = "سلفة", body = "نص يبقى هنا")

        vm.onIntent(NoteEditorIntent.Save)
        advanceUntilIdle()
        assertEquals("نص يبقى هنا", vm.state.value.draft.body)
        assertEquals(StorageError.DiskFull, assertNotNull(vm.state.value.failure).error)

        repository.writeFailure = null
        vm.onIntent(NoteEditorIntent.Save)
        advanceUntilIdle()
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun editingFollowsCleanEmissionsAndConflictsWhenDirty() = runTest(dispatcher) {
        val repository = FakeNoteRepository(listOf(note("n1", "سلفة", "نص")))
        val vm = editor(NoteEditorTarget.Existing("n1"), repository)
        assertEquals("نص", vm.state.value.draft.body)

        vm.onIntent(NoteEditorIntent.BodyChanged("تعديل محلي"))
        repository.externalUpdate("n1", "سلفة", "تعديل آخر")
        advanceUntilIdle()
        assertEquals("تعديل محلي", vm.state.value.draft.body)
        assertTrue(vm.state.value.conflict)

        vm.onIntent(NoteEditorIntent.RequestReload)
        vm.onIntent(NoteEditorIntent.ConfirmReload)
        advanceUntilIdle()
        assertEquals("تعديل آخر", vm.state.value.draft.body)
        assertFalse(vm.state.value.conflict)
    }

    @Test
    fun deleteAndDiscardFollowTheSameConfirmedPaths() = runTest(dispatcher) {
        val repository = FakeNoteRepository(listOf(note("n1", "سلفة", "نص")))
        val vm = editor(NoteEditorTarget.Existing("n1"), repository)
        vm.onIntent(NoteEditorIntent.BodyChanged("تغيير"))
        var proceeded: Boolean? = null
        vm.onLeaveRequested { proceeded = it }
        assertEquals(NoteConfirmation.Discard, vm.state.value.confirmation)
        vm.onIntent(NoteEditorIntent.ConfirmDiscard)
        assertEquals(true, proceeded)
        assertEquals("نص", vm.state.value.draft.body)

        vm.onIntent(NoteEditorIntent.RequestDelete)
        vm.onIntent(NoteEditorIntent.ConfirmDelete)
        advanceUntilIdle()
        assertEquals(listOf(1L), repository.deleteRevisions)
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun aMissingNoteIsAMissingRecordAndALockClearsTheDraft() = runTest(dispatcher) {
        val repository = FakeNoteRepository()
        val missing = editor(NoteEditorTarget.Existing("nope"), repository)
        advanceUntilIdle()
        assertEquals(JournalPhase.Missing, missing.state.value.phase)

        val fresh = editor(NoteEditorTarget.New, repository)
        fresh.fill(title = "سلفة", body = "نص سري")
        repository.setLocked(true)
        advanceUntilIdle()
        assertEquals(JournalPhase.Locked, fresh.state.value.phase)
        assertEquals("", fresh.state.value.draft.body)
        assertNull(fresh.state.value.confirmation)
    }

    @Test
    fun aConcealedNoteEditorAcceptsNoWritesUntilRevealed() = runTest(dispatcher) {
        val repository = FakeNoteRepository()
        val visibility = ValuesVisibility()
        val vm = editor(NoteEditorTarget.New, repository, visibility)
        vm.fill(title = "سلفة", body = "نص")
        visibility.conceal()
        advanceUntilIdle()

        vm.onIntent(NoteEditorIntent.Save)
        advanceUntilIdle()
        assertTrue(repository.createdInputs.isEmpty())
        assertTrue(vm.state.value.concealed)
    }
}
