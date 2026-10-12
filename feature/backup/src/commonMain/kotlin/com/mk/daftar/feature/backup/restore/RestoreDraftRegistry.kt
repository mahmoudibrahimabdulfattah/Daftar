package com.mk.daftar.feature.backup.restore

/**
 * The live restore wizard, if one is open. A privacy event reaches it synchronously through [invalidateAll], so the
 * password, the decoded review and the PIN drafts are gone before the cover goes up. A wizard registers while it is
 * alive and removes its registration when it is cleared.
 */
class RestoreDraftRegistry {
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
