package com.mk.daftar.feature.journal

import com.mk.daftar.core.domain.repository.NoteRepository
import com.mk.daftar.core.domain.repository.ZakatRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObservePersonalNotesUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatPaymentsUseCase
import com.mk.daftar.core.navigation.NotesRoute
import com.mk.daftar.core.navigation.ZakatRoute
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.journal.testing.FakeNoteRepository
import com.mk.daftar.feature.journal.testing.FakeZakatRepository
import com.mk.daftar.feature.journal.testing.TODAY
import com.mk.daftar.feature.journal.testing.note
import com.mk.daftar.feature.journal.testing.payment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class JournalOverviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun overview(
        zakat: ZakatRepository,
        notes: NoteRepository,
        visibility: ValuesVisibility = ValuesVisibility(),
    ) = JournalViewModel(ObserveZakatPaymentsUseCase(zakat), ObservePersonalNotesUseCase(notes), visibility)

    @Test
    fun theLastPaymentDateAndNoteCountComeFromTheJournal() = runTest(dispatcher) {
        val zakat = FakeZakatRepository(
            listOf(
                payment("a", LocalDate(2026, 3, 20), "10000"),
                payment("b", LocalDate(2026, 1, 15), "5000"),
            ),
        )
        val notes = FakeNoteRepository(listOf(note("n1", "قرض", "قرض ٥٬٠٠٠ جنيه"), note("n2", "فاتورة", "الرف العلوي")))
        val vm = overview(zakat, notes)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(JournalPhase.Ready, state.phase)
        assertEquals(LocalDate(2026, 3, 20), state.lastPaymentOn)
        assertEquals(2, state.noteCount)
    }

    @Test
    fun anEmptyJournalHasNoLastPaymentAndNoNotes() = runTest(dispatcher) {
        val vm = overview(FakeZakatRepository(), FakeNoteRepository())
        advanceUntilIdle()

        assertEquals(null, vm.state.value.lastPaymentOn)
        assertEquals(0, vm.state.value.noteCount)
        assertEquals(JournalPhase.Ready, vm.state.value.phase)
    }

    @Test
    fun aLockedOrFailedSourceDecidesThePhaseInThatOrder() {
        assertEquals(
            JournalPhase.Locked,
            overviewOf(VaultRead.Locked, VaultRead.Failed(StorageError.Io), hidden = false).phase,
        )
        assertEquals(
            JournalPhase.Failed(StorageError.Io),
            overviewOf(VaultRead.Loading, VaultRead.Failed(StorageError.Io), hidden = false).phase,
        )
        assertEquals(
            JournalPhase.Loading,
            overviewOf(VaultRead.Loading, VaultRead.Ready(1, emptyList()), hidden = true).phase,
        )
    }

    @Test
    fun theOverviewTogglesMaskingAndOpensEachListAsAnEffect() = runTest(dispatcher) {
        val visibility = ValuesVisibility()
        val vm = overview(FakeZakatRepository(), FakeNoteRepository(), visibility)
        advanceUntilIdle()
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { vm.effects.collect { effects += it } }

        vm.onIntent(JournalIntent.ToggleHidden)
        vm.onIntent(JournalIntent.OpenZakat)
        vm.onIntent(JournalIntent.OpenNotes)
        advanceUntilIdle()

        assertTrue(visibility.isHidden())
        assertTrue(vm.state.value.hidden)
        assertEquals(
            listOf<JournalEffect>(JournalEffect.Navigate(ZakatRoute), JournalEffect.Navigate(NotesRoute)),
            effects,
        )
        collector.cancel()
    }

    @Test
    fun overviewDerivesFromJournalValuesOnly() {
        // The overview is built from the zakat and note reads alone: there is no asset input that could change it.
        val state = overviewOf(
            VaultRead.Ready(1, listOf(com.mk.daftar.core.domain.storage.Stored(payment("a", TODAY, "1"), 1))),
            VaultRead.Ready(1, emptyList()),
            hidden = false,
        )
        assertEquals(TODAY, state.lastPaymentOn)
    }

    @Test
    fun flowsFromTheSessionArriveThroughTheOverview() = runTest(dispatcher) {
        val zakat = object : ZakatRepository by FakeZakatRepository() {
            override fun observeAll() = flowOf(VaultRead.Locked)
        }
        val vm = overview(zakat, FakeNoteRepository())
        advanceUntilIdle()
        assertEquals(JournalPhase.Locked, vm.state.value.phase)
    }
}
