package com.mk.daftar.core.domain.usecase

import com.mk.daftar.core.domain.repository.BankRepository
import com.mk.daftar.core.domain.repository.CashRepository
import com.mk.daftar.core.domain.repository.GoldRepository
import com.mk.daftar.core.domain.repository.InvestmentRepository
import com.mk.daftar.core.domain.repository.PriceRepository
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.valuation.PortfolioInput
import com.mk.daftar.core.domain.valuation.PortfolioValuation
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.domain.valuation.selectQuotes
import com.mk.daftar.core.domain.valuation.valuePortfolio
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.InvestmentAccount
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Selected quotes, re-evaluated for age while collected. Emits only for the open session's generation. */
class ObserveSelectedQuotesUseCase(
    private val session: VaultSession,
    private val prices: PriceRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<VaultRead<SelectedQuotes>> = observeInSession(session) { generation ->
        combine(prices.observeQuotes(), clockTicks(clock)) { read, _ ->
            // The tick only re-evaluates ages. The instant is read now, so a quote fetched since the last tick is never
            // judged to be in the future.
            val now = clock.now()
            when (read) {
                is VaultRead.Failed -> read

                is VaultRead.Ready -> read.valueOf(generation)
                    ?.let { snapshot -> VaultRead.Ready(generation, selectQuotes(snapshot, now)) }
                    ?: VaultRead.Loading

                VaultRead.Loading, VaultRead.Locked -> VaultRead.Loading
            }
        }
    }
}

/**
 * The raw quote snapshot of the open generation: remote candidates with provenance, and manual slots with their
 * revisions. Editing needs these, because a selected quote alone cannot show the source beneath an override or the
 * revision a save must match. Emits only for the open session's generation; a lock yields Locked-equivalent Loading.
 */
class ObserveQuoteSnapshotUseCase(private val session: VaultSession, private val prices: PriceRepository) {
    operator fun invoke(): Flow<VaultRead<QuoteSnapshot>> = observeInSession(session) { generation ->
        prices.observeQuotes().map { read ->
            when (read) {
                is VaultRead.Failed -> read

                is VaultRead.Ready -> read.valueOf(generation)?.let { VaultRead.Ready(generation, it) }
                    ?: VaultRead.Loading

                VaultRead.Loading, VaultRead.Locked -> VaultRead.Loading
            }
        }
    }
}

/**
 * The whole portfolio valued from one combined frame. The four inputs are separate repository observations, not a
 * transactionally consistent snapshot, so a frame is accepted only when every input belongs to the same open
 * generation. Any mismatch, lock or loading input yields [VaultRead.Loading] rather than a partial total.
 */
class ObservePortfolioValuationUseCase(
    private val session: VaultSession,
    private val gold: GoldRepository,
    private val banks: BankRepository,
    private val cash: CashRepository,
    private val investments: InvestmentRepository,
    private val prices: PriceRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<VaultRead<PortfolioValuation>> = observeInSession(session) { generation ->
        // The four record reads and the quotes make one frame; the clock tick re-evaluates ages between them.
        val records = combine(
            gold.observeAll(),
            banks.observeAccounts(),
            cash.observeAll(),
            investments.observeAll(),
        ) { goldRead, accountRead, cashRead, investmentRead ->
            RecordReads(goldRead, accountRead, cashRead, investmentRead)
        }
        combine(records, prices.observeQuotes(), clockTicks(clock)) { reads, quoteRead, _ ->
            val now = clock.now()
            val failure = listOf<VaultRead<*>>(
                reads.gold,
                reads.accounts,
                reads.cash,
                reads.investments,
                quoteRead,
            ).filterIsInstance<VaultRead.Failed>().firstOrNull()
            if (failure != null) return@combine failure
            val holdings = reads.gold.valueOf(generation)?.map { it.value } ?: return@combine VaultRead.Loading
            val accounts = reads.accounts.valueOf(generation)?.map { it.value } ?: return@combine VaultRead.Loading
            val cashBalances = reads.cash.valueOf(generation)?.map { it.value } ?: return@combine VaultRead.Loading
            val investmentAccounts = reads.investments.valueOf(generation)?.map { it.value }
                ?: return@combine VaultRead.Loading
            val snapshot = quoteRead.valueOf(generation) ?: return@combine VaultRead.Loading
            val input = PortfolioInput(
                gold = holdings,
                bankAccounts = accounts,
                cash = cashBalances,
                investments = investmentAccounts,
            )
            VaultRead.Ready(generation, valuePortfolio(input, selectQuotes(snapshot, now)))
        }
    }

    /** One frame of the four record observations, typed so each list keeps its own record type. */
    private class RecordReads(
        val gold: VaultRead<List<Stored<GoldHolding>>>,
        val accounts: VaultRead<List<Stored<BankAccount>>>,
        val cash: VaultRead<List<Stored<CashBalance>>>,
        val investments: VaultRead<List<Stored<InvestmentAccount>>>,
    )
}
