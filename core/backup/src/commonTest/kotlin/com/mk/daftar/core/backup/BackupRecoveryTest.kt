package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.RecoveryKeyFormat
import com.mk.daftar.core.security.createPasswordCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * Backups made while a recovery key exists: the version-two container opens with its password or with the recovery
 * key, never with another key, and files made without one keep the frozen version-one layout.
 */
class BackupRecoveryTest {
    private val crypto = createPasswordCrypto()
    private val recoveryKey = RecoveryKeyFormat.format(ByteArray(RecoveryKeyFormat.SECRET_BYTES) { (it * 11).toByte() })
    private val otherKey = RecoveryKeyFormat.format(ByteArray(RecoveryKeyFormat.SECRET_BYTES) { 3 })
    private val password = "correct horse battery"

    private suspend fun withRecovery(): BackupCodec {
        val publicKey = crypto.recoveryPublicKeyOf(recoveryKey)
        return BackupCodec(crypto) { publicKey }
    }

    private suspend fun BackupCodec.export(): ByteArray {
        val encoded = encode(BackupFixtures.snapshot(), password.encodeToByteArray(), BackupFixtures.exportedAt, "1.0")
        return assertIs<Outcome.Success<ByteArray>>(encoded).data
    }

    @Test
    fun aRecoveryBackupUsesVersionTwoAndOpensWithItsPassword() = runTest {
        val file = withRecovery().export()
        assertEquals(2, containerVersionOf(file))
        val decoded = BackupCodec(crypto).decrypt(file, password.encodeToByteArray())
        assertEquals(BackupFixtures.snapshot(), assertIs<Outcome.Success<DecodedBackup>>(decoded).data.snapshot)
    }

    @Test
    fun aForgottenPasswordIsReplacedByTheRecoveryKey() = runTest {
        val file = withRecovery().export()
        val decoded = BackupCodec(crypto).decryptWithRecoveryKey(file, recoveryKey.lowercase())
        assertEquals(BackupFixtures.snapshot(), assertIs<Outcome.Success<DecodedBackup>>(decoded).data.snapshot)
    }

    @Test
    fun anotherKeyOrAWrongPasswordIsRefused() = runTest {
        val file = withRecovery().export()
        assertEquals(
            BackupError.AuthenticationFailed,
            assertIs<Outcome.Failure<BackupError>>(BackupCodec(crypto).decryptWithRecoveryKey(file, otherKey)).error,
        )
        assertEquals(
            BackupError.AuthenticationFailed,
            assertIs<Outcome.Failure<BackupError>>(
                BackupCodec(crypto).decrypt(file, "wrong password here".encodeToByteArray()),
            ).error,
        )
    }

    @Test
    fun aChangedHeaderByteFailsBothWays() = runTest {
        val file = withRecovery().export()
        val tampered = file.copyOf().also { it[150] = (it[150].toInt() xor 1).toByte() }
        assertIs<Outcome.Failure<BackupError>>(BackupCodec(crypto).decryptWithRecoveryKey(tampered, recoveryKey))
        assertIs<Outcome.Failure<BackupError>>(BackupCodec(crypto).decrypt(tampered, password.encodeToByteArray()))
    }

    @Test
    fun withoutARecoveryKeyTheFileStaysVersionOneAndTheKeyCannotOpenIt() = runTest {
        val file = BackupCodec(crypto).export()
        assertEquals(1, containerVersionOf(file))
        assertEquals(
            BackupError.AuthenticationFailed,
            assertIs<Outcome.Failure<BackupError>>(BackupCodec(crypto).decryptWithRecoveryKey(file, recoveryKey)).error,
        )
    }
}
