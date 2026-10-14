package com.mk.daftar.feature.investments

import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.feature.investments.testing.NOW
import com.mk.daftar.feature.investments.testing.account
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class InvestmentStateTest {

    private fun ready(
        vararg accounts: com.mk.daftar.core.model.InvestmentAccount,
    ): VaultRead<List<Stored<com.mk.daftar.core.model.InvestmentAccount>>> =
        VaultRead.Ready(1, accounts.map { Stored(it, 1) })

    @Test
    fun exactlyTwentyFourHoursIsNotDueAndOneNanosecondMoreIsDue() {
        val valuedAt = NOW - 24.hours
        assertEquals(ValueDateHint.None, valueDateHintOf(valuedAt, NOW))
        assertEquals(ValueDateHint.UpdateDue, valueDateHintOf(valuedAt - 1.nanoseconds, NOW))
    }

    @Test
    fun aValueDatedInTheFutureAsksForACheckNotAnUpdate() {
        assertEquals(ValueDateHint.CheckDate, valueDateHintOf(NOW + 1.hours, NOW))
    }

    @Test
    fun theNextThresholdIsTheEarliestOneStillAhead() {
        val recent = NOW - 2.hours
        val older = NOW - 30.hours
        val future = NOW + 3.hours
        // The 24-hour mark of the recent value is the next change, because the older value has already crossed its own.
        assertEquals(recent + 24.hours, nextValueDateThreshold(listOf(recent, older), NOW))
        assertEquals(future, nextValueDateThreshold(listOf(future), NOW))
        assertNull(nextValueDateThreshold(listOf(NOW - 30.hours), NOW))
    }

    @Test
    fun theListTotalIsTheExactSumOfEveryAccountAndKeepsOldValuesInFull() {
        val state = investmentListStateOf(
            ready(
                account("a", "0.10", valueAsOf = NOW - 60.hours),
                account("b", "0.10", valueAsOf = NOW),
            ),
            hidden = false,
            now = NOW,
        )
        assertEquals(InvestmentPhase.Ready, state.phase)
        assertEquals(Decimal.parseCanonical("0.20"), state.total)
        assertEquals(ValueDateHint.UpdateDue, state.rows[0].hint)
        assertEquals(ValueDateHint.None, state.rows[1].hint)
    }

    @Test
    fun noAccountsIsAnEmptyListWithAZeroTotal() {
        val state =
            investmentListStateOf(
                VaultRead.Ready(1, emptyList<Stored<com.mk.daftar.core.model.InvestmentAccount>>()),
                false,
                NOW,
            )
        assertEquals(InvestmentPhase.Empty, state.phase)
        assertEquals(Decimal.ZERO, state.total)
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun loadingLockedAndFailedReadsShowNoRows() {
        assertEquals(InvestmentPhase.Loading, investmentListStateOf(VaultRead.Loading, false, NOW).phase)
        assertEquals(InvestmentPhase.Locked, investmentListStateOf(VaultRead.Locked, true, NOW).phase)
        val failed = investmentListStateOf(VaultRead.Failed(StorageError.DiskFull), false, NOW)
        assertEquals(InvestmentPhase.Failed(StorageError.DiskFull), failed.phase)
        assertTrue(failed.rows.isEmpty())
    }

    @Test
    fun hiddenValuesKeepTheRowsAndDatesButMarkTheListHidden() {
        val state = investmentListStateOf(ready(account("a", "5", providerId = "eg.thndr")), hidden = true, now = NOW)
        assertTrue(state.hidden)
        assertEquals(1, state.rows.size)
        assertEquals("eg.thndr", state.rows[0].providerId?.value)
        assertEquals(NOW, state.rows[0].valueAsOf)
    }

    @Test
    fun aRestoredFutureDateStaysInTheTotalAndIsOnlyCheckedNotReset() {
        val future = Instant.parse("2026-11-01T00:00:00Z")
        val state = investmentListStateOf(ready(account("a", "7", valueAsOf = future)), false, NOW)
        assertEquals(Decimal.parseCanonical("7"), state.total)
        assertEquals(ValueDateHint.CheckDate, state.rows[0].hint)
        assertEquals(future, state.rows[0].valueAsOf)
    }
}
