package com.mk.daftar.feature.journal.zakat

import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.domain.zakat.ZakatEstimate
import com.mk.daftar.core.domain.zakat.ZakatQuoteSource
import com.mk.daftar.core.domain.zakat.ZakatUnavailableReason
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.navigation.PricesRoute
import com.mk.daftar.core.navigation.ZakatAddRoute
import com.mk.daftar.core.navigation.ZakatDateRoute
import com.mk.daftar.core.navigation.ZakatHistoryRoute
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.testing.FakeZakatYearRepository
import com.mk.daftar.feature.journal.testing.declared
import com.mk.daftar.feature.journal.testing.fixedDay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ZakatCalculatorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun dec(text: String): Decimal = Decimal.parseCanonical(text)

    private fun available(
        estimate: String = "26905.32",
        total: String = "1076212.74",
        empty: Boolean = false,
        stale: Boolean = false,
    ) = ZakatEstimate.Available(
        exactTotalEgp = dec(total),
        exactEstimateEgp = dec("26905.3185"),
        totalEgp = dec(total),
        estimateEgp = dec(estimate),
        prefillEgp = if (dec(estimate) > Decimal.ZERO) dec(estimate) else null,
        categories = emptyList(),
        quoteSource = ZakatQuoteSource.LIVE,
        oldestQuoteAt = null,
        stale = stale,
        clockAhead = false,
        isEmpty = empty,
    )

    private fun rig(
        estimate: ZakatEstimate,
        years: FakeZakatYearRepository = FakeZakatYearRepository(),
        day: LocalDate = LocalDate(2026, 10, 13),
        visibility: ValuesVisibility = ValuesVisibility(),
    ): ZakatCalculatorViewModel = ZakatCalculatorViewModel(
        estimates = { flowOf(VaultRead.Ready(1, estimate)) },
        observeTracking = ObserveZakatTrackingUseCase(years),
        observeDay = fixedDay(day),
        visibility = visibility,
    )

    @Test
    fun theNextRamadanIsTheFirstUpcomingDateWithItsCountdown() = runTest(dispatcher) {
        val vm = rig(available())
        advanceUntilIdle()

        val next = vm.state.value.nextRamadan!!
        assertEquals(1448, next.hijriYear)
        assertEquals(LocalDate(2027, 2, 8), next.date)
        assertEquals(118, next.daysRemaining)
        assertFalse(next.adjusted)
    }

    @Test
    fun aOneDayAdjustmentMovesTheCountdownAndIsMarkedAsAdjusted() = runTest(dispatcher) {
        val years =
            FakeZakatYearRepository(startYear = 1447, years = listOf(declared(1447), declared(1448, overrideDays = -1)))
        val vm = rig(available(), years = years)
        advanceUntilIdle()

        val next = vm.state.value.nextRamadan!!
        assertEquals(LocalDate(2027, 2, 7), next.date)
        assertEquals(117, next.daysRemaining)
        assertTrue(next.adjusted)
    }

    @Test
    fun aVisibleEstimateOffersItsRoundedAmountAsTheEditorDraft() = runTest(dispatcher) {
        val vm = rig(available())
        advanceUntilIdle()
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { vm.effects.collect { effects += it } }

        assertEquals(dec("26905.32"), vm.state.value.prefill)
        vm.onIntent(ZakatCalculatorIntent.RecordWithEstimate)
        advanceUntilIdle()

        assertEquals(listOf<JournalEffect>(JournalEffect.Navigate(ZakatAddRoute(prefillAmount = "26905.32"))), effects)
        collector.cancel()
    }

    @Test
    fun aHiddenEstimateNeverReachesTheEditorUntilRevealed() = runTest(dispatcher) {
        val visibility = ValuesVisibility()
        visibility.toggle()
        val vm = rig(available(), visibility = visibility)
        advanceUntilIdle()
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { vm.effects.collect { effects += it } }

        assertNull(vm.state.value.prefill, "no draft is offered while values are hidden")
        vm.onIntent(ZakatCalculatorIntent.RecordWithEstimate)
        advanceUntilIdle()
        assertTrue(effects.isEmpty())

        vm.onIntent(ZakatCalculatorIntent.RevealForRecord)
        advanceUntilIdle()
        assertFalse(vm.state.value.hidden)
        assertEquals(dec("26905.32"), vm.state.value.prefill)
        collector.cancel()
    }

    @Test
    fun aMissingRequiredQuoteMakesTheEstimateUnavailableWithNoPrefill() = runTest(dispatcher) {
        val unavailable = ZakatEstimate.Unavailable(
            reason = ZakatUnavailableReason.MISSING_QUOTE,
            missingQuotes = listOf(QuoteKey.UsdEgp),
            availableSubtotalEgp = dec("398400"),
        )
        val vm = rig(unavailable)
        advanceUntilIdle()

        assertEquals(JournalPhase.Ready, vm.state.value.phase)
        assertNull(vm.state.value.prefill)
        assertEquals(unavailable, vm.state.value.estimate)
    }

    @Test
    fun aZeroEstimateOffersNoPaymentDraft() = runTest(dispatcher) {
        val vm = rig(available(estimate = "0.00", total = "0", empty = true))
        advanceUntilIdle()

        assertNull(vm.state.value.prefill, "a payment amount must be positive")
    }

    @Test
    fun aStaleEstimateStaysCalculableAndKeepsItsDraft() = runTest(dispatcher) {
        val vm = rig(available(stale = true))
        advanceUntilIdle()

        assertEquals(dec("26905.32"), vm.state.value.prefill)
    }

    @Test
    fun pricesAndYearlyStatusAreReachedAsEffects() = runTest(dispatcher) {
        val vm = rig(available())
        advanceUntilIdle()
        val effects = mutableListOf<JournalEffect>()
        val collector = launch { vm.effects.collect { effects += it } }

        vm.onIntent(ZakatCalculatorIntent.ReviewPrices)
        vm.onIntent(ZakatCalculatorIntent.OpenYearlyStatus)
        vm.onIntent(ZakatCalculatorIntent.AdjustNextRamadan)
        advanceUntilIdle()

        assertEquals(
            listOf<JournalEffect>(
                JournalEffect.Navigate(PricesRoute),
                JournalEffect.Navigate(ZakatHistoryRoute),
                JournalEffect.Navigate(ZakatDateRoute(1448)),
            ),
            effects,
        )
        collector.cancel()
    }

    @Test
    fun aDateAfterTheLastSupportedRamadanHasNoNextDate() = runTest(dispatcher) {
        val vm = rig(available(), day = LocalDate(2174, 11, 26))
        advanceUntilIdle()

        assertNull(vm.state.value.nextRamadan)
        assertTrue(vm.state.value.nextUnavailable)
    }

    @Test
    fun trackingIsNotNeededForTheEstimateItself() = runTest(dispatcher) {
        val vm = rig(available(), years = FakeZakatYearRepository().also { it.lock() })
        advanceUntilIdle()

        assertEquals(dec("26905.32"), vm.state.value.prefill)
    }
}
