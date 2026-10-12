package com.mk.daftar.feature.journal

import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.ui.presentation.moneyText
import com.mk.daftar.feature.journal.testing.payment
import com.mk.daftar.feature.journal.zakat.groupByYear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class JournalFormatTest {

    @Test
    fun moneyIsGroupedWithAtLeastTwoDecimalsAndNothingIsRounded() {
        assertEquals("15,000.00", (Decimal.parseCanonical("15000")).moneyText())
        assertEquals("10,000.50", (Decimal.parseCanonical("10000.5")).moneyText())
        assertEquals("0.00", (Decimal.ZERO).moneyText())
        assertEquals("1,234,567.25", (Decimal.parseCanonical("1234567.25")).moneyText())
        assertEquals("999.99", (Decimal.parseCanonical("999.99")).moneyText())
    }

    @Test
    fun isoDatesRoundTripAndAcceptArabicIndicDigits() {
        assertEquals("2026-03-20", LocalDate(2026, 3, 20).isoText())
        assertEquals(LocalDate(2026, 3, 20), parseIsoDate("2026-03-20"))
        assertEquals(LocalDate(2026, 3, 20), parseIsoDate("٢٠٢٦-٠٣-٢٠"))
        assertEquals(LocalDate(2026, 3, 20), parseIsoDate("  ۲۰۲۶-۰۳-۲۰  "))
    }

    @Test
    fun aDateIsNeverReadAsADecimalNumberOrAsAnImpossibleDay() {
        assertNull(parseIsoDate("2026.03.20"))
        assertNull(parseIsoDate("2026-3-20"))
        assertNull(parseIsoDate("2026-02-29"))
        assertEquals(LocalDate(2028, 2, 29), parseIsoDate("2028-02-29"))
        assertNull(parseIsoDate("2026-04-31"))
        assertNull(parseIsoDate("2026-00-10"))
    }

    @Test
    fun paymentsGroupByCalendarYearInOrderWithExactSums() {
        val groups = groupByYear(
            listOf(
                payment("a", LocalDate(2026, 3, 20), "10000"),
                payment("b", LocalDate(2026, 1, 15), "5000"),
                payment("c", LocalDate(2025, 12, 31), "0.1"),
            ),
        )
        assertEquals(listOf(2026, 2025), groups.map { it.year })
        assertEquals("15000", groups[0].total.toPlainString())
        assertEquals("0.1", groups[1].total.toPlainString())
        assertEquals(listOf("a", "b"), groups[0].payments.map { it.id.value })
    }
}
