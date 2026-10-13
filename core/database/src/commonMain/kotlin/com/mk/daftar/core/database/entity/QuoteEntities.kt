package com.mk.daftar.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Last successful remote quote per key and provider. A failed fetch never changes a row. Both FX providers keep their
 * own rows, so a fallback never erases the primary's quote.
 */
@Entity(tableName = "remote_quotes", primaryKeys = ["quote_key", "provider"])
data class RemoteQuoteEntity(
    @ColumnInfo(name = "quote_key") val quoteKey: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    /** TIMESTAMP, DATE_ONLY or UNKNOWN. Exactly the matching source fields below are populated. */
    @ColumnInfo(name = "source_time_kind") val sourceTimeKind: String,
    @ColumnInfo(name = "source_seconds") val sourceSeconds: Long?,
    @ColumnInfo(name = "source_nanos") val sourceNanos: Int?,
    @ColumnInfo(name = "source_date") val sourceDate: String?,
    @ColumnInfo(name = "fetched_seconds") val fetchedSeconds: Long,
    @ColumnInfo(name = "fetched_nanos") val fetchedNanos: Int,
    @ColumnInfo(name = "recorded_seconds") val recordedSeconds: Long,
    @ColumnInfo(name = "recorded_nanos") val recordedNanos: Int,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "next_update_seconds") val nextUpdateSeconds: Long?,
    @ColumnInfo(name = "next_update_nanos") val nextUpdateNanos: Int?,
    @ColumnInfo(name = "attribution_url") val attributionUrl: String?,
    /** For USD to EGP only: the FX basis. Null for gold. Version 3. */
    @ColumnInfo(name = "basis") val basis: String? = null,
)

/**
 * The user's manual quote per writable key. [pinned] makes it the effective quote; an unpinned value stays available
 * as a last-resort candidate. Refresh never writes this table.
 */
@Entity(tableName = "manual_quotes")
data class ManualQuoteEntity(
    @PrimaryKey @ColumnInfo(name = "quote_key") val quoteKey: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "entered_seconds") val enteredSeconds: Long,
    @ColumnInfo(name = "entered_nanos") val enteredNanos: Int,
    @ColumnInfo(name = "pinned") val pinned: Boolean,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "revision") val revision: Long,
)
