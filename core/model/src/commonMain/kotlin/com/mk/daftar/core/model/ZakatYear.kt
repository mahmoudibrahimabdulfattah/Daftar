package com.mk.daftar.core.model

import kotlin.time.Instant

/** The first Hijri year the notebook supports. Its Ramadan is the first zakat key. */
const val HIJRI_MIN_YEAR: Int = 1300

/** The last Hijri year the notebook supports. Conversions stop at its last day, 30 Dhu al-Hijjah. */
const val HIJRI_MAX_YEAR: Int = 1600

/** The largest per-year change to a calculated Ramadan date, in days, in either direction. */
const val HIJRI_MAX_OVERRIDE_DAYS: Int = 1

/**
 * The user's annual declaration, keyed by the Hijri year of Ramadan. [paid] is the user's own statement, independent of
 * any payment or estimate. [overrideDays] moves this year's Ramadan date by at most one day; [userAdjusted] records
 * that the user made that change, and is true exactly when the date is adjusted.
 */
data class ZakatYear(
    val hijriYear: Int,
    val paid: Boolean,
    val markedAt: Instant?,
    val overrideDays: Int,
    val userAdjusted: Boolean,
) {
    init {
        require(hijriYear in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR) { "Hijri year is outside the supported range" }
        require(overrideDays in -HIJRI_MAX_OVERRIDE_DAYS..HIJRI_MAX_OVERRIDE_DAYS) {
            "date adjustment is -1, 0 or +1 day"
        }
        require(paid == (markedAt != null)) { "a paid year has a mark time, and only a paid year has one" }
        require(userAdjusted == (overrideDays != 0)) { "provenance follows the date adjustment" }
    }

    companion object {
        /** A new year with the calculated date and no declaration. */
        fun unchecked(hijriYear: Int): ZakatYear = ZakatYear(
            hijriYear = hijriYear,
            paid = false,
            markedAt = null,
            overrideDays = 0,
            userAdjusted = false,
        )
    }
}
