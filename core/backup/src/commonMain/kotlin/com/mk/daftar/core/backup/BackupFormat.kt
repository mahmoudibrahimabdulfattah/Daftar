package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import kotlinx.datetime.LocalDate

/**
 * The version-one `.dftar` container. Every width, order and identifier below is part of the format and is frozen.
 *
 * File layout, one contiguous byte sequence:
 *
 * ```
 * offset  size  field
 *      0     8  magic "DFTRBAK\0" (44 46 54 52 42 41 4B 00)
 *      8     2  format version, u16 big-endian, 1
 *     10     1  KDF identifier, 1 = Argon2id v1.3
 *     11     1  AEAD identifier, 1 = XChaCha20-Poly1305-IETF
 *     12     4  KDF memory, u32 big-endian, KiB, 65536 (64 MiB)
 *     16     4  KDF passes (opslimit), u32 big-endian, 3
 *     20     1  KDF lanes, 1
 *     21     1  derived key length, 32
 *     22     1  salt length, 16
 *     23    16  salt, fresh per export
 *     39     1  nonce length, 24
 *     40    24  nonce, fresh per export
 *     64     4  ciphertext length, u32 big-endian, plaintext length + 16
 *     68     n  ciphertext, XChaCha20-Poly1305 with the 16-byte tag appended
 * ```
 *
 * The first 68 bytes are the associated data of the AEAD, so any header change fails authentication. The ciphertext
 * decrypts to UTF-8 JSON whose schema version is separate from the container version. Nothing about the financial
 * contents appears in the header or the file name.
 */
object BackupFormat {
    const val FILE_EXTENSION: String = "dftar"

    /** The binary MIME type. The storage picker must not guess a text or image type for an encrypted file. */
    const val MIME_TYPE: String = "application/octet-stream"

    const val CONTAINER_VERSION: Int = 1
    const val PAYLOAD_SCHEMA_VERSION: Int = 1

    /** Largest plaintext payload, before encryption, in bytes. Larger exports fail explicitly. */
    const val MAX_PLAINTEXT_BYTES: Int = 16 * 1024 * 1024

    /** Largest number of records across every collection in one export. */
    const val MAX_RECORDS: Int = 50_000

    const val KDF_ARGON2ID13: Int = 1
    const val AEAD_XCHACHA20_POLY1305: Int = 1
    const val MEMORY_KIB: Int = 64 * 1024
    const val PASSES: Int = 3
    const val LANES: Int = 1
    const val KEY_BYTES: Int = 32
    const val SALT_BYTES: Int = 16
    const val NONCE_BYTES: Int = 24
    const val TAG_BYTES: Int = 16
    const val HEADER_BYTES: Int = 68

    val MAGIC: ByteArray = byteArrayOf(0x44, 0x46, 0x54, 0x52, 0x42, 0x41, 0x4B, 0x00)

    /** The name suggested to the picker. It carries only the export date. */
    fun suggestedFileName(date: LocalDate): String = "daftar-$date.$FILE_EXTENSION"
}

/** The parsed fixed header. [ciphertextLength] includes the tag. */
internal class BackupHeader(val salt: ByteArray, val nonce: ByteArray, val ciphertextLength: Int) {
    fun encode(): ByteArray {
        val out = ByteArray(BackupFormat.HEADER_BYTES)
        BackupFormat.MAGIC.copyInto(out, 0)
        putU16(out, 8, BackupFormat.CONTAINER_VERSION)
        out[10] = BackupFormat.KDF_ARGON2ID13.toByte()
        out[11] = BackupFormat.AEAD_XCHACHA20_POLY1305.toByte()
        putU32(out, 12, BackupFormat.MEMORY_KIB.toLong())
        putU32(out, 16, BackupFormat.PASSES.toLong())
        out[20] = BackupFormat.LANES.toByte()
        out[21] = BackupFormat.KEY_BYTES.toByte()
        out[22] = BackupFormat.SALT_BYTES.toByte()
        salt.copyInto(out, 23)
        out[39] = BackupFormat.NONCE_BYTES.toByte()
        nonce.copyInto(out, 40)
        putU32(out, 64, ciphertextLength.toLong())
        return out
    }

    companion object {
        /**
         * Parses and validates the header of a whole file. The length, the allowlisted parameters and the ciphertext
         * bound are checked here, before any key derivation or memory allocation.
         */
        fun decode(file: ByteArray): Outcome<BackupHeader, BackupError> {
            if (file.size < BackupFormat.HEADER_BYTES + BackupFormat.TAG_BYTES) return failure(BackupError.NotABackup)
            if (!file.copyOfRange(0, BackupFormat.MAGIC.size).contentEquals(BackupFormat.MAGIC)) {
                return failure(BackupError.NotABackup)
            }
            val version = readU16(file, 8)
            if (version != BackupFormat.CONTAINER_VERSION) return failure(BackupError.UnsupportedFormat)
            if (file[10].toInt() != BackupFormat.KDF_ARGON2ID13) return failure(BackupError.UnsupportedFormat)
            if (file[11].toInt() != BackupFormat.AEAD_XCHACHA20_POLY1305) return failure(BackupError.UnsupportedFormat)
            val memory = readU32(file, 12)
            val passes = readU32(file, 16)
            if (memory != BackupFormat.MEMORY_KIB.toLong() || passes != BackupFormat.PASSES.toLong()) {
                return failure(BackupError.UnsupportedFormat)
            }
            if (file[20].toInt() != BackupFormat.LANES) return failure(BackupError.UnsupportedFormat)
            if (file[21].toInt() != BackupFormat.KEY_BYTES) return failure(BackupError.UnsupportedFormat)
            if (file[22].toInt() != BackupFormat.SALT_BYTES) return failure(BackupError.NotABackup)
            if (file[39].toInt() != BackupFormat.NONCE_BYTES) return failure(BackupError.NotABackup)
            val ciphertextLength = readU32(file, 64)
            val maxCiphertext = BackupFormat.MAX_PLAINTEXT_BYTES.toLong() + BackupFormat.TAG_BYTES
            if (ciphertextLength < BackupFormat.TAG_BYTES || ciphertextLength > maxCiphertext) {
                return failure(BackupError.NotABackup)
            }
            if (file.size.toLong() !=
                BackupFormat.HEADER_BYTES + ciphertextLength
            ) {
                return failure(BackupError.NotABackup)
            }
            val header = BackupHeader(
                salt = file.copyOfRange(23, 23 + BackupFormat.SALT_BYTES),
                nonce = file.copyOfRange(40, 40 + BackupFormat.NONCE_BYTES),
                ciphertextLength = ciphertextLength.toInt(),
            )
            return Outcome.Success(header)
        }

        private fun failure(error: BackupError): Outcome<BackupHeader, BackupError> = Outcome.Failure(error)
    }
}

private fun putU16(out: ByteArray, offset: Int, value: Int) {
    out[offset] = (value ushr 8).toByte()
    out[offset + 1] = value.toByte()
}

private fun putU32(out: ByteArray, offset: Int, value: Long) {
    out[offset] = (value ushr 24).toByte()
    out[offset + 1] = (value ushr 16).toByte()
    out[offset + 2] = (value ushr 8).toByte()
    out[offset + 3] = value.toByte()
}

private fun readU16(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

private fun readU32(bytes: ByteArray, offset: Int): Long = ((bytes[offset].toLong() and 0xFF) shl 24) or
    ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
    ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
    (bytes[offset + 3].toLong() and 0xFF)
