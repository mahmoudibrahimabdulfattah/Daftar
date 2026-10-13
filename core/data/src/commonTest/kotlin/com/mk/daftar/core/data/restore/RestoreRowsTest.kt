package com.mk.daftar.core.data.restore

import com.mk.daftar.core.backup.BackupSnapshot
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.ZakatYear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RestoreRowsTest {

    private val created = Instant.parse("2026-10-10T09:00:00Z")

    @Test
    fun everyRecordBecomesOneRowAtTheFirstRevisionInForeignKeyOrder() {
        val bank = Bank(BankId("bank-1"), "Bank", created, created)
        val account = BankAccount(
            id = BankAccountId("account-1"),
            bankId = BankId("bank-1"),
            name = "Current",
            balance = Money(Decimal.parseCanonicalOrNull("10.5")!!, Currency.EGP),
            note = null,
            createdAt = created,
            updatedAt = created,
        )
        val snapshot: BackupSnapshot = emptySnapshot().copy(banks = listOf(bank), bankAccounts = listOf(account))

        val rows = snapshot.toRestoreRows()

        assertEquals(listOf("bank-1"), rows.banks.map { it.id })
        assertEquals(listOf("account-1"), rows.bankAccounts.map { it.id })
        assertEquals(1L, rows.banks.single().revision)
        assertEquals(1L, rows.bankAccounts.single().revision)
        assertEquals(0, rows.goldHoldings.size)
        assertEquals(0, rows.manualQuotes.size)
        assertEquals(ThemeMode.DARK, emptySnapshot().preferences.themeMode)
        assertEquals(AppLanguage.EN, emptySnapshot().preferences.language)
        assertEquals(InactivityTimeout.SECONDS_300, emptySnapshot().preferences.inactivityTimeout)
    }

    @Test
    fun declarationsAndTheTrackingStartBecomeRestoredRows() {
        val marked = Instant.parse("2026-02-20T08:00:00Z")
        val years = listOf(
            ZakatYear(1447, paid = true, markedAt = marked, overrideDays = 0, userAdjusted = false),
            ZakatYear.unchecked(1448),
        )
        val withYears = emptySnapshot().copy(zakatYears = years, zakatTrackingStartYear = 1447)

        val rows = withYears.toRestoreRows()

        assertEquals(listOf(1447, 1448), rows.zakatYears.map { it.hijriYear })
        assertEquals(1447, rows.zakatTracking?.trackingStartYear)
        assertEquals(marked.epochSeconds, rows.zakatYears.first().markedSeconds)
    }

    @Test
    fun aSnapshotWithoutTrackingWritesNoTrackingRow() {
        val rows = emptySnapshot().toRestoreRows()
        assertEquals(emptyList(), rows.zakatYears)
        assertEquals(null, rows.zakatTracking)
    }
}
