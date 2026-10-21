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

    /**
     * The payload schema. Version 2 added the annual declarations, the tracking start and the payment year links;
     * version 3 the bank identity and FX basis; version 4 the investment accounts. Versions 1 to 3 still decode.
     */
    const val PAYLOAD_SCHEMA_VERSION: Int = 4

    /** Largest plaintext payload, before encryption, in bytes. Larger exports fail explicitly. */
    const val MAX_PLAINTEXT_BYTES: Int = 16 * 1024 * 1024

    /** Largest number of records across every collection in one export. */
    const val MAX_RECORDS: Int = 50_000

    const val KDF_ARGON2ID13: Int = 1
    const val AEAD_XCHACHA20_POLY1305: Int = 1

    /** The Standard profile: 64 MiB, three passes. Every export tries it first. */
    const val MEMORY_KIB: Int = 64 * 1024
    const val PASSES: Int = 3

    /** The LowMemory profile: 19 MiB, three passes. Used for an export only after Standard cannot allocate. */
    const val LOW_MEMORY_KIB: Int = 19456

    /**
     * Whether [file] records the Standard profile (64 MiB). False for LowMemory, for any other or unreadable header. Reads
     * only the header bytes, so it never derives a key.
     */
    fun isStandardProfile(file: ByteArray): Boolean =
        file.size >= HEADER_BYTES && readU32(file, 12) == MEMORY_KIB.toLong() && readU32(file, 16) == PASSES.toLong()

    /** The only Argon2id profiles a reader accepts. Anything else is refused before any memory is allocated. */
    fun isReadableProfile(memoryKiB: Long, passes: Long): Boolean =
        passes == PASSES.toLong() && (memoryKiB == MEMORY_KIB.toLong() || memoryKiB == LOW_MEMORY_KIB.toLong())
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
internal class BackupHeader(
    val salt: ByteArray,
    val nonce: ByteArray,
    val ciphertextLength: Int,
    val memoryKiB: Int = BackupFormat.MEMORY_KIB,
    val passes: Int = BackupFormat.PASSES,
) {
    val profile: BackupKdfProfile get() = BackupKdfProfile(memoryKiB, passes)

    fun encode(): ByteArray {
        val out = ByteArray(BackupFormat.HEADER_BYTES)
        BackupFormat.MAGIC.copyInto(out, 0)
        putU16(out, 8, BackupFormat.CONTAINER_VERSION)
        out[10] = BackupFormat.KDF_ARGON2ID13.toByte()
        out[11] = BackupFormat.AEAD_XCHACHA20_POLY1305.toByte()
        putU32(out, 12, memoryKiB.toLong())
        putU32(out, 16, passes.toLong())
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
            if (!BackupFormat.isReadableProfile(memory, passes)) return failure(BackupError.UnsupportedFormat)
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
                memoryKiB = memory.toInt(),
                passes = passes.toInt(),
            )
            return Outcome.Success(header)
        }

        /** Validates only the shared 64-byte prefix (magic to nonce) of [bytes], whatever follows it. */
        fun decodePrefix(bytes: ByteArray): Outcome<BackupHeader, BackupError> {
            if (bytes.size < BackupFormat.HEADER_BYTES) return failure(BackupError.NotABackup)
            if (!bytes.copyOfRange(0, BackupFormat.MAGIC.size).contentEquals(BackupFormat.MAGIC)) {
                return failure(BackupError.NotABackup)
            }
            if (bytes[10].toInt() != BackupFormat.KDF_ARGON2ID13) return failure(BackupError.UnsupportedFormat)
            if (bytes[11].toInt() != BackupFormat.AEAD_XCHACHA20_POLY1305) return failure(BackupError.UnsupportedFormat)
            if (!BackupFormat.isReadableProfile(readU32(bytes, 12), readU32(bytes, 16))) {
                return failure(BackupError.UnsupportedFormat)
            }
            if (bytes[20].toInt() != BackupFormat.LANES) return failure(BackupError.UnsupportedFormat)
            if (bytes[21].toInt() != BackupFormat.KEY_BYTES) return failure(BackupError.UnsupportedFormat)
            if (bytes[22].toInt() != BackupFormat.SALT_BYTES) return failure(BackupError.NotABackup)
            if (bytes[39].toInt() != BackupFormat.NONCE_BYTES) return failure(BackupError.NotABackup)
            return Outcome.Success(
                BackupHeader(
                    salt = bytes.copyOfRange(23, 23 + BackupFormat.SALT_BYTES),
                    nonce = bytes.copyOfRange(40, 40 + BackupFormat.NONCE_BYTES),
                    ciphertextLength = 0,
                    memoryKiB = readU32(bytes, 12).toInt(),
                    passes = readU32(bytes, 16).toInt(),
                ),
            )
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

/**
 * The version-two container, written when the vault has a recovery key. The payload is encrypted under a random backup
 * key; that key is wrapped twice, under the password's Argon2id key and sealed to the recovery public key, so either
 * the password or the recovery key opens the file. Version one stays readable and is still written without a recovery
 * key. Every width below is part of the format and is frozen.
 *
 * ```
 * offset  size  field
 *      0    64  as version one (magic, version = 2, KDF and AEAD identifiers and parameters, salt, payload nonce)
 *     64    24  wrap nonce
 *     88    48  backup key (32) wrapped under the password key, XChaCha20-Poly1305, associated data = bytes 0..64
 *    136     1  recovery slot present, 0 or 1
 *    137    80  backup key sealed to the recovery public key (crypto_box_seal), zeros when absent
 *    217     4  ciphertext length, u32 big-endian, plaintext length + 16
 *    221     n  payload ciphertext under the backup key, associated data = bytes 0..221
 * ```
 */
internal class BackupHeaderV2(
    val salt: ByteArray,
    val nonce: ByteArray,
    val wrapNonce: ByteArray,
    val wrappedKey: ByteArray,
    val recoverySealed: ByteArray?,
    val ciphertextLength: Int,
    val memoryKiB: Int = BackupFormat.MEMORY_KIB,
    val passes: Int = BackupFormat.PASSES,
) {
    val profile: BackupKdfProfile get() = BackupKdfProfile(memoryKiB, passes)

    /** The first 64 bytes: the associated data of the wrapped key. */
    fun prefix(): ByteArray = BackupHeader(salt, nonce, 0, memoryKiB, passes).encode()
        .copyOf(PREFIX_BYTES)
        .also { putU16(it, 8, VERSION) }

    fun encode(): ByteArray {
        val out = ByteArray(HEADER_BYTES)
        prefix().copyInto(out, 0)
        wrapNonce.copyInto(out, 64)
        wrappedKey.copyInto(out, 88)
        if (recoverySealed != null) {
            out[136] = 1
            recoverySealed.copyInto(out, 137)
        }
        putU32(out, 217, ciphertextLength.toLong())
        return out
    }

    companion object {
        const val VERSION = 2
        const val PREFIX_BYTES = 64
        const val HEADER_BYTES = 221
        const val WRAPPED_KEY_BYTES = BackupFormat.KEY_BYTES + BackupFormat.TAG_BYTES
        const val SEALED_KEY_BYTES = BackupFormat.KEY_BYTES + 48

        fun decode(file: ByteArray): Outcome<BackupHeaderV2, BackupError> {
            if (file.size < HEADER_BYTES + BackupFormat.TAG_BYTES) return Outcome.Failure(BackupError.NotABackup)
            // The shared prefix is validated by the version-one rules, read with its own version number.
            val asV1 = file.copyOf(BackupFormat.HEADER_BYTES).also { putU16(it, 8, BackupFormat.CONTAINER_VERSION) }
            putU32(asV1, 64, 0)
            val prefix = when (val parsed = BackupHeader.decodePrefix(asV1)) {
                is Outcome.Failure -> return Outcome.Failure(parsed.error)
                is Outcome.Success -> parsed.data
            }
            val flag = file[136].toInt()
            if (flag != 0 && flag != 1) return Outcome.Failure(BackupError.NotABackup)
            val ciphertextLength = readU32(file, 217)
            val maxCiphertext = BackupFormat.MAX_PLAINTEXT_BYTES.toLong() + BackupFormat.TAG_BYTES
            if (ciphertextLength < BackupFormat.TAG_BYTES || ciphertextLength > maxCiphertext) {
                return Outcome.Failure(BackupError.NotABackup)
            }
            if (file.size.toLong() != HEADER_BYTES + ciphertextLength) return Outcome.Failure(BackupError.NotABackup)
            return Outcome.Success(
                BackupHeaderV2(
                    salt = prefix.salt,
                    nonce = prefix.nonce,
                    wrapNonce = file.copyOfRange(64, 88),
                    wrappedKey = file.copyOfRange(88, 88 + WRAPPED_KEY_BYTES),
                    recoverySealed = if (flag == 1) file.copyOfRange(137, 137 + SEALED_KEY_BYTES) else null,
                    ciphertextLength = ciphertextLength.toInt(),
                    memoryKiB = prefix.memoryKiB,
                    passes = prefix.passes,
                ),
            )
        }
    }
}

/** The container version of a file that starts with the magic, or null when it is not a backup at all. */
internal fun containerVersionOf(file: ByteArray): Int? {
    if (file.size < 10 || !file.copyOfRange(0, BackupFormat.MAGIC.size).contentEquals(BackupFormat.MAGIC)) return null
    return readU16(file, 8)
}

/** An Argon2id profile for a backup: memory in KiB and passes. The lanes, salt and output size are fixed. */
internal data class BackupKdfProfile(val memoryKiB: Int, val passes: Int) {
    companion object {
        /** Every export tries this profile first. */
        val STANDARD = BackupKdfProfile(BackupFormat.MEMORY_KIB, BackupFormat.PASSES)

        /** Used once, and only after Standard fails with an allocation failure. */
        val LOW_MEMORY = BackupKdfProfile(BackupFormat.LOW_MEMORY_KIB, BackupFormat.PASSES)
    }
}
