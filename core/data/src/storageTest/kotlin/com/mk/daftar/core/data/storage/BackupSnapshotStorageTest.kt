package com.mk.daftar.core.data.storage

import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupSnapshot
import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.backup.VaultBackupSnapshotReader
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.BankAccountInput
import com.mk.daftar.core.domain.repository.BankInput
import com.mk.daftar.core.domain.repository.CashBalanceInput
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.domain.repository.PersonalNoteInput
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.repository.ZakatPaymentInput
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldWeight
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.QuoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * The backup reader over real Room and SQLCipher: one consistent snapshot of every table, with the settings read on
 * their own. A locked vault yields no snapshot at all.
 */
class BackupSnapshotStorageTest {

    private class FixedPreferences(private val prefs: UserPreferences) : UserPreferencesRepository {
        override fun observe(): Flow<PreferencesRead> =
            flowOf(PreferencesRead.Ready(prefs, recoveredFromCorruption = false))

        override suspend fun setThemeMode(mode: ThemeMode): Outcome<Unit, StorageError> = Outcome.Success(Unit)

        override suspend fun setLanguage(language: AppLanguage): Outcome<Unit, StorageError> = Outcome.Success(Unit)

        override suspend fun setInactivityTimeout(timeout: InactivityTimeout): Outcome<Unit, StorageError> =
            Outcome.Success(Unit)

        override suspend fun setBiometricEnabled(enabled: Boolean): Outcome<Unit, StorageError> = Outcome.Success(Unit)
    }

    private fun withVault(block: suspend (StorageFixture) -> Unit) = runTest {
        val location = newTestLocation()
        val fixture = StorageFixture(
            location,
            FakeGold(Outcome.Failure(DataError.Transport)),
            FakeFx(Outcome.Failure(com.mk.daftar.core.network.FxFetchFailure(emptyList()))),
        )
        try {
            block(fixture)
        } finally {
            fixture.engine.lock()
            location.remove()
        }
    }

    private suspend fun StorageFixture.open(byte: Int = 1) = engine.unlock { Outcome.Success(key(byte)) }

    private fun egp(amount: String) = Money(Decimal.parseCanonical(amount), Currency.EGP)

    private fun reader(fixture: StorageFixture) = VaultBackupSnapshotReader(
        host = fixture.host,
        preferences = FixedPreferences(
            UserPreferences(
                themeMode = ThemeMode.DARK,
                language = AppLanguage.EN,
                inactivityTimeout = InactivityTimeout.SECONDS_300,
                biometricEnabled = true,
            ),
        ),
    )

    @Test
    fun aLockedVaultYieldsNoSnapshot() = withVault { f ->
        val result = reader(f).read()
        assertEquals(Outcome.Failure(BackupError.Storage(StorageError.Locked)), result)
    }

    @Test
    fun anOpenVaultYieldsEveryRecordAndTheSettingsOnce() = withVault { f ->
        f.open()
        f.goldRecords.create(
            com.mk.daftar.core.domain.repository.GoldHoldingInput(
                karat = GoldKarat.K21,
                form = GoldForm.BAR,
                weight = GoldWeight(Decimal.parseCanonical("3.25")),
                note = null,
            ),
        ).requireSuccess()
        val bank = f.banks.create(BankInput("Bank Misr")).requireSuccess().value
        f.banks.createAccount(
            BankAccountInput(bankId = bank.id, name = "Current", balance = egp("12345.67"), note = null),
        ).requireSuccess()
        f.cash.create(CashBalanceInput("Wallet", CashKind.WALLET, egp("900"), null)).requireSuccess()
        f.journal.zakat.create(ZakatPaymentInput(LocalDate(2026, 10, 1), egp("250.5"), null)).requireSuccess()
        f.journal.notes.create(PersonalNoteInput("ملاحظة", "نص")).requireSuccess()
        f.prices.setManual(ManualQuoteInput(QuoteKey.UsdEgp, Decimal.parseCanonical("48.5"), null), null)
            .requireSuccess()

        val snapshot: BackupSnapshot = assertIs<Outcome.Success<BackupSnapshot>>(reader(f).read()).data

        assertEquals(1, snapshot.goldHoldings.size)
        assertEquals(1, snapshot.banks.size)
        assertEquals(1, snapshot.bankAccounts.size)
        assertEquals(1, snapshot.cashBalances.size)
        assertEquals(1, snapshot.zakatPayments.size)
        assertEquals(1, snapshot.personalNotes.size)
        assertEquals(0, snapshot.remoteQuotes.size)
        assertEquals(1, snapshot.manualQuotes.size)
        assertEquals(7, snapshot.recordCount)
        assertEquals(ThemeMode.DARK, snapshot.preferences.themeMode)
        assertEquals(AppLanguage.EN, snapshot.preferences.language)
        assertEquals(InactivityTimeout.SECONDS_300, snapshot.preferences.inactivityTimeout)
    }
}
