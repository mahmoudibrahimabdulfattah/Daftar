package com.mk.daftar.feature.journal

import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether the journal conceals its values: the shared privacy state, read inverted. */
internal fun ValuesVisibility.hiddenFlow(): Flow<Boolean> = visible.map { !it }

internal fun ValuesVisibility.isHidden(): Boolean = !visible.value

/** Shows the values, the only way out of a concealed state. Showing is always an explicit call. */
internal fun ValuesVisibility.reveal() {
    if (!visible.value) toggle()
}

/** Conceals the values. A no-op when they are already hidden. */
internal fun ValuesVisibility.conceal() {
    if (visible.value) toggle()
}
