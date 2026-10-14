package com.mk.daftar.core.ui.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the owner's last hide/show choice is remembered. It holds a flag only, never a value. */
interface ValuesVisibilityStore {
    /** The remembered choice, or null when the store cannot be read; the values then stay as they are. */
    suspend fun hidden(): Boolean?

    suspend fun setHidden(hidden: Boolean)
}

/**
 * Whether private amounts are shown on the private screens. One instance serves every screen of an open vault
 * generation. The owner's last choice is remembered through [store], so hidden values stay hidden after the app is
 * closed and reopened, and [reset] on lock returns to that remembered choice instead of a temporary state.
 */
class ValuesVisibility(private val store: ValuesVisibilityStore? = null, private val scope: CoroutineScope? = null) {
    private val mutable = MutableStateFlow(true)

    private var rememberedVisible = true

    // A toggle made before the store answers wins over the stored choice.
    private var chosen = false

    val visible: StateFlow<Boolean> = mutable.asStateFlow()

    init {
        if (store != null && scope != null) {
            scope.launch {
                val hidden = store.hidden() ?: return@launch
                if (!chosen) {
                    rememberedVisible = !hidden
                    mutable.value = !hidden
                }
            }
        }
    }

    fun toggle() {
        val next = !mutable.value
        chosen = true
        rememberedVisible = next
        mutable.value = next
        if (store != null && scope != null) scope.launch { store.setHidden(!next) }
    }

    fun reset() {
        mutable.value = rememberedVisible
    }
}
