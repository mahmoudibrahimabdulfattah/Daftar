package com.mk.daftar.core.database.driver

private const val RAW_KEY_PREFIX = "x'"
private const val RAW_KEY_SUFFIX = "'"
private val HEX_DIGITS = "0123456789abcdef".encodeToByteArray()

/**
 * Builds SQLCipher's raw-key literal, `x'<64 lowercase hex digits>'`, as ASCII bytes. A raw key bypasses SQLCipher's
 * passphrase derivation, so the 32 random bytes are used exactly as given. The caller wipes the returned buffer.
 */
internal fun rawKeyLiteral(key: ByteArray): ByteArray {
    require(key.size == RAW_KEY_BYTES) { "database key must be $RAW_KEY_BYTES bytes" }
    val prefix = RAW_KEY_PREFIX.encodeToByteArray()
    val suffix = RAW_KEY_SUFFIX.encodeToByteArray()
    val out = ByteArray(prefix.size + key.size * 2 + suffix.size)
    prefix.copyInto(out)
    var position = prefix.size
    for (byte in key) {
        val value = byte.toInt() and 0xFF
        out[position++] = HEX_DIGITS[value ushr 4]
        out[position++] = HEX_DIGITS[value and 0x0F]
    }
    suffix.copyInto(out, position)
    return out
}
