package com.mk.daftar.core.domain.usecase

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.BankRepository
import com.mk.daftar.core.domain.repository.CashRepository
import com.mk.daftar.core.domain.repository.FirstRunGuideRepository
import com.mk.daftar.core.domain.repository.GoldRepository
import com.mk.daftar.core.domain.repository.InvestmentRepository
import com.mk.daftar.core.domain.repository.JournalRepository
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.repository.ZakatYearRepository
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Where the first-run guide stands for the open vault. [Unknown] covers loading, locked and failed reads. */
sealed interface FirstRunGuideState {
    /** A read has not settled, so neither the guide nor the empty state is shown. */
    data object Unknown : FirstRunGuideState

    /**
     * The vault holds a record, so the guide does not show. [doneRecorded] is false until the flag is written: the first
     * time Home observes a record, the guide is completed.
     */
    data class HasRecords(val doneRecorded: Boolean) : FirstRunGuideState

    /** The vault is empty and the guide was dismissed. The plain empty state shows instead. */
    data object Dismissed : FirstRunGuideState

    /** The vault is empty and the guide was never dismissed. */
    data object Pending : FirstRunGuideState
}

/**
 * Eligibility for the guide: no user record of any kind, and the flag not yet set. Bank shells, accounts, gold, cash,
 * investments, notes and zakat payments count. A zakat year counts only when the owner declared or adjusted it: years
 * tracking creates on its own do not. Quotes and preferences never count.
 */
class ObserveFirstRunGuideUseCase(
    private val session: VaultSession,
    private val gold: GoldRepository,
    private val banks: BankRepository,
    private val cash: CashRepository,
    private val investments: InvestmentRepository,
    private val journal: JournalRepository,
    private val zakatYears: ZakatYearRepository,
    private val guide: FirstRunGuideRepository,
) {
    operator fun invoke(): Flow<FirstRunGuideState> = observeInSession(session) { generation ->
        val assets = combine(
            gold.observeAll(),
            banks.observeAll(),
            cash.observeAll(),
            investments.observeAll(),
            journal.notes.observeAll(),
        ) { goldRead, bankRead, cashRead, investmentRead, noteRead ->
            val counts = listOf(
                goldRead.countFor(generation),
                bankRead.countFor(generation),
                cashRead.countFor(generation),
                investmentRead.countFor(generation),
                noteRead.countFor(generation),
            )
            counts.sumOrNull()
        }
        val zakat = combine(journal.zakat.observeAll(), zakatYears.observeTracking()) { paymentRead, trackingRead ->
            val payments = paymentRead.countFor(generation)
            val declared = (trackingRead.valueOf(generation))?.let { tracking -> tracking.declaredCount() }
            if (payments == null || declared == null) null else payments + declared
        }
        combine(assets, zakat, guide.observeDone()) { recordCount, zakatCount, done ->
            when {
                recordCount == null || zakatCount == null || done == null -> FirstRunGuideState.Unknown
                recordCount + zakatCount > 0 -> FirstRunGuideState.HasRecords(doneRecorded = done)
                done -> FirstRunGuideState.Dismissed
                else -> FirstRunGuideState.Pending
            }
        }.map { state -> VaultRead.Ready(generation, state) }
    }.map { read -> (read as? VaultRead.Ready<FirstRunGuideState>)?.value ?: FirstRunGuideState.Unknown }
}

/** Marks the guide done: the owner dismissed it, or the vault now holds a record. */
class CompleteFirstRunGuideUseCase(private val guide: FirstRunGuideRepository) {
    suspend operator fun invoke(): Outcome<Unit, StorageError> = guide.markDone()
}

private fun <T> VaultRead<List<T>>.countFor(generation: Long): Int? = valueOf(generation)?.size

private fun List<Int?>.sumOrNull(): Int? = if (any { it == null }) null else sumOf { it ?: 0 }

/** The zakat years the owner declared or adjusted. Years that tracking created on its own do not count. */
private fun ZakatTrackingState.declaredCount(): Int =
    years.count { year -> year.paid || year.userAdjusted || year.overrideDays != 0 }
