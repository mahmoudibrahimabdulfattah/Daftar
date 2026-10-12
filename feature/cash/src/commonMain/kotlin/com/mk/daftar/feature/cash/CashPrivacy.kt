package com.mk.daftar.feature.cash

import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether cash screens conceal their values: the shared privacy state, read inverted. */
internal fun ValuesVisibility.hiddenFlow(): Flow<Boolean> = visible.map { !it }

internal fun ValuesVisibility.isHidden(): Boolean = !visible.value

/** Shows the values, the only way out of a concealed state. Showing is always an explicit call. */
internal fun ValuesVisibility.reveal() {
    if (!visible.value) toggle()
}
