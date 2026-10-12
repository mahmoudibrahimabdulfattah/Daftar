package com.mk.daftar.core.data.storage

import com.mk.daftar.core.database.mapper.CorruptRowException
import com.mk.daftar.core.domain.storage.StorageError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageErrorsTest {

    @Test
    fun corruptRowsAreCorruptDataNeverSilentlyDropped() {
        assertEquals(StorageError.CorruptData, CorruptRowException("banks", "name").toStorageError())
    }

    @Test
    fun sqliteMessagesMapByTheirCategory() {
        assertEquals(StorageError.Busy, sqliteStorageError("database is locked", write = false))
        assertEquals(StorageError.DiskFull, sqliteStorageError("database or disk is full", write = false))
        assertEquals(StorageError.Conflict, sqliteStorageError("UNIQUE constraint failed: banks.id", write = true))
        assertEquals(StorageError.CorruptData, sqliteStorageError("file is not a database", write = false))
        assertEquals(StorageError.Io, sqliteStorageError("disk I/O error", write = false))
    }

    @Test
    fun aRejectedWriteIsInvalidInputWhileAReadFailureIsIo() {
        // Validation triggers raise plain messages with no SQLite keyword, so only the write default applies.
        assertEquals(StorageError.InvalidInput, sqliteStorageError("name must be nonblank and trimmed", write = true))
        assertEquals(StorageError.Io, sqliteStorageError("name must be nonblank and trimmed", write = false))
    }

    @Test
    fun aForeignKeyRefusalIsRecognizedFromItsMessage() {
        assertTrue(isForeignKeyMessage("FOREIGN KEY constraint failed"))
        assertFalse(isForeignKeyMessage("UNIQUE constraint failed"))
    }

    @Test
    fun validationFailuresAreInvalidInput() {
        assertEquals(
            StorageError.InvalidInput,
            IllegalArgumentException("gold weight must be positive").toStorageError(),
        )
    }

    @Test
    fun schemaMismatchesAreUnsupportedAndUnavailableAtOpen() {
        val mismatch = IllegalStateException("validation triggers do not match schema version 1")
        assertEquals(StorageError.UnsupportedSchema, mismatch.toStorageError())
        assertTrue(mismatch.toStorageError().isUnavailableAtOpen())
        assertFalse(StorageError.Busy.isUnavailableAtOpen())
    }
}
