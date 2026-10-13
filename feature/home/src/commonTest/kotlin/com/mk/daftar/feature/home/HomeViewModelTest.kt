@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.home

import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveBanksUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.model.ZakatYear
import com.mk.daftar.core.navigation.GoldRoute
import com.mk.daftar.core.navigation.TopLevelDestination
import com.mk.daftar.core.navigation.ZakatCalculatorRoute
import com.mk.daftar.core.navigation.ZakatHistoryRoute
import com.mk.daftar.core.navigation.ZakatYearRoute
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

class HomeViewModelTest {

    /**
     * Main has its own scheduler, so the view model's endless clock ticks never block the test's own completion. The
     * tests settle Main explicitly with [settle].
     */
    private val dispatcher = StandardTestDispatcher(TestCoroutineScheduler())

    private fun settle() = dispatcher.scheduler.runCurrent()

    /** The effects one intent emits. The collector is started first, because effects are consumed once. */
    private fun Rig.effectOf(intent: HomeIntent): List<HomeEffect> {
        val effects = mutableListOf<HomeEffect>()
        CoroutineScope(dispatcher).launch { effects += model.effects.first() }
        settle()
        model.onIntent(intent)
        settle()
        return effects
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Rig(val portfolio: FakePortfolio, val visibility: ValuesVisibility, val model: HomeViewModel)

    private fun rig(
        tracking: VaultRead<ZakatTrackingState> = VaultRead.Ready(1, ZakatTrackingState(null, emptyList())),
    ): Rig {
        val portfolio = FakePortfolio()
        val visibility = ValuesVisibility()
        val model = HomeViewModel(
            observePortfolio = ObservePortfolioValuationUseCase(
                session = portfolio.session as VaultSession,
                gold = portfolio.gold,
                banks = portfolio.banks,
                cash = portfolio.cash,
                prices = portfolio.prices,
                clock = FixedClock,
            ),
            observeQuotes = ObserveSelectedQuotesUseCase(portfolio.session, portfolio.prices, FixedClock),
            observeBanks = ObserveBanksUseCase(portfolio.banks),
            observeTracking = ObserveZakatTrackingUseCase(FakeZakatTracking(tracking)),
            observeDay = fixedZakatDay(LocalDate(2026, 10, 13)),
            visibility = visibility,
            refresh = portfolio.refresh,
        )
        return Rig(portfolio, visibility, model)
    }

    @Test
    fun readyValuesAppearOnceTheSessionOpens() {
        val rig = rig()
        rig.portfolio.load(1)
        assertIs<HomeLoad.Loading>(rig.model.state.value.load, "nothing shows before a session opens")
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()
        val ready = assertIs<HomeLoad.Ready>(rig.model.state.value.load)
        assertEquals("1076212.74", ready.figures.total!!.toPlainString())
    }

    @Test
    fun togglingMaskingHidesValuesWithoutDroppingThem() {
        val rig = rig()
        rig.portfolio.load(1)
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()

        rig.model.onIntent(HomeIntent.ToggleValues)
        settle()
        assertTrue(rig.model.state.value.valuesHidden)
        assertFalse(rig.visibility.visible.value)
        assertIs<HomeLoad.Ready>(rig.model.state.value.load, "masking keeps the figures for the masked presentation")

        rig.model.onIntent(HomeIntent.ToggleValues)
        settle()
        assertFalse(rig.model.state.value.valuesHidden)
    }

    @Test
    fun aNewGenerationNeverShowsTheOldGenerationsValues() {
        val rig = rig()
        rig.portfolio.load(1)
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()
        assertIs<HomeLoad.Ready>(rig.model.state.value.load)

        // The records still belong to generation 1 while generation 2 is open: no mixed state may be shown.
        rig.portfolio.session.set(VaultSessionState.Open(2))
        settle()
        assertEquals(HomeLoad.Loading, rig.model.state.value.load)
    }

    @Test
    fun lockingShowsNoValuesAndNoRefreshState() {
        val rig = rig()
        rig.portfolio.load(1)
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()
        rig.portfolio.refresh.current.value = PriceRefreshStatus.Refreshing(1)
        settle()
        assertTrue(rig.model.state.value.refreshing)

        rig.portfolio.session.set(VaultSessionState.Locked)
        settle()
        assertEquals(HomeLoad.Loading, rig.model.state.value.load)
        assertFalse(rig.model.state.value.refreshing)
    }

    @Test
    fun refreshIntentOnlyForwardsToTheController() {
        val rig = rig()
        rig.portfolio.load(1)
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()

        rig.model.onIntent(HomeIntent.Refresh)
        rig.model.onIntent(HomeIntent.Refresh)
        assertEquals(2, rig.portfolio.refresh.refreshCalls, "each tap is forwarded; coalescing lives in the controller")
    }

    @Test
    fun openingACategoryEmitsItsRoute() {
        val rig = rig()
        val effects = mutableListOf<HomeEffect>()
        CoroutineScope(dispatcher).launch { effects += rig.model.effects.first() }
        settle()
        rig.model.onIntent(HomeIntent.OpenCategory(HomeCategory.GOLD))
        settle()
        assertEquals(listOf<HomeEffect>(HomeEffect.Open(GoldRoute)), effects)
    }

    @Test
    fun aNeutralZakatEntryOpensTheCalculatorWhenNothingIsDue() {
        val rig = rig(VaultRead.Ready(1, ZakatTrackingState(1448, listOf(ZakatYear.unchecked(1448)))))
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()
        assertTrue(rig.model.state.value.zakatDue.isEmpty(), "2027-02-08 is still upcoming on 2026-10-13")

        assertEquals(
            listOf<HomeEffect>(HomeEffect.Open(ZakatCalculatorRoute)),
            rig.effectOf(HomeIntent.OpenZakatCalculator),
        )
    }

    @Test
    fun aSingleDueYearShowsItsOwnReview() {
        val rig = rig(VaultRead.Ready(1, ZakatTrackingState(1447, listOf(ZakatYear.unchecked(1447)))))
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()

        assertEquals(listOf(ZakatDueYear(1447, 2026)), rig.model.state.value.zakatDue)
        assertEquals(
            listOf<HomeEffect>(HomeEffect.Open(ZakatYearRoute(1447))),
            rig.effectOf(HomeIntent.OpenZakatReview),
        )
    }

    @Test
    fun severalDueYearsOpenTheHistoryOldestFirst() {
        val rig = rig(
            VaultRead.Ready(
                1,
                ZakatTrackingState(1446, listOf(ZakatYear.unchecked(1446), ZakatYear.unchecked(1447))),
            ),
        )
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()

        assertEquals(listOf(1446, 1447), rig.model.state.value.zakatDue.map { it.hijriYear })
        assertEquals(listOf<HomeEffect>(HomeEffect.Open(ZakatHistoryRoute)), rig.effectOf(HomeIntent.OpenZakatReview))
    }

    @Test
    fun aPaidYearIsNeverAReminder() {
        val paid = ZakatYear(1447, paid = true, markedAt = FIXTURE_NOW, overrideDays = 0, userAdjusted = false)
        val rig = rig(VaultRead.Ready(1, ZakatTrackingState(1447, listOf(paid))))
        rig.portfolio.session.set(VaultSessionState.Open(1))
        settle()

        assertTrue(rig.model.state.value.zakatDue.isEmpty())
    }
}
