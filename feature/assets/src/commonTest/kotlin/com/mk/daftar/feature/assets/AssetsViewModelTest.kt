@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.assets

import com.mk.daftar.core.domain.refresh.PriceRefreshController
import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.usecase.ObserveBanksUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.domain.valuation.AssetCategory
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.navigation.BanksRoute
import com.mk.daftar.core.navigation.CashRoute
import com.mk.daftar.core.navigation.GoldRoute
import com.mk.daftar.core.navigation.InvestmentsRoute
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class AssetsViewModelTest {

    /** Main has its own scheduler: the view model's clock ticks never make a test wait for them. */
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
        val portfolio: FakePortfolio,
        val visibility: ValuesVisibility,
        val model: AssetsOverviewViewModel,
    )

    private fun rig(accounts: List<InvestmentAccount> = emptyList()): Rig {
        val portfolio = FakePortfolio()
        val visibility = ValuesVisibility()
        val model = AssetsOverviewViewModel(
            observePortfolio = ObservePortfolioValuationUseCase(
                session = portfolio.session as VaultSession,
                gold = portfolio.gold,
                banks = portfolio.banks,
                cash = portfolio.cash,
                investments = NoInvestments(portfolio.cash.observeAll(), accounts),
                prices = portfolio.prices,
                clock = FixedClock,
            ),
            observeBanks = ObserveBanksUseCase(portfolio.banks),
            visibility = visibility,
            refresh = IdleRefresh,
        )
        return Rig(portfolio, visibility, model)
    }

    @Test
    fun readyFiguresAppearOnceTheSessionOpens() {
        val rig = rig()
        rig.portfolio.load(1)
        assertIs<AssetsLoad.Loading>(rig.model.state.value.load)
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()
        val ready = assertIs<AssetsLoad.Ready>(rig.model.state.value.load).figures
        assertEquals("677812.74", ready.gold.total!!.toPlainString())
        assertEquals(3, ready.bankCount)
        assertEquals(2, ready.cash.count)
        assertFalse(ready.incomplete)
    }

    @Test
    fun investmentsAreTheFourthCategoryAndCountAccountsNotHoldings() {
        val account = InvestmentAccount.create(
            id = com.mk.daftar.core.model.InvestmentAccountId("i1"),
            providerId = null,
            name = "Broker",
            amount = egpAmount("157000"),
            note = null,
            valueAsOf = FIXTURE_NOW,
            convertedFromCashId = null,
            createdAt = FIXTURE_NOW,
            updatedAt = FIXTURE_NOW,
        )
        val rig = rig(listOf(account))
        rig.portfolio.load(1)
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()
        val ready = assertIs<AssetsLoad.Ready>(rig.model.state.value.load).figures
        assertEquals(1, ready.investments.count)
        assertEquals("157000", ready.investments.total!!.toPlainString())
    }

    @Test
    fun togglingMaskingKeepsTheFiguresAndSharesTheSessionChoice() {
        val rig = rig()
        rig.portfolio.load(1)
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()

        rig.model.onIntent(AssetsIntent.ToggleValues)
        settle()
        assertTrue(rig.model.state.value.valuesHidden)
        assertFalse(rig.visibility.visible.value, "the one privacy state is the shared one")
        assertIs<AssetsLoad.Ready>(rig.model.state.value.load)
    }

    @Test
    fun aNewGenerationNeverShowsTheOldValues() {
        val rig = rig()
        rig.portfolio.load(1)
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()
        rig.portfolio.session.set(VaultSessionState.Open(2))
        settle()
        assertIs<AssetsLoad.Loading>(rig.model.state.value.load)
    }

    @Test
    fun lockingShowsNoFigures() {
        val rig = rig()
        rig.portfolio.load(1)
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()
        rig.portfolio.session.set(VaultSessionState.Locked)
        settle()
        assertIs<AssetsLoad.Loading>(rig.model.state.value.load)
    }

    @Test
    fun eachCategoryOpensItsRoute() {
        val rig = rig()
        val effects = mutableListOf<AssetsEffect>()
        CoroutineScope(dispatcher).launch { repeat(4) { effects += rig.model.effects.first() } }
        settle()
        rig.model.onIntent(AssetsIntent.OpenCategory(AssetCategory.GOLD))
        rig.model.onIntent(AssetsIntent.OpenCategory(AssetCategory.BANKS))
        rig.model.onIntent(AssetsIntent.OpenCategory(AssetCategory.CASH))
        rig.model.onIntent(AssetsIntent.OpenCategory(AssetCategory.INVESTMENTS))
        settle()
        assertEquals(
            listOf<AssetsEffect>(
                AssetsEffect.Open(GoldRoute),
                AssetsEffect.Open(BanksRoute),
                AssetsEffect.Open(CashRoute),
                AssetsEffect.Open(InvestmentsRoute),
            ),
            effects,
        )
    }
}

/** A refresh controller that never refreshes: the Assets tests only read values and privacy state. */
private object IdleRefresh : PriceRefreshController {
    override val status = kotlinx.coroutines.flow.MutableStateFlow<PriceRefreshStatus>(PriceRefreshStatus.Idle)

    override fun start() = Unit

    override fun refresh() = Unit
}
