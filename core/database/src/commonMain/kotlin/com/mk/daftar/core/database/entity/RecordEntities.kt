package com.mk.daftar.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Schema version 1 tables. Every column is NOT NULL unless it is nullable below. Instants are stored as seconds and
 * nanoseconds, enums by name, decimals as canonical text, and dates as ISO text. Room annotations carry the keys and
 * indexes; enum membership, timestamp ordering, revisions and numeric text are enforced by the validation triggers.
 */

@Entity(
    tableName = "gold_holdings",
    indices = [Index(value = ["created_seconds", "created_nanos", "id"])],
)
data class GoldHoldingEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "created_seconds") val createdSeconds: Long,
    @ColumnInfo(name = "created_nanos") val createdNanos: Int,
    @ColumnInfo(name = "updated_seconds") val updatedSeconds: Long,
    @ColumnInfo(name = "updated_nanos") val updatedNanos: Int,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "karat") val karat: String,
    @ColumnInfo(name = "form") val form: String,
    @ColumnInfo(name = "weight_grams") val weightGrams: String,
    @ColumnInfo(name = "note") val note: String?,
)

@Entity(
    tableName = "banks",
    indices = [Index(value = ["created_seconds", "created_nanos", "id"])],
)
data class BankEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "created_seconds") val createdSeconds: Long,
    @ColumnInfo(name = "created_nanos") val createdNanos: Int,
    @ColumnInfo(name = "updated_seconds") val updatedSeconds: Long,
    @ColumnInfo(name = "updated_nanos") val updatedNanos: Int,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "name") val name: String,
)

@Entity(
    tableName = "bank_accounts",
    foreignKeys = [
        ForeignKey(
            entity = BankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bank_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["bank_id", "created_seconds", "created_nanos", "id"])],
)
data class BankAccountEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "created_seconds") val createdSeconds: Long,
    @ColumnInfo(name = "created_nanos") val createdNanos: Int,
    @ColumnInfo(name = "updated_seconds") val updatedSeconds: Long,
    @ColumnInfo(name = "updated_nanos") val updatedNanos: Int,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "bank_id") val bankId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "balance") val balance: String,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "note") val note: String?,
)

@Entity(
    tableName = "cash_balances",
    indices = [Index(value = ["created_seconds", "created_nanos", "id"])],
)
data class CashBalanceEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "created_seconds") val createdSeconds: Long,
    @ColumnInfo(name = "created_nanos") val createdNanos: Int,
    @ColumnInfo(name = "updated_seconds") val updatedSeconds: Long,
    @ColumnInfo(name = "updated_nanos") val updatedNanos: Int,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "balance") val balance: String,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "note") val note: String?,
)

@Entity(
    tableName = "zakat_payments",
    indices = [Index(value = ["paid_on", "id"])],
)
data class ZakatPaymentEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "created_seconds") val createdSeconds: Long,
    @ColumnInfo(name = "created_nanos") val createdNanos: Int,
    @ColumnInfo(name = "updated_seconds") val updatedSeconds: Long,
    @ColumnInfo(name = "updated_nanos") val updatedNanos: Int,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "paid_on") val paidOn: String,
    @ColumnInfo(name = "amount") val amount: String,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "note") val note: String?,
)

@Entity(
    tableName = "personal_notes",
    indices = [Index(value = ["updated_seconds", "updated_nanos", "id"])],
)
data class PersonalNoteEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "created_seconds") val createdSeconds: Long,
    @ColumnInfo(name = "created_nanos") val createdNanos: Int,
    @ColumnInfo(name = "updated_seconds") val updatedSeconds: Long,
    @ColumnInfo(name = "updated_nanos") val updatedNanos: Int,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
)
