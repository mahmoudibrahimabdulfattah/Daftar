package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupFormatTest {

    private fun validHeader(ciphertextLength: Int = 100): ByteArray = BackupHeader(
        salt = ByteArray(16) { it.toByte() },
        nonce = ByteArray(24) { (0x20 + it).toByte() },
        ciphertextLength = ciphertextLength,
    ).encode()

    /** A header with one field replaced, so each check is tested in isolation. */
    private fun withByte(index: Int, value: Int, ciphertextLength: Int = 100): ByteArray {
        val file = validHeader(ciphertextLength) + ByteArray(ciphertextLength)
        file[index] = value.toByte()
        return file
    }

    @Test
    fun headerLayoutMatchesTheFrozenWidths() {
        val header = validHeader()
        assertEquals(BackupFormat.HEADER_BYTES, header.size)
        assertEquals(68, header.size)
        assertContentEquals(byteArrayOf(0x44, 0x46, 0x54, 0x52, 0x42, 0x41, 0x4B, 0x00), header.copyOfRange(0, 8))
        assertEquals(0, header[8].toInt())
        assertEquals(1, header[9].toInt())
        assertEquals(1, header[10].toInt())
        assertEquals(1, header[11].toInt())
        // 64 MiB is 65,536 KiB, big-endian: 00 01 00 00.
        assertContentEquals(byteArrayOf(0, 1, 0, 0), header.copyOfRange(12, 16))
        assertContentEquals(byteArrayOf(0, 0, 0, 3), header.copyOfRange(16, 20))
        assertEquals(16, header[22].toInt())
        assertEquals(24, header[39].toInt())
        // Ciphertext length is 100, big-endian.
        assertContentEquals(byteArrayOf(0, 0, 0, 100), header.copyOfRange(64, 68))
    }

    @Test
    fun wellFormedHeaderParsesBack() {
        val file = validHeader(ciphertextLength = 32) + ByteArray(32)
        val parsed = assertIs<Outcome.Success<BackupHeader>>(BackupHeader.decode(file)).data
        assertEquals(32, parsed.ciphertextLength)
        assertContentEquals(ByteArray(16) { it.toByte() }, parsed.salt)
        assertContentEquals(ByteArray(24) { (0x20 + it).toByte() }, parsed.nonce)
    }

    @Test
    fun wrongMagicIsNotABackup() {
        assertEquals(BackupError.NotABackup, failure(withByte(0, 0x00)))
    }

    @Test
    fun newerContainerVersionIsUnsupported() {
        assertEquals(BackupError.UnsupportedFormat, failure(withByte(9, 2)))
    }

    @Test
    fun unknownKdfOrAeadIsUnsupported() {
        assertEquals(BackupError.UnsupportedFormat, failure(withByte(10, 2)))
        assertEquals(BackupError.UnsupportedFormat, failure(withByte(11, 2)))
    }

    @Test
    fun parametersOutsideTheAllowlistAreUnsupported() {
        // A lower memory cost must never be silently accepted.
        assertEquals(BackupError.UnsupportedFormat, failure(withByte(13, 0)))
        assertEquals(BackupError.UnsupportedFormat, failure(withByte(19, 4)))
    }

    @Test
    fun truncatedOrLengthMismatchedFilesAreNotABackup() {
        val file = validHeader(ciphertextLength = 100) + ByteArray(100)
        assertEquals(BackupError.NotABackup, failure(file.copyOf(40)))
        assertEquals(BackupError.NotABackup, failure(file.copyOf(file.size - 1)))
        assertEquals(BackupError.NotABackup, failure(file + byteArrayOf(0)))
    }

    @Test
    fun ciphertextBoundIsEnforcedBeforeAllocation() {
        val tooLarge = BackupFormat.MAX_PLAINTEXT_BYTES + BackupFormat.TAG_BYTES + 1
        assertEquals(BackupError.NotABackup, failure(validHeader(tooLarge)))
    }

    @Test
    fun suggestedNameCarriesOnlyTheDate() {
        val name = BackupFormat.suggestedFileName(kotlinx.datetime.LocalDate(2026, 10, 12))
        assertEquals("daftar-2026-10-12.dftar", name)
        assertTrue(BackupFormat.MIME_TYPE == "application/octet-stream")
    }

    private fun failure(file: ByteArray): BackupError =
        assertIs<Outcome.Failure<BackupError>>(BackupHeader.decode(file)).error
}
