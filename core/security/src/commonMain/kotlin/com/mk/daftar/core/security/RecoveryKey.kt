@file:OptIn(ExperimentalUnsignedTypes::class)

package com.mk.daftar.core.security

import com.ionspin.kotlin.crypto.box.Box
import com.ionspin.kotlin.crypto.box.BoxCorruptedOrTamperedDataException
import com.ionspin.kotlin.crypto.generichash.GenericHash

/**
 * The owner's recovery key: 18 random bytes and a 2-byte checksum, written as 32 Crockford base32 characters in eight
 * groups of four (`XXXX-XXXX-...`). It is shown once and never stored. Only its X25519 public key is kept, inside the
 * hardware-sealed recovery slot, so the app can seal keys to it without being able to open them.
 *
 * Parsing forgives case, spaces, dashes and the look-alikes O/I/L, and the checksum catches a mistyped character
 * before any key material is derived.
 */
object RecoveryKeyFormat {
    const val SECRET_BYTES = 18
    private const val CHECK_BYTES = 2
    private const val TOTAL_BYTES = SECRET_BYTES + CHECK_BYTES
    const val CHARACTERS = TOTAL_BYTES * 8 / 5
    const val GROUP = 4
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** Formats [secret] with its checksum as eight dash-separated groups. */
    fun format(secret: ByteArray): String {
        require(secret.size == SECRET_BYTES)
        val text = encode(secret + checksum(secret))
        return text.chunked(GROUP).joinToString("-")
    }

    /** The secret bytes of [text], or null when it is not a well-formed key with a matching checksum. */
    fun parse(text: String): ByteArray? {
        val cleaned = buildString {
            for (char in text) {
                when (val upper = char.uppercaseChar()) {
                    ' ', '-', ' ', '⁦', '⁩' -> Unit
                    'O' -> append('0')
                    'I', 'L' -> append('1')
                    else -> append(upper)
                }
            }
        }
        if (cleaned.length != CHARACTERS) return null
        val bytes = decode(cleaned) ?: return null
        val secret = bytes.copyOfRange(0, SECRET_BYTES)
        val check = bytes.copyOfRange(SECRET_BYTES, TOTAL_BYTES)
        bytes.fill(0)
        if (!checksum(secret).contentEquals(check)) {
            secret.fill(0)
            return null
        }
        return secret
    }

    /** Whether [text] is a complete key with a valid checksum. Used to enable the submit action. */
    fun isWellFormed(text: String): Boolean = parse(text)?.also { it.fill(0) } != null

    /**
     * CRC-16/CCITT-FALSE of the secret. It only catches typing mistakes, so it needs no cryptography and runs before
     * libsodium is loaded (the lock screen checks the field as the owner types).
     */
    private fun checksum(secret: ByteArray): ByteArray {
        var crc = 0xFFFF
        for (byte in secret) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return byteArrayOf((crc ushr 8).toByte(), crc.toByte())
    }

    private fun encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        return out.toString()
    }

    private fun decode(text: String): ByteArray? {
        val out = ByteArray(TOTAL_BYTES)
        var buffer = 0
        var bits = 0
        var index = 0
        for (char in text) {
            val value = ALPHABET.indexOf(char)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out[index++] = (buffer shr (bits - 8)).toByte()
                bits -= 8
            }
        }
        return out
    }
}

/** The X25519 key pair of a recovery key. The secret key is wiped by [wipe]. */
internal class RecoveryKeyPair(val publicKey: ByteArray, private val secretKey: ByteArray) {
    /** Opens a box sealed to this key pair, or null when it was sealed to another key or was altered. */
    fun open(sealed: ByteArray): ByteArray? = try {
        Box.sealOpen(sealed.toUnsigned(), publicKey.toUnsigned(), secretKey.toUnsigned()).toSigned()
    } catch (_: BoxCorruptedOrTamperedDataException) {
        null
    }

    fun wipe() {
        secretKey.fill(0)
    }

    companion object {
        /** Derives the key pair of [secret]. The same secret always gives the same pair. */
        fun derive(secret: ByteArray): RecoveryKeyPair {
            val seed = GenericHash.genericHash(secret.toUnsigned(), SEED_BYTES, SEED_KEY.toUnsigned())
            val pair = Box.seedKeypair(seed)
            seed.fill(0u)
            val result = RecoveryKeyPair(pair.publicKey.toSigned(), pair.secretKey.toSigned())
            pair.secretKey.fill(0u)
            return result
        }

        private const val SEED_BYTES = 32
        private val SEED_KEY = "Daftar recovery seed v1".encodeToByteArray()
    }
}

/** Seals [message] so that only the holder of the recovery key behind [publicKey] can open it. */
internal fun sealToRecoveryKey(message: ByteArray, publicKey: ByteArray): ByteArray =
    Box.seal(message.toUnsigned(), publicKey.toUnsigned()).toSigned()
