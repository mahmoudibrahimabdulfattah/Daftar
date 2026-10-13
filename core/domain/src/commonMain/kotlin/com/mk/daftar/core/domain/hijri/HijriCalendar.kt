package com.mk.daftar.core.domain.hijri

import com.mk.daftar.core.model.HIJRI_MAX_OVERRIDE_DAYS
import com.mk.daftar.core.model.HIJRI_MAX_YEAR
import com.mk.daftar.core.model.HIJRI_MIN_YEAR
import kotlinx.datetime.LocalDate

/**
 * A validated civil Hijri date. The year is one of the supported years, the month is 1..12, and the day fits the
 * month's length in that year. Construct through [HijriDate.createOrNull] when the input is untrusted.
 */
data class HijriDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(year in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR) { "Hijri year is outside the supported range" }
        require(month in 1..12) { "Hijri month is 1..12" }
        require(day in 1..HijriCalendar.monthLength(month, year)) { "Hijri day does not exist in this month" }
    }

    companion object {
        /** The date, or null when any part is outside the supported calendar. Never clamps. */
        fun createOrNull(year: Int, month: Int, day: Int): HijriDate? {
            if (year !in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR || month !in 1..12) return null
            if (day !in 1..HijriCalendar.monthLength(month, year)) return null
            return HijriDate(year, month, day)
        }
    }
}

/** A Ramadan start for one Hijri year, after any per-year adjustment, as a civil Gregorian date. */
data class RamadanDate(val hijriYear: Int, val date: LocalDate)

/**
 * The civil Islamic calendar, type II, Friday epoch. This is plain integer arithmetic with no tables, network or
 * platform calendar, so Android and iOS agree exactly. It is a planning calendar: Egypt confirms Ramadan by sighting,
 * and the civil date may differ by a day. Dates are only converted within [FIRST_DATE] .. [LAST_DATE].
 */
object HijriCalendar {
    /** 1 Muharram 1 AH as a Unix epoch day, 622-07-19 proleptic Gregorian. */
    private const val EPOCH_DAY: Long = -492148L

    /** 1 Muharram 1300 AH, 1882-11-12. */
    val FIRST_DATE: LocalDate = LocalDate(1882, 11, 12)

    /** 30 Dhu al-Hijjah 1600 AH, 2174-11-25. */
    val LAST_DATE: LocalDate = LocalDate(2174, 11, 25)

    /** Whether [year] has 355 days. Leap years are 2, 5, 7, 10, 13, 16, 18, 21, 24, 26 and 29 of each 30-year cycle. */
    fun isLeap(year: Int): Boolean = ((11L * year + 14L) % 30L) < 11L

    /** The length of [month] in [year]: odd months have 30 days, even months 29, except a leap year's twelfth month. */
    fun monthLength(month: Int, year: Int): Int = when {
        month == 12 && isLeap(year) -> 30
        month % 2 == 1 -> 30
        else -> 29
    }

    /** Days from the first of the year to the first of [month]. */
    fun monthOffset(month: Int): Long = (59L * (month - 1) + 1L) / 2L

    /** The epoch day of 1 Muharram of [year]. Valid for any year the arithmetic covers, including 1601 as a sentinel. */
    fun yearStart(year: Int): Long = EPOCH_DAY + 354L * (year - 1) + (3L + 11L * year) / 30L

    /** The epoch day of [date]. */
    fun epochDay(date: HijriDate): Long = yearStart(date.year) + monthOffset(date.month) + date.day - 1

    /**
     * The Hijri date of [epochDay], or null when it lies outside [FIRST_DATE] .. [LAST_DATE]. The year comes from a
     * binary search over the year starts, and the month from a walk of at most twelve month lengths.
     */
    fun fromEpochDay(epochDay: Long): HijriDate? {
        if (epochDay < yearStart(HIJRI_MIN_YEAR) || epochDay >= yearStart(HIJRI_MAX_YEAR + 1)) return null
        var low = HIJRI_MIN_YEAR
        var high = HIJRI_MAX_YEAR
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (yearStart(middle) <= epochDay) low = middle else high = middle - 1
        }
        var remaining = epochDay - yearStart(low)
        for (month in 1..12) {
            val length = monthLength(month, low)
            if (remaining < length) return HijriDate(low, month, remaining.toInt() + 1)
            remaining -= length
        }
        error("a Hijri year has at most 355 days")
    }

    /** The Gregorian date of [date]. Always inside the supported range, because [HijriDate] is validated. */
    fun toLocalDate(date: HijriDate): LocalDate = LocalDate.fromEpochDays(epochDay(date).toInt())

    /** The Hijri date of [date], or null when [date] is outside the supported range. */
    fun fromLocalDate(date: LocalDate): HijriDate? = fromEpochDay(date.toEpochDays())

    /** The calculated Ramadan start of [year] moved by [overrideDays], or null when [year] is unsupported. */
    fun ramadanStart(year: Int, overrideDays: Int = 0): LocalDate? {
        require(overrideDays in -HIJRI_MAX_OVERRIDE_DAYS..HIJRI_MAX_OVERRIDE_DAYS) {
            "date adjustment is -1, 0 or +1 day"
        }
        if (year !in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR) return null
        val day = epochDay(HijriDate(year, 9, 1)) + overrideDays
        return LocalDate.fromEpochDays(day.toInt())
    }

    /**
     * The first supported Ramadan whose adjusted date is on or after [today], or null when every supported Ramadan
     * has passed. [overrideDays] gives each year's adjustment; a missing year means the calculated date.
     */
    fun nextRamadan(today: LocalDate, overrideDays: Map<Int, Int> = emptyMap()): RamadanDate? {
        for (year in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR) {
            val date = ramadanStart(year, overrideDays[year] ?: 0) ?: continue
            if (date >= today) return RamadanDate(year, date)
        }
        return null
    }

    /** Whole civil days from [today] to [target]. Negative when [target] has passed. */
    fun daysUntil(today: LocalDate, target: LocalDate): Long = target.toEpochDays() - today.toEpochDays()
}
