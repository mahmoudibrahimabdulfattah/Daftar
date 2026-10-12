package com.mk.daftar.core.ui.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Whether private amounts are shown on the private screens. One instance serves every screen of an open vault
 * generation. It starts visible, is never persisted, and [reset] returns it to visible when the vault locks, so the
 * next generation never inherits a hidden or revealed choice.
 */
class ValuesVisibility {
    private val mutable = MutableStateFlow(true)

    val visible: StateFlow<Boolean> = mutable.asStateFlow()

    fun toggle() {
        mutable.update { !it }
    }

    fun reset() {
        mutable.value = true
    }
}
