package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.createPasswordCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * Version-four payloads: broker accounts round-trip with their provider, value date and conversion source, and a file
 * that would count one cash entry twice is rejected whole. The invalid cases are sealed by hand, with the primitives the
 * codec uses, so each one is an authentic file whose content is wrong.
 */
class BackupInvestmentsTest {

    private val crypto = createPasswordCrypto()
    private val codec = BackupCodec(crypto)
    private val password = "correct horse battery"
    private val salt = ByteArray(16) { (it + 1).toByte() }
    private val nonce = ByteArray(24) { (it + 3).toByte() }

    private fun canonicalText(snapshot: BackupSnapshot): String = BackupJson.encodeToString(
        PayloadDocument.serializer(),
        snapshot.toDocument(BackupFixtures.exportedAt, "1.0"),
    )

    private suspend fun sealed(text: String): ByteArray {
        val key = assertIs<Outcome.Success<com.mk.daftar.core.security.SecretBytes>>(
            crypto.deriveKey(password.encodeToByteArray(), salt, BackupFormat.MEMORY_KIB, BackupFormat.PASSES),
        ).data
        val plaintext = text.encodeToByteArray()
        val header = BackupHeader(salt, nonce, plaintext.size + BackupFormat.TAG_BYTES).encode()
        return header + crypto.seal(key, nonce, header, plaintext)
    }

    private suspend fun decodeText(text: String): Outcome<DecodedBackup, BackupError> =
        codec.decode(sealed(text), password.encodeToByteArray())

    @Test
    fun brokerAccountsRoundTripExactly() = runTest {
        val snapshot = BackupFixtures.snapshot()
        val decoded = assertIs<Outcome.Success<DecodedBackup>>(decodeText(canonicalText(snapshot))).data
        assertEquals(snapshot.investmentAccounts, decoded.snapshot.investmentAccounts)
        assertEquals(2, decoded.snapshot.investmentAccounts.size)
    }

    @Test
    fun aConvertedSourceThatIsStillACashEntryIsRejected() = runTest {
        val text = canonicalText(BackupFixtures.snapshot()).replace("\"converted-source-1\"", "\"cash-1\"")
        assertEquals(Outcome.Failure(BackupError.CorruptPayload), decodeText(text))
    }

    @Test
    fun twoAccountsConvertedFromOneSourceAreRejected() = runTest {
        val base = BackupFixtures.snapshot()
        val twice = base.copy(
            investmentAccounts = base.investmentAccounts.map { stored ->
                stored.copy(
                    value = stored.value.copy(
                        convertedFromCashId = com.mk.daftar.core.model.CashBalanceId("converted-source-1"),
                    ),
                )
            },
        )
        assertEquals(Outcome.Failure(BackupError.CorruptPayload), decodeText(canonicalText(twice)))
    }

    @Test
    fun aNegativeAmountIsRejected() = runTest {
        val text = canonicalText(BackupFixtures.snapshot()).replace("\"157000\"", "\"-157000\"")
        assertEquals(Outcome.Failure(BackupError.CorruptPayload), decodeText(text))
    }
}
