package com.mk.daftar.core.ui.motion

import androidx.compose.runtime.Composable

/**
 * True when the user has asked the platform to reduce motion. Navigation then changes screens without animating.
 * Read once per composition, so a changed setting takes effect on the next screen change.
 */
@Composable
expect fun rememberReducedMotion(): Boolean
