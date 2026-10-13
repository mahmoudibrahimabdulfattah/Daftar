package com.mk.daftar.feature.journal.zakat

import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.ObserveZakatPaymentsUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.domain.usecase.SetZakatYearPaidUseCase
import com.mk.daftar.core.domain.zakat.ZakatYearStatus
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId
import com.mk.daftar.core.navigation.ZakatAddRoute
import com.mk.daftar.core.navigation.ZakatCalculatorRoute
import com.mk.daftar.core.navigation.ZakatDateRoute
import com.mk.daftar.core.navigation.ZakatHistoryRoute
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.testing.FakeZakatRepository
import com.mk.daftar.feature.journal.testing.FakeZakatYearRepository
import com.mk.daftar.feature.journal.testing.NOW
import com.mk.daftar.feature.journal.testing.TODAY
import com.mk.daftar.feature.journal.testing.declared
import com.mk.daftar.feature.journal.testing.egp
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

@OptIn(ExperimentalCoroutinesApi::class)
class ZakatYearViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun linked(id: String, paidOn: LocalDate, amount: String, year: Int?) = ZakatPayment.create(
        id = ZakatPaymentId(id),
        paidOn = paidOn,
        amount = egp(amount),
        note = null,
        createdAt = NOW,
        updatedAt = NOW,
        forHijriYear = year,
    )

    private fun rig(
        years: FakeZakatYearRepository,
        payments: List<ZakatPayment> = emptyList(),
        hijriYear: Int = 1447,
        day: LocalDate = TODAY,
        visibility: ValuesVisibility = ValuesVisibility(),
    ): ZakatYearViewModel = ZakatYearViewModel(
        hijriYear = hijriYear,
        observeTracking = ObserveZakatTrackingUseCase(years),
        observePayments = ObserveZakatPaymentsUseCase(FakeZakatRepository(payments)),
        observeDay = fixedDay(day),
        setPaid = SetZakatYearPaidUseCase(years),
        visibility = visibility,
    )

    @Test
    fun anUnmarkedDueYearAwaitsConfirmationWithItsDateAndNoCommittedMark() = runTest(dispatcher) {
        val vm = rig(FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447))))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(JournalPhase.Ready, state.phase)
        assertEquals(ZakatYearStatus.DUE, state.status)
        assertEquals(2026, state.gregorianYear)
        assertEquals(LocalDate(2026, 2, 18), state.date)
        assertFalse(state.checked)
        assertFalse(state.committed)
    }

    @Test
    fun aMarkCommitsOnlyThroughTheStoreAndThenShowsTheSavedMessage() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447)))
        val vm = rig(years)
        advanceUntilIdle()

        vm.onIntent(ZakatYearIntent.SetChecked(true))
        advanceUntilIdle()

        assertEquals(listOf(1447 to true), years.paidWrites)
        assertTrue(vm.state.value.committed)
        assertTrue(vm.state.value.checked)
        assertEquals(true, vm.state.value.justSaved)
    }

    @Test
    fun unmarkingAsksFirstAndKeepsTheMarkWhenCancelled() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447, paid = true)))
        val vm = rig(years)
        advanceUntilIdle()

        vm.onIntent(ZakatYearIntent.SetChecked(false))
        advanceUntilIdle()
        assertTrue(vm.state.value.confirmUnmark)
        assertTrue(years.paidWrites.isEmpty(), "nothing is written before the user confirms")

        vm.onIntent(ZakatYearIntent.DismissUnmark)
        advanceUntilIdle()
        assertTrue(vm.state.value.committed)
        assertTrue(vm.state.value.checked)
        assertTrue(years.paidWrites.isEmpty())
    }

    @Test
    fun confirmingAnUnmarkClearsTheMarkAndResumesTheReminder() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447, paid = true)))
        val vm = rig(years)
        advanceUntilIdle()

        vm.onIntent(ZakatYearIntent.SetChecked(false))
        vm.onIntent(ZakatYearIntent.ConfirmUnmark)
        advanceUntilIdle()

        assertEquals(listOf(1447 to false), years.paidWrites)
        assertFalse(vm.state.value.committed)
        assertEquals(ZakatYearStatus.DUE, vm.state.value.status)
    }

    @Test
    fun aFailedSaveKeepsTheCommittedStateAndRetriesTheSameValue() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447)))
        years.writeFailure = StorageError.Io
        val vm = rig(years)
        advanceUntilIdle()

        vm.onIntent(ZakatYearIntent.SetChecked(true))
        advanceUntilIdle()
        assertFalse(vm.state.value.committed)
        assertFalse(vm.state.value.checked, "the box returns to the committed value")
        assertTrue(vm.state.value.saveFailed)
        assertNull(vm.state.value.justSaved, "nothing reports success before the commit")

        years.writeFailure = null
        vm.onIntent(ZakatYearIntent.RetrySave)
        advanceUntilIdle()
        assertTrue(vm.state.value.committed)
        assertFalse(vm.state.value.saveFailed)
    }

    @Test
    fun paymentsAreLinkedToTheYearAndNeverChangeTheDeclaration() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447)))
        val payments = listOf(
            linked("a", LocalDate(2026, 3, 20), "10000", 1447),
            linked("b", LocalDate(2026, 1, 15), "5000", 1447),
            linked("c", LocalDate(2026, 2, 1), "700", null),
        )
        val vm = rig(years, payments)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.linked.size)
        assertEquals(0, state.linkedTotal.compareTo(Decimal.parseCanonical("15000")))
        assertEquals(listOf("c"), state.unassigned.map { it.id.value })
        assertFalse(state.committed, "a payment never marks the year paid")
    }

    @Test
    fun aYearThatIsNotTrackedIsMissing() = runTest(dispatcher) {
        val vm = rig(FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447))), hijriYear = 1400)
        advanceUntilIdle()

        assertEquals(JournalPhase.Missing, vm.state.value.phase)
    }

    @Test
    fun theNavigationEffectsCarryOnlyIdentifiers() = runTest(dispatcher) {
        val vm = rig(FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447))))
        advanceUntilIdle()
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { vm.effects.collect { effects += it } }

        vm.onIntent(ZakatYearIntent.RecordPayment)
        vm.onIntent(ZakatYearIntent.OpenHistory)
        vm.onIntent(ZakatYearIntent.OpenCalculator)
        vm.onIntent(ZakatYearIntent.OpenDate)
        advanceUntilIdle()

        assertEquals(
            listOf<JournalEffect>(
                JournalEffect.Navigate(ZakatAddRoute(forHijriYear = 1447)),
                JournalEffect.Navigate(ZakatHistoryRoute),
                JournalEffect.Navigate(ZakatCalculatorRoute),
                JournalEffect.Navigate(ZakatDateRoute(1447)),
            ),
            effects,
        )
        collector.cancel()
    }
}
