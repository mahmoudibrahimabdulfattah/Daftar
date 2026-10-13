package com.mk.daftar.core.domain.hijri

import com.mk.daftar.core.model.HIJRI_MAX_YEAR
import com.mk.daftar.core.model.HIJRI_MIN_YEAR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class HijriCalendarTest {

    private fun hijri(year: Int, month: Int, day: Int): HijriDate = HijriDate(year, month, day)

    @Test
    fun epochBoundariesMatchTheCivilEpoch() {
        assertEquals(LocalDate(1882, 11, 12), HijriCalendar.toLocalDate(hijri(1300, 1, 1)))
        assertEquals(LocalDate(2174, 11, 25), HijriCalendar.toLocalDate(hijri(1600, 12, 30)))
        assertEquals(HijriCalendar.FIRST_DATE, LocalDate(1882, 11, 12))
        assertEquals(HijriCalendar.LAST_DATE, LocalDate(2174, 11, 25))
        assertEquals(-492148L, HijriCalendar.yearStart(1))
    }

    @Test
    fun ramadanFixturesConvertToTheirGregorianDates() {
        assertEquals(LocalDate(2026, 2, 18), HijriCalendar.ramadanStart(1447))
        assertEquals(LocalDate(2027, 2, 8), HijriCalendar.ramadanStart(1448))
        assertEquals(LocalDate(2028, 1, 28), HijriCalendar.ramadanStart(1449))
    }

    @Test
    fun leapYearsFollowTheThirtyYearCycle() {
        assertTrue(HijriCalendar.isLeap(1600))
        assertTrue(HijriCalendar.isLeap(1447))
        assertFalse(HijriCalendar.isLeap(1448))
        val leapPositions = (1..30).filter { HijriCalendar.isLeap(it) }
        assertEquals(listOf(2, 5, 7, 10, 13, 16, 18, 21, 24, 26, 29), leapPositions)
    }

    @Test
    fun twelfthMonthIsThirtyDaysOnlyInLeapYears() {
        assertEquals(30, HijriCalendar.monthLength(12, 1447))
        assertEquals(29, HijriCalendar.monthLength(12, 1448))
        assertEquals(30, HijriCalendar.monthLength(1, 1448))
        assertEquals(29, HijriCalendar.monthLength(2, 1448))
    }

    @Test
    fun monthOffsetsAccumulateTheMonthLengths() {
        for (year in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR) {
            var running = 0L
            for (month in 1..12) {
                assertEquals(running, HijriCalendar.monthOffset(month), "offset of $month in $year")
                running += HijriCalendar.monthLength(month, year)
            }
        }
    }

    @Test
    fun yearLengthsAreExactlyThreeHundredFiftyFourOrFiveFiveFive() {
        for (year in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR) {
            val length = HijriCalendar.yearStart(year + 1) - HijriCalendar.yearStart(year)
            assertEquals(if (HijriCalendar.isLeap(year)) 355L else 354L, length, "length of $year")
        }
    }

    @Test
    fun leapAndCommonTwelfthMonthsRejectTheDaysTheyDoNotHave() {
        assertNotNull(HijriDate.createOrNull(1447, 12, 30))
        assertNull(HijriDate.createOrNull(1448, 12, 30))
        assertFailsWith<IllegalArgumentException> { HijriDate(1448, 12, 30) }
    }

    @Test
    fun outOfRangeDatesAreRejectedNotClamped() {
        assertNull(HijriDate.createOrNull(1299, 12, 30))
        assertNull(HijriDate.createOrNull(1601, 1, 1))
        assertNull(HijriDate.createOrNull(1447, 13, 1))
        assertNull(HijriDate.createOrNull(1447, 1, 31))
        assertNull(HijriCalendar.fromLocalDate(LocalDate(1882, 11, 11)))
        assertNull(HijriCalendar.fromLocalDate(LocalDate(2174, 11, 26)))
        assertNull(HijriCalendar.ramadanStart(1601))
        assertNull(HijriCalendar.ramadanStart(1299))
    }

    @Test
    fun everySupportedDayRoundTripsExactly() {
        val first = HijriCalendar.FIRST_DATE.toEpochDays()
        val last = HijriCalendar.LAST_DATE.toEpochDays()
        var previous: HijriDate? = null
        for (day in first..last) {
            val date = assertNotNull(HijriCalendar.fromEpochDay(day), "day $day inside the range")
            assertEquals(day, HijriCalendar.epochDay(date), "epoch day of $date")
            val gregorian = LocalDate.fromEpochDays(day.toInt())
            assertEquals(date, HijriCalendar.fromLocalDate(gregorian))
            assertEquals(gregorian, HijriCalendar.toLocalDate(date))
            previous?.let { assertTrue(HijriCalendar.epochDay(it) + 1 == day) }
            previous = date
        }
        assertEquals(HijriCalendar.FIRST_DATE, HijriCalendar.toLocalDate(hijri(1300, 1, 1)))
        assertEquals(hijri(1600, 12, 30), HijriCalendar.fromLocalDate(HijriCalendar.LAST_DATE))
    }

    @Test
    fun nextRamadanOnTheDayItIsReportedAsItself() {
        val onTheDay = assertNotNull(HijriCalendar.nextRamadan(LocalDate(2027, 2, 8)))
        assertEquals(1448, onTheDay.hijriYear)
        val afterward = assertNotNull(HijriCalendar.nextRamadan(LocalDate(2027, 2, 9)))
        assertEquals(1449, afterward.hijriYear)
    }

    @Test
    fun nextRamadanFromTheDesignDateIsOneThousandFourHundredFortyEight() {
        val next = assertNotNull(HijriCalendar.nextRamadan(LocalDate(2026, 10, 13)))
        assertEquals(1448, next.hijriYear)
        assertEquals(LocalDate(2027, 2, 8), next.date)
        assertEquals(118L, HijriCalendar.daysUntil(LocalDate(2026, 10, 13), next.date))
    }

    @Test
    fun overridesShiftNextRamadanByOneDayWithoutChangingTheYearKey() {
        val today = LocalDate(2026, 10, 13)
        val minus = assertNotNull(HijriCalendar.nextRamadan(today, mapOf(1448 to -1)))
        val plus = assertNotNull(HijriCalendar.nextRamadan(today, mapOf(1448 to 1)))
        assertEquals(1448, minus.hijriYear)
        assertEquals(1448, plus.hijriYear)
        assertEquals(117L, HijriCalendar.daysUntil(today, minus.date))
        assertEquals(119L, HijriCalendar.daysUntil(today, plus.date))
    }

    @Test
    fun nextRamadanIsUnavailableOnceEveryRamadanHasPassed() {
        assertNull(HijriCalendar.nextRamadan(LocalDate(2174, 11, 26)))
        assertNull(HijriCalendar.nextRamadan(LocalDate(2174, 11, 25)))
    }

    @Test
    fun ramadanAdjustmentsAreLimitedToOneDay() {
        assertFailsWith<IllegalArgumentException> { HijriCalendar.ramadanStart(1447, 2) }
        assertFailsWith<IllegalArgumentException> { HijriCalendar.ramadanStart(1447, -2) }
    }

    @Test
    fun daysUntilCountsCivilDays() {
        assertEquals(0L, HijriCalendar.daysUntil(LocalDate(2026, 2, 18), LocalDate(2026, 2, 18)))
        assertEquals(-1L, HijriCalendar.daysUntil(LocalDate(2026, 2, 19), LocalDate(2026, 2, 18)))
    }
}
