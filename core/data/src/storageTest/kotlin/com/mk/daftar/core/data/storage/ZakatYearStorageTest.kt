package com.mk.daftar.core.data.storage

import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.repository.ZakatYearRecordRepository
import com.mk.daftar.core.domain.repository.ZakatPaymentInput
import com.mk.daftar.core.domain.repository.ZakatYearRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.network.FxFetchFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * Declarations through the production repository, over real Room and SQLCipher. The mark, the date adjustment and the
 * additions are checked against the committed state after every failed attempt, and payments are shown never to move
 * a declaration.
 */
class ZakatYearStorageTest {

    private val today = LocalDate(2026, 10, 13)

    private fun withVault(block: suspend (StorageFixture, ZakatYearRepository) -> Unit) = runTest {
        val location = newTestLocation()
        val fixture = StorageFixture(
            location,
            FakeGold(Outcome.Failure(DataError.Transport)),
            FakeFx(Outcome.Failure(FxFetchFailure(emptyList()))),
        )
        try {
            block(fixture, ZakatYearRecordRepository(fixture.host))
        } finally {
            fixture.engine.lock()
            location.remove()
        }
    }

    private suspend fun StorageFixture.open(byte: Int = 1) = engine.unlock { Outcome.Success(key(byte)) }

    private fun egp(amount: String) = Money(Decimal.parseCanonical(amount), Currency.EGP)

    private fun <T> failureOf(outcome: Outcome<T, StorageError>): StorageError =
        assertIs<Outcome.Failure<StorageError>>(outcome).error

    @Test
    fun lockedTrackingExposesNothing() = withVault { _, years ->
        assertEquals(VaultRead.Locked, years.observeTracking().first())
    }

    @Test
    fun firstUnlockStartsTrackingAndRepeatedUnlocksChangeNothing() = withVault { f, years ->
        f.open()
        val first = years.ensureTracking(today).requireSuccess()
        assertEquals(1447, first.startYear)
        assertEquals(listOf(1447, 1448), first.years.map { it.hijriYear })
        assertTrue(first.years.none { it.paid })
        assertEquals(first, years.ensureTracking(today).requireSuccess())
    }

    @Test
    fun marksAreStampedClearedAndNeverCreatePayments() = withVault { f, years ->
        f.open()
        years.ensureTracking(today).requireSuccess()
        val marked = years.setPaid(1447, true).requireSuccess()
        assertTrue(marked.paid)
        assertNotNull(marked.markedAt)
        val unmarked = years.setPaid(1447, false).requireSuccess()
        assertNull(unmarked.markedAt)
        assertEquals(StorageError.NotFound, failureOf(years.setPaid(1300, true)))
        assertEquals(emptyList(), f.journal.zakat.observeAll().first().requireReady())
    }

    @Test
    fun adjustmentsKeepTheMarkAndAFailedAdditionKeepsTheCommittedState() = withVault { f, years ->
        f.open()
        years.ensureTracking(today).requireSuccess()
        years.setPaid(1447, true).requireSuccess()

        val adjusted = years.setDateAdjustment(1447, 1).requireSuccess()
        assertEquals(1, adjusted.overrideDays)
        assertTrue(adjusted.userAdjusted)
        assertTrue(adjusted.paid)
        assertEquals(StorageError.InvalidInput, failureOf(years.setDateAdjustment(1447, 2)))

        val restored = years.setDateAdjustment(1447, 0).requireSuccess()
        assertEquals(0, restored.overrideDays)
        assertFalse(restored.userAdjusted)

        assertEquals(StorageError.Conflict, failureOf(years.addYear(1447, today)))
        assertEquals(StorageError.InvalidInput, failureOf(years.addYear(1449, today)))
        assertEquals(StorageError.InvalidInput, failureOf(years.addYear(1299, today)))
        val past = years.addYear(1446, today).requireSuccess()
        assertEquals(1446, past.hijriYear)
        assertFalse(past.paid)

        val state = years.observeTracking().awaitReady { it.years.size == 3 }
        assertEquals(listOf(1446, 1447, 1448), state.years.map { it.hijriYear })
        assertTrue(state.years.single { it.hijriYear == 1447 }.paid)
    }

    @Test
    fun paymentsNeverChangeADeclaration() = withVault { f, years ->
        f.open()
        years.ensureTracking(today).requireSuccess()
        years.setPaid(1447, true).requireSuccess()
        val created = f.journal.zakat.create(
            ZakatPaymentInput(LocalDate(2026, 10, 1), egp("5000"), null, forHijriYear = 1447),
        ).requireSuccess()
        val edited = f.journal.zakat.update(
            created.value.id,
            expectedRevision = 1,
            input = ZakatPaymentInput(LocalDate(2026, 10, 1), egp("7000"), null, forHijriYear = null),
        ).requireSuccess()
        assertEquals(null, edited.value.forHijriYear)
        assertEquals(Outcome.Success(Unit), f.journal.zakat.delete(created.value.id, expectedRevision = 2))

        val state = years.observeTracking().awaitReady { it.years.isNotEmpty() }
        assertTrue(state.years.single { it.hijriYear == 1447 }.paid)
    }

    @Test
    fun declarationsSurviveLockAndUnlock() = withVault { f, years ->
        f.open()
        years.ensureTracking(today).requireSuccess()
        years.setPaid(1447, true).requireSuccess()
        f.engine.lock()
        assertEquals(VaultRead.Locked, years.observeTracking().first())

        f.open()
        val reopened = years.observeTracking().awaitReady { it.years.isNotEmpty() }
        assertEquals(1447, reopened.startYear)
        assertTrue(reopened.years.single { it.hijriYear == 1447 }.paid)
        assertNotNull(reopened.years.single { it.hijriYear == 1447 }.markedAt)
    }
}
