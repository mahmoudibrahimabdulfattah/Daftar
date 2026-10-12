package com.mk.daftar.feature.journal.zakat

import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.ObserveZakatPaymentsUseCase
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.navigation.ZakatAddRoute
import com.mk.daftar.core.navigation.ZakatEditRoute
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.conceal
import com.mk.daftar.feature.journal.isHidden
import com.mk.daftar.feature.journal.testing.FakeZakatRepository
import com.mk.daftar.feature.journal.testing.payment
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ZakatListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.list(
        repository: FakeZakatRepository,
        visibility: ValuesVisibility = ValuesVisibility(),
    ): Pair<ZakatListViewModel, ValuesVisibility> {
        val viewModel = ZakatListViewModel(ObserveZakatPaymentsUseCase(repository), visibility)
        advanceUntilIdle()
        return viewModel to visibility
    }

    @Test
    fun theFixtureSumsToFifteenThousandForTwentyTwentySix() = runTest(dispatcher) {
        val repository = FakeZakatRepository(
            listOf(
                payment("a", LocalDate(2026, 3, 20), "10000", "دفعة رمضان"),
                payment("b", LocalDate(2026, 1, 15), "5000", "دفعة مقدّمة"),
            ),
        )
        val (vm, _) = list(repository)

        val years = vm.state.value.years
        assertEquals(listOf(2026), years.map { it.year })
        assertEquals("15000", years.single().total.toPlainString())
        assertEquals("15,000.00", (years.single().total).moneyText())
        assertEquals(JournalPhase.Ready, vm.state.value.phase)
    }

    @Test
    fun yearBoundariesAndLeapDaysGroupByCalendarYear() = runTest(dispatcher) {
        val repository = FakeZakatRepository(
            listOf(
                payment("a", LocalDate(2026, 1, 1), "1"),
                payment("b", LocalDate(2025, 12, 31), "2"),
                payment("c", LocalDate(2024, 2, 29), "3.25"),
                payment("d", LocalDate(2024, 12, 31), "4"),
            ),
        )
        val (vm, _) = list(repository)

        val totals = vm.state.value.years.associate { it.year to it.total }
        assertEquals(Decimal.parseCanonical("1"), totals.getValue(2026))
        assertEquals(Decimal.parseCanonical("2"), totals.getValue(2025))
        assertEquals(Decimal.parseCanonical("7.25"), totals.getValue(2024))
        assertEquals(listOf(2026, 2025, 2024), vm.state.value.years.map { it.year })
    }

    @Test
    fun emptyLoadingLockedAndFailedAreDistinctPhases() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        val (vm, _) = list(repository)
        assertEquals(JournalPhase.Empty, vm.state.value.phase)

        repository.setLocked(true)
        advanceUntilIdle()
        assertEquals(JournalPhase.Locked, vm.state.value.phase)
        assertTrue(vm.state.value.years.isEmpty())
    }

    @Test
    fun aFailedReadIsReportedAsItsOwnPhase() = runTest(dispatcher) {
        val failing = object : com.mk.daftar.core.domain.repository.ZakatRepository by FakeZakatRepository() {
            override fun observeAll() = kotlinx.coroutines.flow.flowOf(
                com.mk.daftar.core.domain.storage.VaultRead.Failed(StorageError.CorruptData),
            )
        }
        val vm = ZakatListViewModel(ObserveZakatPaymentsUseCase(failing), ValuesVisibility())
        advanceUntilIdle()

        assertEquals(JournalPhase.Failed(StorageError.CorruptData), vm.state.value.phase)
    }

    @Test
    fun paymentsKeepTheRepositoryOrderAfterAnEdit() = runTest(dispatcher) {
        val repository = FakeZakatRepository(
            listOf(
                payment("a", LocalDate(2026, 3, 20), "10000"),
                payment("b", LocalDate(2026, 2, 10), "300"),
            ),
        )
        val (vm, _) = list(repository)
        assertEquals(listOf("a", "b"), vm.state.value.years.single().payments.map { it.id.value })

        repository.externalUpdate("a", LocalDate(2026, 2, 1), "10000")
        advanceUntilIdle()

        assertEquals(listOf("b", "a"), vm.state.value.years.single().payments.map { it.id.value })
    }

    @Test
    fun openingAnEditorWhileValuesAreHiddenWaitsForAnExplicitReveal() = runTest(dispatcher) {
        val repository = FakeZakatRepository(listOf(payment("a", LocalDate(2026, 3, 20), "10000")))
        val (vm, visibility) = list(repository)
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { vm.effects.collect { effects += it } }
        visibility.conceal()
        advanceUntilIdle()

        vm.onIntent(ZakatListIntent.OpenPayment(com.mk.daftar.core.model.ZakatPaymentId("a")))
        advanceUntilIdle()
        assertTrue(effects.isEmpty(), "nothing opens before the reveal")
        assertEquals(ZakatEditRoute("a"), vm.state.value.pendingRoute)
        assertTrue(vm.state.value.hidden)

        vm.onIntent(ZakatListIntent.RevealAndOpen)
        advanceUntilIdle()
        assertEquals(listOf<JournalEffect>(JournalEffect.Navigate(ZakatEditRoute("a"))), effects)
        assertFalse(visibility.isHidden())
        assertNull(vm.state.value.pendingRoute)
        collector.cancel()
    }

    @Test
    fun cancellingTheRevealOpensNothingAndKeepsValuesHidden() = runTest(dispatcher) {
        val (vm, visibility) = list(FakeZakatRepository())
        visibility.conceal()
        advanceUntilIdle()

        vm.onIntent(ZakatListIntent.OpenAdd)
        vm.onIntent(ZakatListIntent.CancelReveal)

        assertNull(vm.state.value.pendingRoute)
        assertTrue(visibility.isHidden())
    }

    @Test
    fun whileVisibleAddOpensImmediately() = runTest(dispatcher) {
        val (vm, _) = list(FakeZakatRepository())
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { vm.effects.collect { effects += it } }

        vm.onIntent(ZakatListIntent.OpenAdd)
        advanceUntilIdle()

        assertEquals(listOf<JournalEffect>(JournalEffect.Navigate(ZakatAddRoute)), effects)
        collector.cancel()
    }

    @Test
    fun aLockClearsAPendingEditorRequest() = runTest(dispatcher) {
        val repository = FakeZakatRepository()
        val (vm, visibility) = list(repository)
        visibility.conceal()
        advanceUntilIdle()
        vm.onIntent(ZakatListIntent.OpenAdd)

        repository.setLocked(true)
        advanceUntilIdle()

        assertNull(vm.state.value.pendingRoute)
    }
}
