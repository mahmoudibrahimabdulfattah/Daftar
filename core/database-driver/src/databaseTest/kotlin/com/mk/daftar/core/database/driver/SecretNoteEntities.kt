package com.mk.daftar.core.database.driver

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/** Test-only table: proves Room runs over the keyed driver. Not a production entity. The database class is per platform. */
@Entity(tableName = "secret_notes")
data class SecretNote(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val body: String)

@Dao
abstract class SecretNoteDao {
    @Insert
    abstract suspend fun insert(note: SecretNote): Long

    @Query("UPDATE secret_notes SET body = :body WHERE id = :id")
    abstract suspend fun updateBody(id: Long, body: String): Int

    @Query("SELECT COUNT(*) FROM secret_notes")
    abstract suspend fun count(): Int

    @Query("SELECT * FROM secret_notes ORDER BY id")
    abstract suspend fun all(): List<SecretNote>

    /** Inserts and then fails inside the same transaction, so Room must roll the insert back. */
    @Transaction
    open suspend fun insertThenFail(note: SecretNote) {
        insert(note)
        throw IllegalStateException("rollback requested")
    }
}
