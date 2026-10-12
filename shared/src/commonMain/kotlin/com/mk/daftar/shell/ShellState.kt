package com.mk.daftar.shell

import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.lifecycle.ViewModel
import com.mk.daftar.core.navigation.Navigator
import com.mk.daftar.core.navigation.VaultNavigation

/**
 * The in-memory state a shell keeps for one open generation: entry state such as scroll positions, which survives
 * configuration changes but is never written to platform saved state. Clearing it drops every private value.
 */
class ShellRetention {
    /** The values the last registry saved. They are restored into the next registry, never into a Bundle. */
    internal var savedStates: Map<String, List<Any?>>? = null

    internal fun clear() {
        savedStates = null
    }

    /** A registry that keeps values in memory and exports them to this retention when the shell leaves. */
    internal fun newRegistry(): SaveableStateRegistry = SaveableStateRegistry(
        restoredValues = savedStates,
        canBeSaved = { true },
    )
}

/**
 * The generation-owned shell state, held by the activity or view controller so it survives configuration changes.
 * [release] runs when the vault locks: navigation, entry state and the registry all go, and the next generation
 * starts at Home with nothing carried over.
 */
class ShellStateOwner : ViewModel() {
    private val navigation = VaultNavigation()

    internal val retention = ShellRetention()

    fun navigatorFor(generation: Long): Navigator {
        if (navigation.generation != generation) retention.clear()
        return navigation.navigatorFor(generation)
    }

    fun release() {
        navigation.release()
        retention.clear()
    }
}
