package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

/**
 * Argon2id v1.3 parameters persisted with the envelope. The allowlist is checked before any memory is allocated.
 *
 * Standard is the calibrated 64 MiB profile, passes 3..6, chosen at creation. LowMemory (19 MiB, passes 3) is used
 * for a new vault only after Standard fails with an allocation failure. Both are the profiles new writes may use.
 * Reads accept exactly the same set, so a stored envelope is never rejected for a profile this build can write.
 */
internal object KdfPolicy {
    /** 64 MiB, the Standard memory cost stored in the envelope as KiB. */
    const val MEMORY_KIB = 64 * 1024

    /** 19 MiB, the LowMemory fallback used only after a Standard allocation failure. */
    const val LOW_MEMORY_KIB = 19456
    const val MIN_PASSES = 3
    const val MAX_PASSES = 6
    const val LOW_MEMORY_PASSES = 3

    /** libsodium's Argon2id implementation is single-lane. */
    const val LANES = 1
    const val SALT_BYTES = 16
    const val OUTPUT_BYTES = 32

    /** Profiles a new write may use: Standard 64 MiB at passes 3..6, or LowMemory at passes 3. */
    fun allowsNewWrite(params: KdfParams): Boolean = allows(params)

    /** Profiles a stored record may carry; anything else is rejected before allocating. */
    fun allows(params: KdfParams): Boolean {
        if (params.lanes != LANES) return false
        return when (params.memoryKiB) {
            MEMORY_KIB -> params.passes in MIN_PASSES..MAX_PASSES
            LOW_MEMORY_KIB -> params.passes == LOW_MEMORY_PASSES
            else -> false
        }
    }
}

internal data class KdfParams(val memoryKiB: Int, val passes: Int, val lanes: Int)

/** Clear-text fields of the PIN slot. They are authenticated as associated data and never encrypted. */
internal class EnvelopeHeader(
    val generation: Long,
    val vaultId: ByteArray,
    val dekId: ByteArray,
    val kdf: KdfParams,
    val salt: ByteArray,
)

internal class ParsedEnvelope(
    val header: EnvelopeHeader,
    val headerBytes: ByteArray,
    val nonce: ByteArray,
    val inner: ByteArray,
)

/**
 * Canonical binary encoding of the PIN envelope. The field order, fixed lengths and big-endian integers leave
 * exactly one encoding per value, so tampering with any field changes the associated data and fails the AEAD.
 *
 * Header, 69 bytes, which is the associated data of the inner AEAD:
 * magic "DFSE" | version 1 | purpose 1 (PIN slot) | aead 1 (XChaCha20-Poly1305-IETF) | kdf 1 (Argon2id v1.3) |
 * generation u32 | vaultId 16 | dekId 16 | memoryKiB u32 | passes u8 | lanes u8 | saltLength u8 (16) | salt 16 |
 * keyLength u8 (32) | nonceLength u8 (24)
 *
 * Plain envelope, 141 bytes, which is sealed by the platform: header | nonce 24 | inner (DEK 32 + tag 16).
 */
internal object EnvelopeCodec {
    /** Upper bound on a sealed record read from storage, checked before any platform call. */
    const val MAX_SEALED_BYTES = 1024
    const val HEADER_BYTES = 69
    const val NONCE_BYTES = 24
    const val INNER_BYTES = 32 + 16
    const val PLAIN_BYTES = HEADER_BYTES + NONCE_BYTES + INNER_BYTES
    const val ID_BYTES = 16
    const val GENERATION_MAX = 0xFFFF_FFFFL

    private const val VERSION = 1
    private const val PURPOSE_PIN = 1
    private const val AEAD_XCHACHA20_POLY1305 = 1
    private const val KDF_ARGON2ID13 = 1
    private val MAGIC = byteArrayOf(0x44, 0x46, 0x53, 0x45)

