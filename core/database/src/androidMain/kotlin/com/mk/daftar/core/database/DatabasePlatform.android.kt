package com.mk.daftar.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/**
 * The database file under `noBackupFilesDir`: private to the app, device-only, and never backed up or transferred.
 * The directory is created on first use.
 */
fun daftarDatabaseFile(context: Context): File {
    val directory = File(context.noBackupFilesDir, "daftar").apply { mkdirs() }
    return File(directory, DAFTAR_DATABASE_FILE_NAME)
}

/**
 * Room's builder for the database at [file]. Debug and release builds have different application IDs, so their files
 * never meet.
 */
fun daftarDatabaseBuilder(context: Context, file: File): RoomDatabase.Builder<DaftarDatabase> =
    Room.databaseBuilder(context, DaftarDatabase::class.java, file.absolutePath)
