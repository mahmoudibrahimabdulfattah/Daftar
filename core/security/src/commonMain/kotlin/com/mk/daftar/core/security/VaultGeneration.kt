package com.mk.daftar.core.security

/**
 * One vault generation: the database file, the security records and the platform keys that belong together.
 *
 * The [LEGACY] generation keeps the names the first release wrote, so an existing vault opens in place with no copy
 * and no key rewrap. Every other generation is named by 32 random hex digits, and [name] prefixes each platform item,
 * so its keys can never collide with the active generation's keys.
 */
class VaultGeneration private constructor(val id: String) {
    val isLegacy: Boolean
        get() = id == LEGACY_ID

    /** The name of a platform item for [purpose]. Legacy names are exactly the purpose. */
    fun name(purpose: String): String = if (isLegacy) purpose else "$id.$purpose"

    override fun equals(other: Any?): Boolean = other is VaultGeneration && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = id

    companion object {
        const val LEGACY_ID: String = "legacy"

        val LEGACY: VaultGeneration = VaultGeneration(LEGACY_ID)

        private val FRESH_ID = Regex("g[0-9a-f]{32}")

        /** A new generation from 16 bytes of platform randomness. */
        fun fresh(randomBytes: ByteArray): VaultGeneration {
            require(randomBytes.size == RANDOM_BYTES) { "a generation needs $RANDOM_BYTES random bytes" }
            return VaultGeneration(
                "g" + randomBytes.joinToString("") { byte ->
                    (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
                },
            )
        }

        /** The generation named by [text], or null when the text is not a generation name. */
        fun parse(text: String): VaultGeneration? = when {
            text == LEGACY_ID -> LEGACY
            FRESH_ID.matches(text) -> VaultGeneration(text)
            else -> null
        }

        const val RANDOM_BYTES: Int = 16
    }
}
