package com.mk.daftar.core.domain.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.ZakatYear
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** The declared years and where tracking began. A null [startYear] means tracking has not started yet. */
data class ZakatTrackingState(val startYear: Int?, val years: List<ZakatYear>)

/**
 * Annual declarations, kept in the encrypted vault. Every write is one transaction, and a failure leaves the previously
 * committed state exactly as it was. Errors use [StorageError.InvalidInput] for a rejected year or adjustment,
 * [StorageError.Conflict] for a duplicate year, and [StorageError.NotFound] for a year that is not tracked.
 */
interface ZakatYearRepository {
    /** The tracking state of the open vault. Emits [VaultRead.Locked] while no session is open. */
    fun observeTracking(): Flow<VaultRead<ZakatTrackingState>>

    /**
     * Starts tracking on first use, at the most recent Ramadan year on or before [today], and creates the missing
     * unchecked years through the next Ramadan year. Idempotent; existing years are never changed or removed.
     */
    suspend fun ensureTracking(today: LocalDate): Outcome<ZakatTrackingState, StorageError>

    /** Records or clears the declaration. A mark stamps the time from the clock; clearing removes the stamp. */
    suspend fun setPaid(hijriYear: Int, paid: Boolean): Outcome<ZakatYear, StorageError>

    /** Moves the year's Ramadan date by -1, 0 or +1 day. Zero restores the calculated date. Never changes the declaration. */
    suspend fun setDateAdjustment(hijriYear: Int, overrideDays: Int): Outcome<ZakatYear, StorageError>

    /** Starts tracking a year that is not tracked yet: any supported year up to the next Ramadan year. */
    suspend fun addYear(hijriYear: Int, today: LocalDate): Outcome<ZakatYear, StorageError>
}
