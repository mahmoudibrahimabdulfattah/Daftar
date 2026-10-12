package com.mk.daftar.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.mk.daftar.core.database.dao.BankAccountDao
import com.mk.daftar.core.database.dao.BankDao
import com.mk.daftar.core.database.dao.CashBalanceDao
import com.mk.daftar.core.database.dao.GoldHoldingDao
import com.mk.daftar.core.database.dao.ManualQuoteDao
import com.mk.daftar.core.database.dao.PersonalNoteDao
import com.mk.daftar.core.database.dao.RemoteQuoteDao
import com.mk.daftar.core.database.dao.ZakatPaymentDao
import com.mk.daftar.core.database.entity.BankAccountEntity
import com.mk.daftar.core.database.entity.BankEntity
import com.mk.daftar.core.database.entity.CashBalanceEntity
import com.mk.daftar.core.database.entity.GoldHoldingEntity
import com.mk.daftar.core.database.entity.ManualQuoteEntity
import com.mk.daftar.core.database.entity.PersonalNoteEntity
import com.mk.daftar.core.database.entity.RemoteQuoteEntity
import com.mk.daftar.core.database.entity.ZakatPaymentEntity

/** The private notebook database. Schema version 1; there is no destructive fallback. */
@Database(
    entities = [
        GoldHoldingEntity::class,
        BankEntity::class,
        BankAccountEntity::class,
        CashBalanceEntity::class,
        ZakatPaymentEntity::class,
        PersonalNoteEntity::class,
        RemoteQuoteEntity::class,
        ManualQuoteEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(DaftarDatabaseConstructor::class)
abstract class DaftarDatabase : RoomDatabase() {
    abstract fun goldHoldingDao(): GoldHoldingDao

    abstract fun bankDao(): BankDao

    abstract fun bankAccountDao(): BankAccountDao

    abstract fun cashBalanceDao(): CashBalanceDao

    abstract fun zakatPaymentDao(): ZakatPaymentDao

    abstract fun personalNoteDao(): PersonalNoteDao

    abstract fun remoteQuoteDao(): RemoteQuoteDao

    abstract fun manualQuoteDao(): ManualQuoteDao
}

/** Room generates the implementation for each platform. */
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object DaftarDatabaseConstructor : RoomDatabaseConstructor<DaftarDatabase> {
    override fun initialize(): DaftarDatabase
}

/** The database file name, inside the platform's private, device-only directory. */
const val DAFTAR_DATABASE_FILE_NAME: String = "daftar.db"
