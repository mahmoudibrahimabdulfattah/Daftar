package com.mk.daftar.feature.gold.list

import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.usecase.ObserveGoldHoldingsUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.domain.valuation.QuoteOrigin
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.navigation.GoldAddRoute
import com.mk.daftar.core.navigation.GoldEditRoute
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.gold.GoldEffect
import com.mk.daftar.feature.gold.GoldPhase
import com.mk.daftar.feature.gold.testing.FakeBankRepository
import com.mk.daftar.feature.gold.testing.FakeCashRepository
import com.mk.daftar.feature.gold.testing.FakeGoldRepository
import com.mk.daftar.feature.gold.testing.FakePriceRepository
import com.mk.daftar.feature.gold.testing.FakeSession
import com.mk.daftar.feature.gold.testing.FixedClock
import com.mk.daftar.feature.gold.testing.NOW
import com.mk.daftar.feature.gold.testing.holding
import com.mk.daftar.feature.gold.testing.pinnedManualGold
import com.mk.daftar.feature.gold.testing.remoteGold
import com.mk.daftar.feature.gold.testing.remoteUsd
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone

/**
 * The gold list. Virtual time is advanced with runCurrent only: the valuation's clock ticks never end on their own, so
 * advancing until idle would not return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoldListViewModelTest {

    private val dispatcher = StandardTestDispatcher(TestCoroutineScheduler())
    private val scope = CoroutineScope(dispatcher)
    private val effects = mutableListOf<GoldEffect>()
    private var collector: Job? = null

    private fun settle() = dispatcher.scheduler.runCurrent()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        collector?.cancel()
        Dispatchers.resetMain()
    }

    private class Rig(
        val session: FakeSession,
        val gold: FakeGoldRepository,
        val prices: FakePriceRepository,
        val visibility: ValuesVisibility,
        val model: GoldListViewModel,
    )

    private fun rig(
        holdings: List<GoldHolding> = fixtureHoldings(),
        quotes: QuoteSnapshot = fixtureQuotes(),
        open: Boolean = true,
    ): Rig {
        val session = FakeSession()
        val gold = FakeGoldRepository(holdings)
        val prices = FakePriceRepository(quotes)
        val banks = FakeBankRepository()
        val cash = FakeCashRepository()
        val clock = FixedClock(NOW)
        val visibility = ValuesVisibility()
        val model = GoldListViewModel(
            observeHoldings = ObserveGoldHoldingsUseCase(gold),
            observePortfolio = ObservePortfolioValuationUseCase(
                session,
                gold,
                banks,
                cash,
                NoInvestments(cash.observeAll()),
                prices,
                clock,
            ),
            observeQuotes = ObserveSelectedQuotesUseCase(session, prices, clock),
            visibility = visibility,
            timeZone = TimeZone.UTC,
        )
        collector = scope.launch { model.effects.collect { effects += it } }
        if (open) session.open()
        settle()
        return Rig(session, gold, prices, visibility, model)
    }

    @Test
    fun theApprovedFixtureTotalsAreTheWeightAndTheSellValuation() {
        val state = rig().model.state.value
        assertEquals(GoldPhase.Ready, state.phase)
        val summary = assertNotNull(state.summary)
        assertEquals("110.06", summary.totalWeight.toPlainString())
        assertEquals("677812.74", summary.totalEgp?.toPlainString())
        assertEquals(0, summary.unvaluedCount)
    }

    @Test
    fun rowsKeepTheRepositoryOrderAndJoinEachValueByTypedId() {
        val state = rig().model.state.value
        assertEquals(listOf("g1", "g2", "g3"), state.rows.map { it.id.value })
        assertEquals("494800", state.rows[0].exactEgp?.toPlainString())
        assertEquals("165572.45", state.rows[1].exactEgp?.toPlainString())
        assertEquals("17440.29", state.rows[2].exactEgp?.toPlainString())
    }

    @Test
    fun aMissingKaratQuoteShowsADashAndAnExplicitlyAvailableSubtotal() {
        val state = rig(quotes = quotesWithout(GoldKarat.K18)).model.state.value
        val summary = assertNotNull(state.summary)
        assertNull(summary.totalEgp, "a partial category has no complete total")
        assertEquals("660372.45", summary.subtotalEgp.toPlainString())
        assertEquals(1, summary.unvaluedCount)
        assertNull(state.rows.single { it.id.value == "g3" }.exactEgp)
        assertEquals(3, state.rows.size, "a missing quote never removes a holding")
    }

    @Test
    fun aKaratWithoutAQuoteIsReportedAsMissingInTheQuoteLines() {
        val state = rig(quotes = quotesWithout(GoldKarat.K18)).model.state.value
        val line = state.quotes.single { it.karat == GoldKarat.K18 }
        assertNull(line.quote)
        assertNotNull(state.quotes.single { it.karat == GoldKarat.K21 }.quote)
    }

    @Test
    fun aPinnedManualQuoteIsVisibleAsManualProvenance() {
        val pinned = pinnedManualGold(GoldKarat.K21, "6200")
        val quotes = QuoteSnapshot(
            remote = listOf(remoteGold(GoldKarat.K18, "5301"), remoteUsd("48.50")),
            manual = listOf(pinned),
        )
        val state = rig(quotes = quotes).model.state.value
        val line = state.quotes.single { it.karat == GoldKarat.K21 }
        assertEquals(QuoteOrigin.PINNED_MANUAL, line.quote?.origin)
        assertEquals("6200", line.quote?.value?.toPlainString())
        assertEquals("496000", state.rows.single { it.id.value == "g1" }.exactEgp?.toPlainString())
    }

    @Test
    fun emptyHoldingsShowTheEmptyPhaseWithoutASummary() {
        val state = rig(holdings = emptyList()).model.state.value
        assertEquals(GoldPhase.Empty, state.phase)
        assertNull(state.summary)
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun aLockedSessionShowsNoDataAndLoadsNothing() {
        val state = rig(open = false).model.state.value
        assertEquals(GoldPhase.Locked, state.phase)
        assertTrue(state.rows.isEmpty())
        assertNull(state.summary)
    }

    @Test
    fun aRepositoryLockClearsTheRequestedRouteAndShowsNothing() {
        val rig = rig()
        rig.visibility.toggle()
        rig.model.onIntent(GoldListIntent.OpenAdd)
        settle()
        assertNotNull(rig.model.state.value.pendingRoute)
        rig.gold.setLocked(true)
        settle()
        assertEquals(GoldPhase.Locked, rig.model.state.value.phase)
        assertNull(rig.model.state.value.pendingRoute)
    }

    @Test
    fun openingAHoldingWhileValuesAreVisibleNavigatesAtOnce() {
        val rig = rig()
        rig.model.onIntent(GoldListIntent.OpenHolding(GoldHoldingId("g2")))
        settle()
        assertEquals(listOf<GoldEffect>(GoldEffect.Navigate(GoldEditRoute("g2"))), effects)
    }

    @Test
    fun openingAHoldingWhileHiddenWaitsForAnExplicitReveal() {
        val rig = rig()
        rig.visibility.toggle()
        rig.model.onIntent(GoldListIntent.OpenHolding(GoldHoldingId("g2")))
        settle()
        assertTrue(effects.isEmpty(), "nothing opens implicitly while hidden")
        assertEquals(GoldEditRoute("g2"), rig.model.state.value.pendingRoute)

        rig.model.onIntent(GoldListIntent.RevealAndOpen)
        settle()
        assertTrue(rig.visibility.visible.value, "the reveal shows the values")
        assertEquals(listOf<GoldEffect>(GoldEffect.Navigate(GoldEditRoute("g2"))), effects)
        assertNull(rig.model.state.value.pendingRoute)
    }

    @Test
    fun cancellingTheRevealKeepsTheValuesHiddenAndOpensNothing() {
        val rig = rig()
        rig.visibility.toggle()
        rig.model.onIntent(GoldListIntent.OpenAdd)
        settle()
        rig.model.onIntent(GoldListIntent.CancelReveal)
        settle()
        assertTrue(effects.isEmpty())
        assertNull(rig.model.state.value.pendingRoute)
        assertTrue(!rig.visibility.visible.value)
    }

    @Test
    fun addOpensTheEditorRouteWhenVisible() {
        rig().model.onIntent(GoldListIntent.OpenAdd)
        settle()
        assertEquals(listOf<GoldEffect>(GoldEffect.Navigate(GoldAddRoute)), effects)
    }

    @Test
    fun hidingTheValuesMasksTheTotalsAndTheRowsWithoutLosingThem() {
        val rig = rig()
        rig.model.onIntent(GoldListIntent.ToggleHidden)
        settle()
        val state = rig.model.state.value
        assertTrue(state.hidden)
        assertEquals(GoldPhase.Ready, state.phase)
        assertEquals(
            "677812.74",
            state.summary?.totalEgp?.toPlainString(),
            "the value stays in state, masked by the UI",
        )
        assertEquals(3, state.rows.size)
    }

    @Test
    fun aHoldingAddedElsewhereAppearsWithoutAFeatureRefresh() {
        val rig = rig()
        rig.gold.externalUpdate("g1", GoldKarat.K21, "90")
        settle()
        val summary = assertNotNull(rig.model.state.value.summary)
        assertEquals("120.06", summary.totalWeight.toPlainString())
    }

    private fun fixtureHoldings(): List<GoldHolding> = listOf(
        holding("g1", GoldKarat.K21, "80"),
        holding("g2", GoldKarat.K21, "26.77", form = GoldForm.JEWELRY),
        holding("g3", GoldKarat.K18, "3.29"),
    )

    private fun fixtureQuotes(): QuoteSnapshot = QuoteSnapshot(
        remote = listOf(
            remoteGold(GoldKarat.K24, "7069"),
            remoteGold(GoldKarat.K21, "6185"),
            remoteGold(GoldKarat.K18, "5301"),
            remoteUsd("48.50"),
        ),
        manual = emptyList(),
    )

    private fun quotesWithout(karat: GoldKarat): QuoteSnapshot = QuoteSnapshot(
        remote = fixtureQuotes().remote.filterNot { (it.quote as? GoldQuote)?.key?.karat == karat },
        manual = emptyList(),
    )
}
