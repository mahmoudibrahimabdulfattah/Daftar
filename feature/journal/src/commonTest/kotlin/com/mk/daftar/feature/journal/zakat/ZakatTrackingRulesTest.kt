package com.mk.daftar.feature.journal.zakat

import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.domain.zakat.ZakatYearStatus
import com.mk.daftar.core.navigation.ZakatYearAddRoute
import com.mk.daftar.core.navigation.ZakatYearRoute
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.testing.FakeZakatYearRepository
import com.mk.daftar.feature.journal.testing.TODAY
import com.mk.daftar.feature.journal.testing.declared
import com.mk.daftar.feature.journal.testing.fixedDay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ZakatTrackingRulesTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun hijriYearsTypedWithArabicIndicDigitsParseLikeAsciiOnes() {
        assertEquals(1446, parseHijriYear("١٤٤٦"))
        assertEquals(1446, parseHijriYear(" 1446 "))
    }

    @Test
    fun aTypedYearThatIsNotWholeIsNotAYear() {
        assertNull(parseHijriYear(""))
        assertNull(parseHijriYear("14a6"))
        assertNull(parseHijriYear("14.6"))
        assertNull(parseHijriYear("١٤٫٦"))
    }

    @Test
    fun oneDueYearOpensItsReviewAndSeveralOpenTheHistory() {
        assertNull(reviewDestinationOf(emptyList()))
        assertEquals(ReviewDestination.Year(1447), reviewDestinationOf(listOf(1447)))
        assertEquals(ReviewDestination.History, reviewDestinationOf(listOf(1446, 1447)))
    }

    @Test
    fun theHistoryListsEveryYearNewestFirstWithItsStatus() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(
            startYear = 1446,
            years = listOf(declared(1446), declared(1447, paid = true), declared(1448)),
        )
        val vm = ZakatHistoryViewModel(ObserveZakatTrackingUseCase(years), fixedDay(TODAY))
        advanceUntilIdle()

        val history = vm.state.value
        assertEquals(JournalPhase.Ready, history.phase)
        assertEquals(listOf(1448, 1447, 1446), history.years.map { it.hijriYear })
        assertEquals(
            listOf(ZakatYearStatus.UPCOMING, ZakatYearStatus.PAID, ZakatYearStatus.DUE),
            history.years.map { it.status },
        )
    }

    @Test
    fun aYearRowAndTheAddActionNavigateByKey() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447)))
        val vm = ZakatHistoryViewModel(ObserveZakatTrackingUseCase(years), fixedDay(TODAY))
        advanceUntilIdle()
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { vm.effects.collect { effects += it } }

        vm.onIntent(ZakatHistoryIntent.OpenYear(1447))
        vm.onIntent(ZakatHistoryIntent.AddPastYear)
        advanceUntilIdle()

        assertEquals(
            listOf<JournalEffect>(
                JournalEffect.Navigate(ZakatYearRoute(1447)),
                JournalEffect.Navigate(ZakatYearAddRoute),
            ),
            effects,
        )
        collector.cancel()
    }

    @Test
    fun aLockedTrackingShowsNoYears() = runTest(dispatcher) {
        val years = FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447)))
        years.lock()
        val vm = ZakatHistoryViewModel(ObserveZakatTrackingUseCase(years), fixedDay(TODAY))
        advanceUntilIdle()

        assertEquals(JournalPhase.Locked, vm.state.value.phase)
        assertEquals(emptyList(), vm.state.value.years)
    }
}
