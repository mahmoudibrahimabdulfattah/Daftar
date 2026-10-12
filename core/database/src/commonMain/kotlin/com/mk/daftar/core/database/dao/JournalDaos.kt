package com.mk.daftar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mk.daftar.core.database.entity.PersonalNoteEntity
import com.mk.daftar.core.database.entity.ZakatPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ZakatPaymentDao {
    /** Newest payment date first. Equal dates keep ID order. */
    @Query("SELECT * FROM zakat_payments ORDER BY paid_on DESC, id ASC")
    abstract fun observeAll(): Flow<List<ZakatPaymentEntity>>

    @Query("SELECT * FROM zakat_payments WHERE id = :id")
    abstract fun observe(id: String): Flow<ZakatPaymentEntity?>

    @Query("SELECT * FROM zakat_payments WHERE id = :id")
    abstract suspend fun get(id: String): ZakatPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: ZakatPaymentEntity): Long

    @Update
    protected abstract suspend fun updateRow(entity: ZakatPaymentEntity): Int

    @Query("SELECT revision FROM zakat_payments WHERE id = :id")
    protected abstract suspend fun revisionOf(id: String): Long?

    @Transaction
    open suspend fun updateIfRevision(entity: ZakatPaymentEntity, expectedRevision: Long): Int {
        if (revisionOf(entity.id) != expectedRevision) return 0
        return updateRow(entity)
    }

    @Query("DELETE FROM zakat_payments WHERE id = :id AND revision = :expectedRevision")
    abstract suspend fun deleteIfRevision(id: String, expectedRevision: Long): Int
}

@Dao
abstract class PersonalNoteDao {
    /** Most recently changed note first. Equal times keep ID order. */
    @Query("SELECT * FROM personal_notes ORDER BY updated_seconds DESC, updated_nanos DESC, id ASC")
    abstract fun observeAll(): Flow<List<PersonalNoteEntity>>

    @Query("SELECT * FROM personal_notes WHERE id = :id")
    abstract fun observe(id: String): Flow<PersonalNoteEntity?>

    @Query("SELECT * FROM personal_notes WHERE id = :id")
    abstract suspend fun get(id: String): PersonalNoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: PersonalNoteEntity): Long

    @Update
    protected abstract suspend fun updateRow(entity: PersonalNoteEntity): Int

    @Query("SELECT revision FROM personal_notes WHERE id = :id")
    protected abstract suspend fun revisionOf(id: String): Long?

    @Transaction
    open suspend fun updateIfRevision(entity: PersonalNoteEntity, expectedRevision: Long): Int {
        if (revisionOf(entity.id) != expectedRevision) return 0
        return updateRow(entity)
    }

    @Query("DELETE FROM personal_notes WHERE id = :id AND revision = :expectedRevision")
    abstract suspend fun deleteIfRevision(id: String, expectedRevision: Long): Int
}
