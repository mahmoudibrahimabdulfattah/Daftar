package com.mk.daftar.core.model

internal const val MAX_NAME_CODE_POINTS = 120
internal const val MAX_NOTE_CODE_POINTS = 4_000
internal const val MAX_NOTE_BODY_CODE_POINTS = 10_000

/** Trims and returns the name, requiring nonblank text of at most [MAX_NAME_CODE_POINTS] code points. */
internal fun requireName(raw: String, field: String): String {
    val trimmed = raw.trim()
    require(trimmed.isNotEmpty()) { "$field must not be blank" }
    require(codePointCount(trimmed) <= MAX_NAME_CODE_POINTS) { "$field exceeds $MAX_NAME_CODE_POINTS code points" }
    return trimmed
}

/** Trims an optional note; blank becomes null. */
internal fun normalizeNote(raw: String?): String? {
    val trimmed = raw?.trim()?.ifEmpty { null } ?: return null
    require(codePointCount(trimmed) <= MAX_NOTE_CODE_POINTS) { "note exceeds $MAX_NOTE_CODE_POINTS code points" }
    return trimmed
}

internal fun requireTrimmed(value: String, field: String) {
    require(value == value.trim()) { "$field must be trimmed" }
}

internal fun codePointCount(text: String): Int = text.count { !it.isLowSurrogate() }

internal fun requireOrdered(createdAt: kotlin.time.Instant, updatedAt: kotlin.time.Instant) {
    require(updatedAt >= createdAt) { "updatedAt must not be before createdAt" }
}
