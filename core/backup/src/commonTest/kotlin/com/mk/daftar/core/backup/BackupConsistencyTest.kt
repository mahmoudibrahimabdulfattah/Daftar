package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.security.createPasswordCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * Cross-record rules: a decoded file that breaks one of them is corrupt as a whole. Each case below encodes a snapshot
 * that the model constructors accept one record at a time, so only the collection-level rule can refuse it.
 */
class BackupConsistencyTest {

    private val codec = BackupCodec(createPasswordCrypto())
    private val password = "correct horse battery".encodeToByteArray()

    private suspend fun refusedAsCorrupt(snapshot: BackupSnapshot) {
        val file = assertIs<Outcome.Success<ByteArray>>(
            codec.encode(snapshot, "correct horse battery".encodeToByteArray(), BackupFixtures.exportedAt, "1.0"),
        ).data
        val error = assertIs<Outcome.Failure<BackupError>>(codec.decode(file, password.copyOf())).error
        assertEquals(BackupError.CorruptPayload, error)
    }

    @Test
    fun aDuplicateGoldHoldingIdIsRefused() = runTest {
        val snapshot = BackupFixtures.snapshot()
        refusedAsCorrupt(snapshot.copy(goldHoldings = snapshot.goldHoldings + snapshot.goldHoldings.first()))
    }

    @Test
    fun aDuplicateBankIdIsRefused() = runTest {
        val snapshot = BackupFixtures.snapshot()
        refusedAsCorrupt(snapshot.copy(banks = snapshot.banks + snapshot.banks.first()))
    }

    @Test
    fun anAccountWhoseBankIsMissingIsAnOrphanAndIsRefused() = runTest {
        val snapshot = BackupFixtures.snapshot()
        val orphan = snapshot.bankAccounts.first().copy(bankId = BankId("bank-missing"))
        refusedAsCorrupt(snapshot.copy(bankAccounts = snapshot.bankAccounts + orphan))
    }

    @Test
    fun aDuplicateManualQuoteKeyIsRefused() = runTest {
        val snapshot = BackupFixtures.snapshot()
        refusedAsCorrupt(snapshot.copy(manualQuotes = snapshot.manualQuotes + snapshot.manualQuotes.first()))
    }

    @Test
    fun aDuplicateRemoteQuoteSlotIsRefused() = runTest {
        val snapshot = BackupFixtures.snapshot()
        refusedAsCorrupt(snapshot.copy(remoteQuotes = snapshot.remoteQuotes + snapshot.remoteQuotes.first()))
    }

    @Test
    fun theFixtureSnapshotIsConsistent() = runTest {
        val snapshot = BackupFixtures.snapshot()
        val file = assertIs<Outcome.Success<ByteArray>>(
            codec.encode(snapshot, "correct horse battery".encodeToByteArray(), BackupFixtures.exportedAt, "1.0"),
        ).data
        assertEquals(
            snapshot,
            assertIs<Outcome.Success<DecodedBackup>>(codec.decode(file, password.copyOf())).data.snapshot,
        )
    }

    @Test
    fun theCanonicalFormDoesNotDependOnListOrder() = runTest {
        val snapshot = BackupFixtures.snapshot()
        val reordered = snapshot.copy(
            goldHoldings = snapshot.goldHoldings.reversed(),
            bankAccounts = snapshot.bankAccounts.reversed(),
            manualQuotes = snapshot.manualQuotes.reversed(),
        )
        assertEquals(snapshot.canonicalForm(), reordered.canonicalForm())
    }
}
