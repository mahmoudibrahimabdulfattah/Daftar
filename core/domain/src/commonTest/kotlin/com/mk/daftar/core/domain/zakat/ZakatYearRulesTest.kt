package com.mk.daftar.core.domain.zakat

import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId
import com.mk.daftar.core.model.ZakatYear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class ZakatYearRulesTest {

    private val today = LocalDate(2026, 10, 13)
    private val markedAt = Instant.parse("2026-02-20T08:00:00Z")

    private fun unchecked(year: Int) = ZakatYear.unchecked(year)

    private fun paid(year: Int) =
        ZakatYear(year, paid = true, markedAt = markedAt, overrideDays = 0, userAdjusted = false)

    @Test
    fun trackingStartsAtTheMostRecentRamadanOnOrBeforeToday() {
        assertEquals(1447, ZakatYearRules.initialStartYear(today))
        assertEquals(1447, ZakatYearRules.initialStartYear(LocalDate(2026, 2, 18)))
        assertEquals(1446, ZakatYearRules.initialStartYear(LocalDate(2026, 2, 17)))
    }

    @Test
    fun trackingBeforeTheFirstSupportedRamadanStartsAtOneThousandThreeHundred() {
        assertEquals(1300, ZakatYearRules.initialStartYear(LocalDate(1882, 11, 11)))
        assertEquals(1300, ZakatYearRules.initialStartYear(LocalDate(1882, 11, 12)))
    }

    @Test
    fun firstUseCreatesTheStartYearAndTheNextRamadanYearOnly() {
        val start = ZakatYearRules.initialStartYear(today)
        assertEquals(listOf(1447, 1448), ZakatYearRules.yearsToCreate(start, today, emptyList()))
    }

    @Test
    fun existingYearsAreNeverRecreatedOrRemoved() {
        val existing = listOf(paid(1446), unchecked(1447))
        assertEquals(listOf(1448), ZakatYearRules.yearsToCreate(1447, today, existing))
    }

    @Test
    fun dueYearsAreUnpaidYearsWhoseDateHasArrivedOldestFirst() {
        val years = listOf(unchecked(1448), unchecked(1447))
        assertEquals(listOf(1447), ZakatYearRules.dueYears(years, today).map { it.hijriYear })
    }

    @Test
    fun equalityOnTheRamadanDateCountsAsDue() {
        val year = unchecked(1447)
        assertEquals(ZakatYearStatus.UPCOMING, ZakatYearRules.statusOf(year, LocalDate(2026, 2, 17)))
        assertEquals(ZakatYearStatus.DUE, ZakatYearRules.statusOf(year, LocalDate(2026, 2, 18)))
        assertEquals(ZakatYearStatus.DUE, ZakatYearRules.statusOf(year, LocalDate(2026, 2, 19)))
    }

    @Test
    fun aDateAdjustmentMovesTheDueDateAndKeepsTheYearKey() {
        val shifted = ZakatYear(1447, paid = false, markedAt = null, overrideDays = 1, userAdjusted = true)
        assertEquals(ZakatYearStatus.UPCOMING, ZakatYearRules.statusOf(shifted, LocalDate(2026, 2, 18)))
        assertEquals(ZakatYearStatus.DUE, ZakatYearRules.statusOf(shifted, LocalDate(2026, 2, 19)))
    }

    @Test
    fun markingAYearPaidClearsItsDueStateAndUnmarkingRestoresIt() {
        val marked = paid(1447)
        assertEquals(emptyList(), ZakatYearRules.dueYears(listOf(marked), today))
        val unmarked = unchecked(1447)
        assertEquals(listOf(1447), ZakatYearRules.dueYears(listOf(unmarked), today).map { it.hijriYear })
    }

    @Test
    fun payingInAdvanceKeepsTheYearPaidWhenItsDateArrives() {
        val advance = paid(1448)
        assertEquals(ZakatYearStatus.PAID, ZakatYearRules.statusOf(advance, LocalDate(2027, 2, 8)))
        assertEquals(emptyList(), ZakatYearRules.dueYears(listOf(advance), LocalDate(2027, 2, 8)))
    }

    @Test
    fun severalOverdueYearsAreOrderedOldestFirst() {
        val years = listOf(unchecked(1447), unchecked(1445), paid(1446), unchecked(1448))
        assertEquals(listOf(1445, 1447), ZakatYearRules.dueYears(years, today).map { it.hijriYear })
    }

    @Test
    fun calendarAdjustmentNeverClearsAPaidMark() {
        val adjusted = paid(1447).copy(overrideDays = -1, userAdjusted = true)
        assertTrue(adjusted.paid)
        assertEquals(markedAt, adjusted.markedAt)
        assertEquals(ZakatYearStatus.PAID, ZakatYearRules.statusOf(adjusted, today))
    }

    @Test
    fun aNewNextRamadanYearLeavesTheOlderUnpaidYearDue() {
        val later = LocalDate(2027, 2, 9)
        val years = listOf(unchecked(1447), unchecked(1448))
        assertEquals(listOf(1447, 1448), ZakatYearRules.dueYears(years, later).map { it.hijriYear })
        assertEquals(
            listOf(1448),
            ZakatYearRules.dueYears(listOf(paid(1447), unchecked(1448)), later).map {
                it.hijriYear
            },
        )
    }

    @Test
    fun addingAYearIsAllowedUpToTheNextRamadanYearOnly() {
        val years = listOf(unchecked(1447), unchecked(1448))
        assertTrue(ZakatYearRules.canAddYear(1300, today, years))
        assertTrue(ZakatYearRules.canAddYear(1446, today, years))
        assertFalse(ZakatYearRules.canAddYear(1447, today, years), "duplicates are refused")
        assertFalse(ZakatYearRules.canAddYear(1449, today, years), "future years stop at the next Ramadan year")
        assertFalse(ZakatYearRules.canAddYear(1299, today, years))
    }

    @Test
    fun preparationYearStopsAtTheLastSupportedYear() {
        assertEquals(1600, ZakatYearRules.preparationYear(LocalDate(2174, 11, 26), emptyList()))
    }

    @Test
    fun linkedPaymentsSumOnlyTheChosenYear() {
        val created = Instant.parse("2026-10-01T00:00:00Z")
        fun payment(id: String, amount: String, year: Int?) = ZakatPayment.create(
            id = ZakatPaymentId(id),
            paidOn = LocalDate(2026, 10, 1),
            amount = Money(Decimal.parseCanonical(amount), Currency.EGP),
            note = null,
            createdAt = created,
            updatedAt = created,
            forHijriYear = year,
        )
        val payments =
            listOf(
                payment("a", "5000", 1447),
                payment("b", "10000", 1447),
                payment("c", "300", null),
                payment("d", "7", 1448),
            )
        assertEquals(Decimal.parseCanonical("15000"), payments.linkedTotalEgp(1447))
        assertEquals(listOf("c"), payments.unassigned().map { it.id.value })
    }

    @Test
    fun theCairoDayFollowsTheCivilDateNotTheDeviceZone() {
        assertEquals(LocalDate(2026, 10, 13), zakatToday(Instant.parse("2026-10-12T21:30:00Z")))
        assertEquals(LocalDate(2026, 10, 12), zakatToday(Instant.parse("2026-10-12T20:59:00Z")))
    }

    @Test
    fun declarationsRejectInvalidStateAtConstruction() {
        assertFailsWith<IllegalArgumentException> {
            ZakatYear(1447, paid = true, markedAt = null, overrideDays = 0, userAdjusted = false)
        }
        assertFailsWith<IllegalArgumentException> {
            ZakatYear(1447, paid = false, markedAt = markedAt, overrideDays = 0, userAdjusted = false)
        }
        assertFailsWith<IllegalArgumentException> {
            ZakatYear(1447, paid = false, markedAt = null, overrideDays = 2, userAdjusted = true)
        }
        assertFailsWith<IllegalArgumentException> {
            ZakatYear(1601, paid = false, markedAt = null, overrideDays = 0, userAdjusted = false)
        }
        assertFailsWith<IllegalArgumentException> {
            ZakatYear(1447, paid = false, markedAt = null, overrideDays = 1, userAdjusted = false)
        }
    }
}
