package com.mk.daftar.feature.cash

import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.valuation.PortfolioInput
import com.mk.daftar.core.domain.valuation.PortfolioValuation
import com.mk.daftar.core.domain.valuation.selectQuotes
import com.mk.daftar.core.domain.valuation.valuePortfolio
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.feature.cash.testing.NOW
import com.mk.daftar.feature.cash.testing.balance
import com.mk.daftar.feature.cash.testing.egp
import com.mk.daftar.feature.cash.testing.quoteSnapshot
import com.mk.daftar.feature.cash.testing.remoteUsd
import com.mk.daftar.feature.cash.testing.usd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CashListStateTest {

    private fun stored(vararg balances: CashBalance): List<Stored<CashBalance>> = balances.map { Stored(it, 1) }

    private fun valuationOf(balances: List<CashBalance>, generation: Long = 1): VaultRead<PortfolioValuation> {
        val quotes = selectQuotes(quoteSnapshot(remoteUsd("48.50")), NOW)
        val input = PortfolioInput(gold = emptyList(), bankAccounts = emptyList(), cash = balances)
        return VaultRead.Ready(generation, valuePortfolio(input, quotes))
    }

    @Test
    fun aLockedOrLoadingBalanceReadDecidesThePhaseBeforeAnyValuation() {
        val valuation = valuationOf(emptyList())

        assertEquals(CashPhase.Locked, cashListStateOf(VaultRead.Locked, valuation, hidden = false).phase)
        assertEquals(CashPhase.Loading, cashListStateOf(VaultRead.Loading, valuation, hidden = false).phase)
        assertEquals(
            CashPhase.Failed(StorageError.Io),
            cashListStateOf(VaultRead.Failed(StorageError.Io), valuation, hidden = false).phase,
        )
    }

    @Test
    fun aFailedValuationIsAFailedListInsteadOfAPartialTotal() {
        val state = cashListStateOf(
            VaultRead.Ready(1, stored(balance("c1", "Cash", money = egp("10")))),
            VaultRead.Failed(StorageError.CorruptData),
            hidden = false,
        )

        assertEquals(CashPhase.Failed(StorageError.CorruptData), state.phase)
        assertNull(state.totalEgp)
    }

    @Test
    fun aValuationFromAnotherGenerationAddsNoFiguresButKeepsTheBalances() {
        val state = cashListStateOf(
            VaultRead.Ready(2, stored(balance("c1", "Cash", money = egp("10")))),
            valuationOf(listOf(balance("c1", "Cash", money = egp("10"))), generation = 1),
            hidden = false,
        )

        assertEquals(CashPhase.Ready, state.phase)
        assertEquals(1, state.rows.size)
        assertNull(state.rows.single().egp, "figures join only within the same generation")
        assertNull(state.totalEgp)
    }

    @Test
    fun aPendingValuationShowsTheBalancesWithoutTotals() {
        val state = cashListStateOf(
            VaultRead.Ready(1, stored(balance("c1", "Cash", money = egp("10")))),
            VaultRead.Loading,
            hidden = true,
        )

        assertEquals(CashPhase.Ready, state.phase)
        assertEquals(1, state.rows.size)
        assertNull(state.totalEgp)
        assertTrue(state.hidden)
    }

    @Test
    fun aJoinedRowTakesItsValuationByTypedIdNotByPosition() {
        val first = balance("c1", "USD", money = usd("10"))
        val second = balance("c2", "EGP", kind = CashKind.WALLET, money = egp("5"))
        // The valuation lists the balances in the opposite order: the join must still match by ID.
        val state = cashListStateOf(
            VaultRead.Ready(1, stored(first, second)),
            valuationOf(listOf(second, first)),
            hidden = false,
        )

        assertEquals(Decimal.parseCanonical("485"), state.rows.single { it.id == CashBalanceId("c1") }.egp)
        assertEquals(Decimal.parseCanonical("5"), state.rows.single { it.id == CashBalanceId("c2") }.egp)
    }

    @Test
    fun anEmptyReadyListIsEmpty() {
        val state = cashListStateOf(VaultRead.Ready(1, emptyList()), valuationOf(emptyList()), hidden = false)

        assertEquals(CashPhase.Empty, state.phase)
        assertEquals(Decimal.ZERO, state.totalEgp)
    }
}
