package com.mk.daftar.core.navigation

/**
 * Holds the navigator of the one live vault generation. Navigation state exists only while a generation is open:
 * asking for a different generation disposes the previous navigator, and [release] disposes it when the vault
 * locks. Owners keep this object across configuration changes, which is what preserves tab positions within a
 * generation, and drop it when the session closes.
 */
class VaultNavigation {
    private var active: Pair<Long, Navigator>? = null

    /** The generation whose navigator is live, or null after [release]. */
    val generation: Long? get() = active?.first

    /** The navigator for [generation], created at Home for a new generation and reused within it. */
    fun navigatorFor(generation: Long): Navigator {
        active?.let { (owned, navigator) ->
            if (owned == generation) return navigator
            navigator.dispose()
        }
        return Navigator().also { active = generation to it }
    }

    /** Disposes the live navigator, if any. The next generation starts again at Home. */
    fun release() {
        active?.second?.dispose()
        active = null
    }
}
