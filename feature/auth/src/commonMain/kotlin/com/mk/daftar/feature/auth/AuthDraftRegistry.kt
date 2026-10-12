package com.mk.daftar.feature.auth

/**
 * The live auth screens that hold drafts. Privacy events reach them synchronously through [invalidateAll], so the
 * digits and the candidate PIN are gone before the cover goes up. A screen registers while it is alive and removes its
 * registration when it is cleared.
 */
class AuthDraftRegistry {
    private val invalidators = mutableListOf<() -> Unit>()

    /** Registers [invalidate]. The returned function removes the registration. */
    fun register(invalidate: () -> Unit): () -> Unit {
        invalidators += invalidate
        return { invalidators.remove(invalidate) }
    }

    fun invalidateAll() {
        invalidators.toList().forEach { it() }
    }
}
