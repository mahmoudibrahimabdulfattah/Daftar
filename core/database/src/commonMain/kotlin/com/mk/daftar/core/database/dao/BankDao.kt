package com.mk.daftar.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mk.daftar.core.database.entity.BankAccountEntity
import com.mk.daftar.core.database.entity.BankEntity
import kotlinx.coroutines.flow.Flow

/**
 * One row of a bank's detail: the bank with one account, or with a null account when the bank has none. The join is a
 * single statement, so the bank and its accounts are read from one consistent snapshot.
 */
data class BankDetailRow(
    @Embedded val bank: BankEntity,
    @Embedded(prefix = "account_") val account: BankAccountEntity?,
)

@Dao
abstract class BankDao {
    @Query("SELECT * FROM banks ORDER BY created_seconds ASC, created_nanos ASC, id ASC")
    abstract fun observeAll(): Flow<List<BankEntity>>

    @Query("SELECT * FROM banks WHERE id = :id")
    abstract fun observe(id: String): Flow<BankEntity?>

    /**
     * The bank and its accounts in creation order, from one statement. Empty when the bank does not exist. A single
     * statement needs no explicit transaction, which keeps the read valid on Room's read-only connections.
     */
    @Query(
        "SELECT b.*, a.id AS account_id, a.created_seconds AS account_created_seconds, " +
            "a.created_nanos AS account_created_nanos, a.updated_seconds AS account_updated_seconds, " +
            "a.updated_nanos AS account_updated_nanos, a.revision AS account_revision, " +
            "a.bank_id AS account_bank_id, a.name AS account_name, a.balance AS account_balance, " +
            "a.currency AS account_currency, a.note AS account_note " +
            "FROM banks AS b LEFT JOIN bank_accounts AS a ON a.bank_id = b.id " +
            "WHERE b.id = :id " +
            "ORDER BY a.created_seconds ASC, a.created_nanos ASC, a.id ASC",
    )
    abstract fun observeDetail(id: String): Flow<List<BankDetailRow>>

    @Query("SELECT * FROM banks WHERE id = :id")
    abstract suspend fun get(id: String): BankEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: BankEntity): Long

    @Update
    protected abstract suspend fun updateRow(entity: BankEntity): Int

    @Query("SELECT revision FROM banks WHERE id = :id")
    protected abstract suspend fun revisionOf(id: String): Long?

    @Transaction
    open suspend fun updateIfRevision(entity: BankEntity, expectedRevision: Long): Int {
        if (revisionOf(entity.id) != expectedRevision) return 0
        return updateRow(entity)
    }

    /**
     * Deletes only at the expected revision. A bank that still has accounts is refused by the RESTRICT foreign key,
     * which raises a constraint failure rather than returning a count.
     */
    @Query("DELETE FROM banks WHERE id = :id AND revision = :expectedRevision")
    abstract suspend fun deleteIfRevision(id: String, expectedRevision: Long): Int
}

@Dao
abstract class BankAccountDao {
    @Query(
        "SELECT * FROM bank_accounts ORDER BY created_seconds ASC, created_nanos ASC, id ASC",
    )
    abstract fun observeAll(): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM bank_accounts WHERE id = :id")
    abstract fun observe(id: String): Flow<BankAccountEntity?>

    @Query("SELECT * FROM bank_accounts WHERE id = :id")
    abstract suspend fun get(id: String): BankAccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: BankAccountEntity): Long

    @Update
    protected abstract suspend fun updateRow(entity: BankAccountEntity): Int

    @Query("SELECT revision FROM bank_accounts WHERE id = :id")
    protected abstract suspend fun revisionOf(id: String): Long?

    @Transaction
    open suspend fun updateIfRevision(entity: BankAccountEntity, expectedRevision: Long): Int {
        if (revisionOf(entity.id) != expectedRevision) return 0
        return updateRow(entity)
    }

    @Query("DELETE FROM bank_accounts WHERE id = :id AND revision = :expectedRevision")
    abstract suspend fun deleteIfRevision(id: String, expectedRevision: Long): Int
}
