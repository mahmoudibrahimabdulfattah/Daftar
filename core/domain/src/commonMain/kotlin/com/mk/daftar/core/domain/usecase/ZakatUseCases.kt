package com.mk.daftar.core.domain.usecase

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.BankRepository
import com.mk.daftar.core.domain.repository.CashRepository
import com.mk.daftar.core.domain.repository.GoldRepository
import com.mk.daftar.core.domain.repository.InvestmentRepository
import com.mk.daftar.core.domain.repository.PriceRepository
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.repository.ZakatYearRepository
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.zakat.ZAKAT_TIME_ZONE
import com.mk.daftar.core.domain.zakat.ZakatEstimate
import com.mk.daftar.core.domain.zakat.estimateZakat
import com.mk.daftar.core.domain.zakat.zakatToday
import com.mk.daftar.core.model.ZakatYear
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/** The declared years and tracking start. Emits only while the vault is open. */
class ObserveZakatTrackingUseCase(private val repository: ZakatYearRepository) {
    operator fun invoke(): Flow<VaultRead<ZakatTrackingState>> = repository.observeTracking()
}

/**
 * The Cairo civil day: emitted now, then again whenever it changes. The flow sleeps until the next Cairo midnight, but
 * never longer than [MAX_WAIT], so a clock correction is seen within that bound. It emits only while collected.
 */
open class ObserveZakatDayUseCase(private val clock: Clock) {
    open operator fun invoke(): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.now()
            emit(zakatToday(now))
            delay(untilNextCairoMidnight(now).coerceAtMost(MAX_WAIT))
        }
    }.distinctUntilChanged()

    private fun untilNextCairoMidnight(now: Instant): Duration {
        val tomorrow = zakatToday(now).plus(1, DateTimeUnit.DAY).atStartOfDayIn(ZAKAT_TIME_ZONE)
        return tomorrow - now
    }

    companion object {
        val MAX_WAIT: Duration = 15.minutes
    }
}

/** Run on every unlock and at each Cairo midnight while the vault is open. The day comes from the clock, in Cairo. */
class EnsureZakatTrackingUseCase(private val repository: ZakatYearRepository, private val clock: Clock) {
    suspend operator fun invoke(): Outcome<ZakatTrackingState, StorageError> =
        repository.ensureTracking(zakatToday(clock.now()))
}

class SetZakatYearPaidUseCase(private val repository: ZakatYearRepository) {
    suspend operator fun invoke(hijriYear: Int, paid: Boolean): Outcome<ZakatYear, StorageError> =
        repository.setPaid(hijriYear, paid)
}

class SetZakatDateAdjustmentUseCase(private val repository: ZakatYearRepository) {
    suspend operator fun invoke(hijriYear: Int, overrideDays: Int): Outcome<ZakatYear, StorageError> =
        repository.setDateAdjustment(hijriYear, overrideDays)
}

class AddZakatYearUseCase(private val repository: ZakatYearRepository, private val clock: Clock) {
    suspend operator fun invoke(hijriYear: Int): Outcome<ZakatYear, StorageError> =
        repository.addYear(hijriYear, zakatToday(clock.now()))
}

/**
 * The planning estimate, from the same valuation Home observes. Valuation already re-evaluates quote age, so the
 * estimate follows it without a second selection.
 */
open class ObserveZakatEstimateUseCase(
    session: VaultSession,
    gold: GoldRepository,
    banks: BankRepository,
    cash: CashRepository,
    investments: InvestmentRepository,
    prices: PriceRepository,
    clock: Clock,
) {
    private val valuation = ObservePortfolioValuationUseCase(session, gold, banks, cash, investments, prices, clock)

    open operator fun invoke(): Flow<VaultRead<ZakatEstimate>> = valuation().map { read ->
        when (read) {
            is VaultRead.Ready -> VaultRead.Ready(read.generation, estimateZakat(read.value))
            is VaultRead.Failed -> read
            VaultRead.Loading -> VaultRead.Loading
            VaultRead.Locked -> VaultRead.Locked
        }
    }
}
