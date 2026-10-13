package com.mk.daftar.core.navigation

import androidx.compose.runtime.compositionLocalOf

/**
 * The navigator of the shell, or null where a screen is drawn outside it, such as a screenshot host. Root screens use it
 * only to react to a reselect; they never need it to render.
 */
val LocalNavigatorOrNull = compositionLocalOf<Navigator?> { null }
