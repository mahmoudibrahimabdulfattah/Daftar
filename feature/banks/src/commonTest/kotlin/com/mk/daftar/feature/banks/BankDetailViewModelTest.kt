package com.mk.daftar.feature.banks

import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveBankDetailUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.navigation.AccountAddRoute
import com.mk.daftar.core.navigation.AccountEditRoute
import com.mk.daftar.core.navigation.BankEditRoute
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
class BankDetailViewModelTest {

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
        val model: BankDetailViewModel,
    )

    private fun rig(bankId: String = "bank-misr"): Rig {
        val repository = FakeBankRepository()
        val session = FakeSession()
        val prices = FakePrices()
        val visibility = ValuesVisibility()
        val model = BankDetailViewModel(
            bankId = bankId,
            observeDetail = ObserveBankDetailUseCase(repository),
            observeQuotes = ObserveSelectedQuotesUseCase(session, prices, FixedClock),
            visibility = visibility,
        )
        return Rig(repository, session, prices, visibility, model)
    }

    private fun Rig.open(snapshot: QuoteSnapshot = usdSnapshot(), generation: Long = 1) {
        session.set(VaultSessionState.Open(1))
        prices.quotes.value = VaultRead.Ready(generation, snapshot)
        settle()
    }

    private fun usdSnapshot(): QuoteSnapshot = QuoteSnapshot(remote = listOf(remoteUsd("48.50")), manual = emptyList())

    private fun collectEffects(rig: Rig): MutableList<BankEffect> {
        val effects = mutableListOf<BankEffect>()
        CoroutineScope(dispatcher).launch { rig.model.effects.toList(effects) }
        settle()
        return effects
    }

    @Test
    fun theDetailIsOneCoherentReadOfTheBankAndItsAccounts() {
        val rig = rig()
        seedApprovedFixture(rig.repository)
        rig.open()

        val state = rig.model.state.value
        assertEquals(BankPhase.Ready, state.phase)
        assertEquals("Bank Misr", state.name)
        assertEquals("163500", requireNotNull(state.total).amount.toPlainString())
        val usd = state.accounts.first { it.currency == Currency.USD }
        assertEquals("3,000.00", usd.balanceText)
        assertEquals("145,500.00", usd.egpText)
        assertTrue(usd.valued)
        val egp = state.accounts.first { it.currency == Currency.EGP }
        assertNull(egp.egpText, "an EGP account carries no conversion")
    }

    @Test
    fun aMissingBankIsAMissingRecordNotAnEmptyBank() {
        val rig = rig(bankId = "gone")
        seedApprovedFixture(rig.repository)
        rig.open()

        assertEquals(BankPhase.Missing, rig.model.state.value.phase)
    }

    @Test
    fun aBankWithoutAccountsIsEmptyAndAddingAnAccountOpensItsForm() {
        val rig = rig(bankId = "cib")
        rig.repository.seed(bank("cib", "CIB"))
        rig.open()
        val effects = collectEffects(rig)

        assertEquals(BankPhase.Empty, rig.model.state.value.phase)
        rig.model.onIntent(BankDetailIntent.AddAccount)
        settle()
        assertEquals(listOf<BankEffect>(BankEffect.Navigate(AccountAddRoute("cib"))), effects)
    }

    @Test
    fun aUsdAccountWithoutARateIsDashedAndMakesTheTotalIncomplete() {
        val rig = rig()
        seedApprovedFixture(rig.repository)
        rig.open(QuoteSnapshot(remote = emptyList(), manual = emptyList()))

        val state = rig.model.state.value
        val usd = state.accounts.first { it.currency == Currency.USD }
        assertFalse(usd.valued)
        assertNull(usd.egpText)
        assertFalse(requireNotNull(state.total).complete)
        assertEquals("18000", requireNotNull(state.total).amount.toPlainString())
    }

    @Test
    fun aQuoteFromAnotherGenerationNeverMixesWithTheBankRead() {
        val rig = rig()
        seedApprovedFixture(rig.repository)
        rig.open(generation = 2)

        assertEquals(BankPhase.Loading, rig.model.state.value.phase)
    }

    @Test
    fun openingAnAccountWhileValuesAreHiddenWaitsForAnExplicitReveal() {
        val rig = rig()
        seedApprovedFixture(rig.repository)
        rig.open()
        val effects = collectEffects(rig)
        rig.model.onIntent(BankDetailIntent.ToggleValues)
        settle()

        rig.model.onIntent(BankDetailIntent.OpenAccount("a-usd"))
        settle()
        assertTrue(effects.isEmpty(), "nothing opens implicitly while hidden")
        assertIs<AccountEditRoute>(rig.model.state.value.pendingRoute)

        rig.model.onIntent(BankDetailIntent.RevealAndOpen)
        settle()
        assertFalse(rig.visibility.isHidden())
        assertEquals(listOf<BankEffect>(BankEffect.Navigate(AccountEditRoute("a-usd"))), effects)
        assertNull(rig.model.state.value.pendingRoute)
    }

    @Test
    fun cancellingTheRevealKeepsValuesHiddenAndOpensNothing() {
        val rig = rig()
        seedApprovedFixture(rig.repository)
        rig.open()
        val effects = collectEffects(rig)
        rig.model.onIntent(BankDetailIntent.ToggleValues)
        rig.model.onIntent(BankDetailIntent.AddAccount)
        settle()

        rig.model.onIntent(BankDetailIntent.CancelReveal)
        settle()

        assertNull(rig.model.state.value.pendingRoute)
        assertTrue(rig.model.state.value.hidden)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun aLockDropsAPendingOpenAndShowsNothing() {
        val rig = rig()
        seedApprovedFixture(rig.repository)
        rig.open()
        rig.model.onIntent(BankDetailIntent.ToggleValues)
        rig.model.onIntent(BankDetailIntent.AddAccount)
        settle()

        rig.session.set(VaultSessionState.Locked)
        settle()

        assertEquals(BankPhase.Locked, rig.model.state.value.phase)
        assertNull(rig.model.state.value.pendingRoute)
    }

    @Test
    fun editingTheBankOpensItsNameFormWithoutARevealBecauseNamesAreNotSensitive() {
        val rig = rig()
        seedApprovedFixture(rig.repository)
        rig.open()
        val effects = collectEffects(rig)
        rig.model.onIntent(BankDetailIntent.ToggleValues)

        rig.model.onIntent(BankDetailIntent.EditBank)
        settle()

        assertEquals(listOf<BankEffect>(BankEffect.Navigate(BankEditRoute("bank-misr"))), effects)
        assertNotNull(rig.model.state.value.name.takeIf { it.isNotEmpty() })
    }
}
