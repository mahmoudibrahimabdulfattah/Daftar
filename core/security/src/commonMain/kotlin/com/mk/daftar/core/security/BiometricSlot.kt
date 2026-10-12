package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

/** A decoded biometric slot. [aad] is the fixed header, which the platform cipher authenticates. */
internal class BiometricSlot(
    val keyId: ByteArray,
    val vaultId: ByteArray,
    val dekId: ByteArray,
    val aad: ByteArray,
    val sealed: ByteArray,
)

/**
 * Canonical binary encoding of the biometric slot. It wraps the existing random DEK under a platform key that needs
 * biometric authentication for each use. Enrollment and removal never touch the PIN envelope or the database.
 *
 * Header, 55 bytes, which is the associated data of the platform cipher:
 * magic "DFBS" | version 1 | purpose 1 (biometric slot) | cipher 1 (platform) | keyId 16 | vaultId 16 | dekId 16
 *
 * Trailer: sealed length u16 (big-endian) | sealed bytes. The length is outside the associated data, and any change to it
 * fails the strict length check on decode.
 */
internal object BiometricSlotCodec {
    const val VERSION = 1
    const val PURPOSE_BIOMETRIC = 2
    const val CIPHER_PLATFORM = 1
    const val ID_BYTES = 16
    const val DEK_BYTES = 32
    const val AAD_BYTES = 4 + 1 + 1 + 1 + ID_BYTES + ID_BYTES + ID_BYTES
    const val MAX_SEALED_BYTES = 512
    const val MAX_BYTES = AAD_BYTES + 2 + MAX_SEALED_BYTES

    private val MAGIC = byteArrayOf(0x44, 0x46, 0x42, 0x53)

    fun aad(keyId: ByteArray, vaultId: ByteArray, dekId: ByteArray): ByteArray {
        require(keyId.size == ID_BYTES && vaultId.size == ID_BYTES && dekId.size == ID_BYTES)
        val out = ByteArray(AAD_BYTES)
        MAGIC.copyInto(out, 0)
        out[4] = VERSION.toByte()
        out[5] = PURPOSE_BIOMETRIC.toByte()
        out[6] = CIPHER_PLATFORM.toByte()
        keyId.copyInto(out, 7)
        vaultId.copyInto(out, 7 + ID_BYTES)
        dekId.copyInto(out, 7 + 2 * ID_BYTES)
        return out
    }

    fun encode(
        keyId: ByteArray,
        vaultId: ByteArray,
        dekId: ByteArray,
        sealed: ByteArray,
    ): Outcome<ByteArray, BiometricError> {
        if (sealed.isEmpty() || sealed.size > MAX_SEALED_BYTES) return Outcome.Failure(BiometricError.CorruptSlot)
        val header = aad(keyId, vaultId, dekId)
        val length = sealed.size
        return Outcome.Success(
            header + byteArrayOf((length ushr 8).toByte(), length.toByte()) + sealed,
        )
    }

    /** Parses a stored slot. Anything unexpected is [BiometricError.CorruptSlot]; nothing is guessed. */
    fun decode(bytes: ByteArray): Outcome<BiometricSlot, BiometricError> {
        if (bytes.size <= AAD_BYTES + 2 || bytes.size > MAX_BYTES) return Outcome.Failure(BiometricError.CorruptSlot)
        if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC)) return Outcome.Failure(BiometricError.CorruptSlot)
        if (bytes[4].toInt() != VERSION) return Outcome.Failure(BiometricError.CorruptSlot)
        if (bytes[5].toInt() != PURPOSE_BIOMETRIC) return Outcome.Failure(BiometricError.CorruptSlot)
        if (bytes[6].toInt() != CIPHER_PLATFORM) return Outcome.Failure(BiometricError.CorruptSlot)
        val length = ((bytes[AAD_BYTES].toInt() and 0xFF) shl 8) or (bytes[AAD_BYTES + 1].toInt() and 0xFF)
        if (length == 0 || length > MAX_SEALED_BYTES) return Outcome.Failure(BiometricError.CorruptSlot)
        if (bytes.size != AAD_BYTES + 2 + length) return Outcome.Failure(BiometricError.CorruptSlot)
        return Outcome.Success(
            BiometricSlot(
                keyId = bytes.copyOfRange(7, 7 + ID_BYTES),
                vaultId = bytes.copyOfRange(7 + ID_BYTES, 7 + 2 * ID_BYTES),
                dekId = bytes.copyOfRange(7 + 2 * ID_BYTES, AAD_BYTES),
                aad = bytes.copyOfRange(0, AAD_BYTES),
                sealed = bytes.copyOfRange(AAD_BYTES + 2, bytes.size),
            ),
        )
    }
}