    fun encodeHeader(header: EnvelopeHeader): ByteArray {
        require(header.generation in 1..GENERATION_MAX)
        require(header.vaultId.size == ID_BYTES && header.dekId.size == ID_BYTES)
        require(header.salt.size == KdfPolicy.SALT_BYTES)
        val out = ByteArray(HEADER_BYTES)
        MAGIC.copyInto(out, 0)
        out[4] = VERSION.toByte()
        out[5] = PURPOSE_PIN.toByte()
        out[6] = AEAD_XCHACHA20_POLY1305.toByte()
        out[7] = KDF_ARGON2ID13.toByte()
        putU32(out, 8, header.generation)
        header.vaultId.copyInto(out, 12)
        header.dekId.copyInto(out, 28)
        putU32(out, 44, header.kdf.memoryKiB.toLong())
        out[48] = header.kdf.passes.toByte()
        out[49] = header.kdf.lanes.toByte()
        out[50] = KdfPolicy.SALT_BYTES.toByte()
        header.salt.copyInto(out, 51)
        out[67] = KdfPolicy.OUTPUT_BYTES.toByte()
        out[68] = NONCE_BYTES.toByte()
        return out
    }

    fun encode(headerBytes: ByteArray, nonce: ByteArray, inner: ByteArray): ByteArray {
        require(headerBytes.size == HEADER_BYTES && nonce.size == NONCE_BYTES && inner.size == INNER_BYTES)
        return headerBytes + nonce + inner
    }

    /** Parses and validates a plain envelope. Unknown versions and parameters outside the allowlist are rejected. */
    fun decode(bytes: ByteArray): Outcome<ParsedEnvelope, SecurityError> {
        if (bytes.size != PLAIN_BYTES) return Outcome.Failure(SecurityError.CorruptState)
        if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC)) return Outcome.Failure(SecurityError.CorruptState)
        if (bytes[4].toInt() != VERSION) return Outcome.Failure(SecurityError.UnsupportedFormat)
        if (bytes[5].toInt() != PURPOSE_PIN) return Outcome.Failure(SecurityError.UnsupportedFormat)
        if (bytes[6].toInt() != AEAD_XCHACHA20_POLY1305) return Outcome.Failure(SecurityError.UnsupportedFormat)
        if (bytes[7].toInt() != KDF_ARGON2ID13) return Outcome.Failure(SecurityError.UnsupportedFormat)
        val generation = readU32(bytes, 8)
        if (generation == 0L) return Outcome.Failure(SecurityError.CorruptState)
        val kdf = KdfParams(
            memoryKiB = readU32(bytes, 44).toInt(),
            passes = bytes[48].toInt() and 0xFF,
            lanes = bytes[49].toInt() and 0xFF,
        )
        if (bytes[50].toInt() != KdfPolicy.SALT_BYTES) return Outcome.Failure(SecurityError.CorruptState)
        if (bytes[67].toInt() != KdfPolicy.OUTPUT_BYTES) return Outcome.Failure(SecurityError.CorruptState)
        if (bytes[68].toInt() != NONCE_BYTES) return Outcome.Failure(SecurityError.CorruptState)
        if (!KdfPolicy.allows(kdf)) return Outcome.Failure(SecurityError.UnsupportedFormat)
        val header = EnvelopeHeader(
            generation = generation,
            vaultId = bytes.copyOfRange(12, 28),
            dekId = bytes.copyOfRange(28, 44),
            kdf = kdf,
            salt = bytes.copyOfRange(51, 67),
        )
        return Outcome.Success(
            ParsedEnvelope(
                header = header,
                headerBytes = bytes.copyOfRange(0, HEADER_BYTES),
                nonce = bytes.copyOfRange(HEADER_BYTES, HEADER_BYTES + NONCE_BYTES),
                inner = bytes.copyOfRange(HEADER_BYTES + NONCE_BYTES, PLAIN_BYTES),
            ),
        )
    }

    private fun putU32(out: ByteArray, offset: Int, value: Long) {
        out[offset] = (value ushr 24).toByte()
        out[offset + 1] = (value ushr 16).toByte()
        out[offset + 2] = (value ushr 8).toByte()
        out[offset + 3] = value.toByte()
    }

    private fun readU32(bytes: ByteArray, offset: Int): Long = ((bytes[offset].toLong() and 0xFF) shl 24) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
        (bytes[offset + 3].toLong() and 0xFF)
}
