package com.mk.daftar.core.database.mapper

import com.mk.daftar.core.database.entity.ZakatTrackingEntity
import com.mk.daftar.core.database.entity.ZakatYearEntity
import com.mk.daftar.core.model.HIJRI_MAX_YEAR
import com.mk.daftar.core.model.HIJRI_MIN_YEAR
import com.mk.daftar.core.model.ZakatYear

/*
 * Mappers for the declarations and the tracking row. Reads validate every column and reject the row; writes take the
 * model, which already passed its own constructor.
 */

private const val YEARS = "zakat_years"
private const val TRACKING = "zakat_tracking"

/** The single tracking row's start year, or null when tracking has not started. More than one row is corrupt. */
fun List<ZakatTrackingEntity>.trackingStartYear(): Int? {
    if (size > 1) corrupt(TRACKING, "row")
    val row = firstOrNull() ?: return null
    if (row.id != TRACKING_ROW_ID) corrupt(TRACKING, "id")
    if (row.trackingStartYear !in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR) corrupt(TRACKING, "tracking_start_year")
    return row.trackingStartYear
}

fun ZakatYearEntity.toModel(): ZakatYear = rowGuard(YEARS) {
    ZakatYear(
        hijriYear = hijriYear,
        paid = paid,
        markedAt = optionalInstantOf(YEARS, "marked", markedSeconds, markedNanos),
        overrideDays = overrideDays,
        userAdjusted = userAdjusted,
    )
}

fun ZakatYear.toEntity(): ZakatYearEntity = ZakatYearEntity(
    hijriYear = hijriYear,
    paid = paid,
    markedSeconds = markedAt?.epochSeconds,
    markedNanos = markedAt?.nanosecondsOfSecond,
    overrideDays = overrideDays,
    userAdjusted = userAdjusted,
)

fun startYearRow(startYear: Int): ZakatTrackingEntity = ZakatTrackingEntity(
    id = TRACKING_ROW_ID,
    trackingStartYear = startYear,
)

/** The one row this table ever holds. */
internal const val TRACKING_ROW_ID: Int = 1
