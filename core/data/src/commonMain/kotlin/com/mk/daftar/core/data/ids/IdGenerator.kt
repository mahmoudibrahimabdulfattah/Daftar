package com.mk.daftar.core.data.ids

/** Produces record identifiers. Callers wrap the text in the typed ID for their record. */
fun interface IdGenerator {
    fun nextId(): String
}

/**
 * Random RFC 4122 version 4 identifiers in canonical lowercase form, drawn from the platform's secure random source.
 * [random] is injectable only so tests can check the bit layout with fixed bytes.
 */
class UuidV4Generator(private val random: (Int) -> ByteArray = ::secureRandomBytes) : IdGenerator {
    override fun nextId(): String = formatUuidV4(random(UUID_BYTES))

    internal companion object {
        const val UUID_BYTES = 16

        private val HEX = "0123456789abcdef"

        /** Formats 16 bytes as a version 4, RFC 4122 variant UUID. The input bytes are not modified. */
        fun formatUuidV4(source: ByteArray): String {
            require(source.size == UUID_BYTES) { "a UUID needs $UUID_BYTES bytes" }
            val bytes = source.copyOf()
            bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
            bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
            val out = StringBuilder(36)
            bytes.forEachIndexed { index, byte ->
                if (index == 4 || index == 6 || index == 8 || index == 10) out.append('-')
                val value = byte.toInt() and 0xFF
                out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
            }
            return out.toString()
        }
    }
}

/** Cryptographically secure random bytes from the platform's source. Throws if none is available. */
internal expect fun secureRandomBytes(size: Int): ByteArray
