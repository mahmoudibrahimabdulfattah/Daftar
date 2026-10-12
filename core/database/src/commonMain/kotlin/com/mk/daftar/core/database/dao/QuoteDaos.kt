package com.mk.daftar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mk.daftar.core.database.entity.ManualQuoteEntity
import com.mk.daftar.core.database.entity.RemoteQuoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RemoteQuoteDao {
    @Query("SELECT * FROM remote_quotes ORDER BY quote_key ASC, provider ASC")
    abstract fun observeAll(): Flow<List<RemoteQuoteEntity>>

    @Query("SELECT * FROM remote_quotes WHERE quote_key = :quoteKey AND provider = :provider")
    abstract suspend fun get(quoteKey: String, provider: String): RemoteQuoteEntity?

    @Upsert
    protected abstract suspend fun upsertRow(entity: RemoteQuoteEntity): Long

    /**
     * Writes every accepted quote of one refresh in one transaction, so a buy and sell pair (or both FX providers)
     * is never half-committed. Rows not in [entities] stay as they were.
     */
    @Transaction
    open suspend fun upsertAll(entities: List<RemoteQuoteEntity>) {
        entities.forEach { upsertRow(it) }
    }
}

@Dao
abstract class ManualQuoteDao {
    @Query("SELECT * FROM manual_quotes ORDER BY quote_key ASC")
    abstract fun observeAll(): Flow<List<ManualQuoteEntity>>

    @Query("SELECT * FROM manual_quotes WHERE quote_key = :quoteKey")
    abstract suspend fun get(quoteKey: String): ManualQuoteEntity?

    /** Create-only path. A row for the key already present fails with a constraint error. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: ManualQuoteEntity): Long

    /** Replaces the value, provenance and pinned state in one statement, at the expected revision. */
    @Query(
        "UPDATE manual_quotes SET value = :value, entered_seconds = :enteredSeconds, " +
            "entered_nanos = :enteredNanos, pinned = 1, note = :note, revision = revision + 1 " +
            "WHERE quote_key = :quoteKey AND revision = :expectedRevision",
    )
    abstract suspend fun replaceIfRevision(
        quoteKey: String,
        expectedRevision: Long,
        value: String,
        enteredSeconds: Long,
        enteredNanos: Int,
        note: String?,
    ): Int

    /** Unpins the value and keeps it as a last-resort candidate. */
    @Query(
        "UPDATE manual_quotes SET pinned = 0, revision = revision + 1 " +
            "WHERE quote_key = :quoteKey AND revision = :expectedRevision",
    )
    abstract suspend fun unpinIfRevision(quoteKey: String, expectedRevision: Long): Int

    @Query("DELETE FROM manual_quotes WHERE quote_key = :quoteKey AND revision = :expectedRevision")
    abstract suspend fun deleteIfRevision(quoteKey: String, expectedRevision: Long): Int
}
