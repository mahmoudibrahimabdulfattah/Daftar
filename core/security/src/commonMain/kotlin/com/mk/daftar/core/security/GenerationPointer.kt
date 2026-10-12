package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

/**
 * The durable record that names the live vault generation. It is replaced as one value, so it is the only place that
 * says which database and which keys are live.
 *
 * - [active] is the generation the app opens. Its database and keys are the only ones a session may use.
 * - [staging] names a generation being built. It is recorded before any of its keys exist, so a crash can find and
 *   erase it.
 * - [previous] names the generation a committed replacement retired. It is erased after the commit is complete.
 * - [preferences] is the imported settings still waiting to be installed. Its text is owned by the data layer.
 */
data class GenerationPointer(
    val active: VaultGeneration,
    val previous: VaultGeneration? = null,
    val staging: VaultGeneration? = null,
    val preferences: String? = null,
) {
    companion object {
        /** No pointer at all means the legacy layout is the vault. */
        val DEFAULT: GenerationPointer = GenerationPointer(VaultGeneration.LEGACY)
    }
}

/** Durable storage for the encoded [GenerationPointer]. [write] replaces the whole value atomically. */
interface GenerationPointerStore {
    /** Null when no pointer was ever written. */
    suspend fun read(): Outcome<ByteArray?, SecurityError>

    suspend fun write(bytes: ByteArray): Outcome<Unit, SecurityError>
}

/**
 * Plain text, one field per line, with a fixed header. Parsing is strict: any unknown field, a bad generation name, a
 * wrong line count or invalid UTF-8 makes the pointer unusable, so a damaged pointer never selects a vault by accident.
 */
internal object GenerationPointerCodec {
    private const val HEADER = "DFTR-GEN v1"
    private const val MAX_BYTES = 1024
    private const val MAX_PREFERENCES = 256
    private const val NONE = "-"
    private const val LINE_COUNT = 5

    fun encode(pointer: GenerationPointer): ByteArray {
        val preferences = pointer.preferences
        require(preferences == null || isValidPreferences(preferences)) { "invalid preferences payload" }
        return buildString {
            append(HEADER).append('\n')
            append("active=").append(pointer.active.id).append('\n')
            append("previous=").append(pointer.previous?.id ?: NONE).append('\n')
            append("staging=").append(pointer.staging?.id ?: NONE).append('\n')
            append("preferences=").append(preferences ?: NONE).append('\n')
        }.encodeToByteArray()
    }

    fun decode(bytes: ByteArray): GenerationPointer? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        val text = bytes.decodeToString()
        // Re-encoding must reproduce the bytes, which rejects invalid UTF-8 that decodeToString would have replaced.
        if (!text.encodeToByteArray().contentEquals(bytes)) return null
        val lines = text.split('\n')
        if (lines.size != LINE_COUNT + 1 || lines.last().isNotEmpty() || lines[0] != HEADER) return null
        val active = VaultGeneration.parse(field(lines[1], "active=") ?: return null) ?: return null
        val previous = (optionalGeneration(field(lines[2], "previous=") ?: return null) ?: return null).generation
        val staging = (optionalGeneration(field(lines[3], "staging=") ?: return null) ?: return null).generation
        val preferencesText = field(lines[4], "preferences=") ?: return null
        val preferences = if (preferencesText == NONE) {
            null
        } else {
            preferencesText.takeIf { isValidPreferences(it) } ?: return null
        }
        val distinct = previous != active && staging != active && (previous == null || previous != staging)
        if (!distinct) return null
        return GenerationPointer(active, previous, staging, preferences)
    }

    private fun field(line: String, key: String): String? = if (line.startsWith(key)) line.removePrefix(key) else null

    /** A generation field: [NONE] is absent, anything else must name a generation. Returns null when invalid. */
    private fun optionalGeneration(text: String): OptionalGeneration? = when (text) {
        NONE -> OptionalGeneration(null)
        else -> VaultGeneration.parse(text)?.let { OptionalGeneration(it) }
    }

    private fun isValidPreferences(text: String): Boolean = text.length in 1..MAX_PREFERENCES && text.all { char ->
        char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '=' || char == ';' || char == '_'
    }
}

private class OptionalGeneration(val generation: VaultGeneration?)
