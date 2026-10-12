package com.mk.daftar.core.cloud

/**
 * SHA-256 (FIPS 180-4) in common code, so the same digest is computed on both platforms. It checks transfer integrity
 * only; authentication of the `.dftar` file is done by its AEAD tag.
 */
object CloudSha256 {
    private val roundConstants = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0x0bf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    private val hexDigits = "0123456789abcdef"

    /** The 32-byte digest of [data]. */
    fun digest(data: ByteArray): ByteArray {
        val state = intArrayOf(
            0x6a09e667,
            -0x4498517b,
            0x3c6ef372,
            -0x5ab00ac6,
            0x510e527f,
            -0x64fa9774,
            0x1f83d9ab,
            0x5be0cd19,
        )
        val bitLength = data.size.toLong() * 8
        val paddedSize = ((data.size + 9 + 63) / 64) * 64
        val padded = ByteArray(paddedSize)
        data.copyInto(padded)
        padded[data.size] = 0x80.toByte()
        for (index in 0 until 8) {
            padded[paddedSize - 1 - index] = (bitLength ushr (8 * index)).toByte()
        }
        val words = IntArray(64)
        for (block in 0 until paddedSize step 64) {
            for (index in 0 until 16) {
                val offset = block + index * 4
                words[index] = ((padded[offset].toInt() and 0xff) shl 24) or
                    ((padded[offset + 1].toInt() and 0xff) shl 16) or
                    ((padded[offset + 2].toInt() and 0xff) shl 8) or
                    (padded[offset + 3].toInt() and 0xff)
            }
            for (index in 16 until 64) {
                val s0 = words[index - 15].rotateRight(7) xor words[index - 15].rotateRight(18) xor
                    (words[index - 15] ushr 3)
                val s1 = words[index - 2].rotateRight(17) xor words[index - 2].rotateRight(19) xor
                    (words[index - 2] ushr 10)
                words[index] = words[index - 16] + s0 + words[index - 7] + s1
            }
            compress(state, words)
        }
        val out = ByteArray(32)
        for (index in 0 until 8) {
            out[index * 4] = (state[index] ushr 24).toByte()
            out[index * 4 + 1] = (state[index] ushr 16).toByte()
            out[index * 4 + 2] = (state[index] ushr 8).toByte()
            out[index * 4 + 3] = state[index].toByte()
        }
        return out
    }

    /** The lowercase hexadecimal digest of [data], as stored in the `sha256` field. */
    fun hexDigest(data: ByteArray): String = hex(digest(data))

    /** Lowercase hexadecimal of [bytes]. */
    fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xff
            out.append(hexDigits[value ushr 4])
            out.append(hexDigits[value and 0x0f])
        }
        return out.toString()
    }

    private fun compress(state: IntArray, words: IntArray) {
        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]
        for (index in 0 until 64) {
            val sum1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choose = (e and f) xor (e.inv() and g)
            val temp1 = h + sum1 + choose + roundConstants[index] + words[index]
            val sum0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = sum0 + majority
            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }
        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }

    private fun Int.rotateRight(bits: Int): Int = (this ushr bits) or (this shl (32 - bits))
}
