package com.mk.daftar.feature.investments

import com.mk.daftar.core.domain.usecase.DeleteInvestmentUseCase
import com.mk.daftar.core.domain.usecase.ObserveInvestmentUseCase
import com.mk.daftar.core.domain.usecase.ObserveInvestmentsUseCase
import com.mk.daftar.core.model.InvestmentAccountId
import com.mk.daftar.core.navigation.ConvertCashRoute
import com.mk.daftar.core.navigation.InvestmentAddRoute
import com.mk.daftar.core.navigation.InvestmentDetailRoute
import com.mk.daftar.core.navigation.InvestmentEditRoute
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.investments.testing.FakeInvestmentRepository
import com.mk.daftar.feature.investments.testing.NOW
import com.mk.daftar.feature.investments.testing.TestClock
import com.mk.daftar.feature.investments.testing.account
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
class InvestmentListAndDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.settle() = advanceUntilIdle()

    private fun listVm(repository: FakeInvestmentRepository, visibility: ValuesVisibility, clock: TestClock) =
        InvestmentListViewModel(ObserveInvestmentsUseCase(repository), visibility, clock)

    /** A clock that moves with the test's virtual time, so review thresholds are reached as they would be in use. */
    private fun movingClock() = TestClock(scheduler = dispatcher.scheduler)

    @Test
    fun anEmptyRepositoryShowsAnEmptyListAndNoTotal() = runTest(dispatcher) {
        val vm = listVm(FakeInvestmentRepository(), ValuesVisibility(), movingClock())
        settle()
        assertEquals(InvestmentPhase.Empty, vm.state.value.phase)
        assertEquals(0, vm.state.value.rows.size)
    }

    @Test
    fun opensAreBlockedWhileHiddenUntilAnExplicitReveal() = runTest(dispatcher) {
        val visibility = ValuesVisibility()
        val vm = listVm(FakeInvestmentRepository(listOf(account("a", "5"))), visibility, movingClock())
        settle()
        val effects = mutableListOf<InvestmentEffect>()
        CoroutineScope(dispatcher).launch { vm.effects.collect { effects += it } }
        settle()

        vm.onIntent(InvestmentListIntent.ToggleHidden)
        settle()
        assertTrue(vm.state.value.hidden)
        vm.onIntent(InvestmentListIntent.OpenAccount(InvestmentAccountId("a")))
        settle()
        assertTrue(effects.isEmpty(), "a hidden account is not opened implicitly")
        assertEquals(InvestmentDetailRoute("a"), vm.state.value.pendingRoute)

        vm.onIntent(InvestmentListIntent.RevealAndOpen)
        settle()
        assertEquals(listOf<InvestmentEffect>(InvestmentEffect.Navigate(InvestmentDetailRoute("a"))), effects)
        assertTrue(!vm.state.value.hidden)
    }

    @Test
    fun addAndConvertOpenTheirRoutesWhenValuesAreVisible() = runTest(dispatcher) {
        val vm = listVm(FakeInvestmentRepository(), ValuesVisibility(), movingClock())
        settle()
        val effects = mutableListOf<InvestmentEffect>()
        CoroutineScope(dispatcher).launch { vm.effects.collect { effects += it } }
        settle()
        vm.onIntent(InvestmentListIntent.OpenAdd)
        vm.onIntent(InvestmentListIntent.OpenConvert)
        settle()
        assertEquals(
            listOf<InvestmentEffect>(
                InvestmentEffect.Navigate(InvestmentAddRoute),
                InvestmentEffect.Navigate(ConvertCashRoute()),
            ),
            effects,
        )
    }

    @Test
    fun theDetailDeletesOnlyAfterConfirmationAndRemovesTheAccount() = runTest(dispatcher) {
        val repository = FakeInvestmentRepository(listOf(account("a", "5")))
        val visibility = ValuesVisibility()
        val detail = InvestmentDetailViewModel(
            investmentId = "a",
            observeInvestment = ObserveInvestmentUseCase(repository),
            deleteInvestment = DeleteInvestmentUseCase(repository),
            visibility = visibility,
            clock = movingClock(),
        )
        settle()
        assertEquals(InvestmentPhase.Ready, detail.state.value.phase)
        detail.onIntent(InvestmentDetailIntent.ConfirmDelete)
        settle()
        assertTrue(repository.current.isNotEmpty(), "nothing is deleted without the confirmation")

        detail.onIntent(InvestmentDetailIntent.RequestDelete)
        detail.onIntent(InvestmentDetailIntent.ConfirmDelete)
        settle()
        assertTrue(repository.current.isEmpty())
    }

    @Test
    fun aMissingAccountIsReportedAsMissingNotRecreated() = runTest(dispatcher) {
        val repository = FakeInvestmentRepository()
        val detail = InvestmentDetailViewModel(
            investmentId = "gone",
            observeInvestment = ObserveInvestmentUseCase(repository),
            deleteInvestment = DeleteInvestmentUseCase(repository),
            visibility = ValuesVisibility(),
            clock = movingClock(),
        )
        settle()
        assertEquals(InvestmentPhase.Missing, detail.state.value.phase)
    }

    @Test
    fun editingFromTheDetailOpensTheEditorWithTheSameIdentifier() = runTest(dispatcher) {
        val repository = FakeInvestmentRepository(listOf(account("a", "5")))
        val detail = InvestmentDetailViewModel(
            investmentId = "a",
            observeInvestment = ObserveInvestmentUseCase(repository),
            deleteInvestment = DeleteInvestmentUseCase(repository),
            visibility = ValuesVisibility(),
            clock = movingClock(),
        )
        settle()
        val effects = mutableListOf<InvestmentEffect>()
        CoroutineScope(dispatcher).launch { detail.effects.collect { effects += it } }
        settle()
        detail.onIntent(InvestmentDetailIntent.OpenEdit)
        settle()
        assertEquals(listOf<InvestmentEffect>(InvestmentEffect.Navigate(InvestmentEditRoute("a"))), effects)
    }
}
