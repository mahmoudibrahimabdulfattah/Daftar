@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.cash

import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.usecase.ObserveCashBalancesUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.domain.valuation.QuoteWarning
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.navigation.CashAddRoute
import com.mk.daftar.core.navigation.CashEditRoute
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.cash.testing.EmptyBanks
import com.mk.daftar.feature.cash.testing.EmptyGold
import com.mk.daftar.feature.cash.testing.FakeCashRepository
import com.mk.daftar.feature.cash.testing.FakePrices
import com.mk.daftar.feature.cash.testing.FakeSession
import com.mk.daftar.feature.cash.testing.FixedClock
import com.mk.daftar.feature.cash.testing.balance
import com.mk.daftar.feature.cash.testing.egp
import com.mk.daftar.feature.cash.testing.pinnedManualUsd
import com.mk.daftar.feature.cash.testing.quoteSnapshot
import com.mk.daftar.feature.cash.testing.remoteUsd
import com.mk.daftar.feature.cash.testing.usd
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class CashListViewModelTest {

    /**
     * Main has its own scheduler, so the portfolio's endless clock ticks never block the test. The tests settle Main
     * explicitly with [settle], as the home tests do.
     */
    private val dispatcher = StandardTestDispatcher(TestCoroutineScheduler())
    private val scope = CoroutineScope(dispatcher)

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
        val session: FakeSession,
        val cash: FakeCashRepository,
        val prices: FakePrices,
        val visibility: ValuesVisibility,
        val model: CashListViewModel,
    )

    private fun rig(balances: List<CashBalance>, snapshot: QuoteSnapshot = quoteSnapshot(remoteUsd("48.50"))): Rig {
        val session = FakeSession()
        // The real repository reports Locked while no session is open; the fake follows the same rule.
        val cash = FakeCashRepository(balances).apply { setLocked(true) }
        val prices = FakePrices(snapshot)
        val visibility = ValuesVisibility()
        val model = CashListViewModel(
            observeBalances = ObserveCashBalancesUseCase(cash),
            observePortfolio = ObservePortfolioValuationUseCase(
                session = session,
                gold = EmptyGold(),
                banks = EmptyBanks(),
                cash = cash,
                investments = NoInvestments(cash.observeAll()),
                prices = prices,
                clock = FixedClock(),
            ),
            visibility = visibility,
        )
        return Rig(session, cash, prices, visibility, model)
    }

    /** Opens the session and lets the observations settle. */
    private fun Rig.open() {
        cash.setLocked(false)
        session.set(VaultSessionState.Open(1))
        settle()
    }

    /** Locks the session, as the vault does: the session and the repository lock together. */
    private fun Rig.lock() {
        cash.setLocked(true)
        session.set(VaultSessionState.Locked)
        settle()
    }

    private fun Rig.collectEffects(): MutableList<CashEffect> {
        val effects = mutableListOf<CashEffect>()
        scope.launch { model.effects.collect { effects += it } }
        settle()
        return effects
    }

    @Test
    fun theApprovedFixtureTotalsHomeCashAndTheThundrWalletAtNineThousandNineHundred() {
        val rig = rig(
            listOf(
                balance("home", "Cash at home", CashKind.CASH, egp("2400")),
                balance("thundr", "Thundr wallet", CashKind.WALLET, egp("7500")),
            ),
        )
        rig.open()

        val state = rig.model.state.value
        assertEquals(CashPhase.Ready, state.phase)
        assertEquals(2, state.rows.size)
        assertEquals(Decimal.parseCanonical("9900"), state.totalEgp)
        assertEquals(Decimal.parseCanonical("9900"), state.availableEgp)
        assertFalse(state.incomplete)
    }

    @Test
    fun aUsdBalanceWithoutARateKeepsItsStoredAmountAndLeavesTheTotalUnstated() {
        val rig = rig(
            listOf(
                balance("home", "Cash at home", CashKind.CASH, egp("2400")),
                balance("usd", "USD savings", CashKind.CASH, usd("3000")),
            ),
            snapshot = quoteSnapshot(),
        )
        rig.open()

        val state = rig.model.state.value
        assertEquals(CashPhase.Ready, state.phase)
        assertNull(state.totalEgp, "a total would hide the missing rate")
        assertEquals(Decimal.parseCanonical("2400"), state.availableEgp, "the subtotal counts only valued balances")
        assertTrue(state.incomplete)
        val usdRow = state.rows.single { it.id == CashBalanceId("usd") }
        assertEquals(Decimal.parseCanonical("3000"), usdRow.amount)
        assertEquals(Currency.USD, usdRow.currency)
        assertNull(usdRow.egp, "the EGP equivalent is unknown without a rate")
    }

    @Test
    fun aUsdBalanceWithARateShowsItsEgpEquivalentAndCountsInTheTotal() {
        val rig = rig(
            listOf(
                balance("home", "Cash at home", CashKind.CASH, egp("2400")),
                balance("usd", "USD savings", CashKind.CASH, usd("3000")),
            ),
        )
        rig.open()

        val state = rig.model.state.value
        assertEquals(Decimal.parseCanonical("145500"), state.rows.single { it.id == CashBalanceId("usd") }.egp)
        assertEquals(Decimal.parseCanonical("147900"), state.totalEgp)
        assertFalse(state.incomplete)
    }

    @Test
    fun aZeroUsdBalanceIsWorthZeroWithoutAnyRate() {
        val rig = rig(
            listOf(balance("usd", "Empty USD", CashKind.CASH, usd("0"))),
            snapshot = quoteSnapshot(),
        )
        rig.open()

        val state = rig.model.state.value
        assertEquals(Decimal.ZERO, state.totalEgp)
        assertEquals(Decimal.ZERO, state.rows.single().egp)
    }

    @Test
    fun aListWithNoBalancesIsEmptyWithoutATotal() {
        val rig = rig(emptyList())
        rig.open()

        val state = rig.model.state.value
        assertEquals(CashPhase.Empty, state.phase)
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun nothingShowsBeforeTheSessionOpensAndEverythingClearsOnLock() {
        val rig = rig(listOf(balance("home", "Cash at home", CashKind.CASH, egp("2400"))))
        settle()
        assertEquals(CashPhase.Locked, rig.model.state.value.phase, "no session is open yet")
        assertTrue(rig.model.state.value.rows.isEmpty())

        rig.open()
        assertEquals(CashPhase.Ready, rig.model.state.value.phase)

        rig.lock()
        assertEquals(CashPhase.Locked, rig.model.state.value.phase)
        assertTrue(rig.model.state.value.rows.isEmpty())
    }

    @Test
    fun aManuallyPinnedRateCarriesItsWarningToTheBalance() {
        val rig = rig(
            listOf(balance("usd", "USD savings", CashKind.CASH, usd("100"))),
            snapshot = quoteSnapshot(manual = listOf(pinnedManualUsd("50"))),
        )
        rig.open()

        val row = rig.model.state.value.rows.single()
        assertEquals(Decimal.parseCanonical("5000"), row.egp)
        assertTrue(QuoteWarning.PINNED_MANUAL in row.warnings)
    }

    @Test
    fun hidingValuesShowsNoAmountsAndAnEditorWaitsForAnExplicitReveal() {
        val rig = rig(listOf(balance("home", "Cash at home", CashKind.CASH, egp("2400"))))
        rig.open()
        val effects = rig.collectEffects()

        rig.model.onIntent(CashListIntent.ToggleHidden)
        settle()
        assertTrue(rig.model.state.value.hidden)

        rig.model.onIntent(CashListIntent.OpenBalance(CashBalanceId("home")))
        settle()
        assertTrue(effects.isEmpty(), "a hidden record is not opened implicitly")
        assertEquals(CashEditRoute("home"), rig.model.state.value.pendingRoute)

        rig.model.onIntent(CashListIntent.RevealAndOpen)
        settle()
        assertEquals(listOf<CashEffect>(CashEffect.Navigate(CashEditRoute("home"))), effects)
        assertFalse(rig.visibility.isHidden(), "the reveal is explicit and shared")
        assertNull(rig.model.state.value.pendingRoute)
    }

    @Test
    fun cancellingTheRevealDropsTheRequestAndOpensNothing() {
        val rig = rig(listOf(balance("home", "Cash at home", CashKind.CASH, egp("2400"))))
        rig.open()
        val effects = rig.collectEffects()
        rig.model.onIntent(CashListIntent.ToggleHidden)
        rig.model.onIntent(CashListIntent.OpenAdd)
        settle()

        rig.model.onIntent(CashListIntent.CancelReveal)
        settle()

        assertNull(rig.model.state.value.pendingRoute)
        assertTrue(effects.isEmpty())
        assertTrue(rig.visibility.isHidden(), "cancelling keeps the values hidden")
    }

    @Test
    fun addingABalanceOpensTheAddRouteWhenValuesAreShown() {
        val rig = rig(emptyList())
        rig.open()
        val effects = rig.collectEffects()

        rig.model.onIntent(CashListIntent.OpenAdd)
        settle()

        assertEquals(listOf<CashEffect>(CashEffect.Navigate(CashAddRoute)), effects)
    }
}
