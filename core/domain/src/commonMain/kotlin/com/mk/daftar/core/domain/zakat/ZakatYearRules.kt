package com.mk.daftar.core.domain.zakat

import com.mk.daftar.core.domain.hijri.HijriCalendar
import com.mk.daftar.core.model.HIJRI_MAX_YEAR
import com.mk.daftar.core.model.HIJRI_MIN_YEAR
import com.mk.daftar.core.model.ZakatYear
import kotlinx.datetime.LocalDate

/** The three states of a declared year. [DUE] means awaiting the user's confirmation, never proof of nonpayment. */
enum class ZakatYearStatus { PAID, DUE, UPCOMING }

/**
 * Tracking and status rules for declared years. Pure: every date is passed in, nothing is read from a clock or from
 * storage. Keys are Hijri years of Ramadan; a year is due on the civil day of its adjusted Ramadan, inclusive.
 */
object ZakatYearRules {
    /** Where tracking begins on first use: the most recent supported Ramadan on or before [today], or 1300. */
    fun initialStartYear(today: LocalDate): Int {
        var start = HIJRI_MIN_YEAR
        for (year in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR) {
            val date = HijriCalendar.ramadanStart(year) ?: break
            if (date <= today) start = year else break
        }
        return start
    }

    /**
     * The last year tracking includes for preparation: the next Ramadan year, computed with the stored adjustments, or
     * the last supported year once no supported Ramadan remains.
     */
    fun preparationYear(today: LocalDate, years: List<ZakatYear>): Int =
        HijriCalendar.nextRamadan(today, years.associate { it.hijriYear to it.overrideDays })?.hijriYear
            ?: HIJRI_MAX_YEAR

    /** Years to create when tracking is ensured: missing years from [startYear] through the preparation year. */
    fun yearsToCreate(startYear: Int, today: LocalDate, years: List<ZakatYear>): List<Int> {
        val tracked = years.map { it.hijriYear }.toSet()
        return (startYear..preparationYear(today, years)).filter { it !in tracked }
    }

    /**
     * Whether [year] may be added by hand. Any supported year up to the preparation year is allowed: past years back to
     * 1300, and future years only through the next Ramadan year. Duplicates are refused.
     */
    fun canAddYear(year: Int, today: LocalDate, years: List<ZakatYear>): Boolean =
        year in HIJRI_MIN_YEAR..preparationYear(today, years) && years.none { it.hijriYear == year }

    /** The civil date of [year]'s Ramadan with its adjustment. */
    fun dateOf(year: ZakatYear): LocalDate =
        checkNotNull(HijriCalendar.ramadanStart(year.hijriYear, year.overrideDays)) { "validated year" }

    /** Paid wins; otherwise due on or after the adjusted date, including the date itself; otherwise upcoming. */
    fun statusOf(year: ZakatYear, today: LocalDate): ZakatYearStatus = when {
        year.paid -> ZakatYearStatus.PAID
        today >= dateOf(year) -> ZakatYearStatus.DUE
        else -> ZakatYearStatus.UPCOMING
    }

    /** Unpaid years whose date has arrived, oldest first. Paid years never appear here. */
    fun dueYears(years: List<ZakatYear>, today: LocalDate): List<ZakatYear> =
        years.filter { statusOf(it, today) == ZakatYearStatus.DUE }.sortedBy { it.hijriYear }
}
