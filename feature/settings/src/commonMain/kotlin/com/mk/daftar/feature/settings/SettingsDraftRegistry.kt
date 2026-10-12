package com.mk.daftar.feature.settings

/**
 * The live settings screens that hold PIN drafts. Privacy events reach them synchronously through [invalidateAll], so
 * digits are gone before the cover goes up. A screen registers while it is alive and removes its registration when it is
 * cleared.
 */
class SettingsDraftRegistry {
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
