package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.LibsodiumPasswordKdf
import com.mk.daftar.core.security.PasswordKdf
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.createPasswordCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The export and restore profile rules. The real libsodium derivation runs for every profile that is allowed to succeed;
 * the recording step only decides which profile is refused with an allocation failure and counts every attempt.
 */
class BackupKdfProfileTest {

    private class RecordingKdf(private val failing: Set<Int> = emptySet()) : PasswordKdf {
        val memories = mutableListOf<Int>()

        override suspend fun derive(
            password: ByteArray,
            salt: ByteArray,
            memoryKiB: Int,
            passes: Int,
        ): Outcome<SecretBytes, SecurityError> {
            memories += memoryKiB
            if (memoryKiB in failing) return Outcome.Failure(SecurityError.InsufficientMemory)
            return LibsodiumPasswordKdf.derive(password, salt, memoryKiB, passes)
        }
    }

    private val password = "correct horse battery"
    private fun pw(): ByteArray = password.encodeToByteArray()

    private suspend fun encodeWith(kdf: RecordingKdf): Outcome<ByteArray, BackupError> =
        BackupCodec(createPasswordCrypto(kdf)).encode(BackupFixtures.snapshot(), pw(), BackupFixtures.exportedAt, "1.0")

    private fun ByteArray.memoryKiB(): Long = readU32(this, 12)

    private fun ByteArray.passes(): Long = readU32(this, 16)

    private fun readU32(bytes: ByteArray, offset: Int): Long = ((bytes[offset].toLong() and 0xFF) shl 24) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
        (bytes[offset + 3].toLong() and 0xFF)

    private fun ByteArray.withMemoryKiB(value: Long): ByteArray = copyOf().also { out ->
        out[12] = (value ushr 24).toByte()
        out[13] = (value ushr 16).toByte()
        out[14] = (value ushr 8).toByte()
        out[15] = value.toByte()
    }

    private fun ByteArray.withPasses(value: Long): ByteArray = copyOf().also { out ->
        out[16] = (value ushr 24).toByte()
        out[17] = (value ushr 16).toByte()
        out[18] = (value ushr 8).toByte()
        out[19] = value.toByte()
    }

    @Test
    fun standardIsTheOnlyAttemptWhenItAllocates() = runTest {
        val kdf = RecordingKdf()
        val file = assertIs<Outcome.Success<ByteArray>>(encodeWith(kdf)).data
        assertEquals(listOf(65536), kdf.memories)
        assertEquals(65536L, file.memoryKiB())
        assertEquals(3L, file.passes())
    }

    @Test
    fun lowMemoryIsWrittenAndReadBackOnlyAfterStandardFails() = runTest {
        val kdf = RecordingKdf(failing = setOf(65536))
        val file = assertIs<Outcome.Success<ByteArray>>(encodeWith(kdf)).data
        assertEquals(listOf(65536, 19456), kdf.memories)
        assertEquals(19456L, file.memoryKiB())
        val decoded = assertIs<Outcome.Success<DecodedBackup>>(
            BackupCodec(createPasswordCrypto()).decode(file, pw()),
        ).data
        assertEquals(BackupFixtures.snapshot(), decoded.snapshot)
    }

    @Test
    fun exportFailsWithoutAFileWhenBothProfilesFail() = runTest {
        val kdf = RecordingKdf(failing = setOf(65536, 19456))
        assertEquals(BackupError.InsufficientMemory, assertIs<Outcome.Failure<BackupError>>(encodeWith(kdf)).error)
        assertEquals(listOf(65536, 19456), kdf.memories)
    }

    @Test
    fun restoreNeverRetriesAStandardFileWithLowMemory() = runTest {
        val file = assertIs<Outcome.Success<ByteArray>>(encodeWith(RecordingKdf())).data
        val kdf = RecordingKdf(failing = setOf(65536))
        val error = assertIs<Outcome.Failure<BackupError>>(
            BackupCodec(createPasswordCrypto(kdf)).decode(file, pw()),
        ).error
        assertEquals(BackupError.InsufficientMemory, error)
        assertEquals(listOf(65536), kdf.memories)
    }

    @Test
    fun headersWithOtherParametersAreRefusedBeforeAnyDerivation() = runTest {
        val file = assertIs<Outcome.Success<ByteArray>>(encodeWith(RecordingKdf())).data
        val cases = listOf(
            file.withMemoryKiB(32768),
            file.withMemoryKiB(19455),
            file.withMemoryKiB(0xFFFF_FFFFL),
            file.withPasses(2),
            file.withPasses(4),
            file.withMemoryKiB(19456).withPasses(4),
        )
        for (tampered in cases) {
            val kdf = RecordingKdf()
            val error = assertIs<Outcome.Failure<BackupError>>(
                BackupCodec(createPasswordCrypto(kdf)).decode(tampered, pw()),
            ).error
            assertEquals(BackupError.UnsupportedFormat, error)
            assertTrue(kdf.memories.isEmpty())
        }
    }

    @Test
    fun aHeaderChangedToAnotherAcceptedProfileFailsAuthentication() = runTest {
        val lowMemory = assertIs<Outcome.Success<ByteArray>>(
            encodeWith(RecordingKdf(failing = setOf(65536))),
        ).data
        // The profile is authenticated: relabelling a LowMemory file as Standard derives with Standard and fails the tag.
        val relabelled = lowMemory.withMemoryKiB(65536)
        val kdf = RecordingKdf()
        val error = assertIs<Outcome.Failure<BackupError>>(
            BackupCodec(createPasswordCrypto(kdf)).decode(relabelled, pw()),
        ).error
        assertEquals(BackupError.AuthenticationFailed, error)
        assertEquals(listOf(65536), kdf.memories)
    }
}
