package com.mk.daftar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mk.daftar.core.database.entity.ZakatTrackingEntity
import com.mk.daftar.core.database.entity.ZakatYearEntity
import kotlinx.coroutines.flow.Flow

/** Declared years and the tracking row. Tracking is one row; both tables live in the encrypted vault. */
@Dao
abstract class ZakatYearDao {
    /** Oldest year first, which is also the order the history and the due list use. */
    @Query("SELECT * FROM zakat_years ORDER BY hijri_year ASC")
    abstract fun observeYears(): Flow<List<ZakatYearEntity>>

    @Query("SELECT * FROM zakat_years ORDER BY hijri_year ASC")
    abstract suspend fun years(): List<ZakatYearEntity>

    @Query("SELECT * FROM zakat_years WHERE hijri_year = :year")
    abstract suspend fun year(year: Int): ZakatYearEntity?

    @Query("SELECT * FROM zakat_tracking WHERE id = 1")
    abstract fun observeTracking(): Flow<List<ZakatTrackingEntity>>

    @Query("SELECT * FROM zakat_tracking WHERE id = 1")
    abstract suspend fun tracking(): List<ZakatTrackingEntity>

    @Update
    abstract suspend fun updateYear(entity: ZakatYearEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertYear(entity: ZakatYearEntity): Long

    /**
     * One transaction for the tracking plan: the tracking row when it is missing, then every missing year. Rows that
     * already exist are left as they are, so a repeated plan changes nothing.
     */
    @Transaction
    open suspend fun applyTrackingPlan(tracking: ZakatTrackingEntity?, years: List<ZakatYearEntity>) {
        if (tracking != null) insertTrackingIfAbsent(tracking)
        years.forEach { insertYearIfAbsent(it) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertTrackingIfAbsent(entity: ZakatTrackingEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertYearIfAbsent(entity: ZakatYearEntity): Long
}
