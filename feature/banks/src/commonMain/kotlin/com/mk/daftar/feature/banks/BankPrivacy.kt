package com.mk.daftar.feature.banks

import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether the banks conceal their values: the shared privacy state, read inverted. */
internal fun ValuesVisibility.hiddenFlow(): Flow<Boolean> = visible.map { !it }

internal fun ValuesVisibility.isHidden(): Boolean = !visible.value

/** Shows the values, the only way out of a concealed state. Showing is always an explicit call. */
internal fun ValuesVisibility.reveal() {
    if (!visible.value) toggle()
}
