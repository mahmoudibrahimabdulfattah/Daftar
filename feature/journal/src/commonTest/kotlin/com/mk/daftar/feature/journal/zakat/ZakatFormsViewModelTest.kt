package com.mk.daftar.feature.journal.zakat

import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.AddZakatYearUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.domain.usecase.SetZakatDateAdjustmentUseCase
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.testing.FakeZakatYearRepository
import com.mk.daftar.feature.journal.testing.FixedClock
import com.mk.daftar.feature.journal.testing.NOW
import com.mk.daftar.feature.journal.testing.TODAY
import com.mk.daftar.feature.journal.testing.declared
import com.mk.daftar.feature.journal.testing.fixedDay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/** The add-year and date-adjustment forms, which validate or commit through the tracking store. */
@OptIn(ExperimentalCoroutinesApi::class)
class ZakatFormsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun addForm(years: FakeZakatYearRepository) = ZakatYearAddViewModel(
        observeTracking = ObserveZakatTrackingUseCase(years),
        observeDay = fixedDay(TODAY),
        addYear = AddZakatYearUseCase(years, FixedClock(NOW)),
    )

    @Test
    fun arabicIndicDigitsAreAcceptedAndTheGregorianRamadanYearAppears() = runTest(dispatcher) {
        val form = addForm(FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447))))
        advanceUntilIdle()

        form.onIntent(ZakatYearAddIntent.TextChanged("١٤٤٦"))
        advanceUntilIdle()

        assertEquals(1446, form.state.value.year)
        assertEquals(2025, form.state.value.gregorianYear)
        assertNull(form.state.value.shownError)
    }

    @Test
    fun aDuplicateYearIsRefusedOnlyAfterASaveAttempt() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447)))
        val form = addForm(years)
        advanceUntilIdle()

        form.onIntent(ZakatYearAddIntent.TextChanged("1447"))
        advanceUntilIdle()
        assertNull(form.state.value.shownError, "errors are not shown while typing")

        form.onIntent(ZakatYearAddIntent.Save)
        advanceUntilIdle()
        assertEquals(ZakatYearError.Duplicate, form.state.value.shownError)
        assertEquals(1, years.current.years.size)
    }

    @Test
    fun aYearOutsideTheRangeIsRefused() = runTest(dispatcher) {
        val form = addForm(FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447))))
        advanceUntilIdle()

        form.onIntent(ZakatYearAddIntent.TextChanged("1299"))
        form.onIntent(ZakatYearAddIntent.Save)
        advanceUntilIdle()

        assertEquals(ZakatYearError.OutOfRange, form.state.value.shownError)
    }

    @Test
    fun aYearBeyondTheNextRamadanYearIsRefused() = runTest(dispatcher) {
        val form = addForm(FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447))))
        advanceUntilIdle()

        form.onIntent(ZakatYearAddIntent.TextChanged("1449"))
        form.onIntent(ZakatYearAddIntent.Save)
        advanceUntilIdle()

        assertEquals(ZakatYearError.OutOfRange, form.state.value.shownError)
    }

    @Test
    fun aValidPastYearIsAddedUnmarkedAndCompletesTheForm() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447)))
        val form = addForm(years)
        advanceUntilIdle()
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { form.effects.collect { effects += it } }

        form.onIntent(ZakatYearAddIntent.TextChanged("1446"))
        form.onIntent(ZakatYearAddIntent.Save)
        advanceUntilIdle()

        val added = years.current.years.first { it.hijriYear == 1446 }
        assertFalse(added.paid)
        assertEquals(listOf<JournalEffect>(JournalEffect.Completed), effects)
        collector.cancel()
    }

    @Test
    fun aFailedAddKeepsTheTypedYearForRetry() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447)))
        years.writeFailure = StorageError.Io
        val form = addForm(years)
        advanceUntilIdle()

        form.onIntent(ZakatYearAddIntent.TextChanged("1446"))
        form.onIntent(ZakatYearAddIntent.Save)
        advanceUntilIdle()

        assertTrue(form.state.value.failed)
        assertEquals("1446", form.state.value.text)
        assertEquals(1, years.current.years.size)
    }

    private fun dateForm(years: FakeZakatYearRepository, hijriYear: Int = 1448) = ZakatDateViewModel(
        hijriYear = hijriYear,
        observeTracking = ObserveZakatTrackingUseCase(years),
        setAdjustment = SetZakatDateAdjustmentUseCase(years),
    )

    @Test
    fun theThreeConcreteOptionsAreOneDayEarlierCalculatedAndOneDayLater() = runTest(dispatcher) {
        val form = dateForm(FakeZakatYearRepository(startYear = 1448, years = listOf(declared(1448))))
        advanceUntilIdle()

        assertEquals(listOf(-1, 0, 1), form.state.value.options.map { it.overrideDays })
        assertEquals(
            listOf(LocalDate(2027, 2, 7), LocalDate(2027, 2, 8), LocalDate(2027, 2, 9)),
            form.state.value.options.map { it.date },
        )
    }

    @Test
    fun aSavedAdjustmentCommitsAtomicallyAndLeavesTheDeclarationAlone() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447, paid = true)))
        val form = dateForm(years, hijriYear = 1447)
        advanceUntilIdle()

        form.onIntent(ZakatDateIntent.Select(1))
        form.onIntent(ZakatDateIntent.Save)
        advanceUntilIdle()

        assertEquals(listOf(1447 to 1), years.adjustmentWrites)
        assertEquals(1, form.state.value.committed)
        val year = years.current.years.single()
        assertTrue(year.paid, "a date change never clears the paid mark")
        assertTrue(year.userAdjusted)
    }

    @Test
    fun restoringTheCalculatedDateSavesZeroAndClearsTheProvenance() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1448, years = listOf(declared(1448, overrideDays = -1)))
        val form = dateForm(years)
        advanceUntilIdle()
        assertEquals(-1, form.state.value.committed)

        form.onIntent(ZakatDateIntent.Restore)
        advanceUntilIdle()

        assertEquals(listOf(1448 to 0), years.adjustmentWrites)
        assertFalse(years.current.years.single().userAdjusted)
    }

    @Test
    fun aFailedSaveKeepsTheCommittedDate() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1448, years = listOf(declared(1448)))
        years.writeFailure = StorageError.Io
        val form = dateForm(years)
        advanceUntilIdle()

        form.onIntent(ZakatDateIntent.Select(1))
        form.onIntent(ZakatDateIntent.Save)
        advanceUntilIdle()

        assertTrue(form.state.value.failed)
        assertEquals(0, form.state.value.committed)
        assertEquals(0, form.state.value.selected)
    }

    @Test
    fun anUntrackedYearHasNoDateToAdjust() = runTest(dispatcher) {
        val form = dateForm(FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447))), hijriYear = 1400)
        advanceUntilIdle()

        assertEquals(JournalPhase.Missing, form.state.value.phase)
    }
}
