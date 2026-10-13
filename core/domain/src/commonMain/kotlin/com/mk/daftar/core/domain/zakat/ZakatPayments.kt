package com.mk.daftar.core.domain.zakat

import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.ZakatPayment

/** The exact sum of the payments linked to [hijriYear]. Unassigned payments are never included. */
fun List<ZakatPayment>.linkedTotalEgp(hijriYear: Int): Decimal = filter { it.forHijriYear == hijriYear }
    .fold(Decimal.ZERO) { sum, payment -> sum + payment.amount.amount }

/** Payments with no declared year. They appear in the log but belong to no year's total. */
fun List<ZakatPayment>.unassigned(): List<ZakatPayment> = filter { it.forHijriYear == null }
