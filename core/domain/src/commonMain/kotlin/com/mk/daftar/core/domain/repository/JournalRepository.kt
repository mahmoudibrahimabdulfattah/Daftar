package com.mk.daftar.core.domain.repository

import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId
import kotlinx.datetime.LocalDate

/** [forHijriYear] links the payment to a declared year, or leaves it unassigned when null. It never marks the year paid. */
data class ZakatPaymentInput(val paidOn: LocalDate, val amount: Money, val note: String?, val forHijriYear: Int? = null)

data class PersonalNoteInput(val title: String, val body: String)

interface ZakatRepository : RecordRepository<ZakatPaymentId, ZakatPayment, ZakatPaymentInput>

interface NoteRepository : RecordRepository<NoteId, PersonalNote, PersonalNoteInput>

/** The journal: zakat payments and personal notes. Neither counts towards wealth totals. */
interface JournalRepository {
    val zakat: ZakatRepository
    val notes: NoteRepository
}
