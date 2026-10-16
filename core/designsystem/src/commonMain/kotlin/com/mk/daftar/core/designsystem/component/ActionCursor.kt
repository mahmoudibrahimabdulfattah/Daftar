package com.mk.daftar.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * The hand cursor over an enabled action. A disabled one keeps the arrow, so the cursor never promises an action that
 * will not run. Only a pointer shows it; touch screens have no cursor.
 */
fun Modifier.actionCursor(enabled: Boolean = true): Modifier = if (enabled) pointerHoverIcon(PointerIcon.Hand) else this
