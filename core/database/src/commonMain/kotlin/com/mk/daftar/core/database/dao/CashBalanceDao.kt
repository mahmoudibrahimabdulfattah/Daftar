package com.mk.daftar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mk.daftar.core.database.entity.CashBalanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CashBalanceDao {
    @Query("SELECT * FROM cash_balances ORDER BY created_seconds ASC, created_nanos ASC, id ASC")
    abstract fun observeAll(): Flow<List<CashBalanceEntity>>

    @Query("SELECT * FROM cash_balances WHERE id = :id")
    abstract fun observe(id: String): Flow<CashBalanceEntity?>

    @Query("SELECT * FROM cash_balances WHERE id = :id")
    abstract suspend fun get(id: String): CashBalanceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: CashBalanceEntity): Long

    @Update
    protected abstract suspend fun updateRow(entity: CashBalanceEntity): Int

    @Query("SELECT revision FROM cash_balances WHERE id = :id")
    protected abstract suspend fun revisionOf(id: String): Long?

    @Transaction
    open suspend fun updateIfRevision(entity: CashBalanceEntity, expectedRevision: Long): Int {
        if (revisionOf(entity.id) != expectedRevision) return 0
        return updateRow(entity)
    }

    @Query("DELETE FROM cash_balances WHERE id = :id AND revision = :expectedRevision")
    abstract suspend fun deleteIfRevision(id: String, expectedRevision: Long): Int
}
