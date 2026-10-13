package com.mk.daftar.feature.journal.testing

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.repository.ZakatYearRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveZakatDayUseCase
import com.mk.daftar.core.domain.zakat.ZakatYearRules
import com.mk.daftar.core.model.ZakatYear
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/**
 * An in-memory declaration store with the contract the data layer keeps: a failed write leaves the committed state
 * untouched, and every rule about tracking, adding and adjusting comes from the domain's [ZakatYearRules].
 */
internal class FakeZakatYearRepository(startYear: Int? = null, years: List<ZakatYear> = emptyList()) :
    ZakatYearRepository {
    private val state = MutableStateFlow(ZakatTrackingState(startYear, years))
    private val locked = MutableStateFlow(false)

    var writeFailure: StorageError? = null
    val paidWrites = mutableListOf<Pair<Int, Boolean>>()
    val adjustmentWrites = mutableListOf<Pair<Int, Int>>()

    val current: ZakatTrackingState get() = state.value

    fun lock() {
        locked.value = true
    }

    override fun observeTracking(): Flow<VaultRead<ZakatTrackingState>> = combine(state, locked) { value, isLocked ->
        if (isLocked) VaultRead.Locked else VaultRead.Ready(1, value)
    }

    override suspend fun ensureTracking(today: LocalDate): Outcome<ZakatTrackingState, StorageError> {
        writeFailure?.let { return Outcome.Failure(it) }
        val before = state.value
        val start = before.startYear ?: ZakatYearRules.initialStartYear(today)
        val created = ZakatYearRules.yearsToCreate(start, today, before.years).map { ZakatYear.unchecked(it) }
        val next = ZakatTrackingState(start, (before.years + created).sortedBy { it.hijriYear })
        state.value = next
        return Outcome.Success(next)
    }

    override suspend fun setPaid(hijriYear: Int, paid: Boolean): Outcome<ZakatYear, StorageError> {
        paidWrites += hijriYear to paid
        writeFailure?.let { return Outcome.Failure(it) }
        val year = state.value.years.firstOrNull { it.hijriYear == hijriYear }
            ?: return Outcome.Failure(StorageError.NotFound)
        val updated = year.copy(paid = paid, markedAt = if (paid) FIXED_MARK else null)
        replace(updated)
        return Outcome.Success(updated)
    }

    override suspend fun setDateAdjustment(hijriYear: Int, overrideDays: Int): Outcome<ZakatYear, StorageError> {
        adjustmentWrites += hijriYear to overrideDays
        writeFailure?.let { return Outcome.Failure(it) }
        val year = state.value.years.firstOrNull { it.hijriYear == hijriYear }
            ?: return Outcome.Failure(StorageError.NotFound)
        val updated = year.copy(overrideDays = overrideDays, userAdjusted = overrideDays != 0)
        replace(updated)
        return Outcome.Success(updated)
    }

    override suspend fun addYear(hijriYear: Int, today: LocalDate): Outcome<ZakatYear, StorageError> {
        writeFailure?.let { return Outcome.Failure(it) }
        val years = state.value.years
        if (years.any { it.hijriYear == hijriYear }) return Outcome.Failure(StorageError.Conflict)
        if (!ZakatYearRules.canAddYear(hijriYear, today, years)) return Outcome.Failure(StorageError.InvalidInput)
        val added = ZakatYear.unchecked(hijriYear)
        state.update { it.copy(years = (it.years + added).sortedBy { year -> year.hijriYear }) }
        return Outcome.Success(added)
    }

    private fun replace(year: ZakatYear) {
        state.update { tracking ->
            tracking.copy(years = tracking.years.map { if (it.hijriYear == year.hijriYear) year else it })
        }
    }
}

/** A fixed declaration instant, used wherever a mark is stamped. */
internal val FIXED_MARK = NOW

/** The Cairo day the screens see: a fixed day that never changes. */
internal fun fixedDay(day: LocalDate): ObserveZakatDayUseCase = object : ObserveZakatDayUseCase(FixedClock(NOW)) {
    override operator fun invoke(): Flow<LocalDate> = flowOf(day)
}

/** A declared year in its unchecked state, or marked paid at [FIXED_MARK]. */
internal fun declared(hijriYear: Int, paid: Boolean = false, overrideDays: Int = 0): ZakatYear = ZakatYear(
    hijriYear = hijriYear,
    paid = paid,
    markedAt = if (paid) FIXED_MARK else null,
    overrideDays = overrideDays,
    userAdjusted = overrideDays != 0,
)
