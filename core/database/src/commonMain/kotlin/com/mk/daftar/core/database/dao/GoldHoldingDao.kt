package com.mk.daftar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mk.daftar.core.database.entity.GoldHoldingEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GoldHoldingDao {
    @Query("SELECT * FROM gold_holdings ORDER BY created_seconds ASC, created_nanos ASC, id ASC")
    abstract fun observeAll(): Flow<List<GoldHoldingEntity>>

    @Query("SELECT * FROM gold_holdings WHERE id = :id")
    abstract fun observe(id: String): Flow<GoldHoldingEntity?>

    @Query("SELECT * FROM gold_holdings WHERE id = :id")
    abstract suspend fun get(id: String): GoldHoldingEntity?

    /** ABORT, never REPLACE: a clash must fail rather than delete and reinsert a row. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: GoldHoldingEntity): Long

    @Update
    protected abstract suspend fun updateRow(entity: GoldHoldingEntity): Int

    @Query("SELECT revision FROM gold_holdings WHERE id = :id")
    protected abstract suspend fun revisionOf(id: String): Long?

    /** Checks the stored revision and writes the edit in one transaction. Returns 0 when the revision moved. */
    @Transaction
    open suspend fun updateIfRevision(entity: GoldHoldingEntity, expectedRevision: Long): Int {
        if (revisionOf(entity.id) != expectedRevision) return 0
        return updateRow(entity)
    }

    @Query("DELETE FROM gold_holdings WHERE id = :id AND revision = :expectedRevision")
    abstract suspend fun deleteIfRevision(id: String, expectedRevision: Long): Int
}
