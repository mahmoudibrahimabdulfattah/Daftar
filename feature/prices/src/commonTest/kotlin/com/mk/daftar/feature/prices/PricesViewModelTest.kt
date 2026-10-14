@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.prices

import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.refresh.RefreshOutcome
import com.mk.daftar.core.domain.repository.RefreshRejection
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveGoldHoldingsUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.domain.usecase.ObserveQuoteSnapshotUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.navigation.ManualQuoteRoute
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class PricesViewModelTest {

    /** Main has its own scheduler, so the view model's age ticks never block the test. Settled explicitly. */
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

    private class Rig(val repos: PricesRig, val visibility: ValuesVisibility, val model: PricesViewModel)

    private fun rig(): Rig {
        val repos = PricesRig()
        val visibility = ValuesVisibility()
        val model = PricesViewModel(
            observeSnapshot = ObserveQuoteSnapshotUseCase(repos.session, repos.prices),
            observeSelected = ObserveSelectedQuotesUseCase(repos.session, repos.prices, FixedClock),
            observePortfolio = ObservePortfolioValuationUseCase(
                session = repos.session,
                gold = repos.gold,
                banks = repos.banks,
                cash = repos.cash,
                investments = NoInvestments(repos.cash.observeAll()),
                prices = repos.prices,
                clock = FixedClock,
            ),
            observeGold = ObserveGoldHoldingsUseCase(repos.gold),
            visibility = visibility,
            refresh = repos.refresh,
            clock = FixedClock,
        )
        return Rig(repos, visibility, model)
    }

    private fun Rig.openWithPrices(generation: Long = 1L) {
        repos.openEmpty(generation)
        repos.publish(fullSnapshot(), generation)
        settle()
    }

    @Test
    fun aClosedVaultShowsLockedAndNoPrices() {
        val rig = rig()
        settle()
        assertEquals(PricesLoad.Locked, rig.model.state.value.load)
    }

    @Test
    fun anOpenVaultShowsTheBoardOfItsGeneration() {
        val rig = rig()
        rig.openWithPrices()
        val load = assertIs<PricesLoad.Ready>(rig.model.state.value.load)
        assertEquals(3, load.board.karats.size)
        assertEquals(false, rig.model.state.value.refreshing)
    }

    @Test
    fun aRefreshRequestOnlyForwardsToTheSharedController() {
        val rig = rig()
        rig.openWithPrices()
        rig.model.onIntent(PricesIntent.Refresh)
        settle()
        assertEquals(1, rig.repos.refresh.refreshCalls)
        assertEquals(0, rig.repos.prices.refreshCalls)
    }

    @Test
    fun aRefreshInFlightMarksTheScreenRefreshingWithoutChangingThePrices() {
        val rig = rig()
        rig.openWithPrices()
        val before = assertIs<PricesLoad.Ready>(rig.model.state.value.load).board
        rig.repos.refresh.current.value = PriceRefreshStatus.Refreshing(1)
        settle()
        assertTrue(rig.model.state.value.refreshing)
        assertEquals(before, assertIs<PricesLoad.Ready>(rig.model.state.value.load).board)
    }

    @Test
    fun aFailedRefreshGivesItsNoticeAndKeepsTheLastPrices() {
        val rig = rig()
        rig.openWithPrices()
        val before = assertIs<PricesLoad.Ready>(rig.model.state.value.load).board
        rig.repos.refresh.current.value = PriceRefreshStatus.Finished(
            1,
            RefreshOutcome.Failed,
            setOf(RefreshRejection.RateLimited),
        )
        settle()
        assertEquals(RefreshNotice.RATE_LIMITED, rig.model.state.value.notice)
        assertEquals(before, assertIs<PricesLoad.Ready>(rig.model.state.value.load).board)
    }

    @Test
    fun aLoadingReadKeepsThePreviousBoardOnScreen() {
        val rig = rig()
        rig.openWithPrices()
        val before = assertIs<PricesLoad.Ready>(rig.model.state.value.load).board
        rig.repos.prices.quotes.value = VaultRead.Loading
        settle()
        assertEquals(before, assertIs<PricesLoad.Ready>(rig.model.state.value.load).board)
    }

    @Test
    fun aLockClearsThePricesAtOnce() {
        val rig = rig()
        rig.openWithPrices()
        rig.repos.session.set(VaultSessionState.Locked)
        settle()
        assertEquals(PricesLoad.Locked, rig.model.state.value.load)
    }

    @Test
    fun aReadFailureIsShownAsAFailureNotAsEmptyPrices() {
        val rig = rig()
        rig.openWithPrices()
        rig.repos.prices.quotes.value = VaultRead.Failed(StorageError.CorruptData)
        settle()
        assertEquals(PricesLoad.Failed(StorageError.CorruptData), rig.model.state.value.load)
    }

    @Test
    fun hidingValuesIsReflectedOnTheScreenState() {
        val rig = rig()
        rig.openWithPrices()
        rig.visibility.toggle()
        settle()
        assertTrue(rig.model.state.value.valuesHidden)
    }

    @Test
    fun aManualEntryRequestOpensTheEditorRouteAsAnEffect() {
        val rig = rig()
        rig.openWithPrices()
        val seen = mutableListOf<PricesEffect>()
        val collector = CoroutineScope(dispatcher).launch { rig.model.effects.collect { seen += it } }
        val route = ManualQuoteRoute(quoteKeyToken(sellKey(GoldKarat.K18)))
        rig.model.onIntent(PricesIntent.OpenManual(route))
        settle()
        assertEquals(listOf<PricesEffect>(PricesEffect.Open(route)), seen)
        collector.cancel()
    }
}
