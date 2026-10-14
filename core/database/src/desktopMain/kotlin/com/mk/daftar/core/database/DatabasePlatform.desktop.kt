package com.mk.daftar.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import com.mk.daftar.core.common.desktop.DesktopAppDirectories
import java.io.File

/** The database directory inside the app's private directory, which is owner-only and excluded from backup. */
fun daftarDatabaseDirectory(): File = DesktopAppDirectories.directory("database")

/** The legacy database file: the first generation's file in the database directory. */
fun daftarDatabaseFile(): File = daftarDatabaseDirectory().resolve(DAFTAR_DATABASE_FILE_NAME)

/** Room's builder for the database at [file]. */
fun daftarDatabaseBuilder(file: File): RoomDatabase.Builder<DaftarDatabase> =
    Room.databaseBuilder<DaftarDatabase>(name = file.absolutePath)
