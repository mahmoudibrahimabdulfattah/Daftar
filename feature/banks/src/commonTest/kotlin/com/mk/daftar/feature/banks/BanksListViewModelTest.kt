package com.mk.daftar.feature.banks

import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveBankAccountsUseCase
import com.mk.daftar.core.domain.usecase.ObserveBanksUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.navigation.BankAddRoute
import com.mk.daftar.core.navigation.BankDetailRoute
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class BanksListViewModelTest {

    /** Main has its own scheduler: the clock ticks of the quote observation never make a test wait for them. */
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

    private class Rig(
        val repository: FakeBankRepository,
        val session: FakeSession,
        val prices: FakePrices,
        val visibility: ValuesVisibility,
        val model: BanksListViewModel,
    )

    private fun rig(): Rig {
        val repository = FakeBankRepository()
        val session = FakeSession()
        val prices = FakePrices()
        val visibility = ValuesVisibility()
        val model = BanksListViewModel(
            observeBanks = ObserveBanksUseCase(repository),
            observeAccounts = ObserveBankAccountsUseCase(repository),
            observeQuotes = ObserveSelectedQuotesUseCase(session, prices, FixedClock),
            visibility = visibility,
        )
        return Rig(repository, session, prices, visibility, model)
    }

    /** Opens the session and publishes the quotes of its generation. */
    private fun Rig.open(snapshot: QuoteSnapshot = usdSnapshot()) {
        session.set(VaultSessionState.Open(1))
        prices.quotes.value = VaultRead.Ready(1, snapshot)
        settle()
    }

    private fun usdSnapshot(): QuoteSnapshot = QuoteSnapshot(remote = listOf(remoteUsd("48.50")), manual = emptyList())

    @Test
    fun theApprovedFixtureTotalsThreeHundredEightyEightThousandFiveHundredEgp() {
        val rig = rig()
        seedApprovedFixture(rig.repository)
        rig.open()

        val state = rig.model.state.value
        assertEquals(BankPhase.Ready, state.phase)
        val total = requireNotNull(state.total)
        assertTrue(total.complete)
        assertEquals("388500", total.amount.toPlainString())
        assertEquals(listOf("163500", "180000", "45000"), state.rows.map { it.valueEgp!!.toPlainString() })
        assertEquals(listOf(2, 1, 1), state.rows.map { it.accountCount })
        assertEquals(listOf(Currency.USD, Currency.EGP), state.rows[0].currencies)
    }

    @Test
    fun noBanksIsTheEmptyStateNotASpinner() {
        val rig = rig()
        rig.open()

        assertEquals(BankPhase.Empty, rig.model.state.value.phase)
        assertTrue(rig.model.state.value.rows.isEmpty())
    }

    @Test
    fun aMissingRateLeavesTheTotalIncompleteAndTheRowsDashed() {
        val rig = rig()
        seedApprovedFixture(rig.repository)
        rig.open(QuoteSnapshot(remote = emptyList(), manual = emptyList()))

        val state = rig.model.state.value
        val total = requireNotNull(state.total)
        assertFalse(total.complete, "a USD balance without a rate cannot be totalled")
        assertEquals("243000", total.amount.toPlainString(), "the labeled subtotal sums the valued accounts only")
        assertNull(state.rows.first { it.name == "Bank Misr" }.valueEgp)
        assertEquals("180000", state.rows.first { it.name == "AlAhli" }.valueEgp!!.toPlainString())
    }

    @Test
    fun aUsdZeroBalanceIsWorthZeroWithoutAnyRate() {
        val rig = rig()
        rig.repository.seed(bank("bank-misr", "Bank Misr"))
        rig.repository.seedAccount(account("a-usd", "bank-misr", "Dollar", usd("0")))
        rig.open(QuoteSnapshot(remote = emptyList(), manual = emptyList()))

        val row = rig.model.state.value.rows.single()
        assertEquals(Decimal.ZERO, row.valueEgp)
        assertTrue(requireNotNull(rig.model.state.value.total).complete)
    }

    @Test
    fun maskingHidesTheAmountsOnlyAndTheSharedStateFollows() {
        val rig = rig()
        seedApprovedFixture(rig.repository)
        rig.open()

        rig.model.onIntent(BankListIntent.ToggleValues)
        settle()

        assertTrue(rig.model.state.value.hidden)
        assertFalse(rig.visibility.visible.value, "the one privacy state is the shared one")
        assertEquals(listOf("Bank Misr", "AlAhli", "CIB"), rig.model.state.value.rows.map { it.name })
    }

    @Test
    fun aLockedSessionShowsNothing() {
        val rig = rig()
        seedApprovedFixture(rig.repository)
        rig.open()

        rig.session.set(VaultSessionState.Locked)
        settle()

        assertEquals(BankPhase.Locked, rig.model.state.value.phase)
        assertTrue(rig.model.state.value.rows.isEmpty())
    }

    @Test
    fun openingABankShowsItsDetailAndAddingABankOpensTheForm() {
        val rig = rig()
        seedApprovedFixture(rig.repository)
        rig.open()
        val effects = mutableListOf<BankEffect>()
        val collector = CoroutineScope(dispatcher).launch { rig.model.effects.toList(effects) }
        settle()

        rig.model.onIntent(BankListIntent.OpenBank("alahli"))
        rig.model.onIntent(BankListIntent.AddBank)
        settle()
        collector.cancel()

        assertEquals(
            listOf<BankEffect>(
                BankEffect.Navigate(BankDetailRoute("alahli")),
                BankEffect.Navigate(BankAddRoute),
            ),
            effects,
        )
    }
}
